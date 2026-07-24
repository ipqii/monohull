package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEnvironmentRequest(
    @NotBlank String name,
    @NotNull Long imageConfigId,
    boolean staticPorts,
    Integer appHttpPort,
    Integer appHttpsPort,
    Integer dbPort,
    boolean includeMock,
    Integer mockHostPort,
    boolean includeSmtp,
    Integer smtpHostPort,
    Integer smtpUiHostPort
) {}
