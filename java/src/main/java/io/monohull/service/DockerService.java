package io.monohull.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.BuildImageResultCallback;

import java.io.File;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private static final String MANAGED_LABEL = "com.pq.maximo.managed";
    /** Shared docker network that the host's Traefik also joins, so it can reach published APP containers. */
    public static final String PUBLIC_NETWORK = "made-public";

    private final DockerClient docker;
    private final RegistryCredentialService registryCredentials;

    // Managed /16 block from which each env network gets an explicit /24. Docker's
    // default address pools (172.17-172.31/16, 192.168.0.0/16) are fully subnetted
    // on busy hosts, so auto-allocation fails ("all predefined address pools have
    // been fully subnetted"). Allocating from this block sidesteps that. Must not
    // overlap the host LAN or any cluster/overlay networks on the host.
    @Value("${monohull.network.subnet-pool:10.100.0.0/16}")
    private String subnetPool;

    public DockerService(DockerClient docker, RegistryCredentialService registryCredentials) {
        this.docker = docker;
        this.registryCredentials = registryCredentials;
    }

    public void ensureNetwork(String name) {
        boolean exists = docker.listNetworksCmd().exec().stream()
            .anyMatch(n -> name.equals(n.getName()));
        if (exists) {
            return;
        }
        String subnet = allocateSubnet();
        Network.Ipam ipam = new Network.Ipam().withConfig(new Network.Ipam.Config().withSubnet(subnet));
        docker.createNetworkCmd().withName(name).withDriver("bridge").withIpam(ipam)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .exec();
        log.info("Created network {} with subnet {}", name, subnet);
    }

    /**
     * Pick the first free /24 in {@link #subnetPool} not already used by any docker
     * network. {@code synchronized} so concurrent (async) builds don't race onto the
     * same subnet. Assumes a /16 pool (iterates the third octet).
     */
    private synchronized String allocateSubnet() {
        String base = subnetPool.split("/")[0];
        String[] octets = base.split("\\.");
        String prefix = octets[0] + "." + octets[1] + ".";
        Set<Integer> used = new java.util.HashSet<>();
        for (Network net : docker.listNetworksCmd().exec()) {
            if (net.getIpam() == null || net.getIpam().getConfig() == null) continue;
            for (Network.Ipam.Config cfg : net.getIpam().getConfig()) {
                String subnet = cfg.getSubnet();
                if (subnet != null && subnet.startsWith(prefix)) {
                    try {
                        used.add(Integer.parseInt(subnet.substring(prefix.length()).split("\\.")[0]));
                    } catch (NumberFormatException ignore) {
                        // non-/24 entry in the block; skip
                    }
                }
            }
        }
        for (int third = 0; third <= 255; third++) {
            if (!used.contains(third)) {
                return prefix + third + ".0/24";
            }
        }
        throw new IllegalStateException("No free /24 subnet available in pool " + subnetPool);
    }

    public void pullImage(String image, Consumer<String> logger) throws InterruptedException {
        pullImage(image, logger, null);
    }

    /**
     * Pull a single image. If `announcedRegistries` is non-null, this method announces
     * "Authenticating to registry: ..." only the first time a given registry is seen
     * (the set is mutated). Pass null to always announce (legacy behaviour).
     */
    public void pullImage(String image, Consumer<String> logger,
                          java.util.Set<String> announcedRegistries) throws InterruptedException {
        logger.accept("Pulling image: " + image);
        var cmd = docker.pullImageCmd(image);
        AuthConfig auth = registryCredentials.authConfigFor(image);
        if (auth != null) {
            String registry = auth.getRegistryAddress();
            if (announcedRegistries == null) {
                logger.accept("Authenticating to registry: " + registry);
            } else if (announcedRegistries.add(registry)) {
                logger.accept("Authenticating to registry: " + registry);
            }
            cmd.withAuthConfig(auth);
        }
        cmd.start().awaitCompletion(20, TimeUnit.MINUTES);
        logger.accept("Pulled image: " + image);
    }

    /**
     * Pull multiple images, announcing authentication once per unique registry.
     */
    public void pullImages(Iterable<String> images, Consumer<String> logger) throws InterruptedException {
        java.util.Set<String> announced = new java.util.HashSet<>();
        for (String image : images) {
            pullImage(image, logger, announced);
        }
    }

    /**
     * Create + start the database container.
     *
     * <p>{@code command} is the argv handed to the image's entrypoint. Several Maximo DB
     * images branch on their first argument to decide whether to restore a backup or leave
     * the freshly created empty database in place — without it they quietly produce an empty
     * DB and the failure only surfaces much later, as a Maximo table that doesn't exist.
     * Blank leaves the container's command unset so the image's own CMD applies, which is
     * the right thing for images that ship the database baked in.
     */
    public String runDbContainer(String name, String image, String network, int hostPort, int containerPort,
                                 String volumeName, String volumeTarget, List<String> env,
                                 List<Bind> extraBinds, String command,
                                 String networkAlias, Consumer<String> logger) {
        java.util.List<Bind> allBinds = new java.util.ArrayList<>();
        allBinds.add(new Bind(volumeName, new Volume(volumeTarget)));
        if (extraBinds != null) allBinds.addAll(extraBinds);

        HostConfig host = HostConfig.newHostConfig()
            .withPortBindings(PortBinding.parse(hostPort + ":" + containerPort))
            .withBinds(allBinds)
            .withPrivileged(true)
            .withNetworkMode(network);

        List<String> cmd = splitCommand(command);

        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, network, networkAlias,
            List.of(hostPort + ":" + containerPort),
            allBinds, env, Map.of(MANAGED_LABEL, "true"),
            null, null, true, cmd));

        replaceLeftover(name, logger);
        var cmdBuilder = docker.createContainerCmd(image)
            .withName(name)
            .withEnv(env)
            .withExposedPorts(ExposedPort.tcp(containerPort))
            .withHostConfig(host)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .withAliases(networkAlias);
        if (cmd != null) {
            cmdBuilder.withCmd(cmd.toArray(new String[0]));
        }
        CreateContainerResponse res = cmdBuilder.exec();

        startOrRemove(res.getId(), name);
        logger.accept("Started DB container: " + name);
        return res.getId();
    }

    /**
     * Split a user-entered command line into argv, honouring single and double quotes so
     * arguments with spaces survive (e.g. {@code restore --file "my backup.tar.gz"}). This
     * is deliberately not a shell: there is no expansion, globbing, or escaping beyond
     * quote grouping, because the string is passed straight to the container's entrypoint
     * rather than through a shell. Returns null for null/blank input, meaning "no command".
     */
    static List<String> splitCommand(String command) {
        if (command == null || command.isBlank()) return null;
        List<String> args = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inArg = false;
        char quote = 0;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (quote != 0) {
                if (ch == quote) quote = 0;
                else current.append(ch);
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                inArg = true;
            } else if (Character.isWhitespace(ch)) {
                if (inArg) {
                    args.add(current.toString());
                    current.setLength(0);
                    inArg = false;
                }
            } else {
                current.append(ch);
                inArg = true;
            }
        }
        if (inArg) args.add(current.toString());
        return args.isEmpty() ? null : args;
    }

    /**
     * Create + start the Maximo APP (Liberty) container.
     *
     * <p>When {@code publicHost} is non-blank, the container is also labelled for
     * Traefik (Host-based routing to its HTTP port 9080) and joined to the shared
     * {@link #PUBLIC_NETWORK}, so the host's Traefik can serve it at
     * {@code https://<publicHost>/maximo} via the host's wildcard DNS/route. Blank
     * {@code publicHost} (no domain configured) leaves the container LAN-only.
     */
    public String runAppContainer(String name, String image, String network,
                                  int httpHostPort, int httpsHostPort,
                                  List<Bind> binds, List<String> env,
                                  String networkAlias, String publicHost, Consumer<String> logger) {
        long shmSize = 2L * 1024 * 1024 * 1024;
        long memory = 6114L * 1024 * 1024;
        HostConfig host = HostConfig.newHostConfig()
            .withShmSize(shmSize)
            .withMemory(memory)
            .withPortBindings(
                PortBinding.parse(httpHostPort + ":9080"),
                PortBinding.parse(httpsHostPort + ":9443"))
            .withBinds(binds)
            .withNetworkMode(network);

        boolean publish = publicHost != null && !publicHost.isBlank();
        Map<String, String> labels = new java.util.HashMap<>();
        labels.put(MANAGED_LABEL, "true");
        if (publish) {
            // Router/service keyed by the (unique) container name. The rule host is the
            // env's public FQDN; Traefik reaches the container over PUBLIC_NETWORK on 9080.
            labels.put("traefik.enable", "true");
            labels.put("traefik.http.routers." + name + ".rule", "Host(`" + publicHost + "`)");
            labels.put("traefik.http.routers." + name + ".entrypoints", "web");
            labels.put("traefik.http.services." + name + ".loadbalancer.server.port", "9080");
            labels.put("traefik.docker.network", PUBLIC_NETWORK);
        }

        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, network, networkAlias,
            List.of(httpHostPort + ":9080", httpsHostPort + ":9443"),
            binds, env, labels,
            shmSize, memory, false, null));

        replaceLeftover(name, logger);
        CreateContainerResponse res = docker.createContainerCmd(image)
            .withName(name)
            .withEnv(env)
            .withExposedPorts(ExposedPort.tcp(9080), ExposedPort.tcp(9443))
            .withHostConfig(host)
            .withLabels(labels)
            .withAliases(networkAlias)
            .exec();

        startOrRemove(res.getId(), name);

        if (publish) {
            // Attach to the shared Traefik network so Traefik can route to it. A failure
            // here must also reap the (already running) container: the caller never
            // learns the ID, so a leftover would squat on the name forever.
            try {
                ensureNetwork(PUBLIC_NETWORK);
                docker.connectToNetworkCmd().withContainerId(res.getId()).withNetworkId(PUBLIC_NETWORK).exec();
            } catch (RuntimeException publishFailure) {
                try {
                    docker.removeContainerCmd(res.getId()).withForce(true).exec();
                } catch (RuntimeException cleanup) {
                    log.warn("Could not remove container {} after failed publish", name, cleanup);
                }
                throw publishFailure;
            }
            logger.accept("Published APP container " + name + " at https://" + publicHost + "/maximo");
        }

        logger.accept("Started APP container: " + name);
        return res.getId();
    }

    public String runMockContainer(String name, String image, String network,
                                   int hostPort, int containerPort,
                                   String networkAlias, Consumer<String> logger) {
        HostConfig host = HostConfig.newHostConfig()
            .withPortBindings(PortBinding.parse(hostPort + ":" + containerPort))
            .withNetworkMode(network);

        // Two aliases on the env network:
        //   - "mock" (role name, short and consistent with Monohull's other aliases)
        //   - "mock-receiver" (matches the hostname existing Maximo endpoint configs use,
        //      so MAXENDPOINTDTL rows pointing at http://mock-receiver:8085 resolve)
        String secondAlias = "mock-receiver";
        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, network, networkAlias + "," + secondAlias,
            List.of(hostPort + ":" + containerPort),
            null, null, Map.of(MANAGED_LABEL, "true"),
            null, null, false, null));

        replaceLeftover(name, logger);
        CreateContainerResponse res = docker.createContainerCmd(image)
            .withName(name)
            .withExposedPorts(ExposedPort.tcp(containerPort))
            .withHostConfig(host)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .withAliases(networkAlias, secondAlias)
            .exec();

        startOrRemove(res.getId(), name);
        logger.accept("Started MOCK container: " + name + " (aliases: " + networkAlias + ", " + secondAlias + ")");
        return res.getId();
    }

    public String runSmtpContainer(String name, String image, String network,
                                   int smtpHostPort, int uiHostPort,
                                   String networkAlias, Consumer<String> logger) {
        HostConfig host = HostConfig.newHostConfig()
            .withPortBindings(
                PortBinding.parse(smtpHostPort + ":1025"),
                PortBinding.parse(uiHostPort + ":8025"))
            .withNetworkMode(network);

        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, network, networkAlias,
            List.of(smtpHostPort + ":1025", uiHostPort + ":8025"),
            null, null, Map.of(MANAGED_LABEL, "true"),
            null, null, false, null));

        replaceLeftover(name, logger);
        CreateContainerResponse res = docker.createContainerCmd(image)
            .withName(name)
            .withExposedPorts(ExposedPort.tcp(1025), ExposedPort.tcp(8025))
            .withHostConfig(host)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .withAliases(networkAlias)
            .exec();

        startOrRemove(res.getId(), name);
        logger.accept("Started SMTP container: " + name);
        return res.getId();
    }

    /**
     * Ensure a local image with the given tag exists, building it from {@code contextDir}
     * if not. Build output streams into the supplied logger. The build context directory
     * must contain a Dockerfile. Used for images that Monohull ships in-tree (mock-receiver)
     * rather than pulling from a registry.
     */
    public void ensureLocalImageBuilt(String imageName, File contextDir, Consumer<String> logger) {
        try {
            docker.inspectImageCmd(imageName).exec();
            logger.accept("Local image already present: " + imageName);
            return;
        } catch (NotFoundException ignored) {
            // fall through to build
        }
        if (!contextDir.isDirectory()) {
            throw new IllegalStateException("Build context not found for " + imageName + ": " + contextDir.getAbsolutePath());
        }
        logger.accept("Building local image " + imageName + " from " + contextDir.getAbsolutePath());
        String imageId = docker.buildImageCmd(contextDir)
            .withTags(Set.of(imageName))
            .exec(new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    if (item.getStream() != null) {
                        for (String line : item.getStream().split("\\r?\\n")) {
                            if (!line.isEmpty()) logger.accept("[build] " + line);
                        }
                    }
                    super.onNext(item);
                }
            })
            .awaitImageId();
        logger.accept("Built local image " + imageName + " (id " + imageId + ")");
    }

    public String runAdmContainer(String name, String image, String network,
                                  List<Bind> binds, List<String> env,
                                  String networkAlias, String runAsUser,
                                  Consumer<String> logger) {
        HostConfig host = HostConfig.newHostConfig()
            .withBinds(binds)
            .withNetworkMode(network);

        List<String> cmd = List.of("/bin/bash", "-c", "while true; do sleep 1000; done;");
        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, network, networkAlias,
            null, binds, env, Map.of(MANAGED_LABEL, "true"),
            null, null, false, runAsUser, cmd));

        var cmdBuilder = docker.createContainerCmd(image)
            .withName(name)
            .withEnv(env)
            .withCmd(cmd.toArray(new String[0]))
            .withHostConfig(host)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .withAliases(networkAlias);
        if (runAsUser != null && !runAsUser.isBlank()) {
            cmdBuilder.withUser(runAsUser);
        }
        replaceLeftover(name, logger);
        CreateContainerResponse res = cmdBuilder.exec();

        startOrRemove(res.getId(), name);
        logger.accept("Started ADM container: " + name);
        return res.getId();
    }

    /** Ephemeral package-builder for BUILDER-type actions: parked on a sleep loop so the
     *  caller can exec the build into it, then remove it. No ports and no env network —
     *  the build only touches its bind mounts. */
    public String runBuilderContainer(String name, String image, List<Bind> binds,
                                      List<String> env, Consumer<String> logger) {
        removeIfExists(name);
        HostConfig host = HostConfig.newHostConfig().withBinds(binds);
        List<String> cmd = List.of("/bin/bash", "-c", "while true; do sleep 1000; done;");
        logger.accept("[docker] " + formatDockerRunCommand(
            name, image, null, null, null, binds, env,
            Map.of(MANAGED_LABEL, "true"), null, null, false, null, cmd));

        CreateContainerResponse res = docker.createContainerCmd(image)
            .withName(name)
            .withEnv(env)
            .withCmd(cmd.toArray(new String[0]))
            .withHostConfig(host)
            .withLabels(Map.of(MANAGED_LABEL, "true"))
            .exec();
        startOrRemove(res.getId(), name);
        logger.accept("Started builder container: " + name);
        return res.getId();
    }

    /** Copy a single file between containers by streaming the tar archive returned by
     *  the source straight into the destination ({@code docker cp} equivalent, daemon-side
     *  data only). The file keeps its name; {@code destDir} is created by the daemon. */
    public void copyFileBetweenContainers(String srcContainerId, String srcPath,
                                          String destContainerId, String destDir) {
        try (var tarStream = docker.copyArchiveFromContainerCmd(srcContainerId, srcPath).exec()) {
            docker.copyArchiveToContainerCmd(destContainerId)
                .withTarInputStream(tarStream)
                .withRemotePath(destDir)
                .exec();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Copy " + srcPath + " between containers failed: " + e.getMessage(), e);
        }
    }

    private static String formatDockerRunCommand(
            String name, String image, String network, String networkAlias,
            List<String> portBindings, List<Bind> binds, List<String> env,
            Map<String, String> labels,
            Long shmSizeBytes, Long memoryBytes, boolean privileged,
            List<String> cmd) {
        return formatDockerRunCommand(name, image, network, networkAlias, portBindings,
            binds, env, labels, shmSizeBytes, memoryBytes, privileged, null, cmd);
    }

    private static String formatDockerRunCommand(
            String name, String image, String network, String networkAlias,
            List<String> portBindings, List<Bind> binds, List<String> env,
            Map<String, String> labels,
            Long shmSizeBytes, Long memoryBytes, boolean privileged,
            String runAsUser,
            List<String> cmd) {
        StringBuilder sb = new StringBuilder("docker run -d");
        sb.append(" --name ").append(name);
        if (network != null && !network.isBlank()) sb.append(" --network ").append(network);
        if (networkAlias != null && !networkAlias.isBlank()) sb.append(" --network-alias ").append(networkAlias);
        if (runAsUser != null && !runAsUser.isBlank()) sb.append(" --user ").append(runAsUser);
        if (privileged) sb.append(" --privileged");
        if (shmSizeBytes != null) sb.append(" --shm-size ").append(formatBytes(shmSizeBytes));
        if (memoryBytes != null) sb.append(" --memory ").append(formatBytes(memoryBytes));
        if (portBindings != null) for (String p : portBindings) sb.append(" -p ").append(p);
        if (binds != null) for (Bind b : binds) sb.append(" -v ").append(formatBind(b));
        if (env != null) for (String e : env) sb.append(" -e ").append(shellQuote(e));
        if (labels != null) for (Map.Entry<String, String> l : labels.entrySet())
            sb.append(" --label ").append(l.getKey()).append("=").append(shellQuote(l.getValue()));
        sb.append(" ").append(image);
        if (cmd != null) for (String c : cmd) sb.append(" ").append(shellQuote(c));
        return sb.toString();
    }

    private static String formatBind(Bind b) {
        StringBuilder sb = new StringBuilder();
        sb.append(b.getPath()).append(":").append(b.getVolume().getPath());
        if (b.getAccessMode() != null && b.getAccessMode() == AccessMode.ro) {
            sb.append(":ro");
        }
        return sb.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes % (1024L * 1024 * 1024) == 0) return (bytes / (1024L * 1024 * 1024)) + "g";
        if (bytes % (1024L * 1024) == 0) return (bytes / (1024L * 1024)) + "m";
        if (bytes % 1024 == 0) return (bytes / 1024) + "k";
        return Long.toString(bytes);
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        if (s.isEmpty()) return "''";
        // Safe characters that don't need quoting
        if (s.matches("[a-zA-Z0-9_+=:./@,-]+")) return s;
        // Use single quotes; escape any embedded single quotes
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public List<String> fetchContainerLogs(String containerId, int tail) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try {
            docker.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        for (String line : new String(frame.getPayload()).split("\\r?\\n")) {
                            lines.add(line);
                        }
                    }
                })
                .awaitCompletion(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return lines;
    }

    public void streamContainerLogs(String containerId, Consumer<String> logger) {
        docker.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    logger.accept(new String(frame.getPayload()));
                }
            });
    }

    public int execInContainer(String containerId, String command, String workingDir,
                               int timeoutSeconds, Consumer<String> logger) {
        var execCreateCmd = docker.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd("/bin/bash", "-c", command);

        if (workingDir != null && !workingDir.isBlank()) {
            execCreateCmd.withWorkingDir(workingDir);
        }

        ExecCreateCmdResponse execCreate = execCreateCmd.exec();
        String execId = execCreate.getId();

        boolean completed;
        try {
            completed = docker.execStartCmd(execId)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        logger.accept(new String(frame.getPayload()));
                    }
                })
                .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.accept("[error] Action interrupted after " + timeoutSeconds + "s wait");
            return -1;
        }

        if (!completed) {
            // awaitCompletion returns false on timeout without throwing — without this branch we'd
            // silently fall through to inspectExecCmd, get a null exit code (process still running),
            // and report a bare "exit -1" with no explanation. The exec inside the container is
            // NOT killed by this; docker-java has no direct kill-exec API. The user has to either
            // wait it out and rerun, or kill the process inside the container manually.
            logger.accept("[error] Action timed out after " + timeoutSeconds + "s. The process may "
                + "still be running inside the container; check before rerunning.");
            return -1;
        }

        Long exitCodeLong = docker.inspectExecCmd(execId).exec().getExitCodeLong();
        return exitCodeLong != null ? exitCodeLong.intValue() : -1;
    }

    /**
     * Start an interactive shell inside a running container ({@code docker exec -it}
     * equivalent) and return a handle for the browser-terminal bridge. Output bytes are
     * pushed to {@code onOutput} from the docker transport thread; {@code onClosed} fires
     * once when the shell exits or the connection drops. Prefers bash, falls back to sh
     * so slim images (busybox/mailpit) still get a working prompt.
     */
    public TerminalSession startTerminal(String containerId, Consumer<byte[]> onOutput, Runnable onClosed) {
        ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
            .withAttachStdin(true)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withTty(true)
            .withEnv(List.of("TERM=xterm-256color"))
            .withCmd("/bin/sh", "-c", "[ -x /bin/bash ] && exec /bin/bash; exec /bin/sh")
            .exec();

        QueueInputStream stdin = new QueueInputStream();
        var callback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                onOutput.accept(frame.getPayload());
            }

            @Override
            public void onComplete() {
                super.onComplete();
                onClosed.run();
            }

            @Override
            public void onError(Throwable throwable) {
                super.onError(throwable);
                onClosed.run();
            }
        };
        docker.execStartCmd(exec.getId())
            .withStdIn(stdin)
            .withTty(true)
            .exec(callback);
        return new TerminalSession(exec.getId(), stdin, callback);
    }

    /** Propagate an xterm resize to the PTY so full-screen tools (vi, top) render correctly.
     *  Best-effort: a failed resize should never kill the terminal itself. */
    public void resizeTerminal(String execId, int rows, int cols) {
        try {
            docker.resizeExecCmd(execId).withSize(rows, cols).exec();
        } catch (RuntimeException e) {
            log.debug("Resize of exec {} to {}x{} failed: {}", execId, cols, rows, e.getMessage());
        }
    }

    /** Live handle for one interactive terminal exec: write() feeds the shell's stdin,
     *  close() tears down stdin and the hijacked connection (the shell then sees EOF/HUP). */
    public static final class TerminalSession implements java.io.Closeable {
        private final String execId;
        private final QueueInputStream stdin;
        private final ResultCallback.Adapter<Frame> callback;

        TerminalSession(String execId, QueueInputStream stdin, ResultCallback.Adapter<Frame> callback) {
            this.execId = execId;
            this.stdin = stdin;
            this.callback = callback;
        }

        public String execId() {
            return execId;
        }

        public void write(byte[] data) {
            stdin.put(data);
        }

        @Override
        public void close() {
            stdin.close();
            try {
                callback.close();
            } catch (java.io.IOException e) {
                log.debug("Closing terminal exec {} raised: {}", execId, e.getMessage());
            }
        }
    }

    /**
     * Stdin bridge fed from websocket threads and drained by the docker transport thread.
     * Deliberately not a {@link java.io.PipedInputStream}: piped streams throw "write end
     * dead" when the last writing thread terminates, and websocket writes hop across the
     * servlet pool's threads.
     */
    static final class QueueInputStream extends java.io.InputStream {
        private static final byte[] EOF = new byte[0];
        private final java.util.concurrent.BlockingQueue<byte[]> queue =
            new java.util.concurrent.LinkedBlockingQueue<>();
        private byte[] current;
        private int pos;
        private volatile boolean closed;

        void put(byte[] data) {
            if (!closed && data != null && data.length > 0) {
                queue.add(data);
            }
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) return 0;
            while (current == null || pos >= current.length) {
                if (closed && queue.isEmpty()) return -1;
                try {
                    current = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                if (current == EOF) {
                    closed = true;
                    current = null;
                    return -1;
                }
                pos = 0;
            }
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        @Override
        public void close() {
            closed = true;
            queue.add(EOF);
        }
    }

    public void removeIfExists(String name) {
        try {
            docker.removeContainerCmd(name).withForce(true).exec();
        } catch (NotFoundException ignored) {
        }
    }

    /**
     * Remove any leftover container holding {@code name} (typically from a previous
     * failed build whose ID was never persisted) so the create that follows can't 409.
     * Announces the removal so the build log explains what happened to the old one.
     */
    private void replaceLeftover(String name, Consumer<String> logger) {
        try {
            docker.removeContainerCmd(name).withForce(true).exec();
            logger.accept("[docker] Removed leftover container " + name + " from a previous build");
        } catch (NotFoundException ignored) {
            // normal case: nothing to replace
        }
    }

    /**
     * Start a just-created container; when start fails (port already bound, out of
     * disk, OOM), remove the container again so no orphan is left squatting on the
     * name — the caller never learns the ID, so nothing else could clean it up.
     */
    private void startOrRemove(String containerId, String name) {
        try {
            docker.startContainerCmd(containerId).exec();
        } catch (RuntimeException start) {
            try {
                docker.removeContainerCmd(containerId).withForce(true).exec();
            } catch (RuntimeException cleanup) {
                log.warn("Could not remove container {} after failed start", name, cleanup);
            }
            throw start;
        }
    }

    public InspectContainerResponse inspectContainer(String containerId) {
        return docker.inspectContainerCmd(containerId).exec();
    }

    public void stopContainer(String containerId) {
        docker.stopContainerCmd(containerId).exec();
    }

    public void startContainer(String containerId) {
        docker.startContainerCmd(containerId).exec();
    }

    public void restartContainer(String containerId) {
        docker.restartContainerCmd(containerId).exec();
    }

    public List<Container> listManagedContainers() {
        return docker.listContainersCmd()
            .withShowAll(true)
            .withLabelFilter(Map.of(MANAGED_LABEL, "true"))
            .exec();
    }

    /** Remove the named network. Already-gone is fine; real failures propagate so the
     *  teardown sweep can report them instead of silently leaking the network. */
    public void removeNetwork(String networkName) {
        docker.listNetworksCmd().withNameFilter(networkName).exec()
            .forEach(n -> docker.removeNetworkCmd(n.getId()).exec());
    }

    /** Remove the named volume. Already-gone is fine; real failures (in use, daemon
     *  down) propagate so the teardown sweep can report them. */
    public void removeVolume(String volumeName) {
        if (volumeName == null || volumeName.isBlank()) return;
        try {
            docker.removeVolumeCmd(volumeName).exec();
        } catch (NotFoundException ignored) {
        }
    }

    /**
     * Recursively delete {@code parentHostPath/subdir} on the docker host.
     *
     * <p>Monohull itself runs inside a container and can't see the host filesystem
     * directly, so we spawn a short-lived BusyBox container with the parent
     * host directory bind-mounted at /parent and have it `rm -rf` the target
     * subdir. The container auto-removes itself once `rm` finishes. We mount
     * the PARENT (not the subdir itself) so that `rm` can also unlink the
     * subdir's mount-point directory — you can't remove a path that's also a
     * bind-mount root from inside the same container.
     *
     * <p>Used during env teardown to clean up the per-env subdir under
     * {@code EnvironmentConfig.hostVolumePath} (e.g.
     * /docker/volumefs/myclient/made-demo-1 with its config/ and logs/ kids).
     * Silently no-ops if either argument is blank or the path doesn't exist.
     */
    public void removeHostPathSubdir(String parentHostPath, String subdir) {
        if (parentHostPath == null || parentHostPath.isBlank()) return;
        if (subdir == null || subdir.isBlank()) return;
        // Defensive: the subdir must be a single path component, no traversal.
        if (subdir.contains("/") || subdir.contains("..") || subdir.equals(".") || subdir.equals("*")) {
            log.warn("Refusing to remove host subdir with suspicious name: {}", subdir);
            return;
        }

        String cleanupImage = "busybox:latest";
        try {
            try {
                docker.inspectImageCmd(cleanupImage).exec();
            } catch (NotFoundException notFound) {
                log.info("Pulling {} for host-path cleanup", cleanupImage);
                docker.pullImageCmd(cleanupImage).start().awaitCompletion(2, TimeUnit.MINUTES);
            }

            HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind(parentHostPath, new Volume("/parent")))
                .withAutoRemove(true);

            CreateContainerResponse res = docker.createContainerCmd(cleanupImage)
                .withHostConfig(hostConfig)
                .withCmd("sh", "-c", "rm -rf '/parent/" + subdir + "'")
                .withLabels(Map.of(MANAGED_LABEL, "true"))
                .exec();

            docker.startContainerCmd(res.getId()).exec();
            docker.waitContainerCmd(res.getId())
                .exec(new com.github.dockerjava.core.command.WaitContainerResultCallback())
                .awaitCompletion(60, TimeUnit.SECONDS);
            log.info("Removed host path: {}/{}", parentHostPath, subdir);
        } catch (Exception e) {
            log.warn("Failed to remove host path {}/{}: {}", parentHostPath, subdir, e.getMessage());
        }
    }
}
