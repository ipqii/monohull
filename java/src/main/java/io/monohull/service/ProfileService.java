package io.monohull.service;

import io.monohull.dto.BundleImportResult;
import io.monohull.dto.CreateEnvironmentRequest;
import io.monohull.dto.EnvironmentResponse;
import io.monohull.dto.ImageConfigBundle;
import io.monohull.dto.ProfileLaunchResult;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.ImageConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-click profile launches (MXF-20). A "profile" is an image config template plus its
 * stored launch defaults (staticPorts / mock / SMTP); launching one provisions and
 * auto-builds an environment without any New Build dialog input — the environment name is
 * generated server-side. {@link #launchBundle} additionally accepts a shared bundle YAML
 * and imports it first (skipped when the template already exists, unless overwrite), so a
 * fresh install goes from empty to building in a single call.
 */
@Service
public class ProfileService {

    /** Environment names are {@code monohull-<client>-<project>-<n>}, matching the UI. */
    static final String NAME_PREFIX = "monohull";

    private final ImageConfigRepository imageConfigRepo;
    private final EnvironmentRepository envRepo;
    private final BundleService bundleService;
    private final EnvironmentService environmentService;

    public ProfileService(ImageConfigRepository imageConfigRepo,
                          EnvironmentRepository envRepo,
                          BundleService bundleService,
                          EnvironmentService environmentService) {
        this.imageConfigRepo = imageConfigRepo;
        this.envRepo = envRepo;
        this.bundleService = bundleService;
        this.environmentService = environmentService;
    }

    @Transactional
    public ProfileLaunchResult launch(Long imageConfigId) {
        ImageConfigEntity ic = imageConfigRepo.findById(imageConfigId)
            .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + imageConfigId));
        EnvironmentResponse env = provisionFromProfile(ic);
        return new ProfileLaunchResult(null, null, env);
    }

    @Transactional
    public ProfileLaunchResult launchBundle(ImageConfigBundle bundle, boolean overwrite) {
        if (bundle == null || bundle.imageConfig() == null) {
            throw new IllegalArgumentException("Bundle is missing the required 'imageConfig' section.");
        }
        ImageConfigBundle.ImageConfigPayload ic = bundle.imageConfig();

        // Import-if-absent: when the template already exists on this instance, launch it
        // as-is instead of failing on the conflict — a shared profile should be one click
        // even the second time around. overwrite=true forces the import to update it.
        boolean exists = imageConfigRepo
            .findByClientAndProjectAndMaximoVersion(ic.client(), ic.project(), ic.maximoVersion())
            .isPresent();

        BundleImportResult importResult = null;
        Long imageConfigId;
        if (exists && !overwrite) {
            imageConfigId = imageConfigRepo
                .findByClientAndProjectAndMaximoVersion(ic.client(), ic.project(), ic.maximoVersion())
                .orElseThrow()
                .getId();
        } else {
            importResult = bundleService.importBundle(bundle, overwrite);
            imageConfigId = importResult.imageConfigId();
        }

        ImageConfigEntity entity = imageConfigRepo.findById(imageConfigId)
            .orElseThrow(() -> new IllegalStateException("Image config vanished mid-launch: " + imageConfigId));
        EnvironmentResponse env = provisionFromProfile(entity);
        return new ProfileLaunchResult(importResult, exists && !overwrite ? Boolean.TRUE : null, env);
    }

    private EnvironmentResponse provisionFromProfile(ImageConfigEntity ic) {
        if (ic.getPipelineDefinition() == null) {
            throw new IllegalArgumentException(
                "Profile '" + ic.getClient() + " / " + ic.getProject() + "' has no pipeline linked, so a "
                + "launched environment would never build. Link a pipeline to the image config first.");
        }
        CreateEnvironmentRequest req = new CreateEnvironmentRequest(
            nextEnvironmentName(ic),
            ic.getId(),
            ic.isLaunchStaticPorts(),
            null, null, null,
            ic.isLaunchIncludeMock(), null,
            ic.isLaunchIncludeSmtp(), null, null);
        return environmentService.createEnvironment(req);
    }

    /**
     * Same convention the New Build dialog uses ({@code monohull-<client>-<project>-<n>},
     * n = 1 + count of environments for that client/project), but collision-checked: names
     * of removed environments free up while the count doesn't shrink, and vice versa.
     */
    String nextEnvironmentName(ImageConfigEntity ic) {
        String base = sanitize(NAME_PREFIX + "-" + ic.getClient() + "-" + ic.getProject());
        long seq = envRepo.countByClientAndProject(ic.getClient(), ic.getProject()) + 1;
        String candidate = base + "-" + seq;
        while (envRepo.existsByName(candidate)) {
            seq++;
            candidate = base + "-" + seq;
        }
        return candidate;
    }

    private static String sanitize(String raw) {
        return raw.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}
