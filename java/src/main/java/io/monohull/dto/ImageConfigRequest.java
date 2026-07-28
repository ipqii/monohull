package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ImageConfigRequest(
    @NotBlank String client,
    @NotBlank String project,
    @NotBlank String maximoVersion,
    @NotBlank String appImage,
    @NotBlank String dbImage,
    @NotBlank String admImage,
    @NotBlank String dbVendor,
    String dbName,
    Integer dbContainerPort,
    String dbCommand,
    String hostVolumePath,
    String dbVolumeName,
    String dbVolumeTarget,
    String workspacePath,
    Integer appHttpPort,
    Integer appHttpsPort,
    Integer dbPort,
    Integer mockHostPort,
    Integer smtpHostPort,
    Integer smtpUiHostPort,
    Long pipelineId,
    String launchDescription,
    boolean launchStaticPorts,
    boolean launchIncludeMock,
    boolean launchIncludeSmtp,
    List<ExtraEnvVar> dbExtraEnv,
    List<ExtraBind> dbExtraBinds,
    List<ExtraEnvVar> appExtraEnv,
    List<ExtraBind> appExtraBinds,
    List<ExtraEnvVar> admExtraEnv,
    List<ExtraBind> admExtraBinds
) {}
