package io.monohull.service;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The dynamic port range used to be the hard-coded literals 12000-12999; it is now
 * {@code monohull.ports.range-start/range-end} so a second Monohull instance sharing
 * the Docker host (a CI instance) can run with a non-overlapping range. Allocation is
 * still DB-only collision checking, which is exactly why the ranges must not overlap.
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServicePortRangeTest {

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
        lenient().when(configRepo.findAllUsedAppHttpPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedAppHttpsPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedDbPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedMockHostPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedSmtpHostPorts()).thenReturn(List.of());
        lenient().when(configRepo.findAllUsedSmtpUiHostPorts()).thenReturn(List.of());
    }

    private int[] allocate(int start, int end, int count) {
        ReflectionTestUtils.setField(service, "portRangeStart", start);
        ReflectionTestUtils.setField(service, "portRangeEnd", end);
        return ReflectionTestUtils.invokeMethod(service, "allocateDynamicPorts", count);
    }

    @Test
    void allocatesFromTheConfiguredRange() {
        assertThat(allocate(13000, 13099, 3)).containsExactly(13000, 13001, 13002);
    }

    @Test
    void skipsPortsAlreadyUsedInTheDatabase() {
        when(configRepo.findAllUsedAppHttpPorts()).thenReturn(List.of(13000));
        when(configRepo.findAllUsedDbPorts()).thenReturn(List.of(13002));
        assertThat(allocate(13000, 13099, 3)).containsExactly(13001, 13003, 13004);
    }

    @Test
    void failsClearlyWhenTheRangeIsExhausted() {
        assertThatThrownBy(() -> allocate(13000, 13001, 3))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("13000-13001");
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() -> allocate(13099, 13000, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("monohull.ports.range-start");
    }
}
