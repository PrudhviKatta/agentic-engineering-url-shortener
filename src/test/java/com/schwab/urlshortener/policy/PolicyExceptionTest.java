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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // Codex REQUEST AMENDMENTS item 3: missing evidence now blocks a mandatory policy (was
        // NOT_APPLICABLE before, harmless) — satisfy every OTHER mandatory policy on this stage
        // explicitly so only SEC-001 (the one under test) is the reason for the safe-stop.
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
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
        // Codex REQUEST AMENDMENTS item 3: missing evidence now blocks a mandatory policy (was
        // NOT_APPLICABLE before, harmless) — satisfy every OTHER mandatory policy on this stage
        // explicitly so only SEC-001 (the one under test) is the reason for the safe-stop.
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager,
                policyEvaluationRepository, policyEngine, policyExceptionRepository);

        String workflowId = engine.submit("demo", stages, "run-29b");
        // Codex REQUEST AMENDMENTS item 4: grantPolicyException now rejects an already-expired
        // expiry outright, so an exception can no longer be granted expired in one step — grant
        // one with a short future expiry and let it actually lapse before retrying, to exercise
        // hasApprovedUnexpiredException's real expiry check rather than a rejected grant.
        engine.grantPolicyException(workflowId, "SEC-001", "prototype demo only",
                "implementation stage", "human-candidate", "manual review before merge",
                Instant.now().plus(Duration.ofMillis(50)));
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        engine.retrySafeStopped(workflowId);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED); // still blocked
    }

    @Test
    void grantingAnExceptionForADifferentPolicyDoesNotExcuseThisOne() {
        // Codex REQUEST AMENDMENTS item 3: missing evidence now blocks a mandatory policy (was
        // NOT_APPLICABLE before, harmless) — satisfy every OTHER mandatory policy on this stage
        // explicitly so only SEC-001 (the one under test) is the reason for the safe-stop.
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
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

    @Test
    void retrySafeStoppedOnANonSafeStoppedWorkflowIsRejected() {
        // Regression test (independent-review finding): calling retrySafeStopped on a workflow
        // that isn't actually SAFE_STOPPED must be rejected, not silently resurrect a FAILED
        // workflow to RUNNING or bypass a pending approval gate elsewhere in the graph.
        StageHandler impl = ctx -> Map.of();
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository, transactionManager,
                policyEvaluationRepository, null, policyExceptionRepository); // no PolicyEngine: never safe-stops

        String workflowId = engine.submit("demo", stages, "run-29d");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED); // definitely not SAFE_STOPPED

        assertThatThrownBy(() -> engine.retrySafeStopped(workflowId))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- Codex REQUEST AMENDMENTS item 4: harden grantPolicyException ---

    private WorkflowEngine engineWithRealPolicy() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, new PolicyEngine("policies/v1.yaml"), policyExceptionRepository);
    }

    private String aWorkflowId(WorkflowEngine engine) {
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", true);
            ctx.addArtifact("ssrfGuardEnforced", true);
            return Map.of();
        };
        return engine.submit("demo", List.of(StageDefinition.of("implementation", List.of(), impl)), "run-4x");
    }

    @Test
    void grantingAnExceptionForAnUnknownWorkflowIsRejected() {
        WorkflowEngine engine = engineWithRealPolicy();
        assertThatThrownBy(() -> engine.grantPolicyException("does-not-exist", "SEC-001", "reason", "scope",
                "human", "control", Instant.now().plus(Duration.ofDays(1))))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
    }

    @Test
    void grantingAnExceptionForAnUnknownPolicyIsRejected() {
        WorkflowEngine engine = engineWithRealPolicy();
        String workflowId = aWorkflowId(engine);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "NOT-A-REAL-POLICY", "reason", "scope",
                "human", "control", Instant.now().plus(Duration.ofDays(1))))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
    }

    @Test
    void blankOrMissingRequiredFieldsAreRejected() {
        WorkflowEngine engine = engineWithRealPolicy();
        String workflowId = aWorkflowId(engine);
        Instant futureExpiry = Instant.now().plus(Duration.ofDays(1));

        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, null, "reason", "scope", "human", "control", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", null, "scope", "human", "control", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "  ", "scope", "human", "control", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", null, "human", "control", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", null, "control", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", "human", null, futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", "human", "   ", futureExpiry))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
    }

    @Test
    void missingOrPastExpiryIsRejected() {
        WorkflowEngine engine = engineWithRealPolicy();
        String workflowId = aWorkflowId(engine);

        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", "human", "control", null))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
        assertThatThrownBy(() -> engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", "human", "control",
                Instant.now().minus(Duration.ofSeconds(1))))
                .isInstanceOf(InvalidPolicyExceptionRequestException.class);
    }

    @Test
    void aValidRequestIsStillAccepted() {
        // Regression guard: the hardening above must not reject a genuinely valid request.
        WorkflowEngine engine = engineWithRealPolicy();
        String workflowId = aWorkflowId(engine);
        engine.grantPolicyException(workflowId, "SEC-001", "reason", "scope", "human", "control",
                Instant.now().plus(Duration.ofDays(1)));
        // No exception thrown — success.
    }
}
