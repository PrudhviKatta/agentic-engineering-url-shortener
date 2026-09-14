package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.WorkflowStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstanceEntity {

    @Id
    private String id;

    private String feature;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "plan_version")
    private int planVersion = 1;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Convert(converter = com.schwab.urlshortener.orchestration.JsonMapAttributeConverter.class)
    @Column(name = "context_json", columnDefinition = "CLOB")
    private Map<String, Object> contextJson = new java.util.HashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Map<String, Object> getContextJson() { return contextJson; }
    public void setContextJson(Map<String, Object> contextJson) { this.contextJson = contextJson; }
}
