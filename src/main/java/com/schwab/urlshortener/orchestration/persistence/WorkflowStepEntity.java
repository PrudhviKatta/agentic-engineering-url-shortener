package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.JsonListAttributeConverter;
import com.schwab.urlshortener.orchestration.JsonMapAttributeConverter;
import com.schwab.urlshortener.orchestration.StepStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "workflow_steps")
public class WorkflowStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    private String name;

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private int sequence;

    @Convert(converter = JsonListAttributeConverter.class)
    @Column(name = "depends_on", columnDefinition = "CLOB")
    private List<String> dependsOn;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> result;

    // Corrected 2026-09-13 (data-model.md's already-documented design; see this task's Interfaces
    // note above): without this column, (workflowId, name) is not a safe uniqueness/lookup
    // assumption once Task 35's replan() can retire and reintroduce a stage name.
    @Column(name = "plan_version")
    private int planVersion = 1;

    // Task 49: stable string keys into StageCatalog for restart-safe lookup of the activation
    // condition and fallback handler configured for this step (nullable — most steps use neither).
    @Column(name = "activation_condition_key")
    private String activationConditionKey;

    @Column(name = "fallback_handler_key")
    private String fallbackHandlerKey;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public String getActivationConditionKey() { return activationConditionKey; }
    public void setActivationConditionKey(String activationConditionKey) { this.activationConditionKey = activationConditionKey; }
    public String getFallbackHandlerKey() { return fallbackHandlerKey; }
    public void setFallbackHandlerKey(String fallbackHandlerKey) { this.fallbackHandlerKey = fallbackHandlerKey; }
}
