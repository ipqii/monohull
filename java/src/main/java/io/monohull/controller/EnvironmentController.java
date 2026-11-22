package io.monohull.controller;

import io.monohull.dto.CreateEnvironmentRequest;
import io.monohull.dto.EnvironmentResponse;
import io.monohull.dto.SetPasswordResult;
import io.monohull.entity.BuildLogEntity;
import io.monohull.service.EnvironmentService;
import io.monohull.service.LogSink;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import java.util.List;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService envService;
    private final LogSink logSink;

    public EnvironmentController(EnvironmentService envService, LogSink logSink) {
        this.envService = envService;
        this.logSink = logSink;
    }

    @PostMapping
    public ResponseEntity<EnvironmentResponse> create(@Valid @RequestBody CreateEnvironmentRequest req) {
        return ResponseEntity.ok(envService.createEnvironment(req));
    }

    @GetMapping
    public ResponseEntity<List<EnvironmentResponse>> list(
            @RequestParam(required = false) String owner) {
        return ResponseEntity.ok(envService.listEnvironments(owner));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(envService.getEnvironment(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        envService.removeEnvironment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        envService.stopEnvironment(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        envService.startEnvironment(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Add a Mailpit SMTP container to a running environment built without one, and point
     * Maximo's mail properties at it. Maximo reads those at startup, so pass
     * {@code restartApp=true} to restart the APP container as part of the call.
     */
    @PostMapping("/{id}/smtp")
    public ResponseEntity<EnvironmentResponse> addSmtp(
            @PathVariable Long id, @RequestParam(defaultValue = "false") boolean restartApp) {
        envService.addSmtp(id, restartApp);
        return ResponseEntity.ok(envService.getEnvironment(id));
    }

    /**
     * Change a Maximo user's password (default MAXADMIN) on this environment's ADM
     * container. These Manage-without-MAS containers expose no UI for it — the value
     * is re-encrypted with Maximo's cryptox cipher and written to MAXUSER.
     */
    @PostMapping("/{id}/maximo-user-password")
    public ResponseEntity<SetPasswordResult> setMaximoUserPassword(
            @PathVariable Long id, @RequestBody SetPasswordRequest req) {
        return ResponseEntity.ok(envService.setMaximoUserPassword(id, req.loginId(), req.password()));
    }

    record SetPasswordRequest(String loginId, String password) {}

    /** Most saved lines a stream sends to fill the gap before going live. */
    private static final int MAX_GAP_FILL = 50_000;
    private static final int LOG_PAGE = 5_000;

    /**
     * Live build log over SSE. Each saved line carries its build_log row id as the SSE id.
     *
     * <p>The live sink doesn't replay, so a viewer that subscribes late only gets lines from
     * then on. With {@code after} (the last row id the client already has, e.g. the end of the
     * history page it loaded) — or the browser's {@code Last-Event-ID} on a reconnect — the
     * stream first sends the saved lines after that id, then the live ones it hasn't sent,
     * so client-side history + stream is the complete log. The live sink is subscribed before
     * the saved lines are read, so nothing written in between is lost.
     */
    @GetMapping(value = "/{id}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLogs(
            @PathVariable Long id,
            @RequestParam(required = false) Long after,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        EnvironmentResponse env = envService.getEnvironment(id);
        Flux<LogSink.LogEvent> live = logSink.streamEvents(env.buildId());

        Long resumeAfter = parseId(lastEventId);
        if (resumeAfter == null) resumeAfter = after;
        if (resumeAfter == null) {
            return live.map(EnvironmentController::toSse);
        }

        final long from = resumeAfter;
        AtomicLong lastSaved = new AtomicLong(from);
        Flux<Tagged> saved = Flux.defer(() -> Flux.fromIterable(savedLinesAfter(id, from)))
            .subscribeOn(Schedulers.boundedElastic())
            .map(e -> new Tagged(true, e));
        // mergeSequential subscribes to both at once and buffers the live lines until the saved
        // ones are out, so the cut-over can't drop a line. Large prefetch: the sink is shared,
        // and a subscriber with no demand stalls it for every viewer.
        return Flux.mergeSequential(LOG_PAGE, saved, live.map(e -> new Tagged(false, e)))
            .filter(t -> {
                Long lineId = t.event().id();
                if (t.saved()) {
                    lastSaved.set(lineId);
                    return true;
                }
                return lineId == null || lineId > lastSaved.get();
            })
            .map(t -> toSse(t.event()));
    }

    private record Tagged(boolean saved, LogSink.LogEvent event) {}

    private List<LogSink.LogEvent> savedLinesAfter(Long envId, long afterId) {
        List<LogSink.LogEvent> out = new ArrayList<>();
        long cursor = afterId;
        while (out.size() < MAX_GAP_FILL) {
            List<BuildLogEntity> page = envService.getHistoricalLogsAfter(envId, cursor,
                Math.min(LOG_PAGE, MAX_GAP_FILL - out.size()));
            for (BuildLogEntity l : page) {
                out.add(new LogSink.LogEvent(l.getId(), l.getLine()));
                cursor = l.getId();
            }
            if (page.size() < LOG_PAGE) break;
        }
        return out;
    }

    private static ServerSentEvent<String> toSse(LogSink.LogEvent e) {
        ServerSentEvent.Builder<String> b = ServerSentEvent.builder(e.line());
        if (e.id() != null) b.id(String.valueOf(e.id()));
        return b.build();
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The environment's whole saved build log as a text file, read in pages. */
    @GetMapping("/{id}/logs/download")
    public ResponseEntity<StreamingResponseBody> downloadLogs(@PathVariable Long id) {
        EnvironmentResponse env = envService.getEnvironment(id);
        StreamingResponseBody body = out -> {
            Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            long cursor = 0;
            while (true) {
                List<BuildLogEntity> page = envService.getHistoricalLogsAfter(id, cursor, LOG_PAGE);
                for (BuildLogEntity l : page) {
                    w.write(l.getLine());
                    w.write('\n');
                    cursor = l.getId();
                }
                if (page.size() < LOG_PAGE) break;
            }
            w.flush();
        };
        String filename = (env.name() != null ? env.name() : "build").replaceAll("[^A-Za-z0-9._-]", "-") + ".log";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(body);
    }

    /**
     * Default page size when the client doesn't specify a limit. The old endpoint returned every
     * row in one shot, which can OOM the browser tab on a multi-hour build. Callers that need
     * everything must page through with explicit offset/limit.
     */
    private static final int DEFAULT_LOG_LIMIT = 2000;
    private static final int MAX_LOG_LIMIT = 10000;

    @GetMapping("/{id}/logs/history")
    public ResponseEntity<LogHistoryResponse> getLogHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit) {

        long total = envService.countHistoricalLogs(id);

        int effLimit = limit == null ? DEFAULT_LOG_LIMIT : Math.min(Math.max(1, limit), MAX_LOG_LIMIT);
        // No offset → return the last page so the user lands on the most recent output. The
        // service paginates in page-aligned chunks (it uses Spring Data PageRequest), so we
        // pick the highest page index. If the last page is partial (e.g. total=2500, limit=2000
        // gives page 1 with only 500 rows), that's fine — the caller pages backwards from
        // there with "Load older".
        int effOffset;
        if (offset == null) {
            int lastPageIndex = total == 0 ? 0 : (int) ((total - 1) / effLimit);
            effOffset = lastPageIndex * effLimit;
        } else {
            int rawOffset = Math.max(0, offset);
            effOffset = (rawOffset / effLimit) * effLimit;
        }

        List<BuildLogEntity> logs = envService.getHistoricalLogsPage(id, effOffset, effLimit);
        List<LogLine> lines = logs.stream()
            .map(l -> new LogLine(l.getId(), l.getLine(), l.getCreatedAt().toString()))
            .toList();
        return ResponseEntity.ok(new LogHistoryResponse(total, effOffset, effLimit, lines));
    }

    record LogLine(Long id, String line, String timestamp) {}
    record LogHistoryResponse(long total, int offset, int limit, List<LogLine> lines) {}
}
