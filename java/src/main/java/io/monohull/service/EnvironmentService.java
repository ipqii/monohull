package io.monohull.service;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import io.monohull.dto.*;
import io.monohull.entity.*;
import io.monohull.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvironmentRepository envRepo;
    private final ContainerRepository containerRepo;
    private final EnvironmentConfigRepository configRepo;
    private final BuildLogRepository logRepo;
    private final ImageConfigRepository imageConfigRepo;
    private final BuildService buildService;
    private final DockerService dockerService;

    @Value("${monohull.mock.image:monohull/mock-receiver:latest}")
    private String mockImage;

    @Value("${monohull.smtp.image:axllent/mailpit:latest}")
    private String smtpImage;

    // Public base domain for per-env Maximo URLs (e.g. "maximo.example.com").
    // Empty (default) => no public URL is advertised (LAN-only).
    @Value("${monohull.public.maximo-domain:}")
    private String maximoDomain;

    public EnvironmentService(EnvironmentRepository envRepo, ContainerRepository containerRepo,
                              EnvironmentConfigRepository configRepo, BuildLogRepository logRepo,
                              ImageConfigRepository imageConfigRepo,
                              BuildService buildService, DockerService dockerService) {
        this.envRepo = envRepo;
        this.containerRepo = containerRepo;
        this.configRepo = configRepo;
        this.logRepo = logRepo;
        this.imageConfigRepo = imageConfigRepo;
        this.buildService = buildService;
        this.dockerService = dockerService;
    }

    @Transactional
    public EnvironmentResponse createEnvironment(CreateEnvironmentRequest req) {
        return toResponse(provisionEnvironment(req, null, true));
    }

    /**
     * Builds and persists an environment and, when {@code autoBuild} is true, kicks off the
     * async build after commit. A non-blank {@code workspaceOverride} replaces the workspace
     * bind source (used by PR builds to mount the per-PR checkout). Returns the saved entity
     * so callers can drive the build and await it themselves.
     */
    @Transactional
    public EnvironmentEntity provisionEnvironment(CreateEnvironmentRequest req, String workspaceOverride, boolean autoBuild) {
        ImageConfigEntity imageConfig = imageConfigRepo.findById(req.imageConfigId())
            .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + req.imageConfigId()));

        String buildId = UUID.randomUUID().toString();
        String sanitizedName = req.name().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String networkName = sanitizedName;
        DbVendor dbVendor = DbVendor.valueOf(imageConfig.getDbVendor().toUpperCase());

        EnvironmentEntity env = new EnvironmentEntity();
        env.setName(req.name());
        env.setBuildId(buildId);
        env.setNetworkName(networkName);
        env.setMaximoVersion(imageConfig.getMaximoVersion());
        env.setDbVendor(dbVendor);
        env.setStatus(EnvironmentStatus.PENDING);
        env.setImageConfig(imageConfig);
        env.setCreatedBy(currentUsername());

        // Config - resolve ports first so containers can reference them
        EnvironmentConfigEntity config = new EnvironmentConfigEntity();
        config.setEnvironment(env);
        config.setHostVolumePath(imageConfig.getHostVolumePath());
        config.setDbVolumeName(imageConfig.getDbVolumeName());
        config.setStaticPorts(req.staticPorts());

        // Inherit container extras (env vars + binds) from the image config; the user can
        // still edit them per-environment afterwards via the Configuration tab.
        config.setDbCommand(imageConfig.getDbCommand());
        config.setDbExtraEnv(imageConfig.getDbExtraEnv());
        config.setDbExtraBinds(imageConfig.getDbExtraBinds());
        config.setAppExtraEnv(imageConfig.getAppExtraEnv());
        config.setAppExtraBinds(imageConfig.getAppExtraBinds());
        config.setAdmExtraEnv(imageConfig.getAdmExtraEnv());
        config.setAdmExtraBinds(imageConfig.getAdmExtraBinds());

        // Total ports to allocate: appHttp, appHttps, db, then (mock?), (smtpSmtp + smtpUi)?
        int needed = 3 + (req.includeMock() ? 1 : 0) + (req.includeSmtp() ? 2 : 0);
        if (req.staticPorts()) {
            // Static ports primarily come from the image config (the template). The
            // request fields are still accepted as one-off overrides for direct API
            // callers, but the Create Environment dialog no longer collects them.
            config.setAppHttpPort(firstNonNull(req.appHttpPort(), imageConfig.getAppHttpPort()));
            config.setAppHttpsPort(firstNonNull(req.appHttpsPort(), imageConfig.getAppHttpsPort()));
            config.setDbPort(firstNonNull(req.dbPort(), imageConfig.getDbPort()));
            if (req.includeMock()) {
                config.setMockHostPort(firstNonNull(req.mockHostPort(), imageConfig.getMockHostPort()));
            }
            if (req.includeSmtp()) {
                config.setSmtpHostPort(firstNonNull(req.smtpHostPort(), imageConfig.getSmtpHostPort()));
                config.setSmtpUiHostPort(firstNonNull(req.smtpUiHostPort(), imageConfig.getSmtpUiHostPort()));
            }
            List<String> missing = new ArrayList<>();
            if (config.getAppHttpPort() == null) missing.add("appHttpPort");
            if (config.getAppHttpsPort() == null) missing.add("appHttpsPort");
            if (config.getDbPort() == null) missing.add("dbPort");
            if (req.includeMock() && config.getMockHostPort() == null) missing.add("mockHostPort");
            if (req.includeSmtp() && config.getSmtpHostPort() == null) missing.add("smtpHostPort");
            if (req.includeSmtp() && config.getSmtpUiHostPort() == null) missing.add("smtpUiHostPort");
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                    "Static ports requested but image config '" + imageConfig.getClient() + " / "
                    + imageConfig.getProject() + "' has no value for: " + String.join(", ", missing)
                    + ". Set them on the image config or untoggle Static Ports.");
            }
        } else {
            int[] ports = allocateDynamicPorts(needed);
            int idx = 0;
            config.setAppHttpPort(ports[idx++]);
            config.setAppHttpsPort(ports[idx++]);
            config.setDbPort(ports[idx++]);
            if (req.includeMock()) config.setMockHostPort(ports[idx++]);
            if (req.includeSmtp()) {
                config.setSmtpHostPort(ports[idx++]);
                config.setSmtpUiHostPort(ports[idx++]);
            }
        }
        config.setMockEnabled(req.includeMock());
        config.setSmtpEnabled(req.includeSmtp());

        // DB container
        ContainerEntity dbContainer = new ContainerEntity();
        dbContainer.setEnvironment(env);
        dbContainer.setContainerName(networkName + "-db");
        dbContainer.setRole(ContainerRole.DB);
        dbContainer.setImage(imageConfig.getDbImage());
        dbContainer.setStatus(ContainerStatus.PENDING);
        dbContainer.setPorts(config.getDbPort() + ":" + (imageConfig.getDbImage().contains("db2") ? "50000" : "1521"));

        // APP container
        ContainerEntity appContainer = new ContainerEntity();
        appContainer.setEnvironment(env);
        appContainer.setContainerName(networkName + "-app");
        appContainer.setRole(ContainerRole.APP);
        appContainer.setImage(imageConfig.getAppImage());
        appContainer.setStatus(ContainerStatus.PENDING);
        appContainer.setPorts(config.getAppHttpPort() + ":9080," + config.getAppHttpsPort() + ":9443");

        // ADM container
        ContainerEntity admContainer = new ContainerEntity();
        admContainer.setEnvironment(env);
        admContainer.setContainerName(networkName + "-adm");
        admContainer.setRole(ContainerRole.ADM);
        admContainer.setImage(imageConfig.getAdmImage());
        admContainer.setStatus(ContainerStatus.PENDING);

        env.getContainers().add(dbContainer);
        env.getContainers().add(appContainer);
        env.getContainers().add(admContainer);

        if (req.includeMock()) {
            ContainerEntity mockContainer = new ContainerEntity();
            mockContainer.setEnvironment(env);
            mockContainer.setContainerName(networkName + "-mock");
            mockContainer.setRole(ContainerRole.MOCK);
            mockContainer.setImage(mockImage);
            mockContainer.setStatus(ContainerStatus.PENDING);
            mockContainer.setPorts(config.getMockHostPort() + ":8085");
            env.getContainers().add(mockContainer);
        }

        if (req.includeSmtp()) {
            ContainerEntity smtpContainer = new ContainerEntity();
            smtpContainer.setEnvironment(env);
            smtpContainer.setContainerName(networkName + "-smtp");
            smtpContainer.setRole(ContainerRole.SMTP);
            smtpContainer.setImage(smtpImage);
            smtpContainer.setStatus(ContainerStatus.PENDING);
            smtpContainer.setPorts(config.getSmtpHostPort() + ":1025," + config.getSmtpUiHostPort() + ":8025");
            env.getContainers().add(smtpContainer);
        }

        if (workspaceOverride != null && !workspaceOverride.isBlank()) {
            config.setWorkspacePathOverride(workspaceOverride);
        }
        env.setConfig(config);

        env = envRepo.save(env);

        Long envId = env.getId();
        if (autoBuild) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    buildService.startBuildForEnvironment(envId);
                }
            });
        }

        return env;
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> listEnvironments() {
        return listEnvironments(null);
    }

    /**
     * Lists non-removed environments, optionally filtered to those created by {@code owner}
     * (the creator's Monohull username / email). Used by external dashboards to show a
     * user their own environments.
     */
    @Transactional(readOnly = true)
    public List<EnvironmentResponse> listEnvironments(String owner) {
        return envRepo.findAll().stream()
            .filter(e -> e.getStatus() != EnvironmentStatus.REMOVED)
            .filter(e -> owner == null || owner.equalsIgnoreCase(e.getCreatedBy()))
            .map(this::toResponseWithLiveState)
            .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironment(Long id) {
        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        return toResponseWithLiveState(env);
    }

    @Transactional
    public void stopEnvironment(Long id) {
        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        for (ContainerEntity c : env.getContainers()) {
            if (c.getDockerContainerId() != null) {
                try {
                    dockerService.stopContainer(c.getDockerContainerId());
                    c.setStatus(ContainerStatus.STOPPED);
                } catch (Exception e) {
                    log.warn("Failed to stop container {}: {}", c.getContainerName(), e.getMessage());
                }
            }
        }
        env.setStatus(EnvironmentStatus.STOPPED);
        envRepo.save(env);
    }

    @Transactional
    public void startEnvironment(Long id) {
        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        for (ContainerEntity c : env.getContainers()) {
            if (c.getDockerContainerId() != null) {
                try {
                    dockerService.startContainer(c.getDockerContainerId());
                    c.setStatus(ContainerStatus.RUNNING);
                } catch (Exception e) {
                    log.warn("Failed to start container {}: {}", c.getContainerName(), e.getMessage());
                }
            }
        }
        env.setStatus(EnvironmentStatus.RUNNING);
        envRepo.save(env);
    }

    @Transactional
    public void removeEnvironment(Long id) {
        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));

        // Best-effort sweep: every resource is attempted even when an earlier one fails,
        // so one docker hiccup can't strand the rest. Failures are collected and thrown
        // at the end; the env is only marked REMOVED on a clean sweep, and every step is
        // idempotent (already-gone is success), so "remove again" retries exactly what's
        // left.
        List<String> failures = new ArrayList<>();

        for (ContainerEntity c : env.getContainers()) {
            // Fall back to the container name: a container whose start failed (or whose
            // create raced a crash) never had its ID persisted, but the name is
            // deterministic and docker's remove accepts either.
            String ref = c.getDockerContainerId() != null ? c.getDockerContainerId() : c.getContainerName();
            try {
                dockerService.removeIfExists(ref);
            } catch (Exception e) {
                failures.add("container " + c.getContainerName() + " (" + e.getMessage() + ")");
            }
        }

        try {
            dockerService.removeNetwork(env.getNetworkName());
        } catch (Exception e) {
            failures.add("network " + env.getNetworkName() + " (" + e.getMessage() + ")");
        }

        // Remove the named DB volume that BuildService created for this env.
        EnvironmentConfigEntity config = env.getConfig();
        String sanitized = env.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        String dbVolume = (config != null && config.getDbVolumeName() != null)
            ? config.getDbVolumeName() : "made-" + sanitized + "-db";
        try {
            dockerService.removeVolume(dbVolume);
        } catch (Exception e) {
            failures.add("volume " + dbVolume + " (" + e.getMessage() + ")");
        }

        // Clean up the per-env subdir under hostVolumePath (contains config/ and logs/).
        // BuildService.createEnvSubdir lays this out as <hostVolumePath>/<sanitized-env>/.
        // Monohull can't touch the host filesystem directly, so DockerService spawns a
        // short-lived busybox container that bind-mounts the parent and rm -rf's the
        // subdir from inside. Best-effort by design (it logs its own warnings).
        if (config != null && config.getHostVolumePath() != null && !config.getHostVolumePath().isBlank()) {
            dockerService.removeHostPathSubdir(config.getHostVolumePath(), sanitized);
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Removed what it could, but teardown is incomplete — "
                + String.join("; ", failures)
                + ". Is the Docker daemon reachable? Fix the cause and remove the environment again: "
                + "already-removed resources are skipped on retry.");
        }

        env.setStatus(EnvironmentStatus.REMOVED);
        envRepo.save(env);
    }

    public ContainerStateResponse getContainerLiveState(Long containerId) {
        ContainerEntity c = containerRepo.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));
        if (c.getDockerContainerId() == null) {
            return new ContainerStateResponse("pending", false, null, null);
        }
        return inspectLiveState(c.getDockerContainerId());
    }

    @Transactional
    public void restartContainer(Long containerId) {
        ContainerEntity c = requireCreated(containerId, "restart");
        dockerService.restartContainer(c.getDockerContainerId());
        c.setStatus(ContainerStatus.RUNNING);
        containerRepo.save(c);
    }

    @Transactional
    public void stopContainer(Long containerId) {
        ContainerEntity c = requireCreated(containerId, "stop");
        dockerService.stopContainer(c.getDockerContainerId());
        c.setStatus(ContainerStatus.STOPPED);
        containerRepo.save(c);
    }

    @Transactional
    public void startContainer(Long containerId) {
        ContainerEntity c = requireCreated(containerId, "start");
        dockerService.startContainer(c.getDockerContainerId());
        c.setStatus(ContainerStatus.RUNNING);
        containerRepo.save(c);
    }

    /** Resolve the docker container id for an interactive terminal, with the same
     *  "was it ever created?" guard the start/stop/restart card buttons use. */
    public String getDockerContainerIdForTerminal(Long containerId) {
        return requireCreated(containerId, "open a terminal on").getDockerContainerId();
    }

    /** Resolve a container row and insist its docker container actually exists —
     *  a null id means the build failed before creating it, which used to surface
     *  as an NPE/500 when the user clicked stop/start/restart on the card. */
    private ContainerEntity requireCreated(Long containerId, String verb) {
        ContainerEntity c = containerRepo.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));
        if (c.getDockerContainerId() == null) {
            throw new IllegalArgumentException("Cannot " + verb + " " + c.getContainerName()
                + ": its container was never created (the build failed before reaching it). "
                + "Re-run the pipeline or remove and recreate the environment.");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public java.util.List<String> getContainerLogs(Long containerId, int tail) {
        ContainerEntity c = containerRepo.findById(containerId)
            .orElseThrow(() -> new IllegalArgumentException("Container not found: " + containerId));
        if (c.getDockerContainerId() == null) return java.util.List.of();
        return dockerService.fetchContainerLogs(c.getDockerContainerId(), tail);
    }

    public List<BuildLogEntity> getHistoricalLogs(Long environmentId) {
        return logRepo.findByEnvironmentIdOrderByCreatedAtAsc(environmentId);
    }

    public long countHistoricalLogs(Long environmentId) {
        return logRepo.countByEnvironmentId(environmentId);
    }

    /**
     * Returns a page of log lines. `offset` must be a multiple of `limit` — callers paginate in
     * fixed-size windows so we can use Spring Data {@link org.springframework.data.domain.PageRequest}
     * without needing a custom offset/limit query.
     */
    public List<BuildLogEntity> getHistoricalLogsPage(Long environmentId, int offset, int limit) {
        if (limit <= 0) return List.of();
        int pageIndex = Math.max(0, offset) / limit;
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(pageIndex, limit);
        return logRepo.findByEnvironmentIdOrderByIdAsc(environmentId, pageable);
    }

    /**
     * Change a Maximo user's password on the environment's ADM container.
     *
     * <p>Monohull's Manage-without-MAS containers expose no UI to do this. Passwords live in
     * {@code MAXUSER.PASSWORD} encrypted with Maximo's cryptox cipher ({@code MXCipherX}),
     * so we re-encrypt the new value using Maximo's own classpath on ADM and update the row
     * — mirroring the build-time {@code set-maxadmin-password} action, parameterised.
     * Credentials are base64-encoded into the command (no shell interpolation), and the JDBC
     * target is computed here (DB alias {@code db} on the env network).
     */
    @Transactional(readOnly = true)
    public SetPasswordResult setMaximoUserPassword(Long id, String loginId, String password) {
        if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId is required");
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("password is required");

        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        ContainerEntity adm = env.getContainers().stream()
            .filter(c -> c.getRole() == ContainerRole.ADM)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("This environment has no ADM container"));
        if (adm.getDockerContainerId() == null) {
            throw new IllegalStateException("ADM container has not been created yet");
        }
        ContainerStateResponse state = inspectLiveState(adm.getDockerContainerId());
        if (state == null || !state.running()) {
            throw new IllegalStateException("ADM container is not running — start the environment first");
        }

        JdbcParts jdbc = resolveJdbc(env.getImageConfig());
        String b64login = java.util.Base64.getEncoder()
            .encodeToString(loginId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String b64pass = java.util.Base64.getEncoder()
            .encodeToString(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String cmd = SET_PASSWORD_SCRIPT
            .replace("__MXE_URL__", jdbc.url())
            .replace("__MXE_DRIVER__", jdbc.driver())
            .replace("__B64LOGIN__", b64login)
            .replace("__B64PASS__", b64pass);

        StringBuilder out = new StringBuilder();
        int rc = dockerService.execInContainer(adm.getDockerContainerId(), cmd, null, 120,
            line -> out.append(line).append('\n'));
        return new SetPasswordResult(rc == 0, out.toString());
    }

    private record JdbcParts(String driver, String url) {}

    /** Mirrors BuildService.resolveJdbcConfig: DB alias is {@code db} on the env network. */
    private JdbcParts resolveJdbc(ImageConfigEntity ic) {
        String vendor = ic != null && ic.getDbVendor() != null ? ic.getDbVendor() : "DB2";
        String dbName = ic != null && ic.getDbName() != null ? ic.getDbName() : "maxdb76";
        boolean isDb2 = !"ORACLE".equalsIgnoreCase(vendor);
        int port = ic != null && ic.getDbContainerPort() != null ? ic.getDbContainerPort()
            : (isDb2 ? 50000 : 1521);
        return isDb2
            ? new JdbcParts("com.ibm.db2.jcc.DB2Driver", "jdbc:db2://db:" + port + "/" + dbName)
            : new JdbcParts("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//db:" + port + "/" + dbName);
    }

    // Re-encrypts a password with Maximo's cryptox cipher (MXCipherX, null params = built-in
    // defaults, which match MAS 9.1 vanilla) and writes it to MAXUSER. Run on ADM (has the
    // Maximo classpath). Credentials arrive base64-encoded to keep the shell injection-safe.
    private static final String SET_PASSWORD_SCRIPT = """
        set -eo pipefail
        LOGINID="$(printf %s '__B64LOGIN__' | base64 -d)"
        PASSWORD="$(printf %s '__B64PASS__' | base64 -d)"
        # Prefer the ADM container's own MXE_DB_* (authoritative for this build); fall
        # back to values computed by Monohull for envs whose ADM doesn't carry them.
        export MXE_DB_URL="${MXE_DB_URL:-__MXE_URL__}"
        export MXE_DB_DRIVER="${MXE_DB_DRIVER:-__MXE_DRIVER__}"
        export MXE_DB_USER="${MXE_DB_USER:-maximo}"
        export MXE_DB_PASSWORD="${MXE_DB_PASSWORD:-maximo}"
        SRC=/tmp/SetMaxUserPassword.java
        CLS=/tmp/made-pwd-classes
        mkdir -p "$CLS"
        cat > "$SRC" <<'JAVA'
        import java.sql.*;
        import psdi.util.MXCipherX;
        public class SetMaxUserPassword {
            public static void main(String[] args) throws Exception {
                MXCipherX c = new MXCipherX(null, null, null, null, null, null, null);
                byte[] enc = c.encData(args[1]);
                Class.forName(System.getenv("MXE_DB_DRIVER"));
                try (Connection conn = DriverManager.getConnection(
                         System.getenv("MXE_DB_URL"), System.getenv("MXE_DB_USER"), System.getenv("MXE_DB_PASSWORD"));
                     PreparedStatement ps = conn.prepareStatement(
                         "UPDATE maxuser SET password=? WHERE UPPER(loginid)=UPPER(?)")) {
                    ps.setBytes(1, enc);
                    ps.setString(2, args[0]);
                    int rows = ps.executeUpdate();
                    System.out.println("[set-password] updated " + rows + " row(s) for loginid=" + args[0]);
                    if (rows == 0) System.exit(3);
                }
            }
        }
        JAVA
        cd /opt/IBM/SMP/maximo/tools/maximo
        . ./commonenv.sh > /dev/null 2>&1
        # Java location differs by Maximo flavour: MAS 9.1 ships the IBM JDK at
        # /opt/ibm/java, whereas 7.6 ADM images expose javac/java only on PATH and
        # leave JAVA_HOME unset after commonenv. Pick whichever is actually present.
        if [ -x /opt/ibm/java/bin/javac ]; then JAVABIN=/opt/ibm/java/bin
        elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then JAVABIN="$JAVA_HOME/bin"
        else JAVABIN="$(dirname "$(command -v javac)")"; fi
        "$JAVABIN/javac" -classpath ".:./classes:$MAXIMO_CLASSPATH" -d "$CLS" "$SRC"
        "$JAVABIN/java" -classpath "$CLS:.:./classes:$MAXIMO_CLASSPATH:../../applications/maximo/properties" SetMaxUserPassword "$LOGINID" "$PASSWORD"
        """;

    private String resolveDbName(EnvironmentEntity env) {
        if (env.getImageConfig() != null && env.getImageConfig().getDbName() != null) {
            return env.getImageConfig().getDbName();
        }
        return "maxdb76";
    }

    /**
     * Public Maximo URL for the env, or null when no public domain is configured.
     * Subdomain is the env's (DNS-safe) networkName, matching the Traefik Host rule
     * Monohull sets on the APP container — see {@link DockerService#runAppContainer}.
     */
    private String publicUrl(EnvironmentEntity env) {
        if (maximoDomain == null || maximoDomain.isBlank() || env.getNetworkName() == null) {
            return null;
        }
        return "https://" + env.getNetworkName() + "." + maximoDomain + "/maximo";
    }

    private EnvironmentResponse toResponse(EnvironmentEntity env) {
        return new EnvironmentResponse(
            env.getId(), env.getName(), env.getBuildId(),
            env.getMaximoVersion(), env.getDbVendor().name(), resolveDbName(env),
            env.getStatus().name(),
            env.getCreatedAt(), env.getUpdatedAt(),
            publicUrl(env), env.getCreatedBy(),
            env.getContainers().stream().map(c -> toContainerResponse(c, null)).toList()
        );
    }

    private EnvironmentResponse toResponseWithLiveState(EnvironmentEntity env) {
        return new EnvironmentResponse(
            env.getId(), env.getName(), env.getBuildId(),
            env.getMaximoVersion(), env.getDbVendor().name(), resolveDbName(env),
            env.getStatus().name(),
            env.getCreatedAt(), env.getUpdatedAt(),
            publicUrl(env), env.getCreatedBy(),
            env.getContainers().stream().map(c -> {
                ContainerStateResponse live = null;
                if (c.getDockerContainerId() != null) {
                    live = inspectLiveState(c.getDockerContainerId());
                }
                return toContainerResponse(c, live);
            }).toList()
        );
    }

    private ContainerResponse toContainerResponse(ContainerEntity c, ContainerStateResponse live) {
        return new ContainerResponse(
            c.getId(), c.getContainerName(), c.getDockerContainerId(),
            c.getRole().name(), c.getImage(), c.getPorts(), c.getStatus().name(), live
        );
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    /** The authenticated user's name (Monohull accounts are keyed by O365 email), or null if none. */
    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        return (name == null || "anonymousUser".equals(name)) ? null : name;
    }

    private int[] allocateDynamicPorts(int count) {
        int rangeStart = 12000;
        int rangeEnd = 12999;

        java.util.Set<Integer> used = new java.util.HashSet<>();
        used.addAll(configRepo.findAllUsedAppHttpPorts());
        used.addAll(configRepo.findAllUsedAppHttpsPorts());
        used.addAll(configRepo.findAllUsedDbPorts());
        used.addAll(configRepo.findAllUsedMockHostPorts());
        used.addAll(configRepo.findAllUsedSmtpHostPorts());
        used.addAll(configRepo.findAllUsedSmtpUiHostPorts());

        int[] result = new int[count];
        int found = 0;
        for (int port = rangeStart; port <= rangeEnd && found < count; port++) {
            if (!used.contains(port)) {
                result[found++] = port;
            }
        }
        if (found < count) {
            throw new IllegalStateException("No available ports in range " + rangeStart + "-" + rangeEnd);
        }
        return result;
    }

    private ContainerStateResponse inspectLiveState(String dockerId) {
        try {
            InspectContainerResponse info = dockerService.inspectContainer(dockerId);
            InspectContainerResponse.ContainerState state = info.getState();
            return new ContainerStateResponse(
                state.getStatus(),
                Boolean.TRUE.equals(state.getRunning()),
                state.getStartedAt(),
                state.getFinishedAt()
            );
        } catch (NotFoundException e) {
            return new ContainerStateResponse("removed", false, null, null);
        } catch (Exception e) {
            return new ContainerStateResponse("unknown", false, null, null);
        }
    }
}
