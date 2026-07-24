package io.monohull.service;

import io.monohull.dto.ConnectedRepositoryRequest;
import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.entity.PrBuildEntity;
import io.monohull.entity.RepoAuthMethod;
import io.monohull.entity.RepoBuildMode;
import io.monohull.entity.RepoProvider;
import io.monohull.repository.ConnectedRepositoryRepository;
import io.monohull.repository.ImageConfigRepository;
import io.monohull.repository.PrBuildRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/** CRUD for connected repositories, plus PR-build history lookups. */
@Service
public class RepositoryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConnectedRepositoryRepository repoRepo;
    private final ImageConfigRepository imageConfigRepo;
    private final PrBuildRepository prBuildRepo;

    public RepositoryService(ConnectedRepositoryRepository repoRepo,
                             ImageConfigRepository imageConfigRepo,
                             PrBuildRepository prBuildRepo) {
        this.repoRepo = repoRepo;
        this.imageConfigRepo = imageConfigRepo;
        this.prBuildRepo = prBuildRepo;
    }

    @Transactional(readOnly = true)
    public List<ConnectedRepositoryEntity> list() {
        return repoRepo.findAll();
    }

    @Transactional(readOnly = true)
    public ConnectedRepositoryEntity get(Long id) {
        return repoRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Transactional
    public ConnectedRepositoryEntity create(ConnectedRepositoryRequest req) {
        ConnectedRepositoryEntity e = new ConnectedRepositoryEntity();
        apply(e, req, null);
        Optional<ConnectedRepositoryEntity> dup =
            repoRepo.findByProviderAndRepoFullName(e.getProvider(), e.getRepoFullName());
        if (dup.isPresent()) {
            throw new IllegalArgumentException(
                "A " + e.getProvider() + " repository '" + e.getRepoFullName() + "' is already connected");
        }
        e.setWebhookSecret(generateSecret());
        return repoRepo.save(e);
    }

    @Transactional
    public ConnectedRepositoryEntity update(Long id, ConnectedRepositoryRequest req) {
        ConnectedRepositoryEntity e = get(id);
        apply(e, req, e);
        repoRepo.findByProviderAndRepoFullName(e.getProvider(), e.getRepoFullName())
            .filter(other -> !other.getId().equals(id))
            .ifPresent(other -> {
                throw new IllegalArgumentException(
                    "A " + e.getProvider() + " repository '" + e.getRepoFullName() + "' is already connected");
            });
        return repoRepo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (!repoRepo.existsById(id)) {
            throw new IllegalArgumentException("Repository not found: " + id);
        }
        repoRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PrBuildEntity> listPrBuilds(Long repoId) {
        get(repoId); // verify the repo exists
        return prBuildRepo.findByRepositoryIdOrderByCreatedAtDesc(repoId);
    }

    @Transactional(readOnly = true)
    public PrBuildEntity getPrBuild(Long prBuildId) {
        return prBuildRepo.findById(prBuildId)
            .orElseThrow(() -> new IllegalArgumentException("PR build not found: " + prBuildId));
    }

    // --- helpers ---

    private void apply(ConnectedRepositoryEntity e, ConnectedRepositoryRequest req,
                       ConnectedRepositoryEntity existing) {
        e.setName(req.name().trim());
        e.setProvider(parseEnum(RepoProvider.class, req.provider(), "provider"));
        e.setAuthMethod(blank(req.authMethod())
            ? RepoAuthMethod.HTTPS
            : parseEnum(RepoAuthMethod.class, req.authMethod(), "authMethod"));
        e.setRepoUrl(req.repoUrl().trim());
        e.setRepoFullName(req.repoFullName().trim());
        e.setDefaultBranch(blank(req.defaultBranch()) ? "main" : req.defaultBranch().trim());
        e.setBuildMode(parseEnum(RepoBuildMode.class, req.buildMode(), "buildMode"));

        ImageConfigEntity ic = imageConfigRepo.findById(req.imageConfigId())
            .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + req.imageConfigId()));
        e.setImageConfig(ic);

        e.setCloneUsername(blank(req.cloneUsername()) ? null : req.cloneUsername().trim());
        // Write-only secrets: keep existing value when the incoming field is blank.
        if (!blank(req.cloneToken())) e.setCloneToken(req.cloneToken());
        if (!blank(req.sshPrivateKey())) e.setSshPrivateKey(req.sshPrivateKey().trim());
        if (!blank(req.sshPassphrase())) e.setSshPassphrase(req.sshPassphrase());
        if (!blank(req.statusToken())) e.setStatusToken(req.statusToken());

        e.setMaxConcurrent(req.maxConcurrent() != null && req.maxConcurrent() > 0 ? req.maxConcurrent() : 2);
        e.setEnabled(req.enabled() == null || req.enabled());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String generateSecret() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }
}
