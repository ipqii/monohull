package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the argv tokenizer behind the DB container's command. Null (rather than an empty
 * list) matters: it is what tells runDbContainer to leave the container's command unset so
 * the image's own CMD applies.
 */
class DockerServiceSplitCommandTest {

    @Test
    void blankInputMeansNoCommand() {
        assertNull(DockerService.splitCommand(null));
        assertNull(DockerService.splitCommand(""));
        assertNull(DockerService.splitCommand("   "));
        assertNull(DockerService.splitCommand("\t\n"));
    }

    @Test
    void singleArgument() {
        assertEquals(List.of("restore"), DockerService.splitCommand("restore"));
    }

    @Test
    void splitsOnWhitespaceAndCollapsesRuns() {
        assertEquals(List.of("restore", "--file", "backup.tar.gz"),
            DockerService.splitCommand("  restore   --file\tbackup.tar.gz "));
    }

    @Test
    void quotedArgumentKeepsItsSpaces() {
        assertEquals(List.of("restore", "--file", "my backup.tar.gz"),
            DockerService.splitCommand("restore --file \"my backup.tar.gz\""));
        assertEquals(List.of("restore", "--file", "my backup.tar.gz"),
            DockerService.splitCommand("restore --file 'my backup.tar.gz'"));
    }

    @Test
    void quotesGroupRatherThanBecomeContent() {
        assertEquals(List.of("--opt=a b"), DockerService.splitCommand("\"--opt=a b\""));
        assertEquals(List.of("--opt=a b"), DockerService.splitCommand("--opt=\"a b\""));
    }

    @Test
    void theOtherQuoteCharacterSurvivesInsideQuotes() {
        assertEquals(List.of("say \"hi\""), DockerService.splitCommand("'say \"hi\"'"));
    }

    @Test
    void emptyQuotedArgumentIsPreserved() {
        assertEquals(List.of("restore", ""), DockerService.splitCommand("restore \"\""));
    }
}
