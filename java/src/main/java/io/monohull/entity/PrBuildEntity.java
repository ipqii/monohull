package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A single build triggered by a PR webhook. {@code buildId} keys the SSE log stream
 * (LogSink), mirroring environment builds. {@code environmentId} is set only when the
 * repository's build mode is BUILD_AND_ENV.
 */
@Entity
@Table(name = "pr_build")
public class PrBuildEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private ConnectedRepositoryEntity repository;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "pr_title", length = 1000)
    private String prTitle;

    @Column(name = "source_branch", nullable = false, length = 500)
    private String sourceBranch;

    @Column(name = "target_branch", length = 500)
    private String targetBranch;

    @Column(name = "commit_sha", length = 100)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PrBuildEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrBuildStatus status = PrBuildStatus.QUEUED;

    @Column(name = "build_id", nullable = false, length = 64)
    private String buildId;

    @Column(name = "environment_id")
    private Long environmentId;

    @Column(name = "workspace_path", length = 1000)
    private String workspacePath;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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

    public ConnectedRepositoryEntity getRepository() { return repository; }
    public void setRepository(ConnectedRepositoryEntity repository) { this.repository = repository; }

    public int getPrNumber() { return prNumber; }
    public void setPrNumber(int prNumber) { this.prNumber = prNumber; }

    public String getPrTitle() { return prTitle; }
    public void setPrTitle(String prTitle) { this.prTitle = prTitle; }

    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

    public String getTargetBranch() { return targetBranch; }
    public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public PrBuildEvent getEvent() { return event; }
    public void setEvent(PrBuildEvent event) { this.event = event; }

    public PrBuildStatus getStatus() { return status; }
    public void setStatus(PrBuildStatus status) { this.status = status; }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public Long getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(Long environmentId) { this.environmentId = environmentId; }

    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
