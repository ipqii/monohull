package io.monohull.service;

import io.monohull.entity.RegistryCredentialEntity;
import io.monohull.repository.RegistryCredentialRepository;
import com.github.dockerjava.api.model.AuthConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RegistryCredentialService {

    private final RegistryCredentialRepository repo;

    public RegistryCredentialService(RegistryCredentialRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<RegistryCredentialEntity> find() {
        return repo.findFirstByOrderByIdAsc();
    }

    @Transactional
    public RegistryCredentialEntity upsert(String url, String username, String password, String description) {
        RegistryCredentialEntity entity = repo.findFirstByOrderByIdAsc().orElseGet(RegistryCredentialEntity::new);
        entity.setUrl(normalizeUrl(url));
        entity.setUsername(username);
        if (password != null && !password.isBlank()) {
            entity.setPassword(password);
        } else if (entity.getPassword() == null) {
            throw new IllegalArgumentException("Password is required when creating registry credentials");
        }
        entity.setDescription(description);
        return repo.save(entity);
    }

    @Transactional
    public void delete() {
        repo.deleteAll();
    }

    /**
     * Returns an AuthConfig if the configured registry's host matches the image's registry host.
     * Returns null when no credentials are configured or the image targets a different registry.
     */
    @Transactional(readOnly = true)
    public AuthConfig authConfigFor(String image) {
        if (image == null || image.isBlank()) return null;
        return repo.findFirstByOrderByIdAsc()
            .filter(c -> imageMatchesRegistry(image, c.getUrl()))
            .map(c -> new AuthConfig()
                .withRegistryAddress(c.getUrl())
                .withUsername(c.getUsername())
                .withPassword(c.getPassword()))
            .orElse(null);
    }

    static boolean imageMatchesRegistry(String image, String registryUrl) {
        String imageHost = extractRegistryHost(image);
        String registryHost = extractRegistryHost(registryUrl);
        if (imageHost == null || registryHost == null) return false;
        return imageHost.equalsIgnoreCase(registryHost);
    }

    /**
     * Extracts the registry host from an image reference or URL.
     * Returns null for Docker Hub images (no registry prefix) since we never want to send
     * private credentials to Docker Hub.
     */
    static String extractRegistryHost(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String s = ref.trim();
        // Strip scheme if present (registry URL form)
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        // Take everything before the first '/'
        int slash = s.indexOf('/');
        String head = slash >= 0 ? s.substring(0, slash) : s;
        // A registry host has either a '.' or a ':' (port) — otherwise it's a Docker Hub namespace
        if (head.contains(".") || head.contains(":") || head.equals("localhost")) {
            return head.toLowerCase();
        }
        return null;
    }

    static String normalizeUrl(String url) {
        if (url == null) return null;
        String s = url.trim();
        // Drop trailing slash
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}