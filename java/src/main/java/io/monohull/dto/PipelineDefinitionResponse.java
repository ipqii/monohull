package io.monohull.dto;

import java.util.List;

public record PipelineDefinitionResponse(
    Long id,
    String name,
    String description,
    Long environmentId,
    List<PipelineStepDetailResponse> steps,
    String createdAt,
    String updatedAt
) {}
