package io.monohull.dto;

import java.time.LocalDateTime;

public record PrBuildResponse(
    Long id,
    Long repositoryId,
    int prNumber,
    String prTitle,
    String sourceBranch,
    String targetBranch,
    String commitSha,
    String event,
    String status,
    String buildId,
    Long environmentId,
    String error,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
