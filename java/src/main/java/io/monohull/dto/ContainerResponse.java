package io.monohull.dto;

public record ContainerResponse(
    Long id,
    String containerName,
    String dockerContainerId,
    String role,
    String image,
    String ports,
    String status,
    ContainerStateResponse liveState
) {}
