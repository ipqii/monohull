package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class DockerErrorsTest {

    @Test
    void portConflictNamesTheFix() {
        RuntimeException e = new RuntimeException(
            "Status 500: {\"message\":\"driver failed programming external connectivity: "
            + "Bind for 0.0.0.0:12000 failed: port is already allocated\"}");
        String msg = DockerErrors.explain(e);
        assertThat(msg).contains("already in use on the Docker host").contains("static ports");
    }

    @Test
    void nameConflictExplainsLeftover() {
        RuntimeException e = new RuntimeException(
            "Status 409: {\"message\":\"Conflict. The container name \\\"/acme-db\\\" "
            + "is already in use by container \\\"abc123\\\"\"}");
        assertThat(DockerErrors.explain(e)).contains("leftover from a previous failed build");
    }

    @Test
    void pullAuthDeniedPointsAtRegistryCredentials() {
        RuntimeException e = new RuntimeException(
            "Status 500: {\"message\":\"pull access denied for acme/maximo, repository does not "
            + "exist or may require 'docker login'\"}");
        assertThat(DockerErrors.explain(e)).contains("registry refused the image pull");
    }

    @Test
    void manifestUnknownIsImageNotFound() {
        RuntimeException e = new RuntimeException(
            "Status 404: {\"message\":\"manifest unknown: manifest unknown\"}");
        assertThat(DockerErrors.explain(e)).contains("Image not found in the registry");
    }

    @Test
    void outOfDiskIsNamed() {
        RuntimeException e = new RuntimeException(
            "Status 500: {\"message\":\"mkdir /var/lib/docker/tmp: no space left on device\"}");
        assertThat(DockerErrors.explain(e)).contains("out of disk space");
    }

    @Test
    void missingContainerSuggestsRerun() {
        RuntimeException e = new RuntimeException(
            "Status 404: {\"message\":\"No such container: abc123\"}");
        assertThat(DockerErrors.explain(e)).contains("no longer exists").contains("Re-run the pipeline");
    }

    @Test
    void connectExceptionInChainMeansDaemonUnreachable() {
        RuntimeException e = new RuntimeException("request failed",
            new ConnectException("Connection refused"));
        assertThat(DockerErrors.explain(e)).contains("Cannot reach the Docker daemon");
    }

    @Test
    void unknownErrorsFallBackToTheRawFirstLine() {
        RuntimeException e = new RuntimeException("something quite novel\nsecond line");
        assertThat(DockerErrors.explain(e)).isEqualTo("something quite novel");
    }

    @Test
    void sniffKnowsDb2LicenseAndOom() {
        assertThat(DockerErrors.sniff("SQL1598N An attempt to connect failed due to licensing"))
            .contains("DB2 license");
        assertThat(DockerErrors.sniff("java.lang.OutOfMemoryError: Java heap space"))
            .contains("memory");
        assertThat(DockerErrors.sniff("perfectly ordinary output")).isNull();
        assertThat(DockerErrors.sniff(null)).isNull();
    }
}
