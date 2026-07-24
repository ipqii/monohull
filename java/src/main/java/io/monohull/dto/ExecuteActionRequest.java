package io.monohull.dto;

import jakarta.validation.constraints.NotNull;

public record ExecuteActionRequest(
    @NotNull String actionId,
    @NotNull Long containerId
) {}
