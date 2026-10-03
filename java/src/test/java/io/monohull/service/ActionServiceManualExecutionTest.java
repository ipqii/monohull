package io.monohull.service;

import io.monohull.dto.ActionExecutionResponse;
import io.monohull.dto.ExecuteActionRequest;
import io.monohull.entity.ActionExecutionEntity;
import io.monohull.entity.ActionExecutionStatus;
import io.monohull.entity.ContainerEntity;
import io.monohull.entity.ContainerRole;
import io.monohull.entity.CustomActionEntity;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.repository.ActionExecutionRepository;
import io.monohull.repository.ActionLogRepository;
import io.monohull.repository.BuildLogRepository;
import io.monohull.repository.ContainerRepository;
import io.monohull.repository.CustomActionRepository;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.ImageConfigRepository;
import io.monohull.repository.PipelineDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression pin for issue #24: a manually executed action must run on the async executor,
 * not on the caller's thread inside executeAction's afterCommit callback. There, repository
 * writes join the already-committed transaction, so the final status was never flushed and
 * the execution stayed RUNNING forever.
 *
 * <p>Uses a real Spring proxy (@EnableAsync + @EnableTransactionManagement with a no-op
 * transaction manager) around a real ActionService, so the self-invocation that skipped
 * @Async is actually exercised. Repositories and Docker are mocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ActionServiceManualExecutionTest.TestConfig.class)
class ActionServiceManualExecutionTest {

    @Configuration
    @EnableAsync
    @EnableTransactionManagement
    @Import(ActionService.class)
    static class TestConfig {
        @Bean CustomActionRepository customActionRepository() { return mock(CustomActionRepository.class); }
        @Bean ActionExecutionRepository actionExecutionRepository() { return mock(ActionExecutionRepository.class); }
        @Bean ActionLogRepository actionLogRepository() { return mock(ActionLogRepository.class); }
        @Bean BuildLogRepository buildLogRepository() { return mock(BuildLogRepository.class); }
        @Bean EnvironmentRepository environmentRepository() { return mock(EnvironmentRepository.class); }
        @Bean ContainerRepository containerRepository() { return mock(ContainerRepository.class); }
        @Bean ImageConfigRepository imageConfigRepository() { return mock(ImageConfigRepository.class); }
        @Bean PipelineDefinitionRepository pipelineDefinitionRepository() { return mock(PipelineDefinitionRepository.class); }
        @Bean DockerService dockerService() { return mock(DockerService.class); }
        @Bean LogSink logSink() { return mock(LogSink.class); }
        @Bean BuildService buildService() { return mock(BuildService.class); }

        /** Commits nothing, but drives the real synchronization callbacks (afterCommit). */
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(Object tx, TransactionDefinition def) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }

    @Autowired private ActionService actionService;
    @Autowired private EnvironmentRepository envRepo;
    @Autowired private ContainerRepository containerRepo;
    @Autowired private CustomActionRepository customActionRepo;
    @Autowired private ActionExecutionRepository executionRepo;
    @Autowired private DockerService dockerService;

    @Test
    void manualExecutionRunsOffTheCallerThreadAndRecordsItsFinalStatus() throws Exception {
        EnvironmentEntity env = new EnvironmentEntity();
        env.setId(1L);

        ContainerEntity adm = new ContainerEntity();
        adm.setId(2L);
        adm.setRole(ContainerRole.ADM);
        adm.setDockerContainerId("adm-container");

        CustomActionEntity action = new CustomActionEntity();
        action.setActionKey("say-hi");
        action.setTargetRole("ADM");
        action.setCommand("echo hi");
        action.setExecutionType("EXEC");
        action.setTimeoutSeconds(30);

        when(envRepo.findByIdWithContainersAndConfig(1L)).thenReturn(Optional.of(env));
        when(containerRepo.findById(2L)).thenReturn(Optional.of(adm));
        when(customActionRepo.findByActionKey("say-hi")).thenReturn(Optional.of(action));

        // Record the status at each save (the same entity instance is mutated in place).
        AtomicReference<ActionExecutionEntity> saved = new AtomicReference<>();
        List<ActionExecutionStatus> savedStatuses = new CopyOnWriteArrayList<>();
        CountDownLatch finalSave = new CountDownLatch(1);
        when(executionRepo.save(any(ActionExecutionEntity.class))).thenAnswer(inv -> {
            ActionExecutionEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            saved.set(e);
            savedStatuses.add(e.getStatus());
            if (e.getStatus() == ActionExecutionStatus.COMPLETED) finalSave.countDown();
            return e;
        });
        when(executionRepo.findById(10L)).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        // The command blocks until the test releases it, so a synchronous run would hold
        // executeAction open until the release times out.
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> execThread = new AtomicReference<>();
        when(dockerService.execInContainer(eq("adm-container"), eq("echo hi"), isNull(), anyInt(), any()))
            .thenAnswer(inv -> {
                execThread.set(Thread.currentThread());
                release.await(5, TimeUnit.SECONDS);
                return 0;
            });

        long start = System.nanoTime();
        ActionExecutionResponse response = actionService.executeAction(1L, new ExecuteActionRequest("say-hi", 2L));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(elapsedMs).as("executeAction must not wait for the command").isLessThan(3000);

        release.countDown();
        assertThat(finalSave.await(5, TimeUnit.SECONDS)).as("final status saved").isTrue();
        assertThat(execThread.get()).isNotNull().isNotSameAs(Thread.currentThread());
        assertThat(savedStatuses).containsExactly(ActionExecutionStatus.RUNNING, ActionExecutionStatus.COMPLETED);
        assertThat(saved.get().getExitCode()).isZero();
        assertThat(saved.get().getFinishedAt()).isNotNull();
    }
}
