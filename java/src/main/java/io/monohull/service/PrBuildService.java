package io.monohull.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.monohull.dto.CreateEnvironmentRequest;
import io.monohull.dto.PrEvent;
import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.entity.EnvironmentStatus;
import io.monohull.entity.PrBuildEntity;
import io.monohull.entity.PrBuildEvent;
import io.monohull.entity.PrBuildStatus;
import io.monohull.entity.RepoBuildMode;
import io.monohull.repository.ConnectedRepositoryRepository;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.PrBuildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Orchestrates a PR build end-to-end: provision a build environment with the per-PR workspace
 * override, check the branch out, run the existing build pipeline, await the result, then keep
 * (BUILD_AND_ENV) or tear down (BUILD_ONLY) the environment. Builds run on a bounded executor
 * so concurrency is capped (monohull.pr-builds.max-concurrent).
 */
@Service
public class PrBuildService {

    private static final Logger log = LoggerFactory.getLogger(PrBuildService.class);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(40);
    private static final long POLL_MS = 5000;

    private final ConnectedRepositoryRepository repoRepo;
    private final PrBuildRepository prBuildRepo;
    private final EnvironmentRepository envRepo;
    private final GitService gitService;
    private final EnvironmentService environmentService;
    private final BuildService buildService;
    private final LogSink logSink;
    private final TransactionTemplate tx;

    @Value("${monohull.pr-builds.max-concurrent:2}")
    private int maxConcurrent;

    private ExecutorService executor;

    public PrBuildService(ConnectedRepositoryRepository repoRepo, PrBuildRepository prBuildRepo,
                          EnvironmentRepository envRepo, GitService gitService,
                          EnvironmentService environmentService, BuildService buildService,
                          LogSink logSink, PlatformTransactionManager txManager) {
        this.repoRepo = repoRepo;
        this.prBuildRepo = prBuildRepo;
        this.envRepo = envRepo;
        this.gitService = gitService;
        this.environmentService = environmentService;
        this.buildService = buildService;
        this.logSink = logSink;
        this.tx = new TransactionTemplate(txManager);
    }

    @PostConstruct
    void init() {
        int n = Math.max(1, maxConcurrent);
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "pr-build-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newFixedThreadPool(n, tf);
        log.info("PR build executor started with {} worker(s)", n);
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) executor.shutdownNow();
    }

    /** Entry point from the webhook layer. On open/synchronize, supersedes any in-flight build
     *  for the PR and queues a new one; on close, tears down kept environments. Runs in its own
     *  transaction(s). */
    public void onPrEvent(Long repoId, PrEvent ev) {
        if (ev.event() == PrBuildEvent.CLOSED) {
            onPrClosed(repoId, ev.prNumber());
            return;
        }
        // A new push supersedes the in-flight build for this PR (frees its env + workspace).
        supersedeActive(repoId, ev.prNumber(), null);

        Long prBuildId = tx.execute(s -> {
            ConnectedRepositoryEntity repo = repoRepo.findById(repoId).orElseThrow();
            PrBuildEntity b = new PrBuildEntity();
            b.setRepository(repo);
            b.setPrNumber(ev.prNumber());
            b.setPrTitle(ev.title());
            b.setSourceBranch(ev.sourceBranch());
            b.setTargetBranch(ev.targetBranch());
            b.setCommitSha(ev.sha());
            b.setEvent(ev.event());
            b.setStatus(PrBuildStatus.QUEUED);
            b.setBuildId(UUID.randomUUID().toString());
            return prBuildRepo.save(b).getId();
        });
        executor.submit(() -> runBuild(prBuildId));
    }

    /** PR closed/merged: stop any in-flight build and remove kept environments + workspaces. */
    private void onPrClosed(Long repoId, int prNumber) {
        supersedeActive(repoId, prNumber, null);
        List<Long> kept = tx.execute(s ->
            prBuildRepo.findByRepositoryIdAndPrNumberAndEnvironmentIdNotNull(repoId, prNumber)
                .stream().map(PrBuildEntity::getId).toList());
        for (Long id : kept) {
            cleanupBuildEnv(id);
            tx.executeWithoutResult(s -> prBuildRepo.findById(id).ifPresent(b -> {
                if (b.getStatus() != PrBuildStatus.SUPERSEDED) b.setStatus(PrBuildStatus.REMOVED);
            }));
        }
        log.info("[pr-build] PR #{} closed on repo {} — cleaned {} environment(s)", prNumber, repoId, kept.size());
    }

    /** Mark in-flight builds for a PR as SUPERSEDED and tear down their environments. The
     *  superseded build's own worker thread finalizes harmlessly (guarded against overwrite). */
    private void supersedeActive(Long repoId, int prNumber, Long exceptId) {
        List<Long> ids = tx.execute(s -> {
            List<Long> out = new ArrayList<>();
            for (PrBuildEntity b : prBuildRepo.findByRepositoryIdAndPrNumberAndStatusIn(
                    repoId, prNumber, List.of(PrBuildStatus.QUEUED, PrBuildStatus.CLONING, PrBuildStatus.BUILDING))) {
                if (exceptId != null && b.getId().equals(exceptId)) continue;
                b.setStatus(PrBuildStatus.SUPERSEDED);
                b.setFinishedAt(LocalDateTime.now());
                out.add(b.getId());
            }
            return out;
        });
        for (Long id : ids) cleanupBuildEnv(id);
    }

    /** Remove the environment + workspace associated with a build (best effort). */
    private void cleanupBuildEnv(Long prBuildId) {
        Object[] info = tx.execute(s -> prBuildRepo.findById(prBuildId)
            .map(b -> new Object[]{b.getEnvironmentId(), b.getWorkspacePath(), b.getBuildId()}).orElse(null));
        if (info == null) return;
        Long envId = (Long) info[0];
        String ws = (String) info[1];
        String bid = (String) info[2];
        if (envId != null) {
            teardown(envId, ws, bid);
            tx.executeWithoutResult(s -> prBuildRepo.findById(prBuildId).ifPresent(b -> b.setEnvironmentId(null)));
        } else {
            gitService.cleanup(ws, bid);
        }
    }

    // --- build pipeline (executor thread) ---

    private void runBuild(Long prBuildId) {
        Ctx c = tx.execute(s -> {
            PrBuildEntity b = prBuildRepo.findById(prBuildId).orElseThrow();
            ConnectedRepositoryEntity r = b.getRepository();
            return new Ctx(r.getId(), r.getImageConfig().getId(), r.getRepoFullName(), r.getBuildMode(),
                b.getPrNumber(), b.getPrTitle(), b.getSourceBranch(), b.getTargetBranch(), b.getCommitSha());
        });

        String buildId = null;
        Long envId = null;
        String workspace = null;
        try {
            ConnectedRepositoryEntity repo = tx.execute(s -> repoRepo.findById(c.repoId()).orElseThrow());
            PrEvent ev = new PrEvent(io.monohull.entity.PrBuildEvent.SYNCHRONIZE, c.prNumber(), c.title(),
                c.sourceBranch(), c.targetBranch(), c.sha(), c.repoFullName(), false);

            // Unique per build so concurrent/superseding builds of the same PR never collide
            // on the Docker name or the checkout directory.
            String disc = "b" + prBuildId;
            Path dir = gitService.workspaceDir(c.repoFullName(), c.prNumber(), disc);
            workspace = dir.toString();

            // Provision the build environment (no auto-build) with the per-PR workspace override.
            String envName = "made-" + safe(c.repoFullName()) + "-pr-" + c.prNumber() + "-" + disc;
            CreateEnvironmentRequest req = new CreateEnvironmentRequest(
                envName, c.imageConfigId(), false, null, null, null, false, null, false, null, null);
            EnvironmentEntity env = environmentService.provisionEnvironment(req, workspace, false);
            envId = env.getId();
            buildId = env.getBuildId(); // unify PR build + env build logs onto one stream

            final Long fEnvId = envId;
            final String fWorkspace = workspace;
            final String fBuildId = buildId;
            setBuild(prBuildId, b -> {
                b.setStatus(PrBuildStatus.CLONING);
                b.setBuildId(fBuildId);
                b.setEnvironmentId(fEnvId);
                b.setWorkspacePath(fWorkspace);
                b.setStartedAt(LocalDateTime.now());
            });

            logSink.append(buildId, "[pr-build] PR #" + c.prNumber() + " " + c.repoFullName()
                + " (" + c.mode() + ") branch=" + c.sourceBranch());

            // 1) checkout into the same dir mounted as the workspace
            gitService.checkout(repo, ev, buildId, dir);

            // 2) build via the existing pipeline
            setBuild(prBuildId, b -> b.setStatus(PrBuildStatus.BUILDING));
            buildService.startBuildForEnvironment(envId);

            boolean ok = awaitBuild(envId);

            if (ok) {
                boolean keep = c.mode() == RepoBuildMode.BUILD_AND_ENV;
                boolean applied = setTerminal(prBuildId, b -> { b.setStatus(PrBuildStatus.SUCCESS); b.setFinishedAt(LocalDateTime.now()); });
                if (applied && keep) {
                    logSink.append(buildId, "[pr-build] SUCCESS — environment kept for testing");
                } else {
                    // BUILD_ONLY, or superseded mid-flight (terminal not applied): drop the env.
                    logSink.append(buildId, "[pr-build] removing build environment");
                    teardown(envId, workspace, buildId);
                    setBuild(prBuildId, b -> b.setEnvironmentId(null));
                }
            } else {
                setTerminal(prBuildId, b -> {
                    b.setStatus(PrBuildStatus.FAILED);
                    b.setError("build did not reach RUNNING");
                    b.setFinishedAt(LocalDateTime.now());
                });
                logSink.append(buildId, "[pr-build] removing build environment");
                teardown(envId, workspace, buildId);
                setBuild(prBuildId, b -> b.setEnvironmentId(null));
            }
        } catch (Exception e) {
            log.warn("PR build {} failed: {}", prBuildId, e.getMessage());
            if (buildId != null) logSink.append(buildId, "[pr-build] ERROR: " + e.getMessage());
            final String msg = e.getMessage();
            setTerminal(prBuildId, b -> {
                b.setStatus(PrBuildStatus.FAILED);
                b.setError(msg);
                b.setFinishedAt(LocalDateTime.now());
            });
            if (envId != null) {
                teardown(envId, workspace, buildId);
                setBuild(prBuildId, b -> b.setEnvironmentId(null));
            }
        } finally {
            if (buildId != null) logSink.complete(buildId);
        }
    }

    /** Poll the env build to a terminal state. RUNNING = success, ERROR/removed = failure. */
    private boolean awaitBuild(Long envId) {
        long deadline = System.nanoTime() + BUILD_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            EnvironmentStatus st = tx.execute(s ->
                envRepo.findById(envId).map(EnvironmentEntity::getStatus).orElse(EnvironmentStatus.ERROR));
            if (st == EnvironmentStatus.RUNNING) return true;
            if (st == EnvironmentStatus.ERROR || st == EnvironmentStatus.REMOVED || st == EnvironmentStatus.STOPPED) {
                return false;
            }
        }
        return false;
    }

    private void teardown(Long envId, String workspace, String buildId) {
        try {
            environmentService.removeEnvironment(envId);
        } catch (Exception ex) {
            log.warn("Failed to remove PR build env {}: {}", envId, ex.getMessage());
        }
        gitService.cleanup(workspace, buildId);
    }

    private void setBuild(Long prBuildId, Consumer<PrBuildEntity> mutator) {
        tx.executeWithoutResult(s ->
            prBuildRepo.findById(prBuildId).ifPresent(mutator)); // dirty-checked, flushed on commit
    }

    /** Apply a terminal status only if the build hasn't already been superseded/removed (so a
     *  superseding event wins). Returns true when applied. */
    private boolean setTerminal(Long prBuildId, Consumer<PrBuildEntity> mutator) {
        return Boolean.TRUE.equals(tx.execute(s -> prBuildRepo.findById(prBuildId).map(b -> {
            if (b.getStatus() == PrBuildStatus.SUPERSEDED || b.getStatus() == PrBuildStatus.REMOVED) {
                return false;
            }
            mutator.accept(b);
            return true;
        }).orElse(false)));
    }

    private static String safe(String repoFullName) {
        return repoFullName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private record Ctx(Long repoId, Long imageConfigId, String repoFullName, RepoBuildMode mode,
                       int prNumber, String title, String sourceBranch, String targetBranch, String sha) {}
}
