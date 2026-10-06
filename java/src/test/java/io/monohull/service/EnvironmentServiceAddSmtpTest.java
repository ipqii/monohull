package io.monohull.service;

import io.monohull.entity.ContainerEntity;
import io.monohull.entity.ContainerRole;
import io.monohull.entity.ContainerStatus;
import io.monohull.entity.DbVendor;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Adding Mailpit to an environment that was built without one. */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceAddSmtpTest {

    @Mock private EnvironmentRepository envRepo;
    @Mock private ContainerRepository containerRepo;
    @Mock private EnvironmentConfigRepository configRepo;
    @Mock private BuildLogRepository logRepo;
    @Mock private ImageConfigRepository imageConfigRepo;
    @Mock private BuildService buildService;
    @Mock private DockerService dockerService;

    private EnvironmentService service;
    private EnvironmentEntity env;

    @BeforeEach
    void setUp() {
        service = new EnvironmentService(envRepo, containerRepo, configRepo, logRepo,
            imageConfigRepo, buildService, dockerService);
        ReflectionTestUtils.setField(service, "portRangeStart", 12600);
        ReflectionTestUtils.setField(service, "portRangeEnd", 12999);
        ReflectionTestUtils.setField(service, "smtpImage", "axllent/mailpit:latest");
        lenient().when(configRepo.findAllUsedAppHttpPorts()).thenReturn(List.of(12600));
        lenient().when(configRepo.findAllUsedAppHttpsPorts()).thenReturn(List.of(12601));
        lenient().when(configRepo.findAllUsedDbPorts()).thenReturn(List.of(12602));
        lenient().when(configRepo.findAllUsedMockHostPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedSmtpHostPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedSmtpUiHostPorts()).thenReturn(List.of());

        env = new EnvironmentEntity();
        env.setId(7L);
        env.setName("acme-eam-1");
        env.setNetworkName("acme-eam-1");
        env.setDbVendor(DbVendor.ORACLE);
        env.setStatus(EnvironmentStatus.RUNNING);
        env.setContainers(new ArrayList<>(List.of(container(ContainerRole.DB), container(ContainerRole.APP),
            container(ContainerRole.ADM))));
        EnvironmentConfigEntity config = new EnvironmentConfigEntity();
        env.setConfig(config);
        lenient().when(envRepo.findByIdWithContainersAndConfig(7L)).thenReturn(Optional.of(env));
        lenient().when(envRepo.findById(7L)).thenReturn(Optional.of(env));
    }

    private static ContainerEntity container(ContainerRole role) {
        ContainerEntity c = new ContainerEntity();
        c.setRole(role);
        c.setStatus(ContainerStatus.RUNNING);
        return c;
    }

    @Test
    void addsSmtpContainerOnFreePortsAndStartsIt() {
        service.addSmtp(7L, true);

        EnvironmentConfigEntity config = env.getConfig();
        assertThat(config.isSmtpEnabled()).isTrue();
        assertThat(config.getSmtpHostPort()).isEqualTo(12603);
        assertThat(config.getSmtpUiHostPort()).isEqualTo(12604);

        ContainerEntity smtp = env.getContainers().stream()
            .filter(c -> c.getRole() == ContainerRole.SMTP).findFirst().orElseThrow();
        assertThat(smtp.getContainerName()).isEqualTo("acme-eam-1-smtp");
        assertThat(smtp.getImage()).isEqualTo("axllent/mailpit:latest");
        assertThat(smtp.getPorts()).isEqualTo("12603:1025,12604:8025");

        verify(envRepo).save(env);
        verify(buildService).attachSmtpContainer(7L, true);
    }

    @Test
    void refusesWhenTheEnvironmentAlreadyHasSmtp() {
        env.getContainers().add(container(ContainerRole.SMTP));

        assertThatThrownBy(() -> service.addSmtp(7L, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already has an SMTP container");
        verify(envRepo, never()).save(any());
        verify(buildService, never()).attachSmtpContainer(anyLong(), anyBoolean());
    }

    @Test
    void refusesWhenTheEnvironmentIsNotRunning() {
        env.setStatus(EnvironmentStatus.STOPPED);

        assertThatThrownBy(() -> service.addSmtp(7L, false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RUNNING");
        verify(buildService, never()).attachSmtpContainer(anyLong(), anyBoolean());
    }
}
