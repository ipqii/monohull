package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create/update payload for a connected repository. {@code cloneToken}/{@code statusToken}/
 * {@code sshPrivateKey}/{@code sshPassphrase} are write-only secrets — when blank on update,
 * the existing value is kept (mirrors the registry-credential pattern). The webhook secret is
 * generated server-side, not supplied here. {@code authMethod} selects HTTPS vs SSH cloning;
 * blank defaults to HTTPS.
 */
public record ConnectedRepositoryRequest(
    @NotBlank String name,
    @NotBlank String provider,        // GITHUB | BITBUCKET | GITLAB
    String authMethod,                // HTTPS | SSH (blank => HTTPS)
    @NotBlank String repoUrl,
    @NotBlank String repoFullName,    // owner/repo
    String defaultBranch,
    @NotBlank String buildMode,       // BUILD_ONLY | BUILD_AND_ENV
    @NotNull Long imageConfigId,
    String cloneUsername,
    String cloneToken,
    String sshPrivateKey,
    String sshPassphrase,
    String statusToken,
    Integer maxConcurrent,
    Boolean enabled
) {}
