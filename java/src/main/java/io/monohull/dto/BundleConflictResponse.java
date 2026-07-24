package io.monohull.dto;

import java.util.List;

/**
 * 409 body: which rows on the destination would be overwritten by this import.
 * Callers can either delete/rename those rows manually or retry with overwrite=true.
 */
public record BundleConflictResponse(
    String error,
    List<String> conflicts
) {}
