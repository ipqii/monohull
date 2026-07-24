package io.monohull.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "action_log")
public class ActionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private ActionExecutionEntity execution;

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

    public ActionExecutionEntity getExecution() { return execution; }
    public void setExecution(ActionExecutionEntity execution) { this.execution = execution; }

    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
