package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "environment")
public class EnvironmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "build_id", nullable = false, unique = true)
    private String buildId;

    @Column(name = "network_name", nullable = false)
    private String networkName;

    @Column(name = "maximo_version", nullable = false)
    private String maximoVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_vendor", nullable = false)
    private DbVendor dbVendor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Username of the creator (Monohull accounts are keyed by O365 email). Nullable for pre-existing rows. */
    @Column(name = "created_by")
    private String createdBy;

    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContainerEntity> containers = new ArrayList<>();

    @OneToOne(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
    private EnvironmentConfigEntity config;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_config_id")
    private ImageConfigEntity imageConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_definition_id")
    private PipelineDefinitionEntity pipelineDefinition;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }

    public String getNetworkName() { return networkName; }
    public void setNetworkName(String networkName) { this.networkName = networkName; }

    public String getMaximoVersion() { return maximoVersion; }
    public void setMaximoVersion(String maximoVersion) { this.maximoVersion = maximoVersion; }

    public DbVendor getDbVendor() { return dbVendor; }
    public void setDbVendor(DbVendor dbVendor) { this.dbVendor = dbVendor; }

    public EnvironmentStatus getStatus() { return status; }
    public void setStatus(EnvironmentStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public List<ContainerEntity> getContainers() { return containers; }
    public void setContainers(List<ContainerEntity> containers) { this.containers = containers; }

    public EnvironmentConfigEntity getConfig() { return config; }
    public void setConfig(EnvironmentConfigEntity config) { this.config = config; }

    public ImageConfigEntity getImageConfig() { return imageConfig; }
    public void setImageConfig(ImageConfigEntity imageConfig) { this.imageConfig = imageConfig; }

    public PipelineDefinitionEntity getPipelineDefinition() { return pipelineDefinition; }
    public void setPipelineDefinition(PipelineDefinitionEntity pipelineDefinition) { this.pipelineDefinition = pipelineDefinition; }
}
