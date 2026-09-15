package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// Task 34: no new production behavior — T028 (mandatory policy FAIL) and T033 (uncompensated retry
// exhaustion) are each already independently tested elsewhere (WorkflowEnginePolicyEnforcementTest,
// WorkflowEngineReliabilityIntegrationTest). This class exists specifically to prove they converge
// on the SAME terminal state (WorkflowStatus.SAFE_STOPPED / StepStatus.SAFE_STOPPED) through the
// SAME SafeStopException path in WorkflowEngine.runStage/runFrom, not merely that each independently
// avoids WorkflowStatus.FAILED — constitution Principle VIII requires safe-stop to be a single,
// well-defined terminal path, not two triggers that happen to look similar from separate tests.
//
// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom dispatches stage handlers onto its internal thread pool, which borrows a
// *different* connection from the same DataSource; without this override those worker threads
// cannot see the test thread's uncommitted inserts. Same root cause and fix as every other
// WorkflowEngine*Test class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineSafeStopTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void mandatoryPolicyFailureConvergesOnSafeStopped() {
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false); // simulate missing control
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, transactionManager, policyEvaluationRepository, policyEngine);

        String workflowId = engine.submit("demo", stages, "safe-stop-policy");

        WorkflowStepEntity step = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getSafeStopReason()).isEqualTo(SafeStopReason.POLICY_BLOCKED);
        // The handler ran and returned normally — this is not a FAILED terminal state, and never
        // was one: SafeStopException is thrown AFTER the handler already succeeded, never mapped
        // through StepStatus.FAILED at any point on this path.
        assertThat(step.getStatus()).isNotEqualTo(StepStatus.FAILED);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(instance.getStatus()).isNotEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void uncompensatedRetryExhaustionConvergesOnSafeStopped() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null, null, null));
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, transactionManager, policyEvaluationRepository, null,
                policyExceptionRepository, new CompensationRegistry()); // nothing registered

        String workflowId = engine.submit("demo", stages, "safe-stop-retry");

        WorkflowStepEntity step = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getSafeStopReason()).isEqualTo(SafeStopReason.RETRY_EXHAUSTED);
        // Retries were genuinely exhausted, not silently swallowed into a FAILED terminal state —
        // T33's own compensated-exhaustion test (stageRetriesThenCompensatesOnExhaustion) proves the
        // COMPLETED path when compensation IS registered; this is the uncompensated contrast case.
        assertThat(step.getStatus()).isNotEqualTo(StepStatus.FAILED);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(instance.getStatus()).isNotEqualTo(WorkflowStatus.FAILED);
    }

    @Test
    void bothSafeStopTriggersProduceTheSameTerminalStatusPairDespiteDifferentReasons() {
        // Same assertion as the two tests above, side by side in one place: both a mandatory
        // policy FAIL (a stage that SUCCEEDED but was retroactively blocked) and an uncompensated
        // retry exhaustion (a stage whose handler never succeeded at all) land on the identical
        // (WorkflowStatus.SAFE_STOPPED, StepStatus.SAFE_STOPPED) pair — proving convergence on one
        // terminal state, not merely that each trigger individually avoids FAILED. The
        // SafeStopReason correctly differs (POLICY_BLOCKED vs RETRY_EXHAUSTED): that field exists
        // precisely so the audit trail can tell these two triggers apart even though the workflow
        // and step status they converge on cannot.
        StageHandler policyFailing = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false);
            return Map.of();
        };
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine policyEngineInstance = new WorkflowEngine(instanceRepository, stepRepository,
                eventRepository, approvalRepository, transactionManager, policyEvaluationRepository,
                policyEngine);
        String policyWorkflowId = policyEngineInstance.submit("demo",
                List.of(StageDefinition.of("implementation", List.of(), policyFailing)), "safe-stop-both-a");

        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        WorkflowEngine retryEngineInstance = new WorkflowEngine(instanceRepository, stepRepository,
                eventRepository, approvalRepository, transactionManager, policyEvaluationRepository, null,
                policyExceptionRepository, new CompensationRegistry());
        String retryWorkflowId = retryEngineInstance.submit("demo",
                List.of(new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null, null, null)),
                "safe-stop-both-b");

        WorkflowStepEntity policyStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(policyWorkflowId, 1, "implementation").orElseThrow();
        WorkflowStepEntity retryStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(retryWorkflowId, 1, "flaky").orElseThrow();

        assertThat(policyStep.getStatus()).isEqualTo(retryStep.getStatus());
        assertThat(policyStep.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(policyStep.getSafeStopReason()).isNotEqualTo(retryStep.getSafeStopReason());
        assertThat(policyStep.getSafeStopReason()).isEqualTo(SafeStopReason.POLICY_BLOCKED);
        assertThat(retryStep.getSafeStopReason()).isEqualTo(SafeStopReason.RETRY_EXHAUSTED);

        assertThat(instanceRepository.findById(policyWorkflowId).orElseThrow().getStatus())
                .isEqualTo(instanceRepository.findById(retryWorkflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }
}
