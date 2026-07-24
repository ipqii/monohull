package io.monohull.controller;

import io.monohull.dto.*;
import io.monohull.entity.ActionLogEntity;
import io.monohull.entity.CustomActionEntity;
import io.monohull.service.ActionService;
import io.monohull.service.LogSink;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ActionController {

    private final ActionService actionService;
    private final LogSink logSink;

    public ActionController(ActionService actionService, LogSink logSink) {
        this.actionService = actionService;
        this.logSink = logSink;
    }

    @GetMapping("/environments/{envId}/actions")
    public ResponseEntity<List<ActionDefinitionResponse>> getActions(@PathVariable Long envId) {
        return ResponseEntity.ok(actionService.getAvailableActions(envId));
    }

    @PostMapping("/environments/{envId}/actions/execute")
    public ResponseEntity<ActionExecutionResponse> executeAction(@PathVariable Long envId,
                                                                  @Valid @RequestBody ExecuteActionRequest req) {
        return ResponseEntity.ok(actionService.executeAction(envId, req));
    }

    @GetMapping(value = "/actions/executions/{executionId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamExecutionLogs(@PathVariable String executionId) {
        return logSink.stream(executionId);
    }

    @GetMapping("/environments/{envId}/actions/history")
    public ResponseEntity<List<ActionExecutionResponse>> getHistory(@PathVariable Long envId) {
        return ResponseEntity.ok(actionService.getExecutionHistory(envId));
    }

    @GetMapping("/actions/executions/{executionId}/logs/history")
    public ResponseEntity<List<LogLine>> getExecutionLogHistory(@PathVariable String executionId) {
        List<ActionLogEntity> logs = actionService.getExecutionLogs(executionId);
        List<LogLine> lines = logs.stream()
            .map(l -> new LogLine(l.getLine(), l.getCreatedAt().toString()))
            .toList();
        return ResponseEntity.ok(lines);
    }

    // Pipeline endpoints

    @PostMapping("/environments/{envId}/pipeline/start")
    public ResponseEntity<Void> startPipeline(@PathVariable Long envId) {
        actionService.runPipelineAsync(envId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/environments/{envId}/pipeline/status")
    public ResponseEntity<PipelineStatusResponse> getPipelineStatus(@PathVariable Long envId) {
        return ResponseEntity.ok(actionService.getPipelineStatus(envId));
    }

    // Custom action CRUD

    @PostMapping("/config/actions")
    public ResponseEntity<CustomActionResponse> createCustomAction(@Valid @RequestBody CreateCustomActionRequest req) {
        CustomActionEntity entity = actionService.createCustomAction(req);
        return ResponseEntity.ok(toResponse(entity));
    }

    @PutMapping("/config/actions/{id}")
    public ResponseEntity<CustomActionResponse> updateCustomAction(@PathVariable Long id,
                                                                    @Valid @RequestBody CreateCustomActionRequest req) {
        CustomActionEntity entity = actionService.updateCustomAction(id, req);
        return ResponseEntity.ok(toResponse(entity));
    }

    @GetMapping("/config/actions")
    public ResponseEntity<List<CustomActionResponse>> listActions() {
        List<CustomActionResponse> list = actionService.listAllActions().stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/config/actions/{id}")
    public ResponseEntity<Void> deleteCustomAction(@PathVariable Long id) {
        actionService.deleteCustomAction(id);
        return ResponseEntity.noContent().build();
    }

    private CustomActionResponse toResponse(CustomActionEntity e) {
        return new CustomActionResponse(
            e.getId(), e.getActionKey(), e.getName(), e.getDescription(),
            e.getTargetRole(), e.getCommand(), e.getWorkingDir(),
            e.getTimeoutSeconds(),
            e.getImageConfig() != null ? e.getImageConfig().getId() : null,
            e.getEnvironment() != null ? e.getEnvironment().getId() : null,
            e.getCreatedAt().toString(),
            e.getAfterAction(), e.isAutoRun(), e.isBuiltIn(),
            e.getExecutionType(), e.getAllowedExitCodes(),
            e.getRunAsUser()
        );
    }

    record LogLine(String line, String timestamp) {}

    record CustomActionResponse(
        Long id, String actionKey, String name, String description,
        String targetRole, String command, String workingDir,
        int timeoutSeconds, Long imageConfigId, Long environmentId, String createdAt,
        String afterAction, boolean autoRun, boolean builtIn,
        String executionType, String allowedExitCodes, String runAsUser
    ) {}
}
