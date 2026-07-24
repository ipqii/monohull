package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;

public record PipelineStepRequest(
    @NotBlank String actionKey,
    int sequenceOrder
) {}
