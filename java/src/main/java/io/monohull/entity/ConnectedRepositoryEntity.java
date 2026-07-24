package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A git repository connected to Monohull. A PR webhook from this repo triggers a build of the
 * PR branch using {@link #imageConfig}'s pipeline. {@code webhookSecret} verifies inbound
 * webhooks. Clones authenticate per {@code authMethod}: HTTPS uses {@code cloneUsername} +
 * {@code cloneToken}; SSH uses {@code sshPrivateKey} (+ optional {@code sshPassphrase}).
 * {@code statusToken} is reserved for Phase 2 (posting build status back to the provider).
 */
@Entity
@Table(name = "connected_repository", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "repo_full_name"})
})
public class ConnectedRepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepoProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, length = 20)
    private RepoAuthMethod authMethod = RepoAuthMethod.HTTPS;

    @Column(name = "repo_url", nullable = false, length = 1000)
    private String repoUrl;

    @Column(name = "repo_full_name", nullable = false, length = 500)
    private String repoFullName;

    @Column(name = "default_branch", nullable = false)
    private String defaultBranch = "main";

    @Enumerated(EnumType.STRING)
    @Column(name = "build_mode", nullable = false, length = 20)
    private RepoBuildMode buildMode = RepoBuildMode.BUILD_ONLY;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_config_id", nullable = false)
    private ImageConfigEntity imageConfig;

    @Column(name = "webhook_secret", nullable = false, length = 100)
    private String webhookSecret;

    @Column(name = "clone_username")
    private String cloneUsername;

    @Column(name = "clone_token", length = 1000)
    private String cloneToken;

    /** PEM private key used for SSH clones (write-only). */
    @Column(name = "ssh_private_key", columnDefinition = "TEXT")
    private String sshPrivateKey;

    /** Optional passphrase protecting {@link #sshPrivateKey} (write-only). */
    @Column(name = "ssh_passphrase", length = 500)
    private String sshPassphrase;

    /** Reserved for Phase 2 status write-back; unused in phase 1. */
    @Column(name = "status_token", length = 1000)
    private String statusToken;

    @Column(name = "max_concurrent", nullable = false)
    private int maxConcurrent = 2;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public RepoProvider getProvider() { return provider; }
    public void setProvider(RepoProvider provider) { this.provider = provider; }

    public RepoAuthMethod getAuthMethod() { return authMethod; }
    public void setAuthMethod(RepoAuthMethod authMethod) { this.authMethod = authMethod; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }

    public RepoBuildMode getBuildMode() { return buildMode; }
    public void setBuildMode(RepoBuildMode buildMode) { this.buildMode = buildMode; }

    public ImageConfigEntity getImageConfig() { return imageConfig; }
    public void setImageConfig(ImageConfigEntity imageConfig) { this.imageConfig = imageConfig; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public String getCloneUsername() { return cloneUsername; }
    public void setCloneUsername(String cloneUsername) { this.cloneUsername = cloneUsername; }

    public String getCloneToken() { return cloneToken; }
    public void setCloneToken(String cloneToken) { this.cloneToken = cloneToken; }

    public String getSshPrivateKey() { return sshPrivateKey; }
    public void setSshPrivateKey(String sshPrivateKey) { this.sshPrivateKey = sshPrivateKey; }

    public String getSshPassphrase() { return sshPassphrase; }
    public void setSshPassphrase(String sshPassphrase) { this.sshPassphrase = sshPassphrase; }

    public String getStatusToken() { return statusToken; }
    public void setStatusToken(String statusToken) { this.statusToken = statusToken; }

    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
