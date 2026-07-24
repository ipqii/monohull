package io.monohull.service;

import io.monohull.dto.*;
import io.monohull.entity.*;
import io.monohull.repository.*;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final CustomActionRepository customActionRepo;
    private final ActionExecutionRepository executionRepo;
    private final ActionLogRepository actionLogRepo;
    private final BuildLogRepository buildLogRepo;
    private final EnvironmentRepository envRepo;
    private final ContainerRepository containerRepo;
    private final ImageConfigRepository imageConfigRepo;
    private final PipelineDefinitionRepository pipelineDefRepo;
    private final DockerService dockerService;
    private final LogSink logSink;
    // @Lazy breaks the BuildService <-> ActionService circular dependency: BuildService
    // injects ActionService for pipeline orchestration, and we need BuildService here so
    // pipeline re-runs can re-create the APP container if the first build failed before
    // the start-app marker fired.
    private final BuildService buildService;

    /** Ephemeral builder image for BUILDER-type actions, built on demand from the context
     *  shipped inside the Monohull image (same pattern as the mock-receiver). */
    @Value("${monohull.builder.image:monohull/maximo-builder:latest}")
    private String builderImage;

    @Value("${monohull.builder.build-path:docker/maximo-builder}")
    private String builderBuildPath;

    public ActionService(CustomActionRepository customActionRepo,
                         ActionExecutionRepository executionRepo,
                         ActionLogRepository actionLogRepo,
                         BuildLogRepository buildLogRepo,
                         EnvironmentRepository envRepo,
                         ContainerRepository containerRepo,
                         ImageConfigRepository imageConfigRepo,
                         PipelineDefinitionRepository pipelineDefRepo,
                         DockerService dockerService,
                         LogSink logSink,
                         @Lazy BuildService buildService) {
        this.customActionRepo = customActionRepo;
        this.executionRepo = executionRepo;
        this.actionLogRepo = actionLogRepo;
        this.buildLogRepo = buildLogRepo;
        this.envRepo = envRepo;
        this.containerRepo = containerRepo;
        this.imageConfigRepo = imageConfigRepo;
        this.pipelineDefRepo = pipelineDefRepo;
        this.dockerService = dockerService;
        this.logSink = logSink;
        this.buildService = buildService;
    }

    public List<ActionDefinitionResponse> getAvailableActions(Long envId) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        List<CustomActionEntity> actions = getActionsForEnv(env);

        List<ActionDefinitionResponse> result = new ArrayList<>();
        for (CustomActionEntity ca : actions) {
            result.add(toDefinitionResponse(ca));
        }
        return result;
    }

    private ActionDefinitionResponse toDefinitionResponse(CustomActionEntity ca) {
        Long imageConfigId = ca.getImageConfig() != null ? ca.getImageConfig().getId() : null;
        Long environmentId = ca.getEnvironment() != null ? ca.getEnvironment().getId() : null;
        return new ActionDefinitionResponse(
            ca.getActionKey(), ca.getName(), ca.getDescription(),
            ca.getTargetRole(), ca.isBuiltIn(), ca.getId(),
            ca.getAfterAction(), ca.isAutoRun(), ca.getExecutionType(),
            ca.getRunAsUser(), imageConfigId, environmentId
        );
    }

    @Transactional
    public ActionExecutionResponse executeAction(Long envId, ExecuteActionRequest req) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        ContainerEntity container = containerRepo.findById(req.containerId())
            .orElseThrow(() -> new IllegalArgumentException("Container not found: " + req.containerId()));

        CustomActionEntity action = customActionRepo.findByActionKey(req.actionId())
            .orElseThrow(() -> new IllegalArgumentException("Action not found: " + req.actionId()));

        if (!isActionVisibleToEnv(action, env)) {
            throw new IllegalArgumentException("Action " + action.getActionKey()
                + " is not available for environment " + env.getId());
        }

        if ("BUILDER".equals(action.getExecutionType())) {
            throw new IllegalArgumentException("Action " + action.getActionKey()
                + " runs in an ephemeral builder container and can only execute as a pipeline step");
        }

        if (!container.getRole().name().equals(action.getTargetRole())) {
            throw new IllegalArgumentException("Action targets role " + action.getTargetRole()
                + " but container has role " + container.getRole().name());
        }

        if (container.getDockerContainerId() == null) {
            throw new IllegalArgumentException("Container has no Docker ID - not yet created");
        }

        String executionId = UUID.randomUUID().toString();

        ActionExecutionEntity execution = new ActionExecutionEntity();
        execution.setExecutionId(executionId);
        execution.setActionKey(req.actionId());
        execution.setEnvironment(env);
        execution.setContainer(container);
        execution.setStatus(ActionExecutionStatus.RUNNING);
        executionRepo.save(execution);

        String dockerContainerId = container.getDockerContainerId();
        Long executionDbId = execution.getId();
        String command = action.getCommand();
        String workingDir = action.getWorkingDir();
        int timeout = action.getTimeoutSeconds();
        String executionType = action.getExecutionType();
        String runAsUser = action.getRunAsUser();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runActionAsync(executionDbId, executionId, dockerContainerId,
                    command, workingDir, timeout, executionType, runAsUser);
            }
        });

        return toExecutionResponse(execution);
    }

    @Async
    public void runActionAsync(Long executionDbId, String executionId, String dockerContainerId,
                               String command, String workingDir, int timeout, String executionType,
                               String runAsUser) {
        logSink.append(executionId, "[action] Starting: " + command);

        try {
            int exitCode;
            Consumer<String> logger = line -> {
                logSink.append(executionId, line);
                persistActionLog(executionDbId, line);
            };

            if ("HOST".equals(executionType)) {
                exitCode = executeHostAction(dockerContainerId, command, logger);
            } else {
                String[] eff = applyRunAsUser(command, workingDir, runAsUser);
                exitCode = dockerService.execInContainer(dockerContainerId, eff[0], eff[1], timeout, logger);
            }

            ActionExecutionEntity execution = executionRepo.findById(executionDbId).orElse(null);
            if (execution != null) {
                execution.setExitCode(exitCode);
                execution.setFinishedAt(LocalDateTime.now());
                execution.setStatus(exitCode == 0 ? ActionExecutionStatus.COMPLETED : ActionExecutionStatus.FAILED);
                executionRepo.save(execution);
            }

            logSink.append(executionId, "[action] Finished with exit code: " + exitCode);
        } catch (Exception e) {
            log.error("Action execution failed: {}", executionId, e);
            logSink.append(executionId, "[error] " + e.getMessage());

            ActionExecutionEntity execution = executionRepo.findById(executionDbId).orElse(null);
            if (execution != null) {
                execution.setFinishedAt(LocalDateTime.now());
                execution.setStatus(ActionExecutionStatus.FAILED);
                execution.setExitCode(-1);
                executionRepo.save(execution);
            }
        } finally {
            logSink.complete(executionId);
        }
    }

    // ========== Pipeline Logic ==========

    public record ResolvedAction(String id, String name, String targetRole,
                                  String command, String workingDir, int timeout,
                                  String afterAction, String executionType,
                                  String allowedExitCodes, String runAsUser,
                                  boolean verbose) {
        boolean isExitCodeAllowed(int exitCode) {
            if (exitCode == 0) return true;
            if (allowedExitCodes == null || allowedExitCodes.isBlank()) return false;
            for (String code : allowedExitCodes.split(",")) {
                try {
                    if (Integer.parseInt(code.trim()) == exitCode) return true;
                } catch (NumberFormatException ignored) {}
            }
            return false;
        }
    }

    public List<ResolvedAction> buildPipelineOrder(Long envId) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        // Env-level override wins over the image-config default.
        PipelineDefinitionEntity pipeline = env.getPipelineDefinition();
        if (pipeline == null && env.getImageConfig() != null) {
            pipeline = env.getImageConfig().getPipelineDefinition();
        }
        if (pipeline != null) {
            return buildFromPipelineDefinition(pipeline, env);
        }

        // Fallback: use autoRun actions sorted by afterAction chain
        List<CustomActionEntity> allActions = getActionsForEnv(env);

        List<ResolvedAction> candidates = new ArrayList<>();
        for (CustomActionEntity ca : allActions) {
            if (ca.isAutoRun()) {
                candidates.add(new ResolvedAction(
                    ca.getActionKey(), ca.getName(), ca.getTargetRole(),
                    ca.getCommand(), ca.getWorkingDir(), ca.getTimeoutSeconds(),
                    ca.getAfterAction(), ca.getExecutionType(), ca.getAllowedExitCodes(),
                    ca.getRunAsUser(), ca.isVerbose()
                ));
            }
        }

        return topologicalSort(candidates);
    }

    private List<ResolvedAction> buildFromPipelineDefinition(PipelineDefinitionEntity pipeline, EnvironmentEntity env) {
        // Re-fetch pipeline with steps eagerly loaded to avoid lazy init issues
        PipelineDefinitionEntity fullPipeline = pipelineDefRepo.findByIdWithSteps(pipeline.getId())
            .orElseThrow(() -> new IllegalStateException("Pipeline definition not found: " + pipeline.getId()));

        // Look up all actions (not just env-scoped) since the pipeline defines exactly what runs
        List<CustomActionEntity> allActions = customActionRepo.findAll();
        Map<String, CustomActionEntity> actionsByKey = new HashMap<>();
        for (CustomActionEntity ca : allActions) {
            actionsByKey.put(ca.getActionKey(), ca);
        }

        List<ResolvedAction> ordered = new ArrayList<>();
        for (PipelineStepEntity step : fullPipeline.getSteps()) {
            CustomActionEntity ca = actionsByKey.get(step.getActionKey());
            if (ca == null) {
                throw new IllegalStateException("Pipeline step references unknown action: '" + step.getActionKey() + "'");
            }
            ordered.add(new ResolvedAction(
                ca.getActionKey(), ca.getName(), ca.getTargetRole(),
                ca.getCommand(), ca.getWorkingDir(), ca.getTimeoutSeconds(),
                ca.getAfterAction(), ca.getExecutionType(), ca.getAllowedExitCodes(),
                ca.getRunAsUser(), ca.isVerbose()
            ));
        }
        return ordered;
    }

    private List<CustomActionEntity> getActionsForEnv(EnvironmentEntity env) {
        Long imageConfigId = env.getImageConfig() != null ? env.getImageConfig().getId() : null;
        return customActionRepo.findVisibleForEnv(imageConfigId, env.getId());
    }

    private boolean isActionVisibleToEnv(CustomActionEntity action, EnvironmentEntity env) {
        boolean global = action.getImageConfig() == null && action.getEnvironment() == null;
        if (global) return true;
        if (action.getEnvironment() != null && action.getEnvironment().getId().equals(env.getId())) return true;
        if (action.getImageConfig() != null && env.getImageConfig() != null
            && action.getImageConfig().getId().equals(env.getImageConfig().getId())) return true;
        return false;
    }

    private List<ResolvedAction> topologicalSort(List<ResolvedAction> candidates) {
        Map<String, ResolvedAction> byId = new LinkedHashMap<>();
        for (ResolvedAction a : candidates) {
            byId.put(a.id(), a);
        }

        Map<String, List<String>> dependents = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (ResolvedAction a : candidates) {
            inDegree.put(a.id(), 0);
            dependents.put(a.id(), new ArrayList<>());
        }
        for (ResolvedAction a : candidates) {
            if (a.afterAction() != null && byId.containsKey(a.afterAction())) {
                inDegree.merge(a.id(), 1, Integer::sum);
                dependents.get(a.afterAction()).add(a.id());
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<ResolvedAction> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(byId.get(current));
            for (String dep : dependents.get(current)) {
                int newDegree = inDegree.get(dep) - 1;
                inDegree.put(dep, newDegree);
                if (newDegree == 0) {
                    queue.add(dep);
                }
            }
        }

        if (sorted.size() != candidates.size()) {
            throw new IllegalStateException("Cycle detected in action pipeline ordering");
        }

        return sorted;
    }

    public void startPipeline(Long envId) {
        startPipelineInternal(envId, null);
    }

    public void startPipeline(Long envId, String buildId) {
        startPipelineInternal(envId, buildId);
    }

    private void startPipelineInternal(Long envId, String buildId) {
        EnvironmentEntity envForLog = envRepo.findById(envId).orElse(null);

        // Logger that streams to SSE + persists to build_log
        Consumer<String> pipelineLogger = line -> {
            if (buildId != null) {
                logSink.append(buildId, line);
            }
            if (envForLog != null) {
                persistBuildLog(envForLog, line);
            }
        };

        List<ResolvedAction> orderedActions;
        try {
            orderedActions = buildPipelineOrder(envId);
        } catch (Exception e) {
            log.error("Failed to build pipeline order for env {}: {}", envId, e.getMessage());
            if (envForLog != null) {
                envForLog.setStatus(EnvironmentStatus.ERROR);
                envRepo.save(envForLog);
            }
            pipelineLogger.accept("[error] Pipeline ordering failed: " + e.getMessage());
            return;
        }

        if (orderedActions.isEmpty()) {
            if (envForLog != null) {
                envForLog.setStatus(EnvironmentStatus.RUNNING);
                envRepo.save(envForLog);
            }
            pipelineLogger.accept("[pipeline] No auto-run actions configured. Environment ready.");
            return;
        }

        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        env.setStatus(EnvironmentStatus.CONFIGURING);
        envRepo.save(env);

        String pipelineRunId = UUID.randomUUID().toString();

        pipelineLogger.accept("[pipeline] Starting action pipeline (" + orderedActions.size() + " steps)");

        List<ActionExecutionEntity> executions = new ArrayList<>();
        for (int i = 0; i < orderedActions.size(); i++) {
            ResolvedAction action = orderedActions.get(i);

            ContainerEntity container = resolveActionContainer(env, action);
            if (container == null) {
                pipelineLogger.accept("[error] No container with role " + anchorRole(action) + " for action " + action.id());
                env.setStatus(EnvironmentStatus.ERROR);
                envRepo.save(env);
                return;
            }

            ActionExecutionEntity exec = new ActionExecutionEntity();
            exec.setExecutionId(UUID.randomUUID().toString());
            exec.setActionKey(action.id());
            exec.setEnvironment(env);
            exec.setContainer(container);
            exec.setStatus(ActionExecutionStatus.PENDING);
            exec.setPipelineRunId(pipelineRunId);
            exec.setSequenceOrder(i);
            executions.add(executionRepo.save(exec));
        }

        boolean pipelineFailed = false;
        for (int i = 0; i < orderedActions.size(); i++) {
            ResolvedAction action = orderedActions.get(i);
            ActionExecutionEntity exec = executions.get(i);

            if (pipelineFailed) {
                exec.setStatus(ActionExecutionStatus.SKIPPED);
                exec.setFinishedAt(LocalDateTime.now());
                executionRepo.save(exec);
                continue;
            }

            ContainerEntity container = exec.getContainer();
            String dockerContainerId = container.getDockerContainerId();

            pipelineLogger.accept("[action] Action \"" + action.name() + "\" started"
                + " (step " + (i + 1) + "/" + orderedActions.size()
                + ", target: " + action.targetRole() + ")");

            exec.setStatus(ActionExecutionStatus.RUNNING);
            exec.setStartedAt(LocalDateTime.now());
            executionRepo.save(exec);

            // Sniff action output for known in-container failure signatures (DB2 license,
            // log full, OOM…) so a failed step ends with a [hint] naming the fix instead
            // of just a bare exit code.
            final String[] failureHint = {null};
            try {
                final boolean suppressLive = action.verbose();
                Consumer<String> stepLogger = line -> {
                    if (failureHint[0] == null) {
                        String hint = DockerErrors.sniff(line);
                        if (hint != null) failureHint[0] = hint;
                    }
                    if (!suppressLive) pipelineLogger.accept(line);
                    persistActionLog(exec.getId(), line);
                };

                int exitCode;
                if ("start-app".equals(action.id())) {
                    // The start-app marker is handled inline by BuildService during the
                    // initial build. On a pipeline re-run (e.g. after a build-ear retry),
                    // the APP container may not exist yet because the original build
                    // failed before this step. Delegate to BuildService.ensureAppContainerStarted
                    // which creates+starts the APP container, or no-ops if it's already up.
                    buildService.ensureAppContainerStarted(envId, stepLogger);
                    exitCode = 0;
                } else if ("HOST".equals(action.executionType())) {
                    exitCode = executeHostAction(dockerContainerId, action.command(), stepLogger);
                } else if ("BUILDER".equals(action.executionType())) {
                    exitCode = executeBuilderAction(env, action, container, stepLogger);
                } else {
                    String[] eff = applyRunAsUser(action.command(), action.workingDir(), action.runAsUser());
                    exitCode = dockerService.execInContainer(dockerContainerId, eff[0],
                        eff[1], action.timeout(), stepLogger);
                }

                exec.setExitCode(exitCode);
                exec.setFinishedAt(LocalDateTime.now());

                if (action.isExitCodeAllowed(exitCode)) {
                    exec.setStatus(ActionExecutionStatus.COMPLETED);
                    executionRepo.save(exec);
                    pipelineLogger.accept("[action] Action \"" + action.name() + "\" finished (exit " + exitCode + ")");
                } else {
                    exec.setStatus(ActionExecutionStatus.FAILED);
                    executionRepo.save(exec);
                    pipelineLogger.accept("[action] Action \"" + action.name() + "\" FAILED (exit " + exitCode + ")");
                    if (failureHint[0] != null) {
                        pipelineLogger.accept("[hint] " + failureHint[0]);
                    }
                    pipelineFailed = true;
                }
            } catch (Exception e) {
                log.error("Pipeline step failed for action {}: {}", action.id(), e.getMessage(), e);
                exec.setFinishedAt(LocalDateTime.now());
                exec.setStatus(ActionExecutionStatus.FAILED);
                exec.setExitCode(-1);
                executionRepo.save(exec);
                pipelineLogger.accept("[error] " + action.id() + ": " + DockerErrors.explain(e));
                if (failureHint[0] != null) {
                    pipelineLogger.accept("[hint] " + failureHint[0]);
                }
                pipelineFailed = true;
            }
        }

        env = envRepo.findById(envId).orElse(env);
        if (pipelineFailed) {
            env.setStatus(EnvironmentStatus.ERROR);
            envRepo.save(env);
            pipelineLogger.accept("[pipeline] Pipeline failed. Environment set to ERROR.");
        } else {
            env.setStatus(EnvironmentStatus.RUNNING);
            envRepo.save(env);
            pipelineLogger.accept("[pipeline] Pipeline completed successfully. Environment ready.");
        }
    }

    @Async
    public void runPipelineAsync(Long envId) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId).orElse(null);
        if (env == null) {
            log.error("Environment not found for pipeline re-run: {}", envId);
            return;
        }
        String buildId = env.getBuildId();
        try {
            startPipeline(envId, buildId);
        } finally {
            logSink.complete(buildId);
        }
    }

    public PipelineStatusResponse getPipelineStatus(Long envId) {
        Optional<ActionExecutionEntity> latestExec =
            executionRepo.findFirstByEnvironmentIdAndPipelineRunIdIsNotNullOrderByStartedAtDesc(envId);

        if (latestExec.isEmpty()) {
            return new PipelineStatusResponse(null, "NONE", List.of());
        }

        String pipelineRunId = latestExec.get().getPipelineRunId();
        List<ActionExecutionEntity> executions =
            executionRepo.findByPipelineRunIdOrderBySequenceOrderAsc(pipelineRunId);

        List<PipelineStepResponse> steps = new ArrayList<>();
        for (ActionExecutionEntity exec : executions) {
            String actionName = resolveActionName(exec.getActionKey());
            String targetRole = resolveActionTargetRole(exec.getActionKey());
            steps.add(new PipelineStepResponse(
                exec.getSequenceOrder() != null ? exec.getSequenceOrder() : 0,
                exec.getActionKey(),
                actionName,
                targetRole,
                exec.getStatus().name(),
                exec.getExecutionId(),
                exec.getStartedAt() != null ? exec.getStartedAt().toString() : null,
                exec.getFinishedAt() != null ? exec.getFinishedAt().toString() : null,
                exec.getExitCode()
            ));
        }

        String overallStatus;
        boolean anyRunning = executions.stream().anyMatch(e -> e.getStatus() == ActionExecutionStatus.RUNNING);
        boolean anyFailed = executions.stream().anyMatch(e -> e.getStatus() == ActionExecutionStatus.FAILED);
        boolean allCompleted = executions.stream().allMatch(e -> e.getStatus() == ActionExecutionStatus.COMPLETED);
        boolean anyPending = executions.stream().anyMatch(e -> e.getStatus() == ActionExecutionStatus.PENDING);

        if (anyRunning || anyPending) {
            overallStatus = "RUNNING";
        } else if (anyFailed) {
            overallStatus = "FAILED";
        } else if (allCompleted) {
            overallStatus = "COMPLETED";
        } else {
            overallStatus = "COMPLETED";
        }

        return new PipelineStatusResponse(pipelineRunId, overallStatus, steps);
    }

    private String resolveActionName(String actionKey) {
        return customActionRepo.findByActionKey(actionKey)
            .map(CustomActionEntity::getName)
            .orElse(actionKey);
    }

    private String resolveActionTargetRole(String actionKey) {
        return customActionRepo.findByActionKey(actionKey)
            .map(CustomActionEntity::getTargetRole)
            .orElse("UNKNOWN");
    }

    /**
     * If runAsUser is set, wrap the command as `su - <user> -c "cd <wd> && <cmd>"`.
     * Returns a 2-tuple-ish array: [effectiveCommand, effectiveWorkingDir]. workingDir is
     * cleared when wrapping with `su -` (login shell resets cwd; we cd inside the wrapped cmd).
     *
     * `su -` deliberately wipes the inherited environment. To make build-time env vars
     * (MAXIMO_*, MXE_*, custom extras…) visible to scripts run as the target user, we
     * snapshot the container's current exports to a temp file, then source it inside the
     * new login shell. Standard login-shell vars (HOME, PATH, USER, …) are filtered out
     * so they keep the values that `su -` set for the target user.
     */
    private static final String SU_ENV_FILTER =
        "^export (HOME|USER|LOGNAME|MAIL|SHELL|PATH|PWD|OLDPWD|SHLVL|_|HOSTNAME|TERM)=";

    private static String[] applyRunAsUser(String command, String workingDir, String runAsUser) {
        if (runAsUser == null || runAsUser.isBlank()) {
            return new String[] { command, workingDir };
        }
        String inner = (workingDir != null && !workingDir.isBlank())
            ? "cd " + workingDir + " && " + command
            : command;
        // Escape backslashes and double-quotes so the wrapped command survives bash -c "...".
        String escaped = inner.replace("\\", "\\\\").replace("\"", "\\\"");
        String wrapped =
            "__made_env=/tmp/.made_env_$$; " +
            // Normalize bash's `declare -x` form to POSIX `export` so /bin/sh can also source it.
            "export -p | sed 's/^declare -x /export /' " +
                "| grep -Ev '" + SU_ENV_FILTER + "' > \"$__made_env\"; " +
            "su - " + runAsUser + " -c \". $__made_env; " + escaped + "\"; " +
            "__rc=$?; rm -f \"$__made_env\"; exit $__rc";
        return new String[] { wrapped, null };
    }

    private ContainerEntity findContainerByRole(EnvironmentEntity env, String targetRole) {
        return env.getContainers().stream()
            .filter(c -> c.getRole().name().equals(targetRole))
            .findFirst()
            .orElse(null);
    }

    /** BUILDER actions run in an ephemeral container that is not part of the env, so their
     *  execution record anchors to the ADM container — also the package's staging target. */
    private static String anchorRole(ResolvedAction action) {
        return "BUILDER".equals(action.executionType()) ? "ADM" : action.targetRole();
    }

    private ContainerEntity resolveActionContainer(EnvironmentEntity env, ResolvedAction action) {
        return findContainerByRole(env, anchorRole(action));
    }

    // ========== Builder Action Execution ==========

    /**
     * Run a BUILDER-type action: spawn an ephemeral builder container (ant + JDK 8) with the
     * env's workspace mounted, execute the action's command in it, then stage the produced
     * {@code /out/package.zip} into the ADM container at {@code /tmp/made-package/} for a
     * follow-up deploy step. The builder is always removed afterwards.
     *
     * When the env runs from a workspace override (a PR checkout), the image-config
     * workspace's {@code maximo-libs} is overlaid read-only into the clone — those Maximo
     * classpath jars are operator-provided and never committed, so a fresh clone lacks them.
     */
    private int executeBuilderAction(EnvironmentEntity env, ResolvedAction action,
                                     ContainerEntity admContainer, Consumer<String> logger) {
        BuildService.WorkspaceBind ws = buildService.resolveWorkspaceBind(env);
        if (ws == null) {
            logger.accept("[builder] No workspace path on the image config; cannot run " + action.id());
            return 1;
        }
        String admId = admContainer.getDockerContainerId();
        if (admId == null) {
            logger.accept("[builder] ADM container not created yet; cannot stage the package");
            return 1;
        }

        String target = "/workspace/" + ws.folderName();
        List<Bind> binds = new ArrayList<>();
        binds.add(new Bind(ws.bindSource(), new Volume(target)));
        if (ws.overridden()) {
            String libs = ws.configSource().replaceAll("/+$", "") + "/maximo-libs";
            binds.add(new Bind(libs, new Volume(target + "/maximo-libs"), AccessMode.ro));
            logger.accept("[builder] overlaying " + libs + " (ro) into the checkout");
        }
        EnvironmentConfigEntity config = env.getConfig();
        if (config != null) {
            binds.addAll(buildService.toBinds(config.getAdmExtraBinds()));
        }

        String builderName = "made-" + env.getName().toLowerCase().replaceAll("[^a-z0-9-]", "-") + "-builder";
        List<String> builderEnv = List.of("MADE_WORKSPACE=" + target, "MADE_PROJECT=" + ws.folderName());

        dockerService.ensureLocalImageBuilt(builderImage, new File(builderBuildPath), logger);
        try {
            String builderId = dockerService.runBuilderContainer(builderName, builderImage, binds, builderEnv, logger);
            int exitCode = dockerService.execInContainer(builderId, action.command(), null, action.timeout(), logger);
            if (!action.isExitCodeAllowed(exitCode)) {
                return exitCode;
            }
            // copyArchiveToContainerCmd requires the destination dir to exist.
            int mk = dockerService.execInContainer(admId, "mkdir -p /tmp/made-package", null, 30, logger);
            if (mk != 0) {
                logger.accept("[builder] Could not create /tmp/made-package in the ADM container");
                return 1;
            }
            dockerService.copyFileBetweenContainers(builderId, "/out/package.zip", admId, "/tmp/made-package");
            logger.accept("[builder] Package staged to ADM:/tmp/made-package/package.zip");
            return exitCode;
        } finally {
            dockerService.removeIfExists(builderName);
        }
    }

    // ========== Host Action Execution ==========

    private int executeHostAction(String dockerContainerId, String command, Consumer<String> logger) {
        try {
            switch (command.trim().toLowerCase()) {
                case "restart" -> {
                    int beforeCount = buildService.countMaximoReadyMarkers(dockerContainerId);
                    logger.accept("[host] Restarting container " + dockerContainerId
                        + " (pre-restart BMXAA6472I count: " + beforeCount + ")");
                    dockerService.restartContainer(dockerContainerId);
                    logger.accept("[host] Container restarted; waiting for Maximo to come back up...");
                    try {
                        buildService.waitForAppReadyAfter(dockerContainerId, beforeCount, logger);
                        // BMXAA6472I fires even when the /meaweb integration servlet lost the
                        // EAR module-startup race and died — which silently dooms the dataload.
                        // Verify integration is actually up, restarting to re-roll the race if not.
                        buildService.ensureMeawebReady(dockerContainerId, logger);
                    } catch (RuntimeException e) {
                        logger.accept("[error] " + e.getMessage());
                        return 1;
                    }
                    logger.accept("[host] Container restarted and Maximo + integration are ready");
                }
                case "stop" -> {
                    logger.accept("[host] Stopping container " + dockerContainerId);
                    dockerService.stopContainer(dockerContainerId);
                    logger.accept("[host] Container stopped successfully");
                }
                case "start" -> {
                    logger.accept("[host] Starting container " + dockerContainerId);
                    dockerService.startContainer(dockerContainerId);
                    logger.accept("[host] Container started successfully");
                }
                default -> {
                    logger.accept("[error] Unknown host command: " + command);
                    return 1;
                }
            }
            return 0;
        } catch (Exception e) {
            logger.accept("[error] Host action failed: " + e.getMessage());
            return 1;
        }
    }

    // ========== Single-action execution (used by phased build orchestration) ==========

    /**
     * Pre-creates a PENDING ActionExecutionEntity per ordered step so the Pipeline tab
     * can render the full plan from the moment a build starts. Returns the pipelineRunId
     * that callers should pass into executeSingleAction for each step.
     *
     * Steps whose targetRole has no matching container in the env are skipped (the build
     * orchestration would fail on them anyway). The 'start-app' marker is also pre-created
     * so it shows up in the timeline.
     */
    @Transactional
    public String preCreatePipelineExecutions(Long envId, List<ResolvedAction> ordered) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));
        String pipelineRunId = UUID.randomUUID().toString();
        int seq = 0;
        for (ResolvedAction action : ordered) {
            ContainerEntity container = resolveActionContainer(env, action);
            if (container == null) continue;
            ActionExecutionEntity exec = new ActionExecutionEntity();
            exec.setExecutionId(UUID.randomUUID().toString());
            exec.setActionKey(action.id());
            exec.setEnvironment(env);
            exec.setContainer(container);
            exec.setStatus(ActionExecutionStatus.PENDING);
            exec.setPipelineRunId(pipelineRunId);
            exec.setSequenceOrder(seq++);
            executionRepo.save(exec);
        }
        return pipelineRunId;
    }

    /**
     * Marks a pre-created step COMPLETED without running an action's command. Used by
     * BuildService for pipeline markers like 'start-app' whose work is done inline by
     * the orchestrator, not by exec'ing a command in a container.
     */
    @Transactional
    public void markStepCompleted(String pipelineRunId, int seqOrder) {
        executionRepo.findByPipelineRunIdAndSequenceOrder(pipelineRunId, seqOrder)
            .ifPresent(exec -> {
                exec.setStatus(ActionExecutionStatus.COMPLETED);
                exec.setStartedAt(LocalDateTime.now());
                exec.setFinishedAt(LocalDateTime.now());
                exec.setExitCode(0);
                executionRepo.save(exec);
            });
    }

    /**
     * Synchronously runs a single action by key against the env's container of matching role.
     * Looks up a pre-created PENDING ActionExecutionEntity by (pipelineRunId, seqOrder) and
     * transitions it through RUNNING → COMPLETED/FAILED. Falls back to creating a fresh
     * record if no pre-created one is found (covers ad-hoc callers that didn't pre-create).
     * Returns true on success.
     */
    public boolean executeSingleAction(Long envId, String actionKey, String pipelineRunId,
                                       int seqOrder, Consumer<String> buildLogger) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(envId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envId));

        CustomActionEntity ca = customActionRepo.findByActionKey(actionKey)
            .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionKey));

        ResolvedAction action = new ResolvedAction(
            ca.getActionKey(), ca.getName(), ca.getTargetRole(),
            ca.getCommand(), ca.getWorkingDir(), ca.getTimeoutSeconds(),
            ca.getAfterAction(), ca.getExecutionType(), ca.getAllowedExitCodes(),
            ca.getRunAsUser(), ca.isVerbose());

        ContainerEntity container = resolveActionContainer(env, action);
        if (container == null) {
            buildLogger.accept("[error] No container with role " + anchorRole(action) + " for action " + action.id());
            return false;
        }

        ActionExecutionEntity exec = executionRepo
            .findByPipelineRunIdAndSequenceOrder(pipelineRunId, seqOrder)
            .orElse(null);
        if (exec == null) {
            exec = new ActionExecutionEntity();
            exec.setExecutionId(UUID.randomUUID().toString());
            exec.setActionKey(action.id());
            exec.setEnvironment(env);
            exec.setContainer(container);
            exec.setPipelineRunId(pipelineRunId);
            exec.setSequenceOrder(seqOrder);
        }
        exec.setStatus(ActionExecutionStatus.RUNNING);
        exec.setStartedAt(LocalDateTime.now());
        exec = executionRepo.save(exec);

        buildLogger.accept("[action] Action \"" + action.name() + "\" started (target: " + action.targetRole() + ")");

        final Long execId = exec.getId();
        final boolean suppressLive = action.verbose();
        Consumer<String> stepLogger = line -> {
            if (!suppressLive) buildLogger.accept(line);
            persistActionLog(execId, line);
        };

        try {
            int exitCode;
            if ("HOST".equals(action.executionType())) {
                exitCode = executeHostAction(container.getDockerContainerId(), action.command(), stepLogger);
            } else if ("BUILDER".equals(action.executionType())) {
                exitCode = executeBuilderAction(env, action, container, stepLogger);
            } else {
                String[] eff = applyRunAsUser(action.command(), action.workingDir(), action.runAsUser());
                exitCode = dockerService.execInContainer(container.getDockerContainerId(),
                    eff[0], eff[1], action.timeout(), stepLogger);
            }
            exec.setExitCode(exitCode);
            exec.setFinishedAt(LocalDateTime.now());
            boolean ok = action.isExitCodeAllowed(exitCode);
            exec.setStatus(ok ? ActionExecutionStatus.COMPLETED : ActionExecutionStatus.FAILED);
            executionRepo.save(exec);
            buildLogger.accept("[action] Action \"" + action.name() + (ok ? "\" finished" : "\" FAILED") + " (exit " + exitCode + ")");
            return ok;
        } catch (Exception e) {
            log.error("Single action {} failed: {}", action.id(), e.getMessage(), e);
            exec.setExitCode(-1);
            exec.setFinishedAt(LocalDateTime.now());
            exec.setStatus(ActionExecutionStatus.FAILED);
            executionRepo.save(exec);
            buildLogger.accept("[error] " + action.id() + ": " + e.getMessage());
            return false;
        }
    }

    // ========== Action CRUD ==========

    @Transactional
    public CustomActionEntity createCustomAction(CreateCustomActionRequest req) {
        String actionKey = req.name().toLowerCase().replaceAll("[^a-z0-9]+", "-");

        if (customActionRepo.findByActionKey(actionKey).isPresent()) {
            actionKey = actionKey + "-" + System.currentTimeMillis();
        }

        CustomActionEntity entity = new CustomActionEntity();
        entity.setActionKey(actionKey);
        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setTargetRole(req.targetRole());
        entity.setCommand(req.command());
        entity.setWorkingDir(req.workingDir());
        entity.setTimeoutSeconds(req.timeoutSeconds() != null ? req.timeoutSeconds() : 300);
        entity.setAutoRun(req.autoRun() != null && req.autoRun());
        entity.setExecutionType(req.executionType() != null ? req.executionType() : "EXEC");
        entity.setAllowedExitCodes(req.allowedExitCodes());
        entity.setRunAsUser(req.runAsUser());

        applyScope(entity, req);

        return customActionRepo.save(entity);
    }

    private void applyScope(CustomActionEntity entity, CreateCustomActionRequest req) {
        if (req.imageConfigId() != null && req.environmentId() != null) {
            throw new IllegalArgumentException(
                "Action cannot be scoped to both an image config and an environment");
        }
        if (req.imageConfigId() != null) {
            ImageConfigEntity imageConfig = imageConfigRepo.findById(req.imageConfigId())
                .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + req.imageConfigId()));
            entity.setImageConfig(imageConfig);
            entity.setEnvironment(null);
        } else if (req.environmentId() != null) {
            EnvironmentEntity env = envRepo.findById(req.environmentId())
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + req.environmentId()));
            entity.setEnvironment(env);
            entity.setImageConfig(null);
        } else {
            entity.setImageConfig(null);
            entity.setEnvironment(null);
        }
    }

    @Transactional
    public CustomActionEntity updateCustomAction(Long id, CreateCustomActionRequest req) {
        CustomActionEntity entity = customActionRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Action not found: " + id));

        entity.setName(req.name());
        entity.setDescription(req.description());
        entity.setTargetRole(req.targetRole());
        entity.setCommand(req.command());
        entity.setWorkingDir(req.workingDir());
        entity.setTimeoutSeconds(req.timeoutSeconds() != null ? req.timeoutSeconds() : 300);
        entity.setAutoRun(req.autoRun() != null && req.autoRun());
        entity.setExecutionType(req.executionType() != null ? req.executionType() : "EXEC");
        entity.setAllowedExitCodes(req.allowedExitCodes());
        entity.setRunAsUser(req.runAsUser());

        applyScope(entity, req);

        return customActionRepo.save(entity);
    }

    public List<CustomActionEntity> listAllActions() {
        return customActionRepo.findAll();
    }

    @Transactional
    public void deleteCustomAction(Long id) {
        CustomActionEntity entity = customActionRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Action not found: " + id));
        if (entity.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot delete built-in action: " + entity.getActionKey());
        }
        customActionRepo.deleteById(id);
    }

    // ========== Execution history ==========

    public List<ActionExecutionResponse> getExecutionHistory(Long envId) {
        return executionRepo.findByEnvironmentIdOrderByStartedAtDesc(envId).stream()
            .map(this::toExecutionResponse)
            .toList();
    }

    public List<ActionLogEntity> getExecutionLogs(String executionId) {
        ActionExecutionEntity execution = executionRepo.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        return actionLogRepo.findByExecutionIdOrderByCreatedAtAsc(execution.getId());
    }

    private void persistBuildLog(EnvironmentEntity env, String line) {
        try {
            BuildLogEntity logEntry = new BuildLogEntity();
            logEntry.setEnvironment(env);
            logEntry.setLine(line);
            buildLogRepo.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist build log line: {}", e.getMessage());
        }
    }

    private void persistActionLog(Long executionId, String line) {
        try {
            ActionExecutionEntity execution = executionRepo.findById(executionId).orElse(null);
            if (execution != null) {
                ActionLogEntity logEntry = new ActionLogEntity();
                logEntry.setExecution(execution);
                logEntry.setLine(line);
                actionLogRepo.save(logEntry);
            }
        } catch (Exception e) {
            log.warn("Failed to persist action log line: {}", e.getMessage());
        }
    }

    private ActionExecutionResponse toExecutionResponse(ActionExecutionEntity e) {
        return new ActionExecutionResponse(
            e.getExecutionId(),
            e.getActionKey(),
            e.getStatus().name(),
            e.getEnvironment().getId(),
            e.getContainer().getId(),
            e.getStartedAt() != null ? e.getStartedAt().toString() : null,
            e.getFinishedAt() != null ? e.getFinishedAt().toString() : null,
            e.getExitCode(),
            e.getPipelineRunId(),
            e.getSequenceOrder()
        );
    }
}
