package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.policy.PolicyOutcome;
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
// WorkflowEngine.runFrom/approve dispatch stage handlers onto its internal thread pool, which
// borrows a *different* connection from the same DataSource; without this override those worker
// threads cannot see the test thread's uncommitted inserts. Same root cause and fix as every other
// WorkflowEngine*Test class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEnginePolicyEnforcementTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine policyAwareEngine(PolicyEngine policyEngine, List<StageDefinition> stages) {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, null, null, com.schwab.urlshortener.support.TestStageCatalogs.fromStages(stages));
    }

    @Test
    void mandatoryPolicyFailureSafeStopsWorkflow() {
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false); // simulate missing control
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = policyAwareEngine(policyEngine, stages);

        String workflowId = engine.submit("demo", stages, "run-28a");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);

        List<PolicyEvaluationEntity> evaluations = policyEvaluationRepository.findAll().stream()
                .filter(e -> workflowId.equals(e.getWorkflowId()))
                .toList();
        PolicyEvaluationEntity security = evaluations.stream()
                .filter(e -> e.getPolicyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.getOutcome()).isEqualTo(PolicyOutcome.FAIL);
        assertThat(security.getPolicyVersion()).isEqualTo("1.0.0");
    }

    @Test
    void passingMandatoryChecksLetTheWorkflowComplete() {
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", true);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = policyAwareEngine(policyEngine, stages);

        String workflowId = engine.submit("demo", stages, "run-28b");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);

        // Confirms the evaluate-and-persist loop actually ran on the success path too, not just
        // the failure path — a PASS row for SEC-001 must exist even though nothing blocked.
        List<PolicyEvaluationEntity> evaluations = policyEvaluationRepository.findAll().stream()
                .filter(e -> workflowId.equals(e.getWorkflowId()))
                .toList();
        PolicyEvaluationEntity security = evaluations.stream()
                .filter(e -> e.getPolicyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.getOutcome()).isEqualTo(PolicyOutcome.PASS);
    }

    @Test
    void noPolicyEngineConfiguredMeansNoEnforcement() {
        // The 5-arg constructor (no PolicyEngine) must behave exactly as before Task 28 — this is
        // what the shared production bean and every pre-existing WorkflowEngine*Test still use.
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, transactionManager, null, null, null, null, com.schwab.urlshortener.support.TestStageCatalogs.fromStages(stages));

        String workflowId = engine.submit("demo", stages, "run-28c");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(policyEvaluationRepository.findAll().stream()
                .noneMatch(e -> workflowId.equals(e.getWorkflowId()))).isTrue();
    }
}
