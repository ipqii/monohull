package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePipelineRequest(
    @NotBlank String name,
    String description,
    Long environmentId,
    @NotNull List<PipelineStepRequest> steps
) {}
