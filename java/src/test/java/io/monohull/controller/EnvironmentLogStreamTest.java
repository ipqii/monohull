package io.monohull.controller;

import io.monohull.dto.EnvironmentResponse;
import io.monohull.entity.BuildLogEntity;
import io.monohull.service.EnvironmentService;
import io.monohull.service.LogSink;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #29: the Logs tab missed everything logged before it was opened, and the download only
 * held what the viewer had loaded. The stream now resumes after the last saved line the client
 * has, and the download reads the whole saved log.
 */
class EnvironmentLogStreamTest {

    private final EnvironmentService envService = mock(EnvironmentService.class);
    private final LogSink sink = new LogSink();
    private final EnvironmentController controller = new EnvironmentController(envService, sink);

    {
        EnvironmentResponse env = mock(EnvironmentResponse.class);
        when(env.buildId()).thenReturn("b1");
        when(env.name()).thenReturn("maximo-vanilla-5");
        when(envService.getEnvironment(7L)).thenReturn(env);
    }

    private static BuildLogEntity row(long id, String line) {
        BuildLogEntity e = new BuildLogEntity();
        e.setId(id);
        e.setLine(line);
        return e;
    }

    @Test
    void resumesAfterTheClientsLastLineWithoutGapsOrDuplicates() {
        // Saved after the client's history page (which ended at row 1).
        when(envService.getHistoricalLogsAfter(eq(7L), eq(1L), anyInt()))
            .thenReturn(List.of(row(2, "two"), row(3, "three")));

        List<String> got = new ArrayList<>();
        var sub = controller.streamLogs(7L, 1L, null)
            .map(ServerSentEvent::data)
            .subscribe(got::add);

        sink.append("b1", 3L, "three");   // already sent from the saved lines
        sink.append("b1", 4L, "four");
        sink.append("b1", null, "unsaved");
        sink.complete("b1");

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!sub.isDisposed() && System.nanoTime() < deadline) Thread.onSpinWait();
        assertEquals(List.of("two", "three", "four", "unsaved"), got);
    }

    @Test
    void lastEventIdFromAReconnectWinsOverTheQueryParameter() {
        when(envService.getHistoricalLogsAfter(eq(7L), eq(5L), anyInt())).thenReturn(List.of(row(6, "six")));
        sink.streamEvents("b1");   // the build's sink exists...
        sink.complete("b1");       // ...and the build has finished
        var events = controller.streamLogs(7L, 1L, "5").collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("six"), events.stream().map(ServerSentEvent::data).toList());
        assertEquals("6", events.get(0).id());
    }

    @Test
    void downloadContainsTheWholeSavedLogAcrossPages() throws Exception {
        List<BuildLogEntity> first = new ArrayList<>();
        for (long i = 1; i <= 5000; i++) first.add(row(i, "line " + i));
        when(envService.getHistoricalLogsAfter(eq(7L), eq(0L), anyInt())).thenReturn(first);
        when(envService.getHistoricalLogsAfter(eq(7L), eq(5000L), anyInt())).thenReturn(List.of(row(5001, "last")));

        var response = controller.downloadLogs(7L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();

        assertEquals(5001, lines.size());
        assertEquals("line 1", lines.get(0));
        assertEquals("last", lines.get(5000));
        assertEquals("attachment; filename=\"maximo-vanilla-5.log\"",
            response.getHeaders().getFirst("Content-Disposition"));
    }
}
