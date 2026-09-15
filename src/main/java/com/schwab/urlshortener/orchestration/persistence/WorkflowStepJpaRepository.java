package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.StepStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkflowStepJpaRepository extends JpaRepository<WorkflowStepEntity, Long> {
    // Corrected 2026-09-13: plan-version-scoped since Task 21 — kept for direct, exact-version
    // lookups (tests asserting against a specific historical plan_version; a caller that genuinely
    // wants "the row created under plan_version N" regardless of whether it is still live).
    Optional<WorkflowStepEntity> findByWorkflowIdAndPlanVersionAndName(String workflowId, int planVersion, String name);
    List<WorkflowStepEntity> findByWorkflowIdAndPlanVersionOrderBySequence(String workflowId, int planVersion);

    // Task 35 remediation (Codex round 3): plan_version is immutable once a row is created — it
    // records the plan active when the row was created, never re-stamped afterward (a replan that
    // leaves a stage untouched leaves its row, and its plan_version, exactly as they were). A bare
    // (workflowId, name) lookup is still ambiguous once a replan can leave an INVALIDATED row and
    // its live replacement sharing the same name — but unlike plan_version, "not INVALIDATED" is
    // what actually disambiguates: by construction, at most one non-INVALIDATED row exists per
    // (workflowId, name) at any moment (a replan invalidates the old row in the SAME transaction
    // that would introduce any replacement of the same name), so this reconstructs "the currently
    // active row for this stage" correctly regardless of which plan_version it happens to still
    // carry — the actual fix for a survivor stage that must remain findable after later replans
    // advance the workflow's plan_version without ever touching the survivor's own row.
    Optional<WorkflowStepEntity> findByWorkflowIdAndNameAndStatusNot(String workflowId, String name, StepStatus excludedStatus);

    // Round-6c correction: pessimistic row lock (SELECT ... FOR UPDATE) for the specific step
    // WorkflowEngine.approve() is about to decide. Serializes concurrent approve() calls targeting
    // the SAME step instance so exactly one observes WAITING_APPROVAL and wins; the loser's read
    // (blocked until the winner's transaction commits) sees the already-decided status and is
    // rejected as "no pending gate" — see WorkflowEngine's recordDecisionAtomically().
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowStepEntity> findWithLockByWorkflowIdAndNameAndStatusNot(String workflowId, String name, StepStatus excludedStatus);

    // T020b / Task 35 remediation: the full ACTIVE row set for a workflow — every stage currently
    // live, regardless of which individual plan_version each surviving row was originally created
    // under. Reconstructing "what's currently in play" means excluding only INVALIDATED rows, never
    // filtering by a single plan_version (a survivor from an earlier plan_version is just as live as
    // a row created under the current one).
    List<WorkflowStepEntity> findByWorkflowIdAndStatusNotOrderBySequence(String workflowId, StepStatus excludedStatus);

    // Task 49: full-graph reconstruction for StageCatalog-based restart-safe resumption — no
    // ordering guarantee needed here (callers reconstruct the dependency graph from depends_on).
    List<WorkflowStepEntity> findByWorkflowIdAndPlanVersion(String workflowId, int planVersion);
}
