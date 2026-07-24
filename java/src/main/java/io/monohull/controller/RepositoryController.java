package io.monohull.controller;

import io.monohull.dto.ConnectedRepositoryRequest;
import io.monohull.dto.ConnectedRepositoryResponse;
import io.monohull.dto.PrBuildResponse;
import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.PrBuildEntity;
import io.monohull.entity.RepoProvider;
import io.monohull.service.LogSink;
import io.monohull.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/config/repositories")
public class RepositoryController {

    private final RepositoryService service;
    private final LogSink logSink;

    /** Public base URL Monohull is reachable at (e.g. https://monohull.example.com), used to render the
     *  webhook URL providers should call. Blank => the UI prefixes its own origin. */
    @Value("${monohull.public.base-url:}")
    private String baseUrl;

    public RepositoryController(RepositoryService service, LogSink logSink) {
        this.service = service;
        this.logSink = logSink;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ConnectedRepositoryResponse>> list() {
        return ResponseEntity.ok(service.list().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ConnectedRepositoryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(service.get(id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ConnectedRepositoryResponse> create(@Valid @RequestBody ConnectedRepositoryRequest req) {
        return ResponseEntity.ok(toResponse(service.create(req)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ConnectedRepositoryResponse> update(@PathVariable Long id,
                                                              @Valid @RequestBody ConnectedRepositoryRequest req) {
        return ResponseEntity.ok(toResponse(service.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pr-builds")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PrBuildResponse>> prBuilds(@PathVariable Long id) {
        return ResponseEntity.ok(service.listPrBuilds(id).stream().map(this::toPrBuildResponse).toList());
    }

    /** Live build log stream for a PR build (SSE). Only carries output while the build is
     *  in-flight; the persisted status/error remain available afterwards. */
    @GetMapping(value = "/pr-builds/{prBuildId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> prBuildLogs(@PathVariable Long prBuildId) {
        return logSink.stream(service.getPrBuild(prBuildId).getBuildId());
    }

    // --- mapping ---

    private ConnectedRepositoryResponse toResponse(ConnectedRepositoryEntity e) {
        var ic = e.getImageConfig();
        String icName = ic == null ? null : ic.getClient() + "/" + ic.getProject();
        return new ConnectedRepositoryResponse(
            e.getId(), e.getName(), e.getProvider().name(), e.getAuthMethod().name(),
            e.getRepoUrl(), e.getRepoFullName(),
            e.getDefaultBranch(), e.getBuildMode().name(),
            ic == null ? null : ic.getId(), icName,
            e.getWebhookSecret(), webhookUrl(e), e.getCloneUsername(),
            e.getCloneToken() != null && !e.getCloneToken().isBlank(),
            e.getSshPrivateKey() != null && !e.getSshPrivateKey().isBlank(),
            e.getStatusToken() != null && !e.getStatusToken().isBlank(),
            e.getMaxConcurrent(), e.isEnabled(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private PrBuildResponse toPrBuildResponse(PrBuildEntity e) {
        return new PrBuildResponse(
            e.getId(), e.getRepository().getId(), e.getPrNumber(), e.getPrTitle(),
            e.getSourceBranch(), e.getTargetBranch(), e.getCommitSha(),
            e.getEvent().name(), e.getStatus().name(), e.getBuildId(), e.getEnvironmentId(),
            e.getError(), e.getStartedAt(), e.getFinishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }

    /** Webhook endpoint a provider should POST to for this repo. Absolute when base-url is
     *  configured; otherwise a path the UI prefixes with its own origin. */
    private String webhookUrl(ConnectedRepositoryEntity e) {
        String path = "/api/webhooks/" + e.getProvider().name().toLowerCase() + "/" + e.getId();
        // Bitbucket Cloud can't sign payloads, so carry the secret as a query token.
        if (e.getProvider() == RepoProvider.BITBUCKET) {
            path = path + "?token=" + e.getWebhookSecret();
        }
        String base = baseUrl == null ? "" : baseUrl.trim();
        return base.isBlank() ? path : base.replaceAll("/+$", "") + path;
    }
}
