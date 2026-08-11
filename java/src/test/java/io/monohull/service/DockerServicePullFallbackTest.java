package io.monohull.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pins the offline-registry fallback: a failed pull falls back to the local
 * image cache and only a genuinely absent image is fatal. Builds must survive
 * the registry host being down when every image they need is already local
 * (the registry lives on a dev box, not in a datacenter — it WILL go away).
 */
@ExtendWith(MockitoExtension.class)
class DockerServicePullFallbackTest {

    private static final String IMAGE = "registry.example.dev/maximo/vanilla/app:mas91";

    @Mock private DockerClient docker;
    @Mock private RegistryCredentialService registryCredentials;
    @Mock private PullImageCmd pullCmd;
    @Mock private InspectImageCmd inspectCmd;

    private DockerService service;
    private final List<String> logLines = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DockerService(docker, registryCredentials);
        when(docker.pullImageCmd(IMAGE)).thenReturn(pullCmd);
        when(registryCredentials.authConfigFor(IMAGE)).thenReturn(null);
        when(pullCmd.start()).thenThrow(new RuntimeException(
            "Connect to registry.example.dev:443 failed: Connection refused"));
        when(docker.inspectImageCmd(IMAGE)).thenReturn(inspectCmd);
    }

    @Test
    void unreachableRegistryFallsBackToLocallyCachedImage() throws Exception {
        when(inspectCmd.exec()).thenReturn(new InspectImageResponse());

        service.pullImage(IMAGE, logLines::add);

        assertThat(logLines).anySatisfy(line -> {
            assertThat(line).contains("using locally cached image: " + IMAGE);
            assertThat(line).contains("Connection refused");
        });
    }

    @Test
    void unreachableRegistryWithNoLocalImageStaysFatal() {
        when(inspectCmd.exec()).thenThrow(new NotFoundException("no such image: " + IMAGE));

        assertThatThrownBy(() -> service.pullImage(IMAGE, logLines::add))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");
        assertThat(logLines).noneSatisfy(line ->
            assertThat(line).contains("using locally cached image"));
    }
}
