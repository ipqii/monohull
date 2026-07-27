package io.monohull.service;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import io.monohull.dto.BuildRequest;
import io.monohull.entity.*;
import io.monohull.repository.BuildLogRepository;
import io.monohull.repository.ContainerRepository;
import io.monohull.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildService.class);

    private final DockerService docker;
    private final LogSink logs;
    private final EnvironmentRepository envRepo;
    private final ContainerRepository containerRepo;
    private final BuildLogRepository logRepo;
    private final ActionService actionService;

    @Value("${monohull.mock.image:monohull/mock-receiver:latest}")
    private String mockImage;

    @Value("${monohull.mock.build-path:docker/mock-receiver}")
    private String mockBuildPath;

    // Host home directory used to expand leading `~/` in bind paths. Empty (the
    // default) disables expansion — the path is passed to Docker unchanged.
    // Set via env APP_DOCKER_HOST_HOME or property app.docker.host-home; on
    // Linux docker hosts this should be the home of the user that owns the
    // mounted secrets (e.g. /home/deploy).
    @Value("${app.docker.host-home:}")
    private String hostHome;

    // Runtime uid:gid for the ADM container. The vanilla ADM image runs ant
    // as maximoinstall (uid 1001), but on Linux docker hosts the host
    // workspace dir is typically owned by another user (often uid 1000).
    // Without an alignment the build can't create classes/ subdirectories and
    // fails with cryptic "creation was not successful for an unknown reason"
    // ant errors. Set this to the host owner's uid:gid (e.g. "1000:1000") to
    // run the container processes as that user. Empty (default) keeps the
    // image's baked-in user.
    @Value("${app.docker.adm-user:}")
    private String admUser;

    // Public base domain for per-environment Maximo URLs (e.g. "maximo.example.com").
    // When set, each env's APP container is published at <networkName>.<domain> via
    // the host Traefik. Empty (default) keeps environments LAN-only.
    @Value("${monohull.public.maximo-domain:}")
    private String maximoDomain;

    // Whether to assert the Maximo schema exists before running pipeline actions.
    // Turn this off only when the schema is created *by* a pipeline action (e.g. a
    // restore step) rather than by the DB image itself.
    @Value("${monohull.build.verify-db-schema:true}")
    private boolean verifyDbSchema;

    public BuildService(DockerService docker, LogSink logs,
                        EnvironmentRepository envRepo, ContainerRepository containerRepo,
                        BuildLogRepository logRepo, ActionService actionService) {
        this.docker = docker;
        this.logs = logs;
        this.envRepo = envRepo;
        this.containerRepo = containerRepo;
        this.logRepo = logRepo;
        this.actionService = actionService;
    }

    @Async
    public void startBuild(BuildRequest req) {
        final String id = req.buildId();
        Consumer<String> logger = line -> logs.append(id, line);

        try {
            logger.accept("[init] Build: " + id);
            docker.ensureNetwork(req.networkName());

            docker.pullImages(List.of(req.dbImage(), req.admImage(), req.appImage()), logger);

            int dbContainerPort = req.dbImage().contains("db2") ? 50000 : 1521;
            docker.runDbContainer(
                req.dbContainerName(), req.dbImage(), req.networkName(),
                req.dbPort(), dbContainerPort,
                req.dbVolumeName() == null ? "maximo-db-volume" : req.dbVolumeName(),
                req.dbImage().contains("db2") ? "/database" : "/opt/oracle",
                List.of("MAXIMO_VERSION=unknown"), null, req.dbCommand(), "db", logger);

            List<Bind> appBinds = new ArrayList<>();
            if (req.appConfigHostPath() != null)
                appBinds.add(new Bind(req.appConfigHostPath(), new Volume("/config")));
            if (req.workspaceHostPath() != null)
                appBinds.add(new Bind(req.workspaceHostPath(), new Volume("/workspace")));
            if (req.logsHostPath() != null)
                appBinds.add(new Bind(req.logsHostPath(), new Volume("/logs")));

            docker.runAppContainer(
                req.appContainerName(), req.appImage(), req.networkName(),
                req.appHttpPort(), req.appHttpsPort(),
                appBinds, List.of("MAXIMO_VERSION=unknown"), "app", null, logger);

            List<Bind> admBinds = new ArrayList<>();
            if (req.appConfigHostPath() != null)
                admBinds.add(new Bind(req.appConfigHostPath(), new Volume("/opt/IBM/SMP/maximo/deployment/was-liberty-default/deployment/maximo-all/maximo-all-server")));

            docker.runAdmContainer(
                req.admContainerName(), req.admImage(), req.networkName(),
                admBinds, List.of("MAXIMO_VERSION=unknown"), "adm", admUser, logger);

            logger.accept("[done] Containers created.");
        } catch (Exception e) {
            logger.accept("[error] " + e.getMessage());
        } finally {
            logs.complete(id);
        }
    }

    @Async
    public void startBuildForEnvironment(Long environmentId) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(environmentId).orElse(null);
        if (env == null) {
            log.error("Environment not found: {}", environmentId);
            return;
        }

        String buildId = env.getBuildId();
        Consumer<String> logger = line -> {
            logs.append(buildId, line);
            persistLogLine(env, line);
        };

        try {
            env.setStatus(EnvironmentStatus.BUILDING);
            envRepo.save(env);

            logger.accept("[init] Building environment: " + env.getName());
            docker.ensureNetwork(env.getNetworkName());

            // mock-receiver is shipped in-tree and built locally on first use; everything else
            // is pulled from a registry. Build before the pull so any build failure short-circuits.
            boolean needsMockBuild = env.getContainers().stream()
                .anyMatch(c -> c.getRole() == ContainerRole.MOCK);
            if (needsMockBuild) {
                docker.ensureLocalImageBuilt(mockImage, new File(mockBuildPath), logger);
            }
            List<String> imagesToPull = env.getContainers().stream()
                .filter(c -> c.getRole() != ContainerRole.MOCK)
                .map(ContainerEntity::getImage)
                .toList();
            docker.pullImages(imagesToPull, logger);

            EnvironmentConfigEntity config = env.getConfig();
            ImageConfigEntity imageConfig = env.getImageConfig();
            WorkspaceBind ws = resolveWorkspaceBind(env);
            String workspaceFolderName = ws != null ? ws.folderName() : null;
            String workspaceBindSource = ws != null ? ws.bindSource() : null;
            if (ws != null) {
                logger.accept("[workspace] Workspace path -> /workspace/" + workspaceFolderName);
            }

            // Phase 1: start DB on its own and wait for it to be ready. The DB image is
            // responsible for ensuring the Maximo database exists; we just wait for the
            // instance/listener to come up. MOCK and SMTP addons have no dependencies and
            // come up alongside the DB so they're ready by the time pipeline actions run.
            ContainerEntity dbContainer = null;
            ContainerEntity admContainer = null;
            ContainerEntity appContainer = null;
            for (ContainerEntity c : env.getContainers()) {
                if (c.getRole() == ContainerRole.DB) {
                    dbContainer = c;
                    createAndStartContainer(c, env, config, workspaceBindSource, workspaceFolderName, logger);
                } else if (c.getRole() == ContainerRole.ADM) {
                    admContainer = c;
                } else if (c.getRole() == ContainerRole.APP) {
                    appContainer = c;
                } else if (c.getRole() == ContainerRole.MOCK || c.getRole() == ContainerRole.SMTP) {
                    createAndStartContainer(c, env, config, workspaceBindSource, workspaceFolderName, logger);
                }
            }

            if (dbContainer != null) {
                waitForDbReady(dbContainer, logger);
                verifyMaximoSchema(dbContainer, env, logger);
            }

            env.setStatus(EnvironmentStatus.CONFIGURING);
            envRepo.save(env);

            // Phase 2: now that the DB is up, start ADM and patch maximo.properties.
            if (admContainer != null) {
                createAndStartContainer(admContainer, env, config, workspaceBindSource, workspaceFolderName, logger);
                configureMaximoProperties(admContainer, imageConfig, logger);
            }

            List<ActionService.ResolvedAction> ordered = actionService.buildPipelineOrder(environmentId);
            // Pre-create a PENDING execution record per step so the Pipeline tab shows the
            // full plan from the start, with each step transitioning PENDING → RUNNING →
            // COMPLETED/FAILED as it progresses (rather than appearing only when reached).
            String pipelineRunId = actionService.preCreatePipelineExecutions(environmentId, ordered);

            // Phase 3: run pipeline actions in order. The "start-app" marker triggers
            // the APP container start mid-stream so subsequent ADM actions that need
            // Maximo over HTTP (dataload, etc.) find the APP container ready.
            int seqOrder = 0;
            boolean appStarted = false;
            for (ActionService.ResolvedAction action : ordered) {
                if ("start-app".equals(action.id())) {
                    if (appContainer != null && !appStarted) {
                        // Drop server-custom.xml into the Liberty config dir AFTER build-ear's
                        // wipe so it survives, and BEFORE APP starts so Liberty loads it.
                        writeServerCustomXml(admContainer, config, logger);
                        ensureWasJmsServerFeature(admContainer, config, logger);
                        // Update SMTP system properties (mail.smtp.host/port, mxe.smtp.user/password)
                        // before Maximo starts so they're loaded fresh. Pipeline restore steps,
                        // if any, have already run and won't overwrite our values.
                        if (config != null && config.isSmtpEnabled()) {
                            configureSmtpProperties(dbContainer, env, logger);
                        }
                        logger.accept("[app-start] Starting APP container now that build artifacts are ready");
                        createAndStartContainer(appContainer, env, config, workspaceBindSource, workspaceFolderName, logger);
                        appStarted = true;
                        waitForAppReady(appContainer, logger);
                    }
                    actionService.markStepCompleted(pipelineRunId, seqOrder++);
                    continue;
                }
                boolean ok = actionService.executeSingleAction(environmentId, action.id(),
                    pipelineRunId, seqOrder++, logger);
                if (!ok) throw new RuntimeException(action.id() + " failed");
            }

            // Safety net: if the pipeline omits start-app but an APP container is defined,
            // start it so the env finishes in a usable state.
            if (appContainer != null && !appStarted) {
                writeServerCustomXml(admContainer, config, logger);
                ensureWasJmsServerFeature(admContainer, config, logger);
                if (config != null && config.isSmtpEnabled()) {
                    configureSmtpProperties(dbContainer, env, logger);
                }
                logger.accept("[app-start] Starting APP container (no start-app step in pipeline)");
                createAndStartContainer(appContainer, env, config, workspaceBindSource, workspaceFolderName, logger);
            }

            EnvironmentEntity finalEnv = envRepo.findById(environmentId).orElse(env);
            finalEnv.setStatus(EnvironmentStatus.RUNNING);
            envRepo.save(finalEnv);
            logger.accept("[done] Environment ready.");
        } catch (Exception e) {
            log.error("Build failed for environment {}", environmentId, e);
            logger.accept("[error] " + DockerErrors.explain(e));
            env.setStatus(EnvironmentStatus.ERROR);
            envRepo.save(env);
        } finally {
            logs.complete(buildId);
        }
    }

    private static final String MAXIMO_PROPERTIES_PATH =
        "/opt/IBM/SMP/maximo/applications/maximo/properties/maximo.properties";

    // ADM-side path that resolves to /config in the APP container via the shared
    // host bind mount. build-ear writes its bundle here; we drop server-custom.xml
    // alongside it so Liberty loads our JMS config at APP startup.
    private static final String LIBERTY_CONFIG_DIR =
        "/opt/IBM/SMP/maximo/deployment/was-liberty-default/deployment/maximo-all/maximo-all-server";
    private static final String SERVER_CUSTOM_XML_PATH = LIBERTY_CONFIG_DIR + "/server-custom.xml";
    private static final String SERVER_XML_PATH = LIBERTY_CONFIG_DIR + "/server.xml";

    private record JdbcConfig(String driver, String url) {}

    private static JdbcConfig resolveJdbcConfig(ImageConfigEntity imageConfig) {
        String dbVendor = imageConfig != null && imageConfig.getDbVendor() != null
            ? imageConfig.getDbVendor() : "DB2";
        String dbName = imageConfig != null && imageConfig.getDbName() != null
            ? imageConfig.getDbName() : "maxdb76";
        boolean isDb2 = !"ORACLE".equalsIgnoreCase(dbVendor);
        int port = resolveDbContainerPort(imageConfig, isDb2);

        if (!isDb2) {
            return new JdbcConfig("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@//db:" + port + "/" + dbName);
        }
        return new JdbcConfig("com.ibm.db2.jcc.DB2Driver", "jdbc:db2://db:" + port + "/" + dbName);
    }

    private static int resolveDbContainerPort(ImageConfigEntity imageConfig, boolean isDb2) {
        if (imageConfig != null && imageConfig.getDbContainerPort() != null) {
            return imageConfig.getDbContainerPort();
        }
        return isDb2 ? 50000 : 1521;
    }

    private void configureMaximoProperties(ContainerEntity admContainer,
                                           ImageConfigEntity imageConfig,
                                           Consumer<String> logger) {
        if (admContainer.getDockerContainerId() == null) return;

        JdbcConfig jdbc = resolveJdbcConfig(imageConfig);

        logger.accept("[adm-config] Setting mxe.db.driver=" + jdbc.driver());
        logger.accept("[adm-config] Setting mxe.db.url=" + jdbc.url());

        // The active properties have leading whitespace (e.g. "\tmxe.db.driver=...").
        // [[:space:]]* allows that. Comment lines start with "//" so they won't match.
        // | as the sed delimiter is safe for both driver class names and JDBC URLs.
        String cmd = String.format(
            "sed -i -E 's|^[[:space:]]*mxe\\.db\\.driver=.*|mxe.db.driver=%s|' %s && " +
            "sed -i -E 's|^[[:space:]]*mxe\\.db\\.url=.*|mxe.db.url=%s|' %s",
            jdbc.driver(), MAXIMO_PROPERTIES_PATH, jdbc.url(), MAXIMO_PROPERTIES_PATH);

        int rc = docker.execInContainer(admContainer.getDockerContainerId(), cmd, null, 30, logger);
        if (rc != 0) {
            logger.accept("[adm-config] Warning: failed to update maximo.properties (exit " + rc + ")");
        }
    }

    /**
     * Drop our Liberty server-custom.xml into the bind-mounted Maximo Liberty config
     * directory. Enables the embedded JMS messaging engine so MAXQUEUE rows for the
     * standard inbound/outbound queues (sqin, sqout, cqin, cqinerr) resolve without
     * an external IBM MQ broker. Runs unconditionally for every env.
     *
     * Must run AFTER build-ear (which wipes the dir before extracting its bundle) and
     * BEFORE APP starts (Liberty reads server-custom.xml at server start).
     */
    private void writeServerCustomXml(ContainerEntity admContainer,
                                      EnvironmentConfigEntity config,
                                      Consumer<String> logger) {
        if (admContainer == null || admContainer.getDockerContainerId() == null) return;
        if (config == null || config.getHostVolumePath() == null) {
            logger.accept("[liberty-config] No host volume path configured; skipping server-custom.xml write");
            return;
        }

        String content;
        try {
            content = new String(
                new ClassPathResource("maximo-config/server-custom.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.accept("[liberty-config] Warning: failed to load server-custom.xml resource: " + e.getMessage());
            return;
        }

        // Single-quoted heredoc delimiter (XML_EOF) suppresses shell expansion, so the
        // XML content (which contains $, backticks, etc. in comments potentially) is
        // written verbatim. mkdir -p guards against the dir not existing (e.g. if a
        // build-ear-less pipeline is used).
        String script = "mkdir -p \"$(dirname " + SERVER_CUSTOM_XML_PATH + ")\" && " +
            "cat > " + SERVER_CUSTOM_XML_PATH + " << 'XML_EOF'\n" +
            content +
            (content.endsWith("\n") ? "" : "\n") +
            "XML_EOF";

        logger.accept("[liberty-config] Writing server-custom.xml (Liberty JMS messaging engine)");
        int rc = docker.execInContainer(admContainer.getDockerContainerId(), script, null, 30, logger);
        if (rc != 0) {
            logger.accept("[liberty-config] Warning: server-custom.xml write exited with " + rc);
        }
    }

    /**
     * Ensure {@code <feature>wasJmsServer-1.0</feature>} appears inside server.xml's
     * featureManager block. Without it, Liberty ignores the messaging engine declared
     * in server-custom.xml. Idempotent: greps for the literal feature line first and
     * exits noop if already present.
     */
    private void ensureWasJmsServerFeature(ContainerEntity admContainer,
                                           EnvironmentConfigEntity config,
                                           Consumer<String> logger) {
        if (admContainer == null || admContainer.getDockerContainerId() == null) return;
        if (config == null || config.getHostVolumePath() == null) return;

        // The sed regex captures whatever indentation the existing </featureManager>
        // line uses (\1) and reuses it for both the new <feature> line (with an extra
        // 4 spaces of indent) and the unchanged closing tag.
        String script = String.join("\n",
            "set -e",
            "if [ ! -f " + SERVER_XML_PATH + " ]; then",
            "    echo '[liberty-config] server.xml not found; skipping wasJmsServer-1.0 check'",
            "    exit 0",
            "fi",
            "if grep -q '<feature>wasJmsServer-1.0</feature>' " + SERVER_XML_PATH + "; then",
            "    echo '[liberty-config] wasJmsServer-1.0 already declared in server.xml'",
            "else",
            "    sed -i -E 's|^([[:space:]]*)</featureManager>|\\1    <feature>wasJmsServer-1.0</feature>\\n\\1</featureManager>|' " + SERVER_XML_PATH,
            "    echo '[liberty-config] Added wasJmsServer-1.0 to server.xml featureManager'",
            "fi"
        );

        int rc = docker.execInContainer(admContainer.getDockerContainerId(), script, null, 30, logger);
        if (rc != 0) {
            logger.accept("[liberty-config] Warning: wasJmsServer-1.0 check exited with " + rc);
        }
    }

    /**
     * Point Maximo's outbound mail system properties at the in-network SMTP catcher
     * (Mailpit, aliased as `smtp` on the env's bridge network). Updates MAXPROPVALUE
     * rows for the COMMON server scope so Maximo loads the new values on next startup.
     * Mailpit accepts unauthenticated SMTP, so user/password are cleared.
     *
     * Only DB2 is wired up here — Oracle support is a follow-up.
     */
    private void configureSmtpProperties(ContainerEntity dbContainer,
                                         EnvironmentEntity env,
                                         Consumer<String> logger) {
        if (dbContainer == null || dbContainer.getDockerContainerId() == null) return;
        boolean isDb2 = dbContainer.getImage().contains("db2");
        if (!isDb2) {
            logger.accept("[smtp-config] Oracle SMTP property update not yet implemented; skipping.");
            return;
        }
        String dbName = env.getImageConfig() != null && env.getImageConfig().getDbName() != null
            ? env.getImageConfig().getDbName() : "maxdb76";

        // Write the SQL as db2inst1, then run it. Single-quoted heredoc keeps the SQL
        // literal (no shell expansion) and avoids the quoting fight with `db2 "..."`.
        String script = String.join("\n",
            "set -e",
            "su - db2inst1 -c \"cat > /tmp/made-smtp-props.sql << 'SQL_EOF'",
            "CONNECT TO " + dbName + ";",
            "UPDATE MAXIMO.MAXPROPVALUE SET PROPVALUE='smtp' WHERE PROPNAME='mail.smtp.host' AND SERVERNAME='COMMON';",
            "UPDATE MAXIMO.MAXPROPVALUE SET PROPVALUE='1025' WHERE PROPNAME='mail.smtp.port' AND SERVERNAME='COMMON';",
            "UPDATE MAXIMO.MAXPROPVALUE SET PROPVALUE=NULL, ENCRYPTEDVALUE=NULL WHERE PROPNAME IN ('mxe.smtp.user','mxe.smtp.password') AND SERVERNAME='COMMON';",
            "COMMIT;",
            "TERMINATE;",
            "SQL_EOF",
            "db2 -tf /tmp/made-smtp-props.sql\""
        );

        logger.accept("[smtp-config] Pointing Maximo SMTP properties at smtp:1025 (Mailpit)");
        int rc = docker.execInContainer(dbContainer.getDockerContainerId(), script, null, 60, logger);
        if (rc != 0) {
            logger.accept("[smtp-config] Warning: SMTP property update exited with " + rc);
        } else {
            logger.accept("[smtp-config] Updated mail.smtp.host, mail.smtp.port, mxe.smtp.user, mxe.smtp.password");
        }
    }

    private void createAndStartContainer(ContainerEntity c, EnvironmentEntity env,
                                         EnvironmentConfigEntity config,
                                         String workspaceBindSource, String workspaceFolderName,
                                         Consumer<String> logger) {
        c.setStatus(ContainerStatus.CREATING);
        containerRepo.save(c);

        String networkAlias = c.getRole().name().toLowerCase();
        String dockerId = switch (c.getRole()) {
            case DB -> {
                boolean isDb2 = c.getImage().contains("db2");
                int containerPort = resolveDbContainerPort(env.getImageConfig(), isDb2);
                int hostPort = config != null && config.getDbPort() != null ? config.getDbPort() : containerPort;
                String volumeName = config != null && config.getDbVolumeName() != null
                    ? config.getDbVolumeName() : "made-" + env.getName().toLowerCase().replaceAll("[^a-z0-9]", "-") + "-db";
                String volumeTarget = isDb2 ? "/database" : "/opt/oracle";
                List<String> dbEnv = new ArrayList<>();
                dbEnv.add("MAXIMO_VERSION=" + env.getMaximoVersion());
                if (config != null && config.getDbPassword() != null && !config.getDbPassword().isBlank()) {
                    dbEnv.add("MAXIMO_DB_PASSWORD=" + config.getDbPassword());
                }
                appendExtraEnv(dbEnv, config != null ? config.getDbExtraEnv() : null);
                List<Bind> dbExtraBinds = toBinds(config != null ? config.getDbExtraBinds() : null);
                yield docker.runDbContainer(c.getContainerName(), c.getImage(), env.getNetworkName(),
                    hostPort, containerPort, volumeName, volumeTarget,
                    dbEnv, dbExtraBinds, config != null ? config.getDbCommand() : null,
                    networkAlias, logger);
            }
            case APP -> {
                List<Bind> binds = new ArrayList<>();
                if (config != null && config.getHostVolumePath() != null) {
                    String configDir = createEnvSubdir(config.getHostVolumePath(), env.getName(), "config", logger);
                    String logsDir = createEnvSubdir(config.getHostVolumePath(), env.getName(), "logs", logger);
                    binds.add(new Bind(configDir, new Volume("/config")));
                    binds.add(new Bind(logsDir, new Volume("/logs")));
                }
                if (workspaceBindSource != null && workspaceFolderName != null) {
                    binds.add(new Bind(workspaceBindSource, new Volume("/workspace/" + workspaceFolderName)));
                }
                binds.addAll(toBinds(config != null ? config.getAppExtraBinds() : null));
                int httpPort = config != null && config.getAppHttpPort() != null ? config.getAppHttpPort() : 9080;
                int httpsPort = config != null && config.getAppHttpsPort() != null ? config.getAppHttpsPort() : 9443;
                // The vanilla/app image ships defaults for MXE_DB_URL/DRIVER pointing at Oracle.
                // Override with the env's actual JDBC config so Maximo connects to the right DB.
                JdbcConfig appJdbc = resolveJdbcConfig(env.getImageConfig());
                List<String> appEnv = new ArrayList<>();
                appEnv.add("MAXIMO_VERSION=" + env.getMaximoVersion());
                appEnv.add("MXE_DB_URL=" + appJdbc.url());
                appEnv.add("MXE_DB_DRIVER=" + appJdbc.driver());
                appEnv.add("MXE_DB_USER=maximo");
                appEnv.add("MXE_DB_PASSWORD=maximo");
                appEnv.add("MXE_DB_SCHEMAOWNER=maximo");
                appendExtraEnv(appEnv, config != null ? config.getAppExtraEnv() : null);
                String publicHost = (maximoDomain == null || maximoDomain.isBlank())
                    ? null : env.getNetworkName() + "." + maximoDomain;
                yield docker.runAppContainer(c.getContainerName(), c.getImage(), env.getNetworkName(),
                    httpPort, httpsPort, binds,
                    appEnv, networkAlias, publicHost, logger);
            }
            case ADM -> {
                List<Bind> binds = new ArrayList<>();
                if (config != null && config.getHostVolumePath() != null) {
                    // createEnvSubdir (not resolveEnvSubdir) so the dir exists with
                    // 777 perms BEFORE the bind mount. Otherwise Docker auto-creates
                    // it as root:root 755 when ADM starts, and the build-ear tar
                    // step (running as maximoinstall in the container) gets
                    // "Permission denied" trying to extract into it.
                    String configDir = createEnvSubdir(config.getHostVolumePath(), env.getName(), "config", logger);
                    binds.add(new Bind(configDir, new Volume("/opt/IBM/SMP/maximo/deployment/was-liberty-default/deployment/maximo-all/maximo-all-server")));
                }
                if (workspaceBindSource != null && workspaceFolderName != null) {
                    binds.add(new Bind(workspaceBindSource, new Volume("/workspace/" + workspaceFolderName)));
                }
                binds.addAll(toBinds(config != null ? config.getAdmExtraBinds() : null));
                JdbcConfig jdbc = resolveJdbcConfig(env.getImageConfig());
                List<String> admEnv = new ArrayList<>();
                admEnv.add("MAXIMO_VERSION=" + env.getMaximoVersion());
                admEnv.add("MXE_DB_URL=" + jdbc.url());
                admEnv.add("MXE_DB_DRIVER=" + jdbc.driver());
                admEnv.add("MXE_DB_USER=maximo");
                admEnv.add("MXE_DB_PASSWORD=maximo");
                admEnv.add("MXE_DB_SCHEMAOWNER=maximo");
                appendExtraEnv(admEnv, config != null ? config.getAdmExtraEnv() : null);
                yield docker.runAdmContainer(c.getContainerName(), c.getImage(), env.getNetworkName(),
                    binds, admEnv, networkAlias, admUser, logger);
            }
            case MOCK -> {
                int hostPort = config != null && config.getMockHostPort() != null ? config.getMockHostPort() : 8085;
                yield docker.runMockContainer(c.getContainerName(), c.getImage(), env.getNetworkName(),
                    hostPort, 8085, networkAlias, logger);
            }
            case SMTP -> {
                int smtpPort = config != null && config.getSmtpHostPort() != null ? config.getSmtpHostPort() : 1025;
                int uiPort = config != null && config.getSmtpUiHostPort() != null ? config.getSmtpUiHostPort() : 8025;
                yield docker.runSmtpContainer(c.getContainerName(), c.getImage(), env.getNetworkName(),
                    smtpPort, uiPort, networkAlias, logger);
            }
        };

        c.setDockerContainerId(dockerId);
        c.setStatus(ContainerStatus.RUNNING);
        containerRepo.save(c);
    }

    private static void appendExtraEnv(List<String> target, List<io.monohull.dto.ExtraEnvVar> extras) {
        if (extras == null) return;
        for (io.monohull.dto.ExtraEnvVar e : extras) {
            if (e == null || e.key() == null || e.key().isBlank()) continue;
            target.add(e.key() + "=" + (e.value() == null ? "" : e.value()));
        }
    }

    /** Public so ActionService can mount an env's extra binds into BUILDER containers. */
    public List<Bind> toBinds(List<io.monohull.dto.ExtraBind> extras) {
        List<Bind> result = new ArrayList<>();
        if (extras == null) return result;
        for (io.monohull.dto.ExtraBind b : extras) {
            if (b == null || b.hostPath() == null || b.hostPath().isBlank()
                || b.containerPath() == null || b.containerPath().isBlank()) continue;
            String src = toDockerHostPath(b.hostPath());
            com.github.dockerjava.api.model.AccessMode mode = b.readOnly()
                ? com.github.dockerjava.api.model.AccessMode.ro
                : com.github.dockerjava.api.model.AccessMode.rw;
            result.add(new Bind(src, new Volume(b.containerPath()), mode));
        }
        return result;
    }

    private static String extractBasename(String path) {
        String trimmed = path.replaceAll("[\\\\/]+$", "");
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    /** The env's effective workspace mount: host bind source (override-aware) plus the
     *  folder name the pipeline expects under /workspace. {@code overridden} is true when
     *  a per-build workspace override (e.g. a PR checkout) replaced the image-config path;
     *  {@code configSource} always carries the image-config path (docker-normalized) so
     *  callers can still reach the main checkout when an override is active. */
    public record WorkspaceBind(String bindSource, String configSource, String folderName, boolean overridden) {}

    /** Resolve the workspace bind for an environment — image config path, replaced by the
     *  env config's per-build override when set. Null when the image config has no
     *  workspace. Shared by container creation here and BUILDER actions in ActionService. */
    public WorkspaceBind resolveWorkspaceBind(EnvironmentEntity env) {
        ImageConfigEntity imageConfig = env.getImageConfig();
        EnvironmentConfigEntity config = env.getConfig();
        String workspacePath = imageConfig != null ? imageConfig.getWorkspacePath() : null;
        if (workspacePath == null || workspacePath.isBlank()) return null;
        String override = config != null ? config.getWorkspacePathOverride() : null;
        boolean overridden = override != null && !override.isBlank();
        // Keep the mount target folder from the image config (the build pipeline expects
        // /workspace/<project>), but let a per-build override replace the bind source.
        String source = overridden ? override : workspacePath;
        return new WorkspaceBind(toDockerHostPath(source), toDockerHostPath(workspacePath),
            extractBasename(workspacePath), overridden);
    }

    private String toDockerHostPath(String path) {
        if (path == null) return null;
        // Expand a leading ~ / ~/ to the configured host home. Docker's API
        // doesn't expand ~ — it sees the literal string, fails the "is this an
        // absolute path?" test, and rejects it as an invalid volume name.
        if (path.equals("~") || path.startsWith("~/")) {
            if (hostHome == null || hostHome.isBlank()) {
                throw new IllegalArgumentException(
                    "Bind path '" + path + "' uses '~' but no host home is configured. "
                    + "Set APP_DOCKER_HOST_HOME (or app.docker.host-home) on Monohull — "
                    + "e.g. APP_DOCKER_HOST_HOME=/home/deploy — or use an absolute path.");
            }
            path = hostHome + path.substring(1);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z]):[\\\\/](.*)$").matcher(path);
        if (m.matches()) {
            String drive = m.group(1).toLowerCase();
            String rest = m.group(2).replace('\\', '/');
            return "/" + drive + "/" + rest;
        }
        return path;
    }

    /**
     * Idempotently bring the APP container up for an environment. Used by both the
     * initial {@link #startBuild} flow (via the inline `start-app` marker) and by
     * pipeline re-runs from ActionService when the first build failed before
     * `start-app` could create the APP container.
     *
     * No-op if there is no APP container, or if it's already running.
     */
    public void ensureAppContainerStarted(Long environmentId, Consumer<String> logger) {
        EnvironmentEntity env = envRepo.findByIdWithContainersAndConfig(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId));

        ContainerEntity appContainer = null;
        ContainerEntity admContainer = null;
        ContainerEntity dbContainer = null;
        for (ContainerEntity c : env.getContainers()) {
            if (c.getRole() == ContainerRole.APP) appContainer = c;
            else if (c.getRole() == ContainerRole.ADM) admContainer = c;
            else if (c.getRole() == ContainerRole.DB) dbContainer = c;
        }
        if (appContainer == null) {
            logger.accept("[app-start] No APP container defined for env " + env.getName() + "; nothing to do");
            return;
        }

        // Already created and reported running by Docker — nothing to do.
        if (appContainer.getDockerContainerId() != null) {
            try {
                var inspect = docker.inspectContainer(appContainer.getDockerContainerId());
                if (inspect != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    logger.accept("[app-start] APP container already running; nothing to do");
                    return;
                }
                logger.accept("[app-start] APP container exists but is not running; starting it");
                docker.startContainer(appContainer.getDockerContainerId());
                waitForAppReady(appContainer, logger);
                return;
            } catch (Exception e) {
                logger.accept("[app-start] APP container id stale (" + e.getMessage() + "); will recreate");
                appContainer.setDockerContainerId(null);
                containerRepo.save(appContainer);
            }
        }

        EnvironmentConfigEntity config = env.getConfig();
        WorkspaceBind ws = resolveWorkspaceBind(env);
        String workspaceFolderName = ws != null ? ws.folderName() : null;
        String workspaceBindSource = ws != null ? ws.bindSource() : null;

        // Mirror the inline start-app block in startBuild(): make sure Liberty
        // sees server-custom.xml + the JMS feature, and SMTP props if enabled,
        // then create the APP container and wait for it.
        writeServerCustomXml(admContainer, config, logger);
        ensureWasJmsServerFeature(admContainer, config, logger);
        if (config != null && config.isSmtpEnabled() && dbContainer != null) {
            configureSmtpProperties(dbContainer, env, logger);
        }
        logger.accept("[app-start] Starting APP container for env " + env.getName());
        createAndStartContainer(appContainer, env, config, workspaceBindSource, workspaceFolderName, logger);
        waitForAppReady(appContainer, logger);
    }

    private String createEnvSubdir(String hostVolumePath, String envName, String subdir, Consumer<String> logger) {
        String sanitized = envName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        Path dir = Path.of(hostVolumePath, sanitized, subdir);
        try {
            Files.createDirectories(dir);
            // Monohull itself runs as root (its container's default), so the dir
            // lands owned by root with mode 755. The downstream APP/ADM
            // containers bind-mount this and run as a non-root image user
            // (e.g. maximoinstall uid 1001) that can't write to a root-owned
            // 755 dir — the build-ear tar step then fails with "Permission
            // denied". Open it up so any container user can write.
            try {
                Files.setPosixFilePermissions(dir,
                    java.util.EnumSet.allOf(java.nio.file.attribute.PosixFilePermission.class));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Non-POSIX FS (Windows dev) or already correctly permissioned — harmless.
            }
            logger.accept("[volume] Created directory: " + dir);
        } catch (IOException e) {
            logger.accept("[volume] Warning: could not create " + dir + ": " + e.getMessage());
        }
        return dir.toString();
    }

    private String resolveEnvSubdir(String hostVolumePath, String envName, String subdir) {
        String sanitized = envName.toLowerCase().replaceAll("[^a-z0-9]", "-");
        return Path.of(hostVolumePath, sanitized, subdir).toString();
    }

    private void waitForDbReady(ContainerEntity dbContainer, Consumer<String> logger) {
        boolean isDb2 = dbContainer.getImage().contains("db2");
        String dbType = isDb2 ? "DB2" : "Oracle";

        // DB2: the vanilla DB2 image's entrypoint logs "[echopoint] DB ready." after it
        // finishes creating MAXIMO + applying maxinst-like setup. That marker is the
        // definitive signal that the entrypoint is no longer mutating DB2 state. The old
        // `db2 list active databases` probe was a misleading proxy — it goes green seconds
        // before the entrypoint finishes its work (racing the restore action's deactivate
        // and producing SQL1035N / SQL30061N), and it also returns exit=2 with SQL1611W
        // once the entrypoint is done but no DB is currently active. Marker > probe.
        //
        // Oracle: no equivalent marker known yet; keep the lsnrctl probe. If we see the
        // same race on Oracle, parameterise this on a vendor-specific marker too.
        int maxAttempts = 60;
        int intervalSeconds = 10;
        logger.accept("[db-wait] Waiting for " + dbType + " database to be ready...");

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (isDb2) {
                    boolean seen = docker.fetchContainerLogs(dbContainer.getDockerContainerId(), 500)
                        .stream().anyMatch(l -> l.contains("[echopoint] DB ready."));
                    if (seen) {
                        logger.accept("[db-wait] DB2 entrypoint finished (attempt " + attempt + ")");
                        return;
                    }
                } else {
                    StringBuilder output = new StringBuilder();
                    int exitCode = docker.execInContainer(
                        dbContainer.getDockerContainerId(), "lsnrctl status", null, 30,
                        line -> output.append(line));
                    if (exitCode == 0) {
                        logger.accept("[db-wait] Oracle listener is up (attempt " + attempt + ")");
                        return;
                    }
                    if (attempt % 5 == 0) {
                        logger.accept("[db-wait] Check output: " + output.toString().trim());
                    }
                }
            } catch (Exception e) {
                // Container may not be fully started yet
            }
            logger.accept("[db-wait] Database not ready yet (attempt " + attempt + "/" + maxAttempts + "), retrying in " + intervalSeconds + "s...");
            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for database");
            }
        }
        throw new RuntimeException(dbType + " database did not become ready after " + (maxAttempts * intervalSeconds) + " seconds");
    }

    /**
     * Assert the Maximo schema actually exists before any pipeline action touches it.
     *
     * <p>"DB ready" only means the instance is up and the database exists — not that it has
     * a Maximo schema in it. A DB image that creates an empty database (because it was never
     * told to restore a backup, or the restore failed) still reaches the ready marker, and
     * the build then runs the whole pipeline against an empty DB. The first action to touch
     * a Maximo table dies with a bare vendor error — DB2 exits 4 with SQL0204N "undefined
     * name" — several minutes and several steps away from the real cause.
     *
     * <p>Failing here instead puts the error next to the thing that caused it. The probe is
     * a plain existence check on a core Maximo table; MAXIMO is the schema owner Monohull
     * assumes throughout (see MXE_DB_SCHEMAOWNER in the APP container env).
     *
     * <p>DB2 only for now, mirroring {@link #configureSmtpProperties}. Disable via
     * {@code monohull.build.verify-db-schema=false} when a pipeline action is what creates
     * the schema.
     */
    private void verifyMaximoSchema(ContainerEntity dbContainer, EnvironmentEntity env,
                                    Consumer<String> logger) {
        if (!verifyDbSchema) return;
        if (dbContainer.getDockerContainerId() == null) return;
        if (!dbContainer.getImage().contains("db2")) {
            logger.accept("[db-verify] Schema check is DB2-only; skipping for this image.");
            return;
        }
        String dbName = env.getImageConfig() != null && env.getImageConfig().getDbName() != null
            ? env.getImageConfig().getDbName() : "maxdb76";

        // One CLP session for both statements: each `db2 ...` invocation is its own process,
        // so a CONNECT issued separately is already gone by the next call (SQL1024N). COUNT(*)
        // rather than a row fetch, so an existing-but-empty table still returns a row and
        // can't be confused with a missing one via SQL0100W.
        String script = String.join("\n",
            "su - db2inst1 -c \"db2 -t <<'SQL_EOF'",
            "CONNECT TO " + dbName + ";",
            "SELECT COUNT(*) FROM MAXIMO.MAXOBJECT;",
            "TERMINATE;",
            "SQL_EOF",
            "\"");

        StringBuilder out = new StringBuilder();
        logger.accept("[db-verify] Checking that the Maximo schema exists in " + dbName + "...");
        int rc = docker.execInContainer(dbContainer.getDockerContainerId(), script, null, 60,
            line -> out.append(line));
        if (rc == 0) {
            logger.accept("[db-verify] Maximo schema present.");
            return;
        }

        for (String line : db2MessageLines(out.toString())) {
            logger.accept("[db-verify] " + line);
        }
        for (String hint : diagnoseEmptyDb(dbContainer)) {
            logger.accept("[hint] " + hint);
        }
        throw new RuntimeException("Database '" + dbName + "' has no Maximo schema (MAXIMO.MAXOBJECT "
            + "is not there). The DB container came up but its database is empty, so every pipeline "
            + "action that touches Maximo tables would fail. Fix the database before rebuilding.");
    }

    /**
     * Pull the diagnosis out of DB2 CLP output. Fed a script on stdin the CLP prints its
     * interactive banner and prefixes each echoed statement with a {@code db2 => } prompt,
     * which buries the one line that matters ("SQL0204N ... is an undefined name"). Keep
     * only lines carrying an SQLnnnn / DB2nnnn message code, prompt stripped.
     */
    static List<String> db2MessageLines(String rawOutput) {
        if (rawOutput == null) return List.of();
        return rawOutput.lines()
            .map(l -> l.replaceFirst("^\\s*db2 => ", "").trim())
            .filter(l -> l.matches("^(SQL|DB2)\\d.*"))
            .toList();
    }

    /**
     * Turn an empty database into actionable hints by reading what the DB image's entrypoint
     * said on startup. The Maximo DB2 images log their chosen mode as
     * {@code [echopoint] Chosen option: <arg>} and fall through to creating an empty database
     * when that argument is missing, so an empty option is a direct pointer at the DB Command
     * setting rather than at the database itself.
     */
    private List<String> diagnoseEmptyDb(ContainerEntity dbContainer) {
        List<String> hints = new ArrayList<>();
        List<String> lines;
        try {
            lines = docker.fetchContainerLogs(dbContainer.getDockerContainerId(), 500);
        } catch (Exception e) {
            return hints;
        }

        boolean emptyOption = lines.stream()
            .filter(l -> l.contains("[echopoint] Chosen option:"))
            .anyMatch(l -> l.substring(l.indexOf("Chosen option:") + "Chosen option:".length()).isBlank());
        boolean created = lines.stream().anyMatch(l -> l.contains("Creating Maximo DB"));
        boolean restored = lines.stream().anyMatch(l -> l.contains("Restoring"));

        if (emptyOption) {
            hints.add("The DB entrypoint logged an empty \"Chosen option\", so it created an empty "
                + "database instead of restoring one. Set the DB Command on the image config "
                + "(or this environment's Configuration tab) to the argument the image expects "
                + "-- commonly \"restore\" -- and rebuild.");
        }
        if (created && !restored) {
            hints.add("The entrypoint ran its create-database path and never logged a restore.");
        }
        if (restored) {
            hints.add("The entrypoint did start a restore -- check the DB container logs for why "
                + "it did not finish (missing credentials for the backup source are a common cause).");
        }
        return hints;
    }

    private void waitForAppReady(ContainerEntity appContainer, Consumer<String> logger) {
        waitForAppReadyAfter(appContainer.getDockerContainerId(), 0, logger);
    }

    /**
     * Count how many BMXAA6472I "Maximo is ready" markers currently exist in the APP
     * container's messages.log. Used by restart paths to take a baseline before
     * bouncing Liberty so they can detect the NEW ready marker rather than the stale
     * one from initial startup. Returns 0 if the log doesn't exist yet or the exec fails.
     */
    /**
     * Count BMXAA6472I "Maximo is ready" markers across ALL /logs/messages*.log
     * files — current and any rolled-over ones. Liberty rotates messages.log on
     * restart (it timestamps the old one and starts a fresh file), so checking
     * only messages.log after a restart would lose the pre-restart marker and
     * the count would falsely appear to stay at 1.
     */
    public int countMaximoReadyMarkers(String dockerContainerId) {
        StringBuilder out = new StringBuilder();
        try {
            docker.execInContainer(dockerContainerId,
                // grep -c prints "file:count" per file; sum the counts. The `|| true`
                // keeps awk's input even if grep finds nothing (which returns 1).
                "grep -c BMXAA6472I /logs/messages*.log 2>/dev/null | "
                + "awk -F: '{s+=$NF} END {print s+0}' || echo 0",
                null, 15, line -> out.append(line));
        } catch (Exception e) {
            return 0;
        }
        for (String token : out.toString().split("\\s+")) {
            try { return Integer.parseInt(token.trim()); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Wait until Maximo logs BMXAA6472I ("Maximo is ready for client connections")
     * in /logs/messages*.log. We count across all rotated logs (Liberty renames
     * messages.log to messages_<ts>.log on each restart) and require the count
     * to exceed {@code beforeCount} — pass the pre-restart total so we detect
     * the NEW ready line, not a stale one from a previous boot.
     *
     * <p>This intentionally does NOT probe any HTTP endpoint. OSLC routes
     * (e.g. /maximo/oslc/whoami) come up before MXServer's RMI session is
     * bound, and Manage 9.1's webclient -> MXServer call still throws
     * BMXAA4188E ("could not connect to application server") for a short
     * window after the OSLC servlet starts responding. The BMXAA6472I log
     * line is emitted only after MXServer is fully initialized, so it's the
     * one signal that covers both Liberty and the business layer.
     *
     * <p>Throws RuntimeException on timeout.
     */
    public void waitForAppReadyAfter(String dockerContainerId, int beforeCount, Consumer<String> logger) {
        if (beforeCount > 0) {
            waitForLibertyLogRotation(dockerContainerId, logger);
        }

        int intervalSeconds = 5;
        int initialBudget = 72;       // 72 * 5s = 6 min — a healthy boot logs BMXAA6472I in ~75s
        int postRetryBudget = 144;    // 144 * 5s = 12 min after the auto-restart
        boolean autoRestarted = false;
        int attemptBudget = initialBudget;

        logger.accept("[app-wait] Waiting for BMXAA6472I in /logs/messages.log...");

        int attempt = 0;
        while (attempt < attemptBudget) {
            attempt++;
            int readyCount = countMaximoReadyMarkersInCurrent(dockerContainerId);
            if (readyCount > 0) {
                logger.accept("[app-wait] Maximo ready (attempt " + attempt + (autoRestarted ? ", after auto-restart" : "") + ")");
                return;
            }
            if (attempt % 12 == 0) {
                logger.accept("[app-wait] Still waiting for BMXAA6472I (attempt " + attempt + "/" + attemptBudget + ")");
            }
            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Maximo ready marker");
            }
            if (!autoRestarted && attempt == attemptBudget) {
                // MXServer init occasionally hangs on a restart — no BMXAA6461I "Bound rmi" and
                // no BMXAA6472I ever appear, even though Liberty + maximoui come up fine. The
                // only known recovery is another container restart. Do it once here so the build
                // doesn't burn the full 20-minute budget waiting on a JVM that won't progress.
                logger.accept("[app-wait] BMXAA6472I not seen after " + (initialBudget * intervalSeconds)
                    + "s — MXServer init appears hung. Issuing one automatic restart and continuing to wait.");
                try {
                    docker.restartContainer(dockerContainerId);
                } catch (Exception e) {
                    throw new RuntimeException("Auto-restart of hung app container failed: " + e.getMessage(), e);
                }
                waitForLibertyLogRotation(dockerContainerId, logger);
                autoRestarted = true;
                attempt = 0;
                attemptBudget = postRetryBudget;
                logger.accept("[app-wait] Waiting up to " + (postRetryBudget * intervalSeconds) + "s for BMXAA6472I after auto-restart...");
            }
        }
        throw new RuntimeException("BMXAA6472I did not appear in /logs/messages.log within budget"
            + (autoRestarted ? " (one auto-restart already attempted)" : ""));
    }

    /**
     * Probe whether Maximo's integration servlet (/meaweb) is actually serving.
     *
     * <p>meaweb.war's MOSServiceServlet is load-on-startup and depends on mboejb.jar's
     * MOSService remote EJB. On a restart these two EAR modules race: if the servlet's
     * init() runs before the EJB is bound, the servlet fails permanently for that boot
     * (SRVE0271E) and every /meaweb request 500s (BMXAA1581E) — which makes the dataload
     * fail wholesale, even though BMXAA6472I (MXServer ready) was logged. An
     * unauthenticated GET returns 200 when the servlet is alive (these dev envs disable
     * OSLC auth via mxe.int.enableosauth=0) and 500 when it lost the race.
     */
    public boolean isMeawebHealthy(String dockerContainerId, Consumer<String> logger) {
        StringBuilder out = new StringBuilder();
        try {
            docker.execInContainer(dockerContainerId,
                "curl -s -o /dev/null -w '%{http_code}' --max-time 25 "
                + "http://localhost:9080/meaweb/os/MXAPIORGANIZATION 2>/dev/null || echo 000",
                null, 40, line -> out.append(line));
        } catch (Exception e) {
            return false;
        }
        int code = 0;
        for (String token : out.toString().trim().split("\\s+")) {
            try { code = Integer.parseInt(token.trim()); } catch (NumberFormatException ignored) {}
        }
        boolean healthy = code > 0 && code < 500;
        logger.accept("[app-wait] /meaweb integration probe -> HTTP " + code
            + (healthy ? " (integration up)" : " (integration DOWN — servlet lost the EJB startup race)"));
        return healthy;
    }

    /**
     * Ensure the /meaweb integration servlet is healthy, restarting to re-roll the
     * EAR module-startup race when it isn't. Call after {@link #waitForAppReadyAfter}
     * on restarts that precede integration work (e.g. dataloads). Throws if it can't
     * recover within {@code maxRestarts} bounces.
     */
    public void ensureMeawebReady(String dockerContainerId, Consumer<String> logger) {
        int maxRestarts = 3;
        for (int i = 0; i <= maxRestarts; i++) {
            if (isMeawebHealthy(dockerContainerId, logger)) {
                return;
            }
            if (i == maxRestarts) break;
            logger.accept("[app-wait] Restarting APP to recover the /meaweb integration servlet ("
                + (i + 1) + "/" + maxRestarts + ")");
            int before = countMaximoReadyMarkers(dockerContainerId);
            try {
                docker.restartContainer(dockerContainerId);
            } catch (Exception e) {
                throw new RuntimeException("Restart to recover /meaweb failed: " + e.getMessage(), e);
            }
            waitForAppReadyAfter(dockerContainerId, before, logger);
        }
        throw new RuntimeException("Maximo /meaweb integration servlet did not recover after "
            + maxRestarts + " restarts");
    }

    /** Count BMXAA6472I markers in the CURRENT /logs/messages.log only — used to
     *  detect the new boot's ready line after Liberty rotates the old log out
     *  of the way. Returns 0 if the file doesn't exist or the exec fails. Use
     *  {@link #countMaximoReadyMarkers} when you need the total across rotated
     *  files. */
    public int countMaximoReadyMarkersInCurrent(String dockerContainerId) {
        StringBuilder out = new StringBuilder();
        try {
            docker.execInContainer(dockerContainerId,
                "grep -c BMXAA6472I /logs/messages.log 2>/dev/null || echo 0",
                null, 15, line -> out.append(line));
        } catch (Exception e) {
            return 0;
        }
        for (String token : out.toString().split("\\s+")) {
            try { return Integer.parseInt(token.trim()); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Block until Liberty has rotated its messages.log after a restart. Liberty
     * rotates on JVM start (not stop), so the stale BMXAA6472I from the previous
     * boot lingers in messages.log for a few seconds after restartContainer
     * returns. Once the new JVM starts up it renames the old file to
     * messages_&lt;ts&gt;.log and creates a fresh empty messages.log — at which
     * point the in-place count drops to 0 and we know rotation is done.
     */
    public void waitForLibertyLogRotation(String dockerContainerId, Consumer<String> logger) {
        logger.accept("[app-wait] Waiting for Liberty to rotate messages.log after restart...");
        int maxAttempts = 30;       // 30 * 2s = 60s cap
        int intervalSeconds = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Liberty log rotation");
            }
            int currentCount = countMaximoReadyMarkersInCurrent(dockerContainerId);
            if (currentCount == 0) {
                logger.accept("[app-wait] messages.log rotated (attempt " + attempt + ")");
                return;
            }
        }
        logger.accept("[app-wait] Log rotation not detected after " + (maxAttempts * intervalSeconds) + "s; proceeding anyway");
    }

    private void persistLogLine(EnvironmentEntity env, String line) {
        try {
            BuildLogEntity logEntry = new BuildLogEntity();
            logEntry.setEnvironment(env);
            logEntry.setLine(line);
            logRepo.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist log line: {}", e.getMessage());
        }
    }
}
