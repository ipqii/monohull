package io.monohull.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "container")
public class ContainerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private EnvironmentEntity environment;

    @Column(name = "container_name", nullable = false)
    private String containerName;

    @Column(name = "docker_container_id")
    private String dockerContainerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerRole role;

    @Column(nullable = false)
    private String image;

    private String ports;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EnvironmentEntity getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentEntity environment) { this.environment = environment; }

    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }

    public String getDockerContainerId() { return dockerContainerId; }
    public void setDockerContainerId(String dockerContainerId) { this.dockerContainerId = dockerContainerId; }

    public ContainerRole getRole() { return role; }
    public void setRole(ContainerRole role) { this.role = role; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getPorts() { return ports; }
    public void setPorts(String ports) { this.ports = ports; }

    public ContainerStatus getStatus() { return status; }
    public void setStatus(ContainerStatus status) { this.status = status; }
}
