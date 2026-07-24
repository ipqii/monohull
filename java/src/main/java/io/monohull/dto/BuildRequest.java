package io.monohull.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BuildRequest(

  @NotBlank String buildId,
  @NotBlank String networkName,
  @NotBlank String appContainerName,
  @NotBlank String dbContainerName,
  @NotBlank String admContainerName,

  // Images
  @NotBlank String appImage,
  @NotBlank String dbImage,
  @NotBlank String admImage,

  // Ports (host)
  @NotNull Integer appHttpPort,
  @NotNull Integer appHttpsPort,
  @NotNull Integer dbPort,

  // Mounts (host paths)
  String appConfigHostPath, // e.g., /host/.../config
  String workspaceHostPath, // e.g., /workspace
  String logsHostPath, // e.g., /logs
  String dbVolumeName // named volume for DB

) {}
