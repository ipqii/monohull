package io.monohull.entity;

/** Git provider a connected repository belongs to. Each signs/verifies webhooks differently. */
public enum RepoProvider {
    GITHUB,
    BITBUCKET,
    GITLAB
}
