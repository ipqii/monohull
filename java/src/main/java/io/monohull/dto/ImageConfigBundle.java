package io.monohull.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Portable bundle of an ImageConfig template, the pipeline it links to, and the
 * custom (non-built-in) actions that pipeline's steps reference. Serialised as YAML
 * by the export endpoint and consumed verbatim by the import endpoint.
 *
 * Stripped on export and re-generated on import: DB ids, timestamps, FK numbers,
 * environment_id on the pipeline (template-scope only), and any builtIn=true rows
 * (those resolve on the destination via application.yml on startup).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageConfigBundle(
    String kind,
    Integer schemaVersion,
    ImageConfigPayload imageConfig,
    PipelinePayload pipeline,
    List<CustomActionPayload> customActions,
    LaunchPayload launch
) {
    public static final String KIND = "madeImageConfigBundle";
    // v1: no launch section. v2 adds the optional launch section (profile defaults).
    // Import accepts MIN..SCHEMA_VERSION; export always writes SCHEMA_VERSION.
    public static final int MIN_SCHEMA_VERSION = 1;
    public static final int SCHEMA_VERSION = 2;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImageConfigPayload(
        String client,
        String project,
        String maximoVersion,
        String appImage,
        String dbImage,
        String admImage,
        String dbVendor,
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
        List<ExtraEnvVar> dbExtraEnv,
        List<ExtraBind> dbExtraBinds,
        List<ExtraEnvVar> appExtraEnv,
        List<ExtraBind> appExtraBinds,
        List<ExtraEnvVar> admExtraEnv,
        List<ExtraBind> admExtraBinds
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PipelinePayload(
        String name,
        String description,
        List<StepPayload> steps
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepPayload(
        String actionKey,
        int sequenceOrder
    ) {}

    /**
     * Profile launch defaults: what a one-click launch on the destination uses in place
     * of New Build dialog input. All fields optional; absent booleans mean false.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LaunchPayload(
        String description,
        Boolean staticPorts,
        Boolean includeMock,
        Boolean includeSmtp
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomActionPayload(
        String actionKey,
        String name,
        String description,
        String targetRole,
        String command,
        String workingDir,
        Integer timeoutSeconds,
        String afterAction,
        Boolean autoRun,
        String executionType,
        String allowedExitCodes,
        String runAsUser,
        Boolean verbose,
        Boolean builtIn
    ) {}
}
