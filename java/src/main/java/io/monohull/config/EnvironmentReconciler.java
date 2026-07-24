package io.monohull.config;

import io.monohull.entity.ContainerEntity;
import io.monohull.entity.ContainerRole;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.entity.EnvironmentStatus;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.service.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles environments that were left in a transient state when Monohull last shut down.
 *
 * Background: BuildService runs the build / readiness wait on an @Async thread. If Monohull is
 * restarted while that thread is mid-wait, the worker dies and the env stays in BUILDING /
 * CONFIGURING / PENDING forever — nothing in the running JVM is watching for the readiness
 * marker. This reconciler runs once on startup, inspects the actual Docker state of each
 * orphaned env, and decides:
 *
 *   - APP container running and BMXAA6472I present in /logs/messages.log → RUNNING
 *   - APP container running, no marker yet                                → leave alone
 *     (still booting — user can re-trigger the pipeline manually, but we don't want to
 *     pre-emptively mark ERROR for a slow boot)
 *   - APP container exists but not running                                → ERROR
 *   - No APP container at all                                             → ERROR
 *
 * Runs after ActionInitializer (which seeds built-in actions).
 */
@Component
@Order(20)
public class EnvironmentReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentReconciler.class);

    private static final Set<EnvironmentStatus> TRANSIENT_STATES =
        EnumSet.of(EnvironmentStatus.PENDING, EnvironmentStatus.BUILDING, EnvironmentStatus.CONFIGURING);

    private static final String READINESS_CHECK = "grep -q 'BMXAA6472I' /logs/messages.log 2>/dev/null";

    private final EnvironmentRepository envRepo;
    private final DockerService docker;

    public EnvironmentReconciler(EnvironmentRepository envRepo, DockerService docker) {
        this.envRepo = envRepo;
        this.docker = docker;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<EnvironmentEntity> orphans = envRepo.findAll().stream()
            .filter(e -> TRANSIENT_STATES.contains(e.getStatus()))
            .toList();

        if (orphans.isEmpty()) return;

        log.info("Reconciling {} environments stuck in transient state", orphans.size());
        for (EnvironmentEntity env : orphans) {
            try {
                reconcile(env);
            } catch (Exception e) {
                log.warn("Reconciliation failed for env {} ({}): {}", env.getId(), env.getName(), e.getMessage());
            }
        }
    }

    private void reconcile(EnvironmentEntity env) {
        ContainerEntity appContainer = env.getContainers().stream()
            .filter(c -> c.getRole() == ContainerRole.APP)
            .findFirst()
            .orElse(null);

        if (appContainer == null || appContainer.getDockerContainerId() == null) {
            log.info("env {} ({}): no APP container — marking ERROR", env.getId(), env.getName());
            env.setStatus(EnvironmentStatus.ERROR);
            envRepo.save(env);
            return;
        }

        boolean running;
        try {
            running = Boolean.TRUE.equals(
                docker.inspectContainer(appContainer.getDockerContainerId()).getState().getRunning());
        } catch (Exception e) {
            log.info("env {} ({}): APP container not inspectable ({}) — marking ERROR",
                env.getId(), env.getName(), e.getMessage());
            env.setStatus(EnvironmentStatus.ERROR);
            envRepo.save(env);
            return;
        }

        if (!running) {
            log.info("env {} ({}): APP container exists but not running — marking ERROR",
                env.getId(), env.getName());
            env.setStatus(EnvironmentStatus.ERROR);
            envRepo.save(env);
            return;
        }

        int rc;
        try {
            rc = docker.execInContainer(appContainer.getDockerContainerId(),
                READINESS_CHECK, null, 15, line -> {});
        } catch (Exception e) {
            log.info("env {} ({}): readiness exec failed ({}) — leaving in {}",
                env.getId(), env.getName(), e.getMessage(), env.getStatus());
            return;
        }

        if (rc == 0) {
            log.info("env {} ({}): BMXAA6472I present — marking RUNNING", env.getId(), env.getName());
            env.setStatus(EnvironmentStatus.RUNNING);
            envRepo.save(env);
        } else {
            log.info("env {} ({}): no readiness marker yet — leaving in {} (still booting?)",
                env.getId(), env.getName(), env.getStatus());
        }
    }
}
