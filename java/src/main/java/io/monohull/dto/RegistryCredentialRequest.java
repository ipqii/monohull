package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistryCredentialRequest(
    @NotBlank String url,
    @NotBlank String username,
    String password,
    String description
) {}