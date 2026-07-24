package io.monohull.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pipeline_step")
public class PipelineStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private PipelineDefinitionEntity pipeline;

    @Column(name = "action_key", nullable = false)
    private String actionKey;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PipelineDefinitionEntity getPipeline() { return pipeline; }
    public void setPipeline(PipelineDefinitionEntity pipeline) { this.pipeline = pipeline; }

    public String getActionKey() { return actionKey; }
    public void setActionKey(String actionKey) { this.actionKey = actionKey; }

    public int getSequenceOrder() { return sequenceOrder; }
    public void setSequenceOrder(int sequenceOrder) { this.sequenceOrder = sequenceOrder; }
}
