package io.monohull.dto;

public record PipelineStepDetailResponse(
    Long id,
    String actionKey,
    String actionName,
    String targetRole,
    int sequenceOrder
) {}
