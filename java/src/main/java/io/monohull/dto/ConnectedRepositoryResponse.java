package io.monohull.dto;

import java.time.LocalDateTime;

/**
 * Connected-repository view. {@code webhookSecret} is returned so the admin can paste it into
 * the provider's webhook config; {@code webhookUrl} is the endpoint to register. Clone/status
 * tokens and the SSH key are never echoed back — only {@code hasCloneToken}/{@code hasStatusToken}/
 * {@code hasSshKey} booleans. {@code authMethod} is HTTPS or SSH.
 */
public record ConnectedRepositoryResponse(
    Long id,
    String name,
    String provider,
    String authMethod,
    String repoUrl,
    String repoFullName,
    String defaultBranch,
    String buildMode,
    Long imageConfigId,
    String imageConfigName,
    String webhookSecret,
    String webhookUrl,
    String cloneUsername,
    boolean hasCloneToken,
    boolean hasSshKey,
    boolean hasStatusToken,
    int maxConcurrent,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
