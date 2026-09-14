package com.schwab.urlshortener.policy;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom/retrySafeStopped dispatch stage handlers onto its internal thread pool,
// which borrows a *different* connection from the same DataSource; without this override those
// worker threads cannot see the test thread's uncommitted inserts. Same root cause and fix as
// every other WorkflowEngine*Test class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PolicyExceptionTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void approvedUnexpiredExceptionAllowsProgressionPastMandatoryFail() {
        StageHandler impl = ctx -> { ctx.addArtifact("urlSchemeAllowlistEnforced", false); return Map.of(); };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager,
                policyEvaluationRepository, policyEngine, policyExceptionRepository);

        String workflowId = engine.submit("demo", stages, "run-29a");

        WorkflowInstanceEntity beforeException = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(beforeException.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);

        engine.grantPolicyException(workflowId, "SEC-001", "prototype demo only",
                "implementation stage", "human-candidate", "manual review before merge",
                Instant.now().plus(Duration.ofDays(1)));
        engine.retrySafeStopped(workflowId);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void expiredExceptionDoesNotExcuseTheMandatoryFail() {
        StageHandler impl = ctx -> { ctx.addArtifact("urlSchemeAllowlistEnforced", false); return Map.of(); };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager,
                policyEvaluationRepository, policyEngine, policyExceptionRepository);

        String workflowId = engine.submit("demo", stages, "run-29b");
        engine.grantPolicyException(workflowId, "SEC-001", "prototype demo only",
                "implementation stage", "human-candidate", "manual review before merge",
                Instant.now().minus(Duration.ofDays(1))); // already expired
        engine.retrySafeStopped(workflowId);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED); // still blocked
    }

    @Test
    void grantingAnExceptionForADifferentPolicyDoesNotExcuseThisOne() {
        StageHandler impl = ctx -> { ctx.addArtifact("urlSchemeAllowlistEnforced", false); return Map.of(); };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager,
                policyEvaluationRepository, policyEngine, policyExceptionRepository);

        String workflowId = engine.submit("demo", stages, "run-29c");
        engine.grantPolicyException(workflowId, "SEC-002", "unrelated policy", "n/a",
                "human-candidate", "n/a", Instant.now().plus(Duration.ofDays(1)));
        engine.retrySafeStopped(workflowId);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED); // SEC-001 still unexcused
    }
}
