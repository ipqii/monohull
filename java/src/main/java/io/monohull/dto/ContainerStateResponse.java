package io.monohull.dto;

public record ContainerStateResponse(
    String state,
    boolean running,
    String startedAt,
    String finishedAt
) {}
