package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.PermanentStageException;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest wraps each test in a transaction on the test thread's own connection.
// WorkflowEngine.runFrom dispatches stage handlers onto its internal thread pool, and Task 33's
// TimeoutExecutor wraps every attempt in a SECOND, per-attempt thread pool — both borrow different
// connections from the same DataSource, so without this override those worker threads cannot see
// the test thread's uncommitted inserts. Same root cause and fix as every other WorkflowEngine*Test
// class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineReliabilityIntegrationTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engineWithCompensation(CompensationRegistry registry) {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, null, policyExceptionRepository, registry);
    }

    private WorkflowEngine engineWithPolicy(PolicyEngine policyEngine) {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, policyExceptionRepository,
                new CompensationRegistry());
    }

    @Test
    void stageRetriesThenCompensatesOnExhaustion() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 2, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        AtomicBoolean compensated = new AtomicBoolean(false);
        registry.register("flaky", result -> compensated.set(true));

        WorkflowEngine engine = engineWithCompensation(registry);

        String workflowId = engine.submit("demo", stages, "run-33a");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(step.getRetryCount()).isEqualTo(2);
        assertThat(compensated.get()).isTrue();

        // Design note (brief): a compensated failure does NOT block — the workflow proceeds
        // normally toward COMPLETED.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void exhaustionWithNoCompensationRegisteredSafeStopsTheWorkflow() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null, null, null));

        WorkflowEngine engine = engineWithCompensation(new CompensationRegistry()); // nothing registered

        String workflowId = engine.submit("demo", stages, "run-33b");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getRetryCount()).isEqualTo(1);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }

    @Test
    void permanentFailureIsNeverRetriedBeforeCompensating() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler broken = ctx -> { attempts.incrementAndGet(); throw new PermanentStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("broken", List.of(), broken, false, false, 5, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        AtomicBoolean compensated = new AtomicBoolean(false);
        registry.register("broken", result -> compensated.set(true));

        WorkflowEngine engine = engineWithCompensation(registry);
        String workflowId = engine.submit("demo", stages, "run-33c");

        assertThat(attempts.get()).isEqualTo(1); // no retries despite maxRetries=5
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "broken").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(step.getRetryCount()).isEqualTo(0); // a PermanentStageException never increments it
        assertThat(compensated.get()).isTrue();
    }

    @Test
    void aFlakyHandlerThatRecoversWithinRetryBudgetSucceedsNormally() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler eventuallyWorks = ctx -> {
            if (attempts.incrementAndGet() < 2) throw new TransientStageException("simulated");
            return Map.of("ok", true);
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), eventuallyWorks, false, false, 3, 5, null, null, null));

        WorkflowEngine engine = engineWithCompensation(new CompensationRegistry());
        String workflowId = engine.submit("demo", stages, "run-33d");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(step.getRetryCount()).isEqualTo(1); // one failed attempt before success
        assertThat(step.getResult()).isEqualTo(Map.of("ok", true));

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void policyEnforcementRunsIdenticallyAfterASuccessfulRetry() {
        // Review finding: untested interaction — a stage that succeeds on a RETRY attempt (not
        // the first) must still go through policy enforcement exactly as a first-attempt success
        // would, not skip it or treat the recovered attempt specially.
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler eventuallyPasses = ctx -> {
            if (attempts.incrementAndGet() < 2) throw new TransientStageException("simulated");
            ctx.addArtifact("urlSchemeAllowlistEnforced", true);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("implementation", List.of(), eventuallyPasses, false, false, 2, 5, null, null, null));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, policyExceptionRepository, new CompensationRegistry());

        String workflowId = engine.submit("demo", stages, "run-33e");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(step.getRetryCount()).isEqualTo(1);

        List<PolicyEvaluationEntity> evaluations = policyEvaluationRepository.findAll().stream()
                .filter(e -> workflowId.equals(e.getWorkflowId()))
                .toList();
        assertThat(evaluations).isNotEmpty(); // the policy block genuinely ran, not bypassed

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void exitGatedStageThatGetsCompensatedStillRequiresApproval() {
        // Review finding: COMPENSATED didn't exist as a step status before this task, and the
        // exit-gate scan's readyForGate check didn't originally account for it — an exit-gated
        // stage that exhausted retries and got compensated silently bypassed its own approval
        // gate. This proves the fix: the workflow halts for approval exactly as it would for a
        // clean SUCCEEDED exit-gated stage.
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, true, 1, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        registry.register("flaky", result -> { });

        WorkflowEngine engine = engineWithCompensation(registry);
        String workflowId = engine.submit("demo", stages, "run-33f");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL); // gated, not silently skipped
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        engine.approve(workflowId, "flaky", "approved", "human", "reviewed the compensation");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        // Codex REQUEST AMENDMENTS item 8 (metrics-facing regression): the step's own persisted
        // status must still read COMPENSATED after approval, not silently overwritten to
        // SUCCEEDED — any reporting/metrics path reading workflow_steps.status needs to be able to
        // tell "recovered via compensation" apart from "succeeded outright," even after a human
        // has signed off on it.
        WorkflowStepEntity approvedStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(approvedStep.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(approvedStep.getPreGateStatus()).isNull(); // cleared once consumed
    }

    @Test
    void rejectingACompensatedExitGateAlsoPreservesCompensatedStatus() {
        // Symmetry check: rejection must preserve COMPENSATED too, matching the pre-existing rule
        // that "the handler's own completion/outcome stands; only downstream is blocked."
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, true, 1, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        registry.register("flaky", result -> { });

        WorkflowEngine engine = engineWithCompensation(registry);
        String workflowId = engine.submit("demo", stages, "run-33j");

        engine.approve(workflowId, "flaky", "rejected", "human", "compensation wasn't good enough");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED); // not FAILED, not SUCCEEDED
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED); // the workflow itself still fails
    }

    // --- Codex REQUEST AMENDMENTS item 2: compensation failure handling ---

    @Test
    void aFailingCompensationSafeStopsTheWorkflowInsteadOfLeavingItRunning() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null, null, null));

        CompensationRegistry registry = new CompensationRegistry();
        registry.register("flaky", result -> { throw new IllegalStateException("compensation itself failed"); });

        WorkflowEngine engine = engineWithCompensation(registry);
        String workflowId = engine.submit("demo", stages, "run-33g");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getSafeStopReason()).isEqualTo(SafeStopReason.COMPENSATION_FAILED);
        assertThat(step.getEndedAt()).isNotNull();
        assertThat(step.getResult()).isNotNull();

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(instance.getStatus()).isNotEqualTo(WorkflowStatus.RUNNING); // no RUNNING state remains

        List<WorkflowEventEntity> events = eventRepository.findByWorkflowIdOrderById(workflowId);
        assertThat(events.stream().anyMatch(e -> e.getAction().equals("compensation_failed:flaky"))).isTrue();
        assertThat(events.stream()
                .filter(e -> e.getAction().equals("compensation_failed:flaky")).findFirst().orElseThrow().getReason())
                .isEqualTo("compensation itself failed");
    }

    // --- Codex REQUEST AMENDMENTS item 6: distinguish policy-blocked vs retry-exhaustion resume ---

    @Test
    void policyBlockedResumeReevaluatesWithoutRerunningTheHandler() {
        AtomicInteger handlerInvocations = new AtomicInteger(0);
        StageHandler impl = ctx -> {
            handlerInvocations.incrementAndGet();
            ctx.addArtifact("urlSchemeAllowlistEnforced", false); // SEC-001 will FAIL
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of("sideEffectId", "only-happens-once");
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = engineWithPolicy(policyEngine);

        String workflowId = engine.submit("demo", stages, "run-33h");
        assertThat(handlerInvocations.get()).isEqualTo(1);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getSafeStopReason()).isEqualTo(SafeStopReason.POLICY_BLOCKED);

        // Retrying WITHOUT granting an exception first: the boundary is re-checked, still blocked,
        // handler is NOT invoked again, and the workflow stays SAFE_STOPPED (not resurrected to
        // RUNNING for no reason).
        engine.retrySafeStopped(workflowId);
        assertThat(handlerInvocations.get()).isEqualTo(1); // still 1 — no re-run
        WorkflowInstanceEntity stillBlocked = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(stillBlocked.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);

        // Grant the exception, retry again: the boundary clears WITHOUT re-running the handler —
        // the same result ("only-happens-once") the first and only invocation produced stands.
        engine.grantPolicyException(workflowId, "SEC-001", "prototype demo only", "implementation stage",
                "human-candidate", "manual review before merge", Instant.now().plus(Duration.ofDays(1)));
        engine.retrySafeStopped(workflowId);

        assertThat(handlerInvocations.get()).isEqualTo(1); // STILL 1 — the handler never re-ran

        WorkflowStepEntity clearedStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(clearedStep.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(clearedStep.getSafeStopReason()).isNull();
        assertThat(clearedStep.getResult()).isEqualTo(Map.of("sideEffectId", "only-happens-once"));

        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void retryExhaustedResumeActuallyReRunsTheHandler() {
        // Contrast case: RETRY_EXHAUSTED (unlike POLICY_BLOCKED) means the handler never actually
        // completed, so retrySafeStopped must genuinely re-invoke it.
        AtomicInteger handlerInvocations = new AtomicInteger(0);
        StageHandler flakyThenWorks = ctx -> {
            if (handlerInvocations.incrementAndGet() == 1) throw new TransientStageException("simulated");
            return Map.of("ok", true);
        };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), flakyThenWorks, false, false, 0, 5, null, null, null));

        WorkflowEngine engine = engineWithCompensation(new CompensationRegistry()); // no compensation registered
        String workflowId = engine.submit("demo", stages, "run-33i");
        assertThat(handlerInvocations.get()).isEqualTo(1);

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);
        assertThat(step.getSafeStopReason()).isEqualTo(SafeStopReason.RETRY_EXHAUSTED);

        engine.retrySafeStopped(workflowId);

        assertThat(handlerInvocations.get()).isEqualTo(2); // genuinely re-ran
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }
}
