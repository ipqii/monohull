package io.monohull.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.monohull.dto.PrEvent;
import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.PrBuildEvent;
import io.monohull.entity.RepoProvider;
import io.monohull.repository.ConnectedRepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies inbound git webhooks (per-provider signature/secret) and normalizes the payload to
 * a {@link PrEvent}. Phase 1 logs the event; {@code PrBuildService} (step 4) hooks in here to
 * enqueue builds.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final ConnectedRepositoryRepository repoRepo;
    private final ObjectMapper mapper;
    private final PrBuildService prBuildService;

    public WebhookService(ConnectedRepositoryRepository repoRepo, ObjectMapper mapper,
                          PrBuildService prBuildService) {
        this.repoRepo = repoRepo;
        this.mapper = mapper;
        this.prBuildService = prBuildService;
    }

    /** Verify + parse an inbound webhook, then enqueue a PR build. Returns a small ack body;
     *  throws ResponseStatusException (401/400/404) when verification or parsing fails.
     *  Not @Transactional so the downstream build enqueue runs in its own transaction. */
    public Map<String, Object> handle(String providerPath, Long repoId, String token,
                                      byte[] body, Map<String, String> rawHeaders) {
        Map<String, String> h = lowerKeys(rawHeaders);
        RepoProvider provider = parseProvider(providerPath);

        ConnectedRepositoryEntity repo = repoRepo.findById(repoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown repository"));
        if (repo.getProvider() != provider) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider does not match this repository");
        }

        verify(provider, repo, token, body, h);

        if (!repo.isEnabled()) {
            return Map.of("ok", true, "ignored", "repository disabled");
        }
        if (isPing(provider, h)) {
            return Map.of("ok", true, "ping", true);
        }

        PrEvent ev = parse(provider, body, h);
        if (ev == null) {
            return Map.of("ok", true, "ignored", "not a build-triggering PR event");
        }

        log.info("[webhook] {} {} PR #{} {} branch={} sha={} -> {}",
            provider, repo.getRepoFullName(), ev.prNumber(), ev.event(),
            ev.sourceBranch(), abbrev(ev.sha()), ev.event());

        prBuildService.onPrEvent(repo.getId(), ev);
        return Map.of("ok", true, "event", ev.event().name(), "prNumber", ev.prNumber());
    }

    // --- verification ---

    private void verify(RepoProvider provider, ConnectedRepositoryEntity repo, String token,
                        byte[] body, Map<String, String> h) {
        String secret = repo.getWebhookSecret();
        switch (provider) {
            case GITHUB -> {
                String sig = h.get("x-hub-signature-256");
                if (sig == null || !sig.startsWith("sha256=")) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing signature");
                }
                String expected = "sha256=" + hmacSha256Hex(secret, body);
                if (!constantTimeEquals(expected, sig)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad signature");
                }
            }
            case GITLAB -> {
                String presented = h.get("x-gitlab-token");
                if (presented == null || !constantTimeEquals(secret, presented)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad token");
                }
            }
            case BITBUCKET -> {
                // Bitbucket Cloud doesn't sign payloads; the secret rides in the URL ?token=.
                if (token == null || !constantTimeEquals(secret, token)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad token");
                }
            }
        }
    }

    private boolean isPing(RepoProvider provider, Map<String, String> h) {
        return provider == RepoProvider.GITHUB && "ping".equalsIgnoreCase(h.get("x-github-event"));
    }

    // --- parsing ---

    private PrEvent parse(RepoProvider provider, byte[] body, Map<String, String> h) {
        JsonNode root = read(body);
        return switch (provider) {
            case GITHUB -> parseGithub(root);
            case GITLAB -> parseGitlab(root);
            case BITBUCKET -> parseBitbucket(root, h.get("x-event-key"));
        };
    }

    private PrEvent parseGithub(JsonNode r) {
        String action = text(r, "action");
        PrBuildEvent event = switch (action == null ? "" : action) {
            case "opened", "reopened" -> PrBuildEvent.OPENED;
            case "synchronize" -> PrBuildEvent.SYNCHRONIZE;
            case "closed" -> PrBuildEvent.CLOSED;
            default -> null;
        };
        if (event == null) return null;
        JsonNode pr = r.path("pull_request");
        return new PrEvent(event,
            r.path("number").asInt(),
            text(pr, "title"),
            text(pr.path("head"), "ref"),
            text(pr.path("base"), "ref"),
            text(pr.path("head"), "sha"),
            text(r.path("repository"), "full_name"),
            pr.path("merged").asBoolean(false));
    }

    private PrEvent parseGitlab(JsonNode r) {
        if (!"merge_request".equals(text(r, "object_kind"))) return null;
        JsonNode oa = r.path("object_attributes");
        String action = text(oa, "action");
        PrBuildEvent event = switch (action == null ? "" : action) {
            case "open", "reopen" -> PrBuildEvent.OPENED;
            // "update" fires on many changes; only treat as a rebuild when code was pushed.
            case "update" -> oa.has("oldrev") ? PrBuildEvent.SYNCHRONIZE : null;
            case "close", "merge" -> PrBuildEvent.CLOSED;
            default -> null;
        };
        if (event == null) return null;
        return new PrEvent(event,
            oa.path("iid").asInt(),
            text(oa, "title"),
            text(oa, "source_branch"),
            text(oa, "target_branch"),
            text(oa.path("last_commit"), "id"),
            text(r.path("project"), "path_with_namespace"),
            "merge".equals(action));
    }

    private PrEvent parseBitbucket(JsonNode r, String eventKey) {
        JsonNode pr = r.path("pullrequest");
        if (pr.isMissingNode()) return null;
        String key = eventKey == null ? "" : eventKey;
        PrBuildEvent event = switch (key) {
            case "pullrequest:created" -> PrBuildEvent.OPENED;
            case "pullrequest:updated" -> PrBuildEvent.SYNCHRONIZE;
            case "pullrequest:fulfilled", "pullrequest:rejected" -> PrBuildEvent.CLOSED;
            default -> null;
        };
        if (event == null) return null;
        return new PrEvent(event,
            pr.path("id").asInt(),
            text(pr, "title"),
            text(pr.path("source").path("branch"), "name"),
            text(pr.path("destination").path("branch"), "name"),
            text(pr.path("source").path("commit"), "hash"),
            text(r.path("repository"), "full_name"),
            "pullrequest:fulfilled".equals(key));
    }

    // --- helpers ---

    private static RepoProvider parseProvider(String path) {
        try {
            return RepoProvider.valueOf(path.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown provider: " + path);
        }
    }

    private JsonNode read(byte[] body) {
        try {
            return mapper.readTree(body == null || body.length == 0 ? "{}".getBytes(StandardCharsets.UTF_8) : body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed JSON payload");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Map<String, String> lowerKeys(Map<String, String> in) {
        Map<String, String> out = new HashMap<>();
        if (in != null) in.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return out;
    }

    private static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(body == null ? new byte[0] : body);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "HMAC failure");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String abbrev(String sha) {
        return sha == null ? null : sha.substring(0, Math.min(8, sha.length()));
    }

    /** Resolve the connected repo for a webhook (used by step 4). */
    @Transactional(readOnly = true)
    public Optional<ConnectedRepositoryEntity> findRepo(Long repoId) {
        return repoRepo.findById(repoId);
    }
}
