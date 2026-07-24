package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "action_execution")
public class ActionExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, unique = true)
    private String executionId;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private EnvironmentEntity environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id", nullable = false)
    private ContainerEntity container;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "pipeline_run_id")
    private String pipelineRunId;

    @Column(name = "sequence_order")
    private Integer sequenceOrder;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }

    public EnvironmentEntity getEnvironment() { return environment; }
    public void setEnvironment(EnvironmentEntity environment) { this.environment = environment; }

    public ContainerEntity getContainer() { return container; }
    public void setContainer(ContainerEntity container) { this.container = container; }

    public ActionExecutionStatus getStatus() { return status; }
    public void setStatus(ActionExecutionStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public String getPipelineRunId() { return pipelineRunId; }
    public void setPipelineRunId(String pipelineRunId) { this.pipelineRunId = pipelineRunId; }

    public Integer getSequenceOrder() { return sequenceOrder; }
    public void setSequenceOrder(Integer sequenceOrder) { this.sequenceOrder = sequenceOrder; }
}
