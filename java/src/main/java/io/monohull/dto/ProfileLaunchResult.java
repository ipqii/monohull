package io.monohull.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of a one-click profile launch. {@code importResult} is present only when the
 * launch came from an uploaded bundle YAML (null when launching a stored profile, or when
 * the bundle's image config already existed and the import was skipped — see
 * {@code importSkipped}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileLaunchResult(
    BundleImportResult importResult,
    Boolean importSkipped,
    EnvironmentResponse environment
) {}
