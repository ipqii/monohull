package io.monohull.service;

import io.monohull.entity.ContainerEntity;
import io.monohull.entity.EnvironmentConfigEntity;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.entity.EnvironmentStatus;
import io.monohull.repository.BuildLogRepository;
import io.monohull.repository.ContainerRepository;
import io.monohull.repository.EnvironmentConfigRepository;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.ImageConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTeardownTest {

    @Mock private EnvironmentRepository envRepo;
    @Mock private ContainerRepository containerRepo;
    @Mock private EnvironmentConfigRepository configRepo;
    @Mock private BuildLogRepository logRepo;
    @Mock private ImageConfigRepository imageConfigRepo;
    @Mock private BuildService buildService;
    @Mock private DockerService dockerService;

    private EnvironmentService service;

    @BeforeEach
    void setUp() {
        service = new EnvironmentService(envRepo, containerRepo, configRepo, logRepo,
            imageConfigRepo, buildService, dockerService);
    }

    /** An env whose DB container has a persisted docker id but whose APP container
     *  failed before its id was ever written — the classic partial-build shape. */
    private EnvironmentEntity partialEnv() {
        EnvironmentEntity env = new EnvironmentEntity();
        env.setName("acme-env-1");
        env.setNetworkName("acme-env-1");
        env.setStatus(EnvironmentStatus.ERROR);

        ContainerEntity db = new ContainerEntity();
        db.setContainerName("acme-env-1-db");
        db.setDockerContainerId("cid-db");
        db.setEnvironment(env);
        env.getContainers().add(db);

        ContainerEntity app = new ContainerEntity();
        app.setContainerName("acme-env-1-app");
        app.setDockerContainerId(null);
        app.setEnvironment(env);
        env.getContainers().add(app);

        EnvironmentConfigEntity config = new EnvironmentConfigEntity();
        config.setDbVolumeName("acme-db-vol");
        env.setConfig(config);
        return env;
    }

    @Test
    void teardownFallsBackToContainerNameWhenIdWasNeverPersisted() {
        EnvironmentEntity env = partialEnv();
        when(envRepo.findById(1L)).thenReturn(Optional.of(env));

        service.removeEnvironment(1L);

        verify(dockerService).removeIfExists("cid-db");
        // the null-id container is removed by its deterministic name, not skipped
        verify(dockerService).removeIfExists("acme-env-1-app");
        verify(dockerService).removeNetwork("acme-env-1");
        verify(dockerService).removeVolume("acme-db-vol");
        assertThat(env.getStatus()).isEqualTo(EnvironmentStatus.REMOVED);
        verify(envRepo).save(env);
    }

    @Test
    void teardownSweepsPastFailuresAndReportsThemWithoutMarkingRemoved() {
        EnvironmentEntity env = partialEnv();
        when(envRepo.findById(1L)).thenReturn(Optional.of(env));
        doThrow(new RuntimeException("Connection refused")).when(dockerService).removeIfExists("cid-db");

        assertThatThrownBy(() -> service.removeEnvironment(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("teardown is incomplete")
            .hasMessageContaining("acme-env-1-db")
            .hasMessageContaining("remove the environment again");

        // one failure must not abort the sweep: everything else is still attempted
        verify(dockerService).removeIfExists("acme-env-1-app");
        verify(dockerService).removeNetwork("acme-env-1");
        verify(dockerService).removeVolume("acme-db-vol");
        // and the env stays visible so the user can retry the removal
        assertThat(env.getStatus()).isNotEqualTo(EnvironmentStatus.REMOVED);
        verify(envRepo, never()).save(any());
    }
}
