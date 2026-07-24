package io.monohull.dto;

public record MaximoVersionResponse(
    String name,
    String appImage,
    String dbImage,
    String admImage
) {}
