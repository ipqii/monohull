package io.monohull.dto;

import java.util.List;

public record ConfigUpdateRequest(
    String hostVolumePath,
    String dbVolumeName,
    boolean staticPorts,
    Integer appHttpPort,
    Integer appHttpsPort,
    Integer dbPort,
    String dbPassword,
    String dbCommand,
    List<ExtraEnvVar> dbExtraEnv,
    List<ExtraBind> dbExtraBinds,
    List<ExtraEnvVar> appExtraEnv,
    List<ExtraBind> appExtraBinds,
    List<ExtraEnvVar> admExtraEnv,
    List<ExtraBind> admExtraBinds
) {}
