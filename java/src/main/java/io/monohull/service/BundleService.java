package io.monohull.service;

import io.monohull.dto.ImageConfigBundle;
import io.monohull.dto.ImageConfigBundle.CustomActionPayload;
import io.monohull.dto.ImageConfigBundle.ImageConfigPayload;
import io.monohull.dto.ImageConfigBundle.PipelinePayload;
import io.monohull.dto.ImageConfigBundle.StepPayload;
import io.monohull.dto.BundleImportResult;
import io.monohull.entity.CustomActionEntity;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.entity.PipelineDefinitionEntity;
import io.monohull.entity.PipelineStepEntity;
import io.monohull.repository.CustomActionRepository;
import io.monohull.repository.ImageConfigRepository;
import io.monohull.repository.PipelineDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class BundleService {

    private final ImageConfigRepository imageConfigRepo;
    private final PipelineDefinitionRepository pipelineRepo;
    private final CustomActionRepository customActionRepo;

    public BundleService(ImageConfigRepository imageConfigRepo,
                         PipelineDefinitionRepository pipelineRepo,
                         CustomActionRepository customActionRepo) {
        this.imageConfigRepo = imageConfigRepo;
        this.pipelineRepo = pipelineRepo;
        this.customActionRepo = customActionRepo;
    }

    // ----- export -----

    @Transactional(readOnly = true)
    public ImageConfigBundle export(Long imageConfigId) {
        ImageConfigEntity ic = imageConfigRepo.findById(imageConfigId)
            .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + imageConfigId));

        ImageConfigPayload icPayload = new ImageConfigPayload(
            ic.getClient(), ic.getProject(), ic.getMaximoVersion(),
            ic.getAppImage(), ic.getDbImage(), ic.getAdmImage(),
            ic.getDbVendor(), ic.getDbName(), ic.getDbContainerPort(),
            ic.getHostVolumePath(), ic.getDbVolumeName(), ic.getWorkspacePath(),
            ic.getAppHttpPort(), ic.getAppHttpsPort(), ic.getDbPort(),
            ic.getMockHostPort(), ic.getSmtpHostPort(), ic.getSmtpUiHostPort(),
            ic.getDbExtraEnv(), ic.getDbExtraBinds(),
            ic.getAppExtraEnv(), ic.getAppExtraBinds(),
            ic.getAdmExtraEnv(), ic.getAdmExtraBinds());

        PipelinePayload pipelinePayload = null;
        List<CustomActionPayload> actionPayloads = List.of();

        PipelineDefinitionEntity pipeline = ic.getPipelineDefinition();
        if (pipeline != null) {
            // Re-fetch with steps eagerly loaded to avoid lazy-init off-session.
            PipelineDefinitionEntity loaded = pipelineRepo.findByIdWithSteps(pipeline.getId())
                .orElse(pipeline);

            List<StepPayload> stepPayloads = loaded.getSteps().stream()
                .sorted(Comparator.comparingInt(PipelineStepEntity::getSequenceOrder))
                .map(s -> new StepPayload(s.getActionKey(), s.getSequenceOrder()))
                .toList();
            pipelinePayload = new PipelinePayload(loaded.getName(), loaded.getDescription(), stepPayloads);

            // Every action the pipeline references travels in the bundle — including
            // built-ins. The destination may be a fresh Monohull that hasn't loaded
            // application.yml yet, or may be running a different application.yml
            // catalog entirely. The import side defers to the destination's copy when
            // one already exists, so shipping the built-in body is harmless and makes
            // the bundle self-contained.
            actionPayloads = stepPayloads.stream()
                .map(StepPayload::actionKey)
                .distinct()
                .flatMap(key -> customActionRepo.findByActionKey(key).stream())
                .map(this::toActionPayload)
                .toList();
        }

        // Launch defaults travel in the bundle so a shared profile is one-click on the
        // destination. Booleans are only written when true to keep the YAML tidy.
        ImageConfigBundle.LaunchPayload launchPayload = new ImageConfigBundle.LaunchPayload(
            ic.getLaunchDescription(),
            ic.isLaunchStaticPorts() ? Boolean.TRUE : null,
            ic.isLaunchIncludeMock() ? Boolean.TRUE : null,
            ic.isLaunchIncludeSmtp() ? Boolean.TRUE : null);

        return new ImageConfigBundle(
            ImageConfigBundle.KIND,
            ImageConfigBundle.SCHEMA_VERSION,
            icPayload,
            pipelinePayload,
            actionPayloads,
            launchPayload);
    }

    private CustomActionPayload toActionPayload(CustomActionEntity a) {
        return new CustomActionPayload(
            a.getActionKey(), a.getName(), a.getDescription(), a.getTargetRole(),
            a.getCommand(), a.getWorkingDir(), a.getTimeoutSeconds(),
            a.getAfterAction(), a.isAutoRun(), a.getExecutionType(),
            a.getAllowedExitCodes(), a.getRunAsUser(), a.isVerbose(), a.isBuiltIn());
    }

    // ----- import -----

    @Transactional
    public BundleImportResult importBundle(ImageConfigBundle bundle, boolean overwrite) {
        validateShape(bundle);

        ImageConfigPayload ic = bundle.imageConfig();
        PipelinePayload pipeline = bundle.pipeline();
        List<CustomActionPayload> actions = bundle.customActions() != null
            ? bundle.customActions() : List.of();

        validateActionKeyResolution(pipeline, actions);

        // -- conflict detection --
        Optional<ImageConfigEntity> existingIc = imageConfigRepo
            .findByClientAndProjectAndMaximoVersion(ic.client(), ic.project(), ic.maximoVersion());
        Optional<PipelineDefinitionEntity> existingPipeline = pipeline != null
            ? pipelineRepo.findByName(pipeline.name())
            : Optional.empty();

        List<CustomActionEntity> existingActions = new ArrayList<>();
        for (CustomActionPayload p : actions) {
            customActionRepo.findByActionKey(p.actionKey()).ifPresent(existingActions::add);
        }

        if (!overwrite) {
            List<String> conflicts = new ArrayList<>();
            existingIc.ifPresent(e -> conflicts.add(
                "image config: " + e.getClient() + " / " + e.getProject() + " / " + e.getMaximoVersion()));
            existingPipeline.ifPresent(e -> conflicts.add("pipeline: " + e.getName()));
            // Existing custom (non-built-in) actions are real conflicts. Built-ins on
            // the destination are authoritative — we never overwrite them — so they're
            // not surfaced as conflicts even when overwrite=false.
            for (CustomActionEntity ea : existingActions) {
                if (!ea.isBuiltIn()) {
                    conflicts.add("custom action: " + ea.getActionKey());
                }
            }
            if (!conflicts.isEmpty()) {
                throw new BundleConflictException(conflicts);
            }
        }

        // -- upsert actions referenced by the pipeline --
        //   - destination has the action AND it's built-in  → leave alone (built-ins
        //     are owned by application.yml on the destination).
        //   - destination has the action AND it's custom    → update body, preserve
        //     the custom flag (overwrite=true gates conflict detection above).
        //   - destination has no match                      → insert, preserving the
        //     bundle's builtIn flag (so a fresh Monohull without its own seeding still
        //     resolves the action).
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (CustomActionPayload p : actions) {
            Optional<CustomActionEntity> match = customActionRepo.findByActionKey(p.actionKey());
            if (match.isPresent() && match.get().isBuiltIn()) {
                skipped.add(p.actionKey());
                continue;
            }
            CustomActionEntity entity = match.orElseGet(CustomActionEntity::new);
            boolean isNew = entity.getId() == null;
            entity.setActionKey(p.actionKey());
            entity.setName(p.name());
            entity.setDescription(p.description());
            entity.setTargetRole(p.targetRole());
            entity.setCommand(p.command());
            entity.setWorkingDir(p.workingDir());
            entity.setTimeoutSeconds(p.timeoutSeconds() != null ? p.timeoutSeconds() : 300);
            entity.setAfterAction(p.afterAction());
            entity.setAutoRun(Boolean.TRUE.equals(p.autoRun()));
            entity.setExecutionType(p.executionType() != null ? p.executionType() : "EXEC");
            entity.setAllowedExitCodes(p.allowedExitCodes());
            entity.setRunAsUser(p.runAsUser());
            entity.setVerbose(Boolean.TRUE.equals(p.verbose()));
            // Imported bundles always produce GLOBAL actions (template-scope).
            // Per-env or per-image-config scoping is intentionally not part of the bundle.
            // Preserve the source's builtIn flag so a bundle from a Monohull with built-in
            // seeding still imports as built-in on a fresh Monohull without it.
            entity.setBuiltIn(Boolean.TRUE.equals(p.builtIn()));
            entity.setImageConfig(null);
            entity.setEnvironment(null);
            customActionRepo.save(entity);
            (isNew ? created : updated).add(p.actionKey());
        }

        // -- upsert pipeline (and replace its steps) --
        PipelineDefinitionEntity savedPipeline = null;
        BundleImportResult.Outcome pipelineOutcome = BundleImportResult.Outcome.NONE;
        if (pipeline != null) {
            boolean pipelineIsNew = existingPipeline.isEmpty();
            PipelineDefinitionEntity pe = existingPipeline.orElseGet(PipelineDefinitionEntity::new);
            pe.setName(pipeline.name());
            pe.setDescription(pipeline.description());
            // Always reset scope: a bundle is a template, never tied to a specific environment.
            pe.setEnvironment(null);
            // orphanRemoval=true on @OneToMany handles deletion of old steps when we clear/re-add.
            pe.getSteps().clear();
            if (pipeline.steps() != null) {
                for (StepPayload sp : pipeline.steps()) {
                    PipelineStepEntity step = new PipelineStepEntity();
                    step.setPipeline(pe);
                    step.setActionKey(sp.actionKey());
                    step.setSequenceOrder(sp.sequenceOrder());
                    pe.getSteps().add(step);
                }
            }
            savedPipeline = pipelineRepo.save(pe);
            pipelineOutcome = pipelineIsNew
                ? BundleImportResult.Outcome.CREATED
                : BundleImportResult.Outcome.UPDATED;
        }

        // -- upsert image config and link the pipeline --
        boolean icIsNew = existingIc.isEmpty();
        ImageConfigEntity ice = existingIc.orElseGet(ImageConfigEntity::new);
        ice.setClient(ic.client());
        ice.setProject(ic.project());
        ice.setMaximoVersion(ic.maximoVersion());
        ice.setAppImage(ic.appImage());
        ice.setDbImage(ic.dbImage());
        ice.setAdmImage(ic.admImage());
        ice.setDbVendor(ic.dbVendor());
        ice.setDbName(ic.dbName());
        ice.setDbContainerPort(ic.dbContainerPort());
        ice.setHostVolumePath(ic.hostVolumePath());
        ice.setDbVolumeName(ic.dbVolumeName());
        ice.setWorkspacePath(ic.workspacePath());
        ice.setAppHttpPort(ic.appHttpPort());
        ice.setAppHttpsPort(ic.appHttpsPort());
        ice.setDbPort(ic.dbPort());
        ice.setMockHostPort(ic.mockHostPort());
        ice.setSmtpHostPort(ic.smtpHostPort());
        ice.setSmtpUiHostPort(ic.smtpUiHostPort());
        ice.setDbExtraEnv(ic.dbExtraEnv());
        ice.setDbExtraBinds(ic.dbExtraBinds());
        ice.setAppExtraEnv(ic.appExtraEnv());
        ice.setAppExtraBinds(ic.appExtraBinds());
        ice.setAdmExtraEnv(ic.admExtraEnv());
        ice.setAdmExtraBinds(ic.admExtraBinds());
        ice.setPipelineDefinition(savedPipeline);
        // v1 bundles have no launch section — leave the destination's launch defaults
        // untouched rather than resetting them.
        ImageConfigBundle.LaunchPayload launch = bundle.launch();
        if (launch != null) {
            ice.setLaunchDescription(launch.description());
            ice.setLaunchStaticPorts(Boolean.TRUE.equals(launch.staticPorts()));
            ice.setLaunchIncludeMock(Boolean.TRUE.equals(launch.includeMock()));
            ice.setLaunchIncludeSmtp(Boolean.TRUE.equals(launch.includeSmtp()));
        }
        ImageConfigEntity savedIc = imageConfigRepo.save(ice);

        return new BundleImportResult(
            icIsNew ? BundleImportResult.Outcome.CREATED : BundleImportResult.Outcome.UPDATED,
            savedIc.getId(),
            pipelineOutcome,
            savedPipeline != null ? savedPipeline.getId() : null,
            created,
            updated,
            skipped);
    }

    private static void validateShape(ImageConfigBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Bundle is empty.");
        }
        if (!ImageConfigBundle.KIND.equals(bundle.kind())) {
            throw new IllegalArgumentException(
                "Unexpected bundle kind '" + bundle.kind() + "'; expected '" + ImageConfigBundle.KIND + "'.");
        }
        if (bundle.schemaVersion() == null
                || bundle.schemaVersion() < ImageConfigBundle.MIN_SCHEMA_VERSION
                || bundle.schemaVersion() > ImageConfigBundle.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported schemaVersion " + bundle.schemaVersion()
                + "; this Monohull handles versions " + ImageConfigBundle.MIN_SCHEMA_VERSION
                + ".." + ImageConfigBundle.SCHEMA_VERSION + ".");
        }
        if (bundle.imageConfig() == null) {
            throw new IllegalArgumentException("Bundle is missing the required 'imageConfig' section.");
        }
        ImageConfigPayload ic = bundle.imageConfig();
        if (isBlank(ic.client()) || isBlank(ic.project()) || isBlank(ic.maximoVersion())) {
            throw new IllegalArgumentException(
                "imageConfig.client, imageConfig.project, and imageConfig.maximoVersion are all required.");
        }
    }

    private void validateActionKeyResolution(PipelinePayload pipeline, List<CustomActionPayload> actions) {
        if (pipeline == null || pipeline.steps() == null) return;
        Set<String> bundleKeys = new HashSet<>();
        for (CustomActionPayload p : actions) bundleKeys.add(p.actionKey());

        List<String> unresolved = new ArrayList<>();
        for (StepPayload sp : pipeline.steps()) {
            String key = sp.actionKey();
            if (key == null || key.isBlank()) {
                unresolved.add("(blank actionKey at sequenceOrder " + sp.sequenceOrder() + ")");
                continue;
            }
            if (bundleKeys.contains(key)) continue;
            if (customActionRepo.findByActionKey(key).isPresent()) continue;
            unresolved.add(key);
        }
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                "Pipeline references unresolvable action key(s): " + String.join(", ", unresolved)
                + ". Include them in the bundle's customActions, or ensure they exist on the destination "
                + "(built-in actions are seeded from application.yml on Monohull startup).");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
