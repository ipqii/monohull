package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DB and ADM startup are pipeline steps (start-db, start-adm) rather than work done before
 * the pipeline begins, so they show up in the Pipeline tab. Saved pipelines that predate
 * those steps must still get them, in front.
 */
class BuildServicePipelineStepsTest {

    private final ActionService actionService = mock(ActionService.class);
    private final BuildService buildService =
        new BuildService(null, null, null, null, null, actionService);

    {
        when(actionService.findResolvedAction(anyString())).thenReturn(Optional.empty());
    }

    private static ActionService.ResolvedAction step(String id, String role) {
        return new ActionService.ResolvedAction(id, id, role, ":", null, 60, null, null, null, null, false);
    }

    private static List<String> ids(List<ActionService.ResolvedAction> actions) {
        return actions.stream().map(ActionService.ResolvedAction::id).toList();
    }

    @Test
    void prependsStartStepsToAPipelineThatPredatesThem() {
        var result = buildService.withContainerStartSteps(
            List.of(step("run-updatedb", "ADM"), step("start-app", "APP")), true, true, true);
        assertEquals(List.of("start-db", "check-db-schema", "start-adm", "run-updatedb", "start-app"), ids(result));
        assertEquals("DB", result.get(0).targetRole());
        assertEquals("DB", result.get(1).targetRole());
        assertEquals("ADM", result.get(2).targetRole());
    }

    @Test
    void keepsThePlacementOfAPipelineThatListsThem() {
        var result = buildService.withContainerStartSteps(
            List.of(step("start-db", "DB"), step("restore-db", "DB"), step("check-db-schema", "DB"),
                step("start-adm", "ADM"), step("run-updatedb", "ADM")), true, true, false);
        assertEquals(List.of("start-db", "restore-db", "check-db-schema", "start-adm", "run-updatedb"), ids(result));
    }

    @Test
    void insertsTheSchemaCheckAndStartAdmRightAfterAListedStartDb() {
        var result = buildService.withContainerStartSteps(
            List.of(step("start-db", "DB"), step("run-updatedb", "ADM")), true, true, false);
        assertEquals(List.of("start-db", "check-db-schema", "start-adm", "run-updatedb"), ids(result));
    }

    @Test
    void aPipelineThatRestoresTheDbItselfKeepsItsSchemaCheckAfterTheRestore() {
        var result = buildService.withContainerStartSteps(
            List.of(step("restore-sp-backup", "DB"), step("check-db-schema", "DB"),
                step("run-updatedb", "ADM")), true, true, false);
        assertEquals(List.of("start-db", "start-adm", "restore-sp-backup", "check-db-schema", "run-updatedb"),
            ids(result));
    }

    @Test
    void readsDatabaseNamesFromListDbDirectory() {
        String out = String.join("\n",
            " System Database Directory",
            " Number of entries in the directory = 1",
            "Database 1 entry:",
            " Database alias                       = MAXIMO",
            " Database name                        = MAXIMO",
            " Local database directory             = /database");
        assertEquals("MAXIMO", BuildService.parseDb2DatabaseNames(out));
    }

    @Test
    void dropsStartStepsForContainersTheEnvironmentDoesNotHave() {
        var result = buildService.withContainerStartSteps(
            List.of(step("start-db", "DB"), step("check-db-schema", "DB"), step("start-adm", "ADM"),
                step("start-app", "APP")), false, true, false);
        assertEquals(List.of("start-adm"), ids(result));
    }
}
