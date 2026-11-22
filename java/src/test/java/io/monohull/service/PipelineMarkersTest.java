package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Saved pipeline definitions list the container-start steps, so the editor shows them. */
class PipelineMarkersTest {

    @Test
    void addsTheMarkersInFrontOfAnOlderDefinition() {
        assertEquals(List.of("start-db", "check-db-schema", "start-adm", "run-updatedb", "start-app"),
            PipelineMarkers.withMarkers(List.of("run-updatedb", "start-app")));
    }

    @Test
    void isIdempotent() {
        List<String> once = PipelineMarkers.withMarkers(List.of("restore-sp", "check-db-schema", "run-updatedb"));
        assertEquals(List.of("start-db", "start-adm", "restore-sp", "check-db-schema", "run-updatedb"), once);
        assertEquals(once, PipelineMarkers.withMarkers(once));
    }

    @Test
    void keepsAMovedSchemaCheckWhereItWasPut() {
        List<String> moved = List.of("start-db", "start-adm", "restore-sp", "check-db-schema", "run-updatedb");
        assertEquals(moved, PipelineMarkers.withMarkers(moved));
    }

    @Test
    void putsBackAMarkerThatWasRemoved() {
        assertEquals(List.of("start-db", "check-db-schema", "start-adm", "run-updatedb"),
            PipelineMarkers.withMarkers(List.of("start-db", "start-adm", "run-updatedb")));
    }
}
