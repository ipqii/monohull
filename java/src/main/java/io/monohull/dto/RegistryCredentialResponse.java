package io.monohull.dto;

import java.time.LocalDateTime;

public record RegistryCredentialResponse(
    Long id,
    String url,
    String username,
    boolean hasPassword,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}