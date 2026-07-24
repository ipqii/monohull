package io.monohull.entity;

/** Lifecycle of a single PR build. */
public enum PrBuildStatus {
    QUEUED,
    CLONING,
    BUILDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    /** Replaced by a newer build for the same PR (a new commit was pushed). */
    SUPERSEDED,
    /** The PR was closed/merged and any associated environment was removed. */
    REMOVED
}
