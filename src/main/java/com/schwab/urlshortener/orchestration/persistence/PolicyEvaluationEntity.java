package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.policy.PolicyOutcome;
import jakarta.persistence.*;

@Entity
@Table(name = "policy_evaluations")
public class PolicyEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "stage_name")
    private String stageName;

    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "policy_version")
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    private PolicyOutcome outcome;

    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public PolicyOutcome getOutcome() { return outcome; }
    public void setOutcome(PolicyOutcome outcome) { this.outcome = outcome; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
