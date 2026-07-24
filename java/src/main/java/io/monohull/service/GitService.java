package io.monohull.service;

import io.monohull.dto.PrEvent;
import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.RepoAuthMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a PR branch out into a per-PR directory under the shared {@code /docker/volumefs}
 * host volume — the same host path is then bind-mounted into the build containers as the
 * workspace. Progress is streamed to {@link LogSink} keyed by the PR build's {@code buildId}.
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    /** Root for per-PR checkouts. Must be on a host-mounted volume so the same path resolves
     *  for bind mounts into build containers. */
    @Value("${monohull.pr-builds.workspace-root:/docker/volumefs/pr-builds}")
    private String workspaceRoot;

    private final LogSink logSink;

    public GitService(LogSink logSink) {
        this.logSink = logSink;
    }

    /** Clone (or update) the repo and check out the PR's commit into {@code dir} (the same path
     *  the caller mounts as the build workspace). Throws on any git failure so the caller can
     *  mark the build FAILED. */
    public Path checkout(ConnectedRepositoryEntity repo, PrEvent ev, String buildId, Path dir) {
        SshSession ssh = null;
        try {
            Map<String, String> env;
            String authUrl;
            if (repo.getAuthMethod() == RepoAuthMethod.SSH) {
                ssh = prepareSsh(repo);          // temp deploy key + GIT_SSH_COMMAND
                env = ssh.env();
                authUrl = repo.getRepoUrl().trim();   // SSH URL carries no embedded secret
            } else {
                env = Map.of();
                authUrl = authUrl(repo);
            }
            return doCheckout(repo, ev, buildId, dir, authUrl, env);
        } finally {
            if (ssh != null) ssh.cleanup();
        }
    }

    private Path doCheckout(ConnectedRepositoryEntity repo, PrEvent ev, String buildId, Path dir,
                            String authUrl, Map<String, String> env) {
        String branch = ev.sourceBranch();
        String sha = ev.sha();

        try {
            Files.createDirectories(dir.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create workspace dir " + dir.getParent() + ": " + e.getMessage(), e);
        }

        boolean cloned = Files.exists(dir.resolve(".git"));
        if (!cloned) {
            run(buildId, dir.getParent(),
                List.of("git", "clone", "--no-tags", "--", authUrl, dir.toString()),
                "clone " + repo.getRepoFullName(), env);
        } else {
            run(buildId, dir, List.of("git", "remote", "set-url", "origin", authUrl), "refresh remote", env);
        }

        run(buildId, dir, List.of("git", "fetch", "--force", "--no-tags", "origin", branch), "fetch " + branch, env);
        String target = (sha != null && !sha.isBlank()) ? sha : "FETCH_HEAD";
        run(buildId, dir, List.of("git", "checkout", "--force", target), "checkout " + shortRef(sha, branch), env);
        run(buildId, dir, List.of("git", "reset", "--hard", target), "reset", env);
        run(buildId, dir, List.of("git", "clean", "-fdx"), "clean", env);
        openUpPermissions(dir, buildId);
        logSink.append(buildId, "[git] checkout complete: " + dir);
        return dir;
    }

    /** The checkout runs as Monohull's container user (root), but the APP/ADM build containers
     *  run as a non-root image user (e.g. maximoinstall uid 1001) that can't write inside a
     *  root-owned 755 tree — ant then fails creating classes dirs (same issue as
     *  BuildService.createEnvSubdir). Open the tree up after every checkout, since each
     *  fetch/checkout creates new root-owned files. Best effort: a failure is logged and the
     *  build proceeds (it may still succeed if the image user matches the tree owner). */
    private void openUpPermissions(Path dir, String buildId) {
        try {
            run(buildId, dir, List.of("chmod", "-R", "a+rwX", dir.toString()), "open workspace permissions", Map.of());
        } catch (RuntimeException e) {
            logSink.append(buildId, "[git] WARN: could not open workspace permissions: " + e.getMessage());
        }
    }

    /** Remove a per-PR workspace (best effort). Refuses paths outside the configured root. */
    public void cleanup(String workspacePath, String buildId) {
        if (workspacePath == null || workspacePath.isBlank()) return;
        Path dir = Paths.get(workspacePath).toAbsolutePath().normalize();
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        if (!dir.startsWith(root) || dir.equals(root)) {
            log.warn("Refusing to clean workspace outside root: {}", dir);
            return;
        }
        try {
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.delete(p); } catch (IOException ignored) { }
                    });
                }
                if (buildId != null) logSink.append(buildId, "[git] removed workspace " + dir);
            }
        } catch (IOException e) {
            log.warn("Failed to clean workspace {}: {}", dir, e.getMessage());
        }
    }

    public Path workspaceDir(String repoFullName, int prNumber, String discriminator) {
        String safe = repoFullName.replaceAll("[^a-zA-Z0-9._-]", "-");
        String leaf = "pr-" + prNumber + (discriminator == null || discriminator.isBlank() ? "" : "-" + discriminator);
        return Paths.get(workspaceRoot, safe, leaf);
    }

    // --- helpers ---

    /** HTTPS clone URL with embedded credentials for private repos. Public repos (no token)
     *  use the URL as-is. Provider-default usernames are applied when none is configured.
     *  Only used for {@link RepoAuthMethod#HTTPS}; SSH repos clone the raw URL. */
    private String authUrl(ConnectedRepositoryEntity repo) {
        String url = repo.getRepoUrl().trim();
        String token = repo.getCloneToken();
        if (token == null || token.isBlank()) return url;
        String user = repo.getCloneUsername();
        if (user == null || user.isBlank()) {
            user = switch (repo.getProvider()) {
                case GITHUB -> "x-access-token";
                case GITLAB -> "oauth2";
                case BITBUCKET -> "x-token-auth";
            };
        }
        String rest = url.replaceFirst("^https?://", "");
        return "https://" + enc(user) + ":" + enc(token) + "@" + rest;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Materialise the repo's SSH deploy key into a private temp dir and build the env that
     *  points git's transport ssh at it. Host keys are trusted on first use (accept-new) into a
     *  persistent known_hosts under the workspace root, so repeat clones of a host are pinned.
     *  Passphrase-protected keys are unlocked non-interactively via SSH_ASKPASS. */
    private SshSession prepareSsh(ConnectedRepositoryEntity repo) {
        String key = repo.getSshPrivateKey();
        if (key == null || key.isBlank()) {
            throw new RuntimeException("SSH auth selected but no private key is configured for "
                + repo.getRepoFullName());
        }
        try {
            Path dir = Files.createTempDirectory("made-ssh-");
            restrict(dir, "rwx------");

            Path keyFile = dir.resolve("id_deploy");
            // OpenSSH refuses keys without a trailing newline ("invalid format").
            Files.writeString(keyFile, key.endsWith("\n") ? key : key + "\n");
            restrict(keyFile, "rw-------");

            Path knownHosts = Paths.get(workspaceRoot, ".ssh_known_hosts");
            Files.createDirectories(knownHosts.getParent());
            if (!Files.exists(knownHosts)) Files.createFile(knownHosts);

            StringBuilder cmd = new StringBuilder("ssh")
                .append(" -i ").append(shellQuote(keyFile.toString()))
                .append(" -o IdentitiesOnly=yes")
                .append(" -o StrictHostKeyChecking=accept-new")
                .append(" -o UserKnownHostsFile=").append(shellQuote(knownHosts.toString()))
                .append(" -o ConnectTimeout=20");

            Map<String, String> env = new HashMap<>();
            env.put("GIT_SSH_COMMAND", cmd.toString());

            String pass = repo.getSshPassphrase();
            if (pass != null && !pass.isEmpty()) {
                Path askpass = dir.resolve("askpass.sh");
                // The passphrase is read from the env rather than baked into the script text.
                Files.writeString(askpass, "#!/bin/sh\nprintf '%s' \"$MONOHULL_SSH_PASSPHRASE\"\n");
                restrict(askpass, "rwx------");
                env.put("SSH_ASKPASS", askpass.toString());
                env.put("SSH_ASKPASS_REQUIRE", "force");   // OpenSSH 8.4+: use askpass with no tty
                env.put("DISPLAY", ":0");
                env.put("MONOHULL_SSH_PASSPHRASE", pass);
            }
            return new SshSession(dir, env);
        } catch (IOException e) {
            throw new RuntimeException("Could not stage SSH key for " + repo.getRepoFullName()
                + ": " + e.getMessage(), e);
        }
    }

    /** Apply POSIX permissions, tolerating non-POSIX filesystems (e.g. a Windows dev box). */
    private static void restrict(Path p, String perms) {
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(perms));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX FS: SSH only enforces this on the Linux runtime where it matters.
        }
    }

    /** Minimal shell quoting for a path embedded in GIT_SSH_COMMAND (git parses it shell-style). */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** A staged SSH key + its env, cleaned up after the checkout completes. */
    private record SshSession(Path dir, Map<String, String> env) {
        void cleanup() {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) { }
                });
            } catch (IOException ignored) { }
        }
    }

    private static String shortRef(String sha, String branch) {
        if (sha != null && !sha.isBlank()) return sha.substring(0, Math.min(8, sha.length()));
        return branch;
    }

    /** Run a git command, streaming output to the build log. The description is logged (never the
     *  authenticated URL). {@code env} carries SSH wiring (GIT_SSH_COMMAND, askpass) for SSH repos;
     *  empty for HTTPS. Throws on non-zero exit. */
    private void run(String buildId, Path cwd, List<String> cmd, String desc, Map<String, String> env) {
        logSink.append(buildId, "[git] " + desc);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        // Never block on an interactive credential/host prompt.
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_ASKPASS", "/bin/true");
        pb.environment().putAll(env);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException("git " + desc + " could not start: " + e.getMessage(), e);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) logSink.append(buildId, line);
            int code = p.waitFor();
            if (code != 0) {
                throw new RuntimeException("git " + desc + " failed (exit " + code + ")");
            }
        } catch (IOException e) {
            throw new RuntimeException("git " + desc + " I/O error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new RuntimeException("git " + desc + " interrupted", e);
        }
    }
}
