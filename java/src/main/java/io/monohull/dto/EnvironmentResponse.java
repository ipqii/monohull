package io.monohull.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EnvironmentResponse(
    Long id,
    String name,
    String buildId,
    String maximoVersion,
    String dbVendor,
    String dbName,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String publicUrl,
    String createdBy,
    List<ContainerResponse> containers
) {}
