package io.monohull.dto;

import java.util.List;

/**
 * What an {@code /api/config/import} call actually did. Returned with HTTP 200 on success.
 */
public record BundleImportResult(
    Outcome imageConfig,
    Long imageConfigId,
    Outcome pipeline,
    Long pipelineId,
    List<String> createdActionKeys,
    List<String> updatedActionKeys,
    List<String> skippedActionKeys
) {
    public enum Outcome { CREATED, UPDATED, NONE }
}
