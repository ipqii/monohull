package io.monohull.dto;

public record PipelineStepResponse(
    int order,
    String actionId,
    String actionName,
    String targetRole,
    String status,
    String executionId,
    String startedAt,
    String finishedAt,
    Integer exitCode
) {}
