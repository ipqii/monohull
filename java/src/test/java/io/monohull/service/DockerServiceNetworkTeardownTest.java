package io.monohull.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Network;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Removing an environment's network: a container still attached (e.g. a mock container an
 * older version never recorded) used to make Docker refuse, so the environment could never
 * be removed. And Docker's name filter is a substring match, so acme-1 must not reach acme-10.
 */
class DockerServiceNetworkTeardownTest {

    private final DockerClient docker = mock(DockerClient.class);
    private final DockerService service = new DockerService(docker, mock(RegistryCredentialService.class));

    private final RemoveNetworkCmd removeNetwork = mock(RemoveNetworkCmd.class);
    private final RemoveContainerCmd removeContainer = mock(RemoveContainerCmd.class);
    private final DisconnectFromNetworkCmd disconnect = mock(DisconnectFromNetworkCmd.class, RETURNS_SELF);

    @BeforeEach
    void setUp() {
        when(docker.removeNetworkCmd(anyString())).thenReturn(removeNetwork);
        when(docker.removeContainerCmd(anyString())).thenReturn(removeContainer);
        when(removeContainer.withForce(anyBoolean())).thenReturn(removeContainer);
        when(docker.disconnectFromNetworkCmd()).thenReturn(disconnect);
    }

    private static Network network(String id, String name) {
        Network n = mock(Network.class);
        when(n.getId()).thenReturn(id);
        when(n.getName()).thenReturn(name);
        return n;
    }

    private void listReturns(String filter, Network... networks) {
        ListNetworksCmd list = mock(ListNetworksCmd.class);
        when(docker.listNetworksCmd()).thenReturn(list);
        when(list.withNameFilter(filter)).thenReturn(list);
        when(list.exec()).thenReturn(List.of(networks));
    }

    private void attached(String networkId, String... containerIds) {
        Network full = mock(Network.class);
        Map<String, Network.ContainerNetworkConfig> containers = new LinkedHashMap<>();
        for (String id : containerIds) containers.put(id, mock(Network.ContainerNetworkConfig.class));
        when(full.getContainers()).thenReturn(containers);
        InspectNetworkCmd inspect = mock(InspectNetworkCmd.class);
        when(docker.inspectNetworkCmd()).thenReturn(inspect);
        when(inspect.withNetworkId(networkId)).thenReturn(inspect);
        when(inspect.exec()).thenReturn(full);
    }

    private void container(String id, String name, boolean managed) {
        InspectContainerResponse r = mock(InspectContainerResponse.class);
        ContainerConfig cfg = mock(ContainerConfig.class);
        when(r.getName()).thenReturn("/" + name);
        when(r.getConfig()).thenReturn(cfg);
        when(cfg.getLabels()).thenReturn(managed ? Map.of("com.pq.maximo.managed", "true") : Map.of());
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        when(docker.inspectContainerCmd(id)).thenReturn(cmd);
        when(cmd.exec()).thenReturn(r);
    }

    @Test
    void removesManagedAndDisconnectsUnmanagedContainersBeforeRemovingTheNetwork() {
        listReturns("abm-nova-10", network("n10", "abm-nova-10"));
        attached("n10", "mock1", "proxy1");
        container("mock1", "abm-nova-10-mock", true);
        container("proxy1", "someones-proxy", false);

        service.removeNetwork("abm-nova-10");

        var order = inOrder(docker, removeContainer, disconnect, removeNetwork);
        order.verify(docker).removeContainerCmd("mock1");
        order.verify(removeContainer).exec();
        order.verify(disconnect).exec();
        order.verify(docker).removeNetworkCmd("n10");
        order.verify(removeNetwork).exec();
        verify(docker, never()).removeContainerCmd("proxy1");
        verify(disconnect).withContainerId("proxy1");
        verify(disconnect).withNetworkId("n10");
    }

    @Test
    void onlyTheNetworkWithExactlyThatNameIsTouched() {
        listReturns("acme-1", network("n1", "acme-1"), network("n10", "acme-10"), network("n11", "acme-11"));
        attached("n1");

        service.removeNetwork("acme-1");

        verify(docker).removeNetworkCmd("n1");
        verify(docker, never()).removeNetworkCmd("n10");
        verify(docker, never()).removeNetworkCmd("n11");
        verify(docker, times(1)).inspectNetworkCmd();
    }

    @Test
    void aContainerThatIsAlreadyGoneIsSkipped() {
        listReturns("acme-1", network("n1", "acme-1"));
        attached("n1", "gone");
        InspectContainerCmd cmd = mock(InspectContainerCmd.class);
        when(docker.inspectContainerCmd("gone")).thenReturn(cmd);
        when(cmd.exec()).thenThrow(new NotFoundException("No such container"));

        service.removeNetwork("acme-1");

        verify(docker, never()).removeContainerCmd(anyString());
        verify(docker).removeNetworkCmd("n1");
    }
}
