package io.monohull.dto;

public record ActionDefinitionResponse(
    String id,
    String name,
    String description,
    String targetRole,
    boolean builtIn,
    Long customActionId,
    String afterAction,
    boolean autoRun,
    String executionType,
    String runAsUser,
    Long imageConfigId,
    Long environmentId
) {}
