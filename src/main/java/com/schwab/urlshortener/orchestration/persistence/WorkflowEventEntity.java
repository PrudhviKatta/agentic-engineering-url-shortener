package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.JsonMapAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "workflow_events")
public class WorkflowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "actor_type")
    private String actorType;

    @Column(name = "actor_id")
    private String actorId;

    private String action;

    private Instant timestamp = Instant.now();

    @Column(name = "affected_artifact")
    private String affectedArtifact;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> payload;

    private String result;

    // Corrected 2026-09-15 (independent review round 4 finding): data-model.md Section 5 specifies
    // VARCHAR(1024) for this column ("Human-readable rationale — required by REQ-026 to be
    // present on every event that has one"); the entity carried no explicit length at all, so
    // Hibernate's default (255) was silently in effect instead — schema drift from the documented
    // data model, not an intentional narrower limit. WorkflowEngine.approve()/replan() reject a
    // caller-supplied reason longer than this before it would ever reach here.
    @Column(length = 1024)
    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Instant getTimestamp() { return timestamp; }
    public String getAffectedArtifact() { return affectedArtifact; }
    public void setAffectedArtifact(String affectedArtifact) { this.affectedArtifact = affectedArtifact; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
