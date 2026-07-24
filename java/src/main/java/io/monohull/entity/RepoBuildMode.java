package io.monohull.entity;

/** What a PR event triggers for a connected repository. */
public enum RepoBuildMode {
    /** Clone + run the build pipeline to report pass/fail; no running environment kept. */
    BUILD_ONLY,
    /** Build and stand up a full Maximo environment for testing, auto-removed on PR close. */
    BUILD_AND_ENV
}
