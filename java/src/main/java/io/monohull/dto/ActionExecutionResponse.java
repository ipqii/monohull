package io.monohull.dto;

public record ActionExecutionResponse(
    String executionId,
    String actionKey,
    String status,
    Long environmentId,
    Long containerId,
    String startedAt,
    String finishedAt,
    Integer exitCode,
    String pipelineRunId,
    Integer sequenceOrder
) {}
