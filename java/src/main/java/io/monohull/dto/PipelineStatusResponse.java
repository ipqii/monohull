package io.monohull.dto;

import java.util.List;

public record PipelineStatusResponse(
    String pipelineRunId,
    String status,
    List<PipelineStepResponse> steps
) {}
