package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The schema-probe diagnosis the build log shows on failure. The sample below is real
 * output captured from a DB2 11.5 container running the probe against a database with
 * no Maximo schema — banner, prompts and all.
 */
class BuildServiceDb2OutputTest {

    private static final String REAL_PROBE_OUTPUT = String.join("\n",
        "(c) Copyright IBM Corporation 1993,2007",
        "Command Line Processor for DB2 Client 11.5.6.0",
        "",
        "You can issue database manager commands and SQL statements from the command ",
        "prompt. For example:",
        "    db2 => connect to sample",
        "    db2 => bind sample.bnd",
        "",
        "To exit db2 interactive mode, type QUIT at the command prompt. Outside ",
        "interactive mode, all commands must be prefixed with 'db2'.",
        "",
        "db2 => ",
        "   Database Connection Information",
        "",
        " Database server        = DB2/LINUXX8664 11.5.6.0",
        " SQL authorization ID   = DB2INST1",
        " Local database alias   = MAXIMO",
        "",
        "db2 => SQL0204N  \"MAXIMO.MAXOBJECT\" is an undefined name.  SQLSTATE=42704",
        "db2 => DB20000I  The TERMINATE command completed successfully.");

    @Test
    void keepsTheDiagnosisAndDropsTheBoilerplate() {
        assertEquals(
            List.of("SQL0204N  \"MAXIMO.MAXOBJECT\" is an undefined name.  SQLSTATE=42704",
                    "DB20000I  The TERMINATE command completed successfully."),
            BuildService.db2MessageLines(REAL_PROBE_OUTPUT));
    }

    @Test
    void theBannerAndConnectionHeaderAreNotMessages() {
        // "Database server = DB2/LINUXX8664" and the sample prompts must not be mistaken
        // for message codes just because they contain "db2"/"DB2".
        assertTrue(BuildService.db2MessageLines(REAL_PROBE_OUTPUT).stream()
            .noneMatch(l -> l.contains("Database server") || l.contains("connect to sample")));
    }

    @Test
    void handlesCarriageReturnsAndNullInput() {
        assertEquals(List.of("SQL0204N  undefined name"),
            BuildService.db2MessageLines("db2 => SQL0204N  undefined name\r\nnoise\r\n"));
        assertEquals(List.of(), BuildService.db2MessageLines(null));
        assertEquals(List.of(), BuildService.db2MessageLines(""));
    }
}
