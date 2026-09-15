package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.JsonListAttributeConverter;
import com.schwab.urlshortener.orchestration.JsonMapAttributeConverter;
import com.schwab.urlshortener.orchestration.SafeStopReason;
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

    // Round-6c correction: the artifacts actually available in the workflow's shared context at
    // the moment this stage's BATCH began — captured once per batch (before any handler in that
    // batch runs) and persisted per step, not the workflow's current/final artifact map re-read at
    // response time. Null until the stage's batch has actually started (PENDING/WAITING_APPROVAL).
    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(name = "inputs_snapshot", columnDefinition = "CLOB")
    private Map<String, Object> inputsSnapshot;

    // Codex REQUEST AMENDMENTS item 6: WHY this step is SAFE_STOPPED (null otherwise) — lets
    // retrySafeStopped distinguish "the handler already succeeded once, only the policy boundary
    // needs re-checking" (POLICY_BLOCKED) from "the handler genuinely never completed and must
    // actually run again" (RETRY_EXHAUSTED, COMPENSATION_FAILED).
    @Enumerated(EnumType.STRING)
    @Column(name = "safe_stop_reason")
    private SafeStopReason safeStopReason;

    // 4th independent-review finding (Section 17 follow-up review, Finding B): the exact
    // evaluationArtifacts view (this stage's batch-start baseline merged with ONLY its own
    // attributed writes — never a sibling's) that produced a POLICY_BLOCKED safe-stop, captured at
    // that moment so retrySafeStopped's reevaluatePolicyBoundary can re-run the SAME check later
    // against the SAME evidence. Before this field existed, reevaluatePolicyBoundary read the
    // live WorkflowContext's artifacts() instead — which, by resume time, could include a
    // TRUSTWORTHY SIBLING's evidence merged in for legitimate forward propagation to later,
    // dependent stages — incorrectly letting THIS stage's own boundary clear on a sibling's
    // evidence it never itself produced, with no new evidence and no granted exception. Null for
    // every other status.
    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(name = "policy_evidence_snapshot", columnDefinition = "CLOB")
    private Map<String, Object> policyEvidenceSnapshot;

    // Codex REQUEST AMENDMENTS item 8: this step's status immediately before an exit-approval gate
    // overwrote it with WAITING_APPROVAL. approve() restores this (rather than hardcoding
    // SUCCEEDED) so a COMPENSATED step that passes through an exit gate is still COMPENSATED
    // afterward, not silently reported as a clean success. Null for steps that never passed
    // through an exit gate.
    @Enumerated(EnumType.STRING)
    @Column(name = "pre_gate_status")
    private StepStatus preGateStatus;

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
    public Map<String, Object> getInputsSnapshot() { return inputsSnapshot; }
    public void setInputsSnapshot(Map<String, Object> inputsSnapshot) { this.inputsSnapshot = inputsSnapshot; }
    public SafeStopReason getSafeStopReason() { return safeStopReason; }
    public void setSafeStopReason(SafeStopReason safeStopReason) { this.safeStopReason = safeStopReason; }
    public Map<String, Object> getPolicyEvidenceSnapshot() { return policyEvidenceSnapshot; }
    public void setPolicyEvidenceSnapshot(Map<String, Object> policyEvidenceSnapshot) { this.policyEvidenceSnapshot = policyEvidenceSnapshot; }
    public StepStatus getPreGateStatus() { return preGateStatus; }
    public void setPreGateStatus(StepStatus preGateStatus) { this.preGateStatus = preGateStatus; }
}
