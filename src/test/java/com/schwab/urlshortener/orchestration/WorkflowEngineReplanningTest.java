package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Task 35. @DataJpaTest wraps each test in a transaction on the test thread's own connection;
// WorkflowEngine.runFrom dispatches stage handlers onto its internal thread pool, which borrows a
// *different* connection from the same DataSource, so without this override those worker threads
// cannot see the test thread's uncommitted inserts — same root cause and fix as every other
// WorkflowEngine*Test class in this suite.
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowEngineReplanningTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager);
    }

    @Test
    void replanInvalidatesDownstreamVersionsThePlanAndResumesPreservingLineage() {
        StageHandler design = ctx -> { ctx.recordDecision("agent", "initial design", "r1"); return Map.of(); };
        StageHandler oldImpl = ctx -> Map.of("shouldNotRun", true);
        // exitApproval: design must actually run and produce its decision before the human reviews
        // it and chooses to replan rather than simply approve-as-is.
        List<StageDefinition> stages = List.of(
                new StageDefinition("design", List.of(), design, false, true, 0, 30, null, null, null),
                StageDefinition.of("impl", List.of("design"), oldImpl));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35a");

        // Halted at design's exit gate — impl (PENDING, blocked behind it) has not run.
        WorkflowInstanceEntity halted = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(halted.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(halted.getPlanVersion()).isEqualTo(1);
        Long designStepIdBeforeReplan = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "design").orElseThrow().getId();

        // Simulate clarification changing the design before approving: replan while design's own
        // gate is STILL open (the governance-hole scenario) — impl_v2 must not slip past it.
        AtomicBoolean ran = new AtomicBoolean(false);
        StageHandler newImpl = ctx -> { ran.set(true); return Map.of(); };
        engine.replan(workflowId, "impl", List.of(StageDefinition.of("impl_v2", List.of("design"), newImpl)),
                "human", "clarified requirement changed implementation approach");

        WorkflowInstanceEntity afterReplan = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(afterReplan.getPlanVersion()).isEqualTo(2);
        // Unresolved-gate blocking: replan's own runFrom call must re-halt on design's still-open
        // gate, not let impl_v2 slip past it just because a new plan version now exists.
        assertThat(afterReplan.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(ran.get()).isFalse();

        WorkflowStepEntity oldImplStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow();
        assertThat(oldImplStep.getStatus()).isEqualTo(StepStatus.INVALIDATED);
        // Prior-history immutability: the invalidated row's own plan_version is untouched — it
        // stays frozen at the version it was actually created under, not silently bumped forward.
        assertThat(oldImplStep.getPlanVersion()).isEqualTo(1);

        // Plan-version isolation: "design" survives the replan (it is not downstream of "impl") and
        // is carried forward under the NEW plan version so it remains addressable by name/version —
        // the row itself (its id, and everything it already recorded) is unchanged.
        WorkflowStepEntity designStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "design").orElseThrow();
        assertThat(designStep.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        assertThat(designStep.getId()).isEqualTo(designStepIdBeforeReplan); // same row, carried forward, not duplicated
        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "design"))
                .isEmpty(); // no longer addressable under the OLD plan version — it moved forward, not duplicated

        WorkflowStepEntity implV2Step = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "impl_v2").orElseThrow();
        assertThat(implV2Step.getStatus()).isEqualTo(StepStatus.PENDING);

        // Complete replan audit evidence: a "replanned" event with the version/stage transition.
        List<WorkflowEventEntity> events = eventRepository.findByWorkflowIdOrderById(workflowId);
        WorkflowEventEntity replannedEvent = events.stream()
                .filter(e -> "replanned".equals(e.getAction())).findFirst().orElseThrow();
        assertThat(replannedEvent.getPayload())
                .containsEntry("fromVersion", 1)
                .containsEntry("toVersion", 2)
                .containsEntry("fromStage", "impl");
        assertThat(replannedEvent.getReason()).isEqualTo("clarified requirement changed implementation approach");

        // Now approve design's still-open gate — the workflow must resume and complete via the
        // NEW plan (impl_v2), never touching the invalidated old "impl".
        engine.approve(workflowId, "design", "approved", "human", "ok after replan");

        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(ran.get()).isTrue();

        // Context/decision lineage: "design"'s decision, recorded before the replan point, survives
        // the replan and the resume — it is neither lost nor duplicated.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) completed.getContextJson().get("decisions");
        assertThat(decisions.stream().filter(d -> "initial design".equals(d.get("decision")))).hasSize(1);

        assertThat(stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "impl").orElseThrow()
                .getStatus()).isEqualTo(StepStatus.INVALIDATED); // still untouched by the later resume/approval
    }

    @Test
    void anApprovalDecidedUnderAnOlderPlanVersionDoesNotGovernASameNamedGateRecreatedLater() {
        // TDD requirement called out explicitly in tasks.md T035: a decision made against one plan
        // version must never be (mis)read as governing a different plan version's reintroduced gate
        // of the same step name.
        // "next" depends on "review" purely so the workflow is still RUNNING (not yet COMPLETED,
        // which replan() correctly refuses to touch) once review's v1 decision is recorded — it
        // does not need to, and does not, survive the replan below.
        StageHandler gate = ctx -> Map.of();
        StageHandler afterReview = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("review", List.of(), gate, false, true, 0, 30, null, null, null),
                new StageDefinition("next", List.of("review"), afterReview, false, true, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35b");

        engine.approve(workflowId, "review", "approved", "human", "v1 approved");
        WorkflowInstanceEntity afterFirstApproval = instanceRepository.findById(workflowId).orElseThrow();
        // "next" ran and hit its own exit gate — still open, so the workflow is not terminal.
        assertThat(afterFirstApproval.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        // Replan from "review" — it and its downstream dependent "next" are both invalidated (the
        // new plan replaces them with a single, freshly-gated "review"), and the new stage reuses
        // the exact same name "review" as its replacement.
        StageHandler newGate = ctx -> Map.of();
        engine.replan(workflowId, "review",
                List.of(new StageDefinition("review", List.of(), newGate, false, true, 0, 30, null, null, null)),
                "human", "reopen review under a new plan version");

        WorkflowStepEntity oldReview = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "review").orElseThrow();
        assertThat(oldReview.getStatus()).isEqualTo(StepStatus.INVALIDATED);
        WorkflowStepEntity newReview = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 2, "review").orElseThrow();
        // A genuinely different row — the v1 decision must not silently satisfy this one's gate.
        assertThat(newReview.getId()).isNotEqualTo(oldReview.getId());
        assertThat(newReview.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);

        WorkflowInstanceEntity afterReplan = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(afterReplan.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        // The v1 approval still exists, still correctly scoped to the OLD row and OLD plan version —
        // it must not be readable as a decision on the NEW row.
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(oldReview.getId(), "approved")).hasSize(1);
        assertThat(approvalRepository.findByStepInstanceIdAndDecision(newReview.getId(), "approved")).isEmpty();

        // The new gate must be decided independently — replaying only the old decision is not enough.
        engine.approve(workflowId, "review", "approved", "human", "v2 approved independently");
        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        List<ApprovalEntity> v2Approvals =
                approvalRepository.findByWorkflowIdAndPlanVersionAndStepNameAndDecision(workflowId, 2, "review", "approved");
        assertThat(v2Approvals).hasSize(1);
        assertThat(v2Approvals.get(0).getStepInstanceId()).isEqualTo(newReview.getId());
    }

    @Test
    void replanOnAnUnknownStageIsRejectedWithoutMutatingAnything() {
        // exitApproval so the workflow is still open (PENDING_APPROVAL, not COMPLETED) when
        // replan() is called — otherwise the terminal-workflow precondition would fire first and
        // this test would no longer be exercising the unknown-stage check at all.
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(
                new StageDefinition("only", List.of(), handler, false, true, 0, 30, null, null, null));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35c");
        assertThat(instanceRepository.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> engine.replan(workflowId, "does-not-exist", List.of(), "human", "typo"))
                .isInstanceOf(IllegalArgumentException.class);

        // Nothing changed: still plan version 1, "only" untouched, no replanned event recorded.
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getPlanVersion()).isEqualTo(1);
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .noneMatch(e -> "replanned".equals(e.getAction()))).isTrue();
    }

    @Test
    void replanOnATerminalWorkflowIsRejected() {
        StageHandler handler = ctx -> Map.of();
        List<StageDefinition> stages = List.of(StageDefinition.of("only", List.of(), handler));
        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-35d");

        WorkflowInstanceEntity completed = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        assertThatThrownBy(() -> engine.replan(workflowId, "only", List.of(), "human", "too late"))
                .isInstanceOf(IllegalStateException.class);
    }
}
