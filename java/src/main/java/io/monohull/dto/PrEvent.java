package io.monohull.dto;

import io.monohull.entity.PrBuildEvent;

/** A pull/merge request webhook normalized across providers. */
public record PrEvent(
    PrBuildEvent event,
    int prNumber,
    String title,
    String sourceBranch,
    String targetBranch,
    String sha,
    String repoFullName,
    boolean merged
) {}
