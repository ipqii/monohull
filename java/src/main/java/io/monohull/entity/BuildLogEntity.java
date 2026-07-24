package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "build_log")
public class BuildLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private EnvironmentEntity environment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String line;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EnvironmentEntity getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentEntity environment) { this.environment = environment; }

    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
