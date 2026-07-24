package io.monohull.controller;

import io.monohull.dto.CreateEnvironmentRequest;
import io.monohull.dto.EnvironmentResponse;
import io.monohull.dto.SetPasswordResult;
import io.monohull.entity.BuildLogEntity;
import io.monohull.service.EnvironmentService;
import io.monohull.service.LogSink;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

    @GetMapping(value = "/{id}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamLogs(@PathVariable Long id) {
        EnvironmentResponse env = envService.getEnvironment(id);
        return logSink.stream(env.buildId());
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
            .map(l -> new LogLine(l.getLine(), l.getCreatedAt().toString()))
            .toList();
        return ResponseEntity.ok(new LogHistoryResponse(total, effOffset, effLimit, lines));
    }

    record LogLine(String line, String timestamp) {}
    record LogHistoryResponse(long total, int offset, int limit, List<LogLine> lines) {}
}
