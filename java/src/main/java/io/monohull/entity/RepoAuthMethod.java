package io.monohull.entity;

/** How Monohull authenticates when cloning a connected repository. */
public enum RepoAuthMethod {
    /** HTTPS clone URL; optional username + token/PAT embedded for private repos. */
    HTTPS,
    /** SSH clone URL (git@host:owner/repo.git or ssh://...) using a stored deploy key. */
    SSH
}
