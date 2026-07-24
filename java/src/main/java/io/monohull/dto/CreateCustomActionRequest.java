package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomActionRequest(
    @NotBlank String name,
    String description,
    @NotBlank String targetRole,
    @NotBlank String command,
    String workingDir,
    Integer timeoutSeconds,
    Long imageConfigId,
    Long environmentId,
    Boolean autoRun,
    String executionType,
    String allowedExitCodes,
    String runAsUser
) {}
