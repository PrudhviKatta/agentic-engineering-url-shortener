package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyCheckResult;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.policy.PolicyOutcome;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.PermanentStageException;
import com.schwab.urlshortener.reliability.RetryExecutor;
import com.schwab.urlshortener.reliability.TimeoutExecutor;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WorkflowEngine {

    // Codex REQUEST AMENDMENTS item 5: base delay for RetryExecutor's exponential backoff
    // (backoffMillis * 2^attempt). Not exposed on StageDefinition/openapi.yaml — a fixed, disclosed
    // constant rather than new per-stage configuration surface this round didn't ask for.
    private static final long RETRY_BACKOFF_BASE_MILLIS = 100L;
    // Codex round 4 finding: data-model.md Section 5 specifies workflow_events.reason as
    // VARCHAR(1024) — approve()/replan() both eventually feed their caller-supplied reason into a
    // recordEvent(...) call, so both reject an over-length reason here, at the point of entry,
    // rather than letting it reach the database and rely on the column's own truncation/constraint
    // behavior (which differs by database and is not a clean, typed error for the caller).
    private static final int REASON_MAX_LENGTH = 1024;

    private final Map<String, StageRegistration> registry = new ConcurrentHashMap<>();
    // Task 35 remediation, round 5 (Codex round 5 finding): moving replan()'s registry.put(...) to
    // strictly after its transaction commits (round 4) narrowed the unsafe window but did not close
    // it — a concurrent runFrom() (via approve() on a SURVIVING gate) could still run, in that
    // window, against the STALE registry, which is not merely "behind" but genuinely blind to any
    // brand-new replacement stage (e.g. "impl_v2") the old graph never listed at all.
    //
    // One monitor object per workflow id. Two things hold it:
    //   1. replan()'s own critical section (its transaction through its registry publish) — so a
    //      SECOND, concurrent replan() for the same workflow cannot commit-and-publish its own
    //      (later) registry in between, which would let publish ordering disagree with commit
    //      ordering.
    //   2. Nothing else. An earlier version of this fix also held this same monitor across
    //      runFrom()'s ENTIRE execution (registry read through batch dispatch to completion), on
    //      the theory that only full mutual exclusion between runFrom() and replan() could close
    //      the race. That serializes replan() against runFrom() for however long a genuinely
    //      slow-running handler takes to finish — REQ-018's own near-instant rejection of an
    //      already-RUNNING stage would then block behind a completely unrelated stage's slow
    //      execution, a real usability regression. The actual defect only ever mattered for ONE
    //      irreversible decision inside runFrom — marking the workflow COMPLETED — because every
    //      OTHER decision it makes from a stale graph is provably harmless (a stage the stale graph
    //      lists that the database has since invalidated is already handled safely by
    //      activeStepOrNull/activeStepStatus's null-tolerant resolution; a stage the stale graph
    //      never lists at all is simply left for the replan's own resume to pick up). Closing that
    //      one decision needs no lock at all — see runFrom's own comment on the DB-authoritative
    //      check it performs immediately before that specific write, which is correct regardless of
    //      timing (transactional atomicity), not merely a narrower window.
    // Java's `synchronized` is reentrant for the owning thread; different workflow ids never share
    // a monitor, so this adds no cross-workflow serialization.
    private final Map<String, Object> workflowLocks = new ConcurrentHashMap<>();

    private Object lockFor(String workflowId) {
        return workflowLocks.computeIfAbsent(workflowId, k -> new Object());
    }

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;
    private final TransactionTemplate transactionTemplate;
    // Task 28/29: all three nullable — policy enforcement (and exceptions to it) are opt-in via
    // the overloaded constructors below. The shared @Component bean (used by
    // ScenarioRunner/WorkflowController today) still resolves via the 5-arg constructor with all
    // three null, since no PolicyEngine is registered as a Spring bean yet — wiring a real
    // policy-aware production instance remains a later task's job, not this one's.
    private final PolicyEvaluationJpaRepository policyEvaluationRepository;
    private final PolicyEngine policyEngine;
    private final PolicyExceptionJpaRepository policyExceptionRepository;
    // Task 33: nullable — compensation is opt-in, same pattern as the policy fields above.
    private final CompensationRegistry compensationRegistry;

    @Autowired
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, null, null, null, null);
    }

    // Task 28's constructor shape, preserved unchanged so WorkflowEnginePolicyEnforcementTest
    // keeps compiling and passing exactly as written — policyExceptionRepository defaults to null
    // (matches hasApprovedUnexpiredException's Task-28-era always-false behavior for any caller
    // that doesn't also opt into Task 29's exception repository).
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, null, null);
    }

    // Task 29's constructor shape, preserved unchanged so PolicyExceptionTest keeps compiling and
    // passing exactly as written — compensationRegistry defaults to null.
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine,
                           PolicyExceptionJpaRepository policyExceptionRepository) {
        this(instanceRepository, stepRepository, eventRepository, approvalRepository,
                transactionManager, policyEvaluationRepository, policyEngine, policyExceptionRepository, null);
    }

    // Task 33: fourth overload across Tasks 22/23/28/29/33 (4 -> 6 -> 7 -> 9 positional
    // parameters). A real code smell — a builder or a small dependencies-holder object would be
    // cleaner — but retrofitting one now means touching five already-green test files for a
    // purely cosmetic win. Recorded as a backlog item for Task 47's final engineering summary
    // ("replace WorkflowEngine's constructor overloads with a builder") rather than silently
    // living with it forever.
    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PlatformTransactionManager transactionManager,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyEngine policyEngine,
                           PolicyExceptionJpaRepository policyExceptionRepository,
                           CompensationRegistry compensationRegistry) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.policyEngine = policyEngine;
        this.policyExceptionRepository = policyExceptionRepository;
        this.compensationRegistry = compensationRegistry;
    }

    public String submit(String feature, List<StageDefinition> stages, String correlationId) {
        return submit(feature, stages, correlationId, null);
    }

    // Codex REQUEST AMENDMENTS follow-up (Section 17 review): the original 3-arg submit() (kept
    // above, delegating here with requirementText=null, for every caller — ScenarioRunner, every
    // existing test — that has no requirement text to carry) validated requirementText at the
    // controller and then dropped it entirely; it never reached contextJson or any persisted
    // column, so Stage 1's own requirement was never captured verbatim or with provenance,
    // contrary to REQ-011/the OpenAPI contract. Seeding it into the initial artifacts map here —
    // before runFrom() ever runs a stage — means it is genuinely part of the workflow context
    // from the very first batch onward: buildContext(instance) reads it back on every subsequent
    // resume, and it shows up in the first stage's own persisted inputsSnapshot
    // (contextArtifactsAtStageStart), inspectable via GET /workflows/{id} without any new API
    // surface.
    public String submit(String feature, List<StageDefinition> stages, String correlationId, String requirementText) {
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        String workflowId = UUID.randomUUID().toString();
        // Task 35 remediation (Codex round 7): every stage created here belongs to plan_version 1
        // — see StageRegistration's own comment for what this map is for and why it exists.
        Map<String, Integer> planVersionByName = stages.stream()
                .collect(java.util.stream.Collectors.toMap(StageDefinition::name, s -> 1));
        registry.put(workflowId, new StageRegistration(stages, graph, planVersionByName));

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature(feature);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(correlationId);
        if (requirementText != null) {
            Map<String, Object> requirement = new LinkedHashMap<>();
            requirement.put("text", requirementText);
            requirement.put("submittedAt", Instant.now().toString());
            requirement.put("submittedVia", "POST /workflows");
            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put("requirement", requirement);
            instance.setContextJson(Map.of("decisions", List.of(), "artifacts", artifacts));
        }
        instanceRepository.save(instance);

        int sequence = 0;
        for (StageDefinition stage : stages) {
            WorkflowStepEntity step = new WorkflowStepEntity();
            step.setWorkflowId(workflowId);
            step.setName(stage.name());
            step.setStatus(StepStatus.PENDING);
            step.setSequence(sequence++);
            step.setDependsOn(stage.dependsOn());
            step.setPlanVersion(instance.getPlanVersion());
            stepRepository.save(step);
        }

        runFrom(workflowId);
        return workflowId;
    }

    // Task 35 (REQ-018 dynamic replanning; REQ-030 change safety), remediated after independent
    // review found the first version genuinely defective — not merely under-tested. Invalidates
    // every stage reachable from fromStage (fromStage itself plus every stage that transitively
    // depends on it), inserts newStages as the replacement, bumps the workflow's plan_version, and
    // resumes. `ctx.decisions()`/`ctx.artifacts()` accumulated before the replan point are
    // untouched — replan never rebuilds or resets the WorkflowContext, it only changes which stages
    // exist going forward.
    //
    // REQ-018 is a hard eligibility gate, not a documentation note: "only the not-yet-started
    // portion" may be altered, and prior history is NEVER rewritten. A stage counts as not-yet-
    // started only if its handler genuinely never ran — status PENDING, or WAITING_APPROVAL behind
    // an ENTRY gate whose preGateStatus is still PENDING (blocked before ever running). A stage
    // whose handler already executed (SUCCEEDED, COMPENSATED, an EXIT gate's WAITING_APPROVAL —
    // preGateStatus not PENDING —, RUNNING, SAFE_STOPPED, or already INVALIDATED) makes the ENTIRE
    // replan request rejected, mutating nothing — never a partial invalidation of "the eligible
    // subset." This is checked, and everything below is validated, strictly BEFORE any row is
    // touched, and the whole operation (eligibility recheck under lock, invalidate, insert
    // replacements, bump plan_version, record the audit event) commits as ONE transaction, with the
    // workflow instance AND every reachable stage's row locked for its duration — runFrom is called
    // only after that transaction has committed, never from inside it (matching every other
    // multi-step mutation in this class — see applyInstanceUpdate's own comment on why: worker
    // threads runBatch dispatches use a different connection and cannot see this transaction's
    // writes until it commits).
    //
    // Plan-version isolation: workflow_steps.plan_version is immutable once a row is created — it
    // records the plan active when that row was created, and this method never re-stamps a
    // survivor's row (an earlier, defective version of this method did; corrected). Every lookup
    // this engine performs (requireStep/stepStatus/claimStage/approve/runFrom's own batch loop) now
    // resolves "the currently active row for this stage name" by excluding INVALIDATED rows, never
    // by matching a single plan_version — see WorkflowStepJpaRepository.findByWorkflowIdAndNameAnd
    // StatusNot's own comment. This is what lets a survivor keep its original plan_version forever
    // while still being found correctly after later replans advance the workflow's plan_version.
    // Approvals reference a step by its durable stepInstanceId (see ApprovalEntity), never by
    // planVersion, so a survivor's untouched row correctly keeps governing any approval already
    // decided against it.
    //
    // Invalidated stages are dropped entirely from the ACTIVE stage list going forward (not kept
    // alongside their replacement) — DependencyGraph keys stages by name in a plain map with no
    // duplicate-name guard, so keeping an invalidated stage around while newStages reintroduces the
    // exact same name (explicitly a legitimate case per data-model.md) would silently collide.
    // Their WorkflowStepEntity rows remain in the database forever, frozen exactly as they were —
    // permanent, immutable history, inspectable via the full-history repository queries the audit
    // trail already uses, just excluded from future scheduling.
    public void replan(String workflowId, String fromStage, List<StageDefinition> newStages,
                        String actor, String reason) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidApprovalDecisionException("actor must be present and non-blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidApprovalDecisionException("reason must be present and non-blank");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new InvalidApprovalDecisionException(
                    "reason must be at most " + REASON_MAX_LENGTH + " characters, got " + reason.length());
        }
        Set<String> newNames = new HashSet<>();
        for (StageDefinition s : newStages) {
            if (!newNames.add(s.name())) {
                throw new IllegalArgumentException("duplicate stage name in newStages: " + s.name());
            }
        }

        // Codex round 5 finding: publishing the replacement registry strictly after the
        // transaction commits (round 4) closes "commit rolls back, registry doesn't" but not the
        // remaining window between that commit and this line — a concurrent runFrom() (e.g. via
        // approve() on a SURVIVING gate) could still run in that window against the STALE registry.
        // That registry is not merely behind, it is genuinely blind to any brand-new replacement
        // stage the old graph never listed at all (unlike a stage the old graph lists that the DB
        // has since invalidated, which activeStepOrNull/activeStepStatus already treat safely as
        // "not currently pending") — so a concurrent runFrom's own batch loop could reach its
        // end-of-loop instance-COMPLETED write with the new stage sitting PENDING forever,
        // unexamined. That specific decision is what runFrom's own DB-authoritative check (see its
        // comment) closes, regardless of timing — it needs no lock from this method at all. What
        // THIS monitor is for is narrower: serializing this workflow's own transaction-through-
        // publish against a SECOND, concurrent replan() for the SAME workflow, so publish ordering
        // can never disagree with commit ordering. It intentionally does NOT extend to this
        // method's own trailing runFrom() call below — see workflowLocks' field comment for why a
        // version that also serialized against runFrom()'s entire execution was tried and reverted.
        synchronized (lockFor(workflowId)) {
            StageRegistration published = transactionTemplate.execute(status -> {
                WorkflowInstanceEntity instance = instanceRepository.findWithLockById(workflowId)
                        .orElseThrow(() -> new IllegalStateException("unknown workflow: " + workflowId));
                if (instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.COMPLETED) {
                    throw new IllegalStateException(
                            "workflow " + workflowId + " is already terminal (" + instance.getStatus() + ")");
                }
                StageRegistration registration = registry.get(workflowId);
                if (registration == null) {
                    throw new IllegalStateException("workflow " + workflowId + " has no in-memory stage "
                            + "registration (process restarted?) — cannot replan it");
                }
                List<StageDefinition> currentStages = registration.stages();
                if (currentStages.stream().noneMatch(s -> s.name().equals(fromStage))) {
                    throw new IllegalArgumentException("unknown stage: " + fromStage);
                }

                Set<String> reachable = reachableFrom(fromStage, currentStages); // includes fromStage
                List<StageDefinition> survivors = currentStages.stream()
                        .filter(s -> !reachable.contains(s.name())).toList();
                for (StageDefinition survivor : survivors) {
                    if (newNames.contains(survivor.name())) {
                        throw new IllegalArgumentException(
                                "newStages reintroduces a surviving stage's own name: " + survivor.name());
                    }
                }

                // Validate the proposed graph BEFORE touching any row: an invalid replan (cycle,
                // unknown dependency) must leave every table completely unchanged, not merely leave
                // the instance stuck in a transitional status. Nothing has been mutated yet at this
                // point (only read-only checks so far), so a thrown exception here rolls the whole
                // transaction back to a no-op.
                List<StageDefinition> updatedStages = new ArrayList<>(survivors);
                updatedStages.addAll(newStages);
                DependencyGraph updatedGraph = new DependencyGraph(updatedStages);
                updatedGraph.validate();

                // REQ-018 eligibility, rechecked under a row lock per reachable stage (not the
                // plain read an earlier version of this method used) so a concurrent
                // claimStage()/approve() targeting the SAME row cannot race this decision —
                // whichever side acquires the row's lock first is serialized against the other,
                // which then re-reads the now-committed state rather than a stale snapshot. Locked
                // in a fixed order (stage name, stable across calls) to avoid a lock-ordering
                // deadlock against another concurrent replan.
                Map<String, WorkflowStepEntity> reachableRows = new LinkedHashMap<>();
                for (String name : reachable.stream().sorted().toList()) {
                    WorkflowStepEntity row = stepRepository
                            .findWithLockByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED)
                            .orElseThrow(() -> new IllegalArgumentException("unknown stage: " + name));
                    boolean notYetStarted = row.getStatus() == StepStatus.PENDING
                            || (row.getStatus() == StepStatus.WAITING_APPROVAL
                                && row.getPreGateStatus() == StepStatus.PENDING);
                    if (!notYetStarted) {
                        throw new IllegalStateException("stage " + name + " has already started (status: "
                                + row.getStatus() + ") — replan may only alter not-yet-started work (REQ-018)");
                    }
                    reachableRows.put(name, row);
                }

                // Every precondition holds — mutate, atomically, still under the instance lock (and
                // every reachable row's own lock) acquired above.
                int oldPlanVersion = instance.getPlanVersion();
                int newPlanVersion = oldPlanVersion + 1;

                for (WorkflowStepEntity row : reachableRows.values()) {
                    row.setStatus(StepStatus.INVALIDATED);
                    stepRepository.save(row);
                }

                List<WorkflowStepEntity> activeSteps =
                        stepRepository.findByWorkflowIdAndStatusNotOrderBySequence(workflowId, StepStatus.INVALIDATED);
                int nextSequence = activeSteps.stream().mapToInt(WorkflowStepEntity::getSequence).max().orElse(-1) + 1;
                for (StageDefinition stage : newStages) {
                    WorkflowStepEntity step = new WorkflowStepEntity();
                    step.setWorkflowId(workflowId);
                    step.setName(stage.name());
                    step.setStatus(StepStatus.PENDING);
                    step.setSequence(nextSequence++);
                    step.setDependsOn(stage.dependsOn());
                    step.setPlanVersion(newPlanVersion);
                    stepRepository.save(step);
                }

                instance.setStatus(WorkflowStatus.REPLANNING);
                instance.setPlanVersion(newPlanVersion);
                instanceRepository.save(instance);

                recordEvent(workflowId, "human", actor, "replanned", fromStage, "OK", reason,
                        Map.of("fromVersion", oldPlanVersion, "toVersion", newPlanVersion, "fromStage", fromStage));

                // Task 35 remediation (Codex round 7): survivors keep their EXISTING plan_version
                // tag (their row is never re-stamped, so this must never change either); newStages
                // — including one that reuses an invalidated stage's exact name — get tagged with
                // the version that was JUST stamped onto their own freshly-inserted rows above. This
                // is what a stale (pre-publish) registration's own tag will disagree with the moment
                // this row becomes visible, closing the "wrong handler, right row" gap — see
                // StageRegistration's own comment.
                Map<String, Integer> updatedPlanVersionByName = new HashMap<>();
                for (StageDefinition survivor : survivors) {
                    updatedPlanVersionByName.put(survivor.name(), registration.planVersionByName().get(survivor.name()));
                }
                for (StageDefinition stage : newStages) {
                    updatedPlanVersionByName.put(stage.name(), newPlanVersion);
                }
                return new StageRegistration(updatedStages, updatedGraph, updatedPlanVersionByName);
            });

            // Only reached if the transaction above committed successfully — see this method's own
            // comment on why publishing here, and never inside the transaction, is what makes this
            // race-free rather than merely "restored on failure." Still inside this workflow's own
            // monitor: a second, concurrent replan() for the SAME workflow must not be able to
            // commit and publish its OWN (later) registry in between this commit and this publish,
            // which would otherwise let publish ordering disagree with commit ordering.
            registry.put(workflowId, published);
        }

        // Deliberately OUTSIDE the synchronized block above: runFrom() no longer needs (or takes)
        // this workflow's monitor for its own execution (see runFrom's own comment on why a lock
        // spanning its whole body — including waiting on a genuinely slow-running handler — was
        // tried and reverted). Calling it unlocked here means a replan() that only needed to
        // invalidate/insert a few rows returns to resuming the workflow promptly rather than
        // holding this monitor for however long the actual handler execution takes.
        runFrom(workflowId);
    }

    // Reachability via forward-dependency BFS from fromStage (includes fromStage itself) — "every
    // stage that fromStage's replacement could affect," not merely stages sequenced after it.
    private Set<String> reachableFrom(String fromStage, List<StageDefinition> stages) {
        Map<String, List<String>> dependents = new HashMap<>();
        for (StageDefinition s : stages) {
            for (String dep : s.dependsOn()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.name());
            }
        }
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromStage);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!result.add(current)) continue;
            for (String dependent : dependents.getOrDefault(current, List.of())) {
                queue.add(dependent);
            }
        }
        return result;
    }

    // Codex REQUEST AMENDMENTS item 7: every instance write below now goes through
    // applyInstanceUpdate — a short, independently-locked transaction that re-reads the instance
    // fresh, checks it isn't ALREADY terminal (a concurrent decision on a different sibling gate
    // may have already committed FAILED/COMPLETED), and abandons the write instead of overwriting
    // a terminal state back to something else. This is deliberately NOT one transaction wrapping
    // runFrom's whole body: an earlier version of this fix tried that, and broke cross-thread
    // visibility of runBatch's OWN worker-thread writes — Hibernate's first-level cache returned
    // the STALE step entity on a second read within the same enclosing transaction/session, since
    // those worker threads commit on separate connections/sessions runFrom's single session never
    // re-queries once an entity is already loaded. Keeping each read of workflow_steps its own
    // independent, uncached round trip (as it always was) while making ONLY the workflow_instances
    // write itself atomic-and-checked is what actually closes the race without breaking that.
    // Codex round 5 finding, resolved WITHOUT holding a lock across this method's whole body: an
    // earlier attempt did exactly that (see the field comment on `workflowLocks`/`lockFor` for why
    // it was tried) but that serializes this method against replan() for its ENTIRE duration,
    // including the time spent waiting on a genuinely slow-running handler — a replan() call
    // targeting a COMPLETELY UNRELATED stage would then block behind that wait too, which is a real
    // usability regression, not just a performance one (REQ-018's own rejection of an
    // already-RUNNING stage should be near-instant; it does not need to wait for that stage to
    // finish). The actual defect was narrower than "this method's whole execution must be
    // serialized against replan()": a stale (pre-replan) in-memory `registration` is completely
    // harmless for every decision this method makes EXCEPT the one irreversible one — marking the
    // workflow COMPLETED — because a brand-new replacement stage a concurrent replan() has already
    // committed is invisible to a stale graph that never listed it. Closing exactly that decision
    // needs no lock at all, only a fresh, DB-authoritative check immediately before that specific
    // write; see the comment at the COMPLETED write below for why that closes the race regardless
    // of timing, not merely narrows it (transactional atomicity: any reader who can see ONE effect
    // of replan()'s commit — an invalidated stage — can already see ALL of them, including the
    // replacement stage's own row).
    private void runFrom(String workflowId) {
        StageRegistration registration = registry.get(workflowId);
        // Independent-review finding (2nd Section 17 follow-up review, D5): the in-memory
        // registry is empty for a workflow id after a process restart, the same pre-existing
        // fragility N1 fixed for retrySafeStopped's own lookup — runFrom itself had no equivalent
        // guard, and every caller of runFrom (submit/approve/retrySafeStopped) would see a raw NPE
        // instead of a clear, typed error.
        if (registration == null) {
            throw new IllegalStateException("workflow " + workflowId + " has no in-memory stage "
                    + "registration (process restarted?) — cannot resume it");
        }
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();

        // Cheap early exit: a concurrent decision may have already driven this workflow to a
        // terminal state before this call was even triggered. Not a substitute for the per-write
        // checks below (those are what actually close the race) — just avoids doing any work at
        // all for the common case where nothing has changed.
        if (instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.COMPLETED) {
            return;
        }

        WorkflowContext ctx = buildContext(instance);

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            // Codex REQUEST AMENDMENTS item 7: re-check terminal status at the START of EVERY
            // batch iteration, not just once at entry. This closes a deeper gap the entry-only
            // guard and the per-write locking above didn't: the exit-gate scan below only checks
            // "has every exit-gated sibling in THIS batch been decided" — it does not (and can't,
            // by itself) know whether one of those decisions was a REJECTION that already failed
            // the whole workflow via a DIFFERENT thread's approve() call. That other thread's
            // rejection never calls runFrom() itself (outcome.rejected() short-circuits it), so
            // nothing stops THIS thread's runFrom() — reached via approving a DIFFERENT sibling —
            // from finding the exit gate satisfied ("all decided") and marching into a dependent
            // batch whose real dependency was rejected, not approved. A fresh read here, before
            // ever touching the next batch, is what actually closes it.
            WorkflowInstanceEntity freshInstance = instanceRepository.findById(workflowId).orElseThrow();
            if (freshInstance.getStatus() == WorkflowStatus.FAILED || freshInstance.getStatus() == WorkflowStatus.COMPLETED) {
                return;
            }

            // Task 35 remediation (Codex round 6, hardened round 7): resolve each batch member's
            // CURRENT active row exactly ONCE, here, and bind every later operation on it — within
            // this SAME batch's processing — to that specific row's id, never to another by-name
            // lookup. See BatchMember's own comment for why: a stale (pre-replan) StageDefinition
            // and a same-named row a concurrent replan() has since inserted are NOT the same stage
            // merely because they share a name, and re-resolving "the current row for this name" at
            // multiple independent points let a stale run claim and execute a brand-new replacement
            // row using the OLD stage's handler — the wrong code running against the right row.
            //
            // Round 6 alone was not sufficient: it prevented a SECOND, later by-name lookup from
            // returning a different row than an earlier one, but this very FIRST resolution can
            // itself already be a mismatch — if a concurrent replan()'s transaction has ALREADY
            // committed a same-named replacement row (durable in the database) before this call
            // even starts, THIS lookup finds that new row on its first and only attempt, and would
            // pair it with the STALE StageDefinition for that name. resolveIfCurrentGeneration
            // closes this by comparing the resolved row's own plan_version against the plan_version
            // THIS registration's own StageDefinition was tagged with (see StageRegistration's
            // planVersionByName) — a mismatch means the row this name currently points to belongs to
            // a DIFFERENT generation of the plan than the StageDefinition in hand, so it is treated
            // exactly like "no active row exists" (null), never claimed or executed against.
            List<BatchMember> members = batch.stream()
                    .map(s -> new BatchMember(s, resolveIfCurrentGeneration(workflowId, s, registration)))
                    .toList();

            List<BatchMember> pending = members.stream()
                    .filter(m -> m.row() != null && m.row().getStatus() == StepStatus.PENDING)
                    .toList();

            // Entry gate: block BEFORE running any handler in this batch. Each entry-gated stage
            // that is still PENDING (i.e. hasn't been approved yet) halts the workflow one stage
            // at a time — a stage this call approves becomes PENDING-and-approved and is excluded
            // here on the next resume, so every entry-gated sibling in a batch is naturally
            // visited in turn across repeated resumes without needing a batch-wide pre-scan.
            for (BatchMember member : pending) {
                if (member.stage().entryApproval()) {
                    // Already resolved above, bound to this exact row — no re-lookup by name.
                    WorkflowStepEntity step = member.row();
                    if (!isApproved(step)) {
                        enterPendingApproval(step);
                        // Halt entirely either way: handler has NOT run, and if applyInstanceUpdate
                        // was abandoned because the workflow is already terminal, there is equally
                        // nothing further for this call to do.
                        applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.PENDING_APPROVAL));
                        return;
                    }
                }
            }

            if (!pending.isEmpty()) {
                // Round-6c correction: freeze the artifacts map as it stood at the START of this
                // batch (before any handler in it runs) and persist THIS snapshot on every step in
                // the batch — not the workflow's current/final artifact map re-read later. Handlers
                // in the same batch run concurrently and may mutate ctx.artifacts() as they go, so
                // capturing once, up front, is what makes "inputs available when the stage began"
                // well-defined instead of a race against siblings.
                Map<String, Object> batchStartArtifacts = Map.copyOf(ctx.artifacts());
                try {
                    runBatch(workflowId, pending, ctx, batchStartArtifacts);
                } catch (SafeStopException e) {
                    // The failing step's own SAFE_STOPPED status/safeStopReason/result were already
                    // persisted inside runStage before this propagated. The instance-level context
                    // (decisions/artifacts) is persisted here so a mixed batch's OTHER
                    // (already-succeeded) siblings' evidence is not discarded just because a
                    // DIFFERENT sibling's SafeStopException happened to be the one that propagated
                    // (Codex REQUEST AMENDMENTS follow-up, Section 17 review).
                    //
                    // 2nd independent-review finding (Section 17 follow-up review, D1): an EARLIER
                    // version of this fix decided this batch-wide, all-or-nothing — "if ANY sibling
                    // looks trustworthy, persist the WHOLE shared artifacts map." Since `ctx` has no
                    // per-stage attribution, that let a genuinely successful sibling's presence
                    // excuse persisting a COMPLETELY DIFFERENT, untrustworthy sibling's own stale
                    // pre-failure evidence too. A SECOND attempt at per-stage attribution (diffing
                    // ctx.artifacts() before/after each stage's own runStage call) was ALSO wrong —
                    // that diff races against a CONCURRENT sibling writing the same shared map in
                    // the same window, misattributing the sibling's write to this stage. The fix
                    // that's actually race-free: WorkflowContext.writtenBy(stageName) attributes
                    // every addArtifact call AT THE MOMENT IT HAPPENS, via a per-worker-thread
                    // marker runStage sets before invoking a stage's handler (see
                    // WorkflowContext.beginStageAttribution) — no shared-state diffing, no window to
                    // race in. An entry only exists for a stage whose handler actually ran (an
                    // entry-gated stage intercepted before ever reaching runStage has none, closing
                    // the D3 sub-finding: WAITING_APPROVAL alone never implied the handler ran).
                    // Starting from batchStartArtifacts (the pre-batch baseline) and merging in ONLY
                    // the keys written by stages whose FINAL status proves their own handler
                    // completed (SUCCEEDED; WAITING_APPROVAL — now safe to trust because
                    // ctx.writtenBy already guarantees the handler ran, an exit gate is reached only
                    // after that; or SAFE_STOPPED/POLICY_BLOCKED, since a policy block happens
                    // strictly after handler success) means an untrustworthy sibling's own keys can
                    // NEVER leak in, regardless of what else in the batch succeeded. COMPENSATED
                    // remains excluded: compensation acts on the stage's RESULT, not on `ctx`, and a
                    // compensated stage's own handler never actually succeeded either.
                    // 3rd independent-review finding (Section 17 follow-up review, D-NEW-1): merge
                    // the VALUES ctx.writtenBy(...) itself captured — never re-read a key's current
                    // value from the live, still-mutable ctx.artifacts() map here. A prior version
                    // of this loop filtered by key but then did exactly that re-read, so a
                    // concurrent untrustworthy sibling that happened to overwrite the SAME key
                    // after the trustworthy stage wrote it could still leak its own value through,
                    // reopening D1.
                    Map<String, Object> safeArtifacts =
                            mergeTrustworthyArtifacts(workflowId, members, ctx, batchStartArtifacts);
                    ctx.resetArtifactsTo(safeArtifacts);
                    applyInstanceUpdate(workflowId, i -> {
                        i.setStatus(WorkflowStatus.SAFE_STOPPED);
                        i.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", safeArtifacts));
                    });
                    recordEvent(workflowId, "orchestrator", "workflow-engine", "safe_stop_triggered",
                            null, "SAFE_STOPPED", e.getMessage());
                    return;
                }

                // 4th independent-review finding (Section 17 follow-up review, Finding A): this
                // success-path persist (reached when runBatch returns WITHOUT throwing — every
                // dispatched stage either SUCCEEDED, is WAITING_APPROVAL-bound, or got COMPENSATED)
                // used to persist the raw, unfiltered ctx.artifacts() unconditionally. A COMPENSATED
                // stage's own handler never actually succeeded — compensation is a fallback ACTION,
                // not evidence its pre-failure writes were ever validated — yet those writes stayed
                // in ctx.artifacts() and flowed straight into this persist, then into the NEXT
                // batch's batchStartArtifacts, directly contradicting the same "COMPENSATED remains
                // excluded" rule the safe-stop merge above already enforces. Using the SAME
                // mergeTrustworthyArtifacts helper here closes that gap consistently on both paths.
                // resetArtifactsTo is what actually closes the WITHIN-ONE-CALL half of the gap: a
                // multi-batch runFrom call takes its NEXT batch's baseline from THIS live ctx
                // object (Map.copyOf(ctx.artifacts()) a few lines below, next loop iteration), not
                // from what was just persisted — so filtering only the persisted row would still
                // let the excluded stage's writes flow into the very next batch via the live map.
                Map<String, Object> safeArtifacts =
                        mergeTrustworthyArtifacts(workflowId, members, ctx, batchStartArtifacts);
                ctx.resetArtifactsTo(safeArtifacts);
                if (!applyInstanceUpdate(workflowId,
                        i -> i.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", safeArtifacts)))) {
                    return; // a concurrent decision already resolved this workflow terminally
                }
            }

            // Scoped re-review finding (post-claimStage follow-up): claimStage stops a stage's
            // handler from running twice, but on its own does nothing to stop the THREAD that
            // lost the claim from walking straight past this batch — pending was empty for it
            // (the only stage in it was already RUNNING, not PENDING, so the filter above dropped
            // it), so it fell through both the runBatch call above and would otherwise reach the
            // exit-gate scan and the next batch while the winner's stage was still executing (or,
            // worse, while the winner's stage had just SAFE_STOPPED and the winner's own
            // SafeStopException catch — which is what actually halts the workflow — hadn't run
            // yet, since that catch lives entirely in the WINNER's call stack and this thread
            // never goes through it). A fresh read here, of every stage in the FULL batch (not
            // just this call's `pending`), is what actually closes both: RUNNING means a sibling
            // is still executing; SAFE_STOPPED means a sibling just failed and the thread that
            // caused it — the only one that runs the SAFE_STOPPED-transition logic above — hasn't
            // necessarily finished doing so. Either way, this call is not the one that gets to
            // carry the workflow forward. WAITING_APPROVAL/SUCCEEDED/COMPENSATED are the outcomes
            // the exit-gate scan below is designed to handle and are safe to fall through to.
            // Fresh, ID-bound read of each member this batch already resolved (Task 35
            // remediation, round 6) — never a fresh by-name lookup, which could now resolve to a
            // DIFFERENT row than the one this call is actually tracking. A member with no row at
            // all (null at snapshot time) maps to null here too — harmless: it matches neither
            // RUNNING nor SAFE_STOPPED below, so it's silently excluded from both checks, exactly
            // like a stage this batch never dispatched at all.
            List<StepStatus> siblingStatuses = members.stream()
                    .map(m -> m.row() == null ? null : activeStepById(m.row().getId()))
                    .map(s -> s == null ? null : s.getStatus())
                    .toList();
            if (siblingStatuses.contains(StepStatus.RUNNING)) {
                return;
            }
            if (siblingStatuses.contains(StepStatus.SAFE_STOPPED)) {
                // Second scoped re-review finding: a mixed batch where ONE sibling is still
                // SAFE_STOPPED (e.g. POLICY_BLOCKED, awaiting grantPolicyException) while ANOTHER
                // sibling was reset by retrySafeStopped (e.g. RETRY_EXHAUSTED) and just re-ran
                // successfully leaves this thread here — correctly halted from advancing — but
                // the instance itself was set RUNNING by retrySafeStopped before this call, and
                // nothing re-asserts SAFE_STOPPED on it. Without this, retrySafeStopped's own
                // precondition ("workflow ... is not SAFE_STOPPED (status: RUNNING)") permanently
                // refuses a second call, even after the operator grants the missing exception —
                // an operationally unrecoverable workflow. applyInstanceUpdate is idempotent with
                // the winner's own SAFE_STOPPED write in the concurrent case (same target state)
                // and a no-op if the instance is already FAILED/COMPLETED.
                applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.SAFE_STOPPED));
                return;
            }

            // Exit gate: block AFTER running, before any dependent batch proceeds. This scans
            // EVERY exit-gated stage in the FULL batch (not just the subset that was `pending`
            // THIS call) and requires each one to carry its own approval before dependents
            // proceed. Round-6 correction (Codex REQUEST AMENDMENTS item 2): the prior version
            // scanned only `pending` and returned on the FIRST unapproved sibling it found: once
            // that sibling was approved and runFrom() resumed, the OTHER exit-gated siblings —
            // already SUCCEEDED from the batch's one-and-only run — were no longer `pending`
            // (PENDING-filtered), so they were silently skipped forever, never receiving their own
            // gate. Re-deriving "still needs a decision" from persisted step status on every call
            // (not from the transient `pending` list) makes this idempotent across any number of
            // partial resumes.
            List<WorkflowStepEntity> stillWaitingForApproval = new ArrayList<>();
            for (BatchMember member : members) {
                if (!member.stage().exitApproval()) continue;
                // No row existed for this stage at snapshot time (or a concurrent replan()
                // invalidated it before then) — no longer this thread's concern; skip rather than
                // crash. Re-read by the id this batch already resolved (Task 35 remediation, round
                // 6), never a fresh by-name lookup.
                if (member.row() == null) continue;
                WorkflowStepEntity step = activeStepById(member.row().getId());
                if (step == null) continue;
                StepStatus status = step.getStatus();
                // Task 33 correction (independent-review finding): COMPENSATED is a real terminal
                // outcome a stage can now reach (retries exhausted, a registered compensation ran)
                // that didn't exist before this task. It satisfied neither SUCCEEDED nor
                // WAITING_APPROVAL, so an exit-gated stage that ended COMPENSATED silently bypassed
                // its own human-approval gate entirely — dependents proceeded without the review the
                // stage's config demands. Arguably a compensated failure needs that review MORE than
                // a clean success does (the original outcome never happened; only a fallback ran).
                boolean readyForGate = status == StepStatus.SUCCEEDED || status == StepStatus.COMPENSATED
                        || status == StepStatus.WAITING_APPROVAL;
                // Round-6b correction (independent-review finding): gate on "has ANY decision yet"
                // (approved OR rejected), not "has an approved decision" alone. A rejected exit
                // gate leaves its step SUCCEEDED (same status a normal completion leaves it in) —
                // checking isApproved() here would treat that as "still needs gating" and hand it a
                // fresh WAITING_APPROVAL on the next resume, letting a second decision overturn the
                // original rejection. hasAnyDecision() makes an already-decided step immune to
                // re-gating regardless of which way it was decided.
                if (readyForGate && !hasAnyDecision(step)) {
                    if (status != StepStatus.WAITING_APPROVAL) {
                        enterPendingApproval(step);
                    }
                    stillWaitingForApproval.add(step);
                }
            }
            // Task 35 remediation: discovered while testing replan against a batch with TWO
            // independent entry-gated siblings decided via separate approve() calls. The entry-gate
            // loop above (over `pending`) only ever catches a stage the FIRST time it is found
            // PENDING-and-unapproved — once enterPendingApproval flips it to WAITING_APPROVAL, it is
            // no longer PENDING, so a LATER call approving a DIFFERENT sibling in the same batch
            // never looks at it again. The exit-gate scan above doesn't cover it either (it
            // explicitly skips non-exit-gated stages). Concretely: sibling A's own approve() leaves
            // it WAITING_APPROVAL (undecided) and returns without touching sibling B; sibling B's
            // own SEPARATE approve() call resumes runFrom, finds B (now approved, PENDING) as the
            // only member of `pending`, runs it, and — with nothing left checking A's still-pending
            // entry gate — the workflow was incorrectly marked COMPLETED with A never having run at
            // all. Re-deriving "still needs a decision" from persisted status over the FULL batch,
            // exactly like the exit-gate scan already does, closes it: an entry-gated stage sitting
            // at WAITING_APPROVAL with no decision yet re-halts the workflow on every call until
            // it's actually decided, regardless of what else in the same batch has resolved.
            for (BatchMember member : members) {
                if (!member.stage().entryApproval()) continue;
                if (member.row() == null) continue;
                WorkflowStepEntity step = activeStepById(member.row().getId());
                if (step != null && step.getStatus() == StepStatus.WAITING_APPROVAL && !hasAnyDecision(step)) {
                    stillWaitingForApproval.add(step);
                }
            }
            if (!stillWaitingForApproval.isEmpty()) {
                applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.PENDING_APPROVAL));
                return; // halt either way; handlers already ran and are WAITING_APPROVAL; approve() resumes
            }
        }

        // Codex round 5 finding: the batch loop above is driven entirely by `registration`, an
        // in-memory snapshot that can be stale relative to a concurrent replan() that has ALREADY
        // committed a brand-new replacement stage this call never knew existed (unlike a stage the
        // stale graph lists that the DB has since invalidated — activeStepOrNull/activeStepStatus
        // already treat that safely as "not currently pending" throughout the loop above). Checked
        // directly against the database, not the registry, right before the one truly irreversible
        // decision this method makes: transactional atomicity means any reader who can already see
        // ONE effect of a concurrent replan()'s commit (an invalidated stage, which is how this
        // call's own batch loop above would have skipped it) can see EVERY other effect of that
        // same commit too, including the replacement stage's own freshly-inserted row — so this
        // check is correct regardless of the relative timing between that commit and this line,
        // not merely a narrower window to race in.
        boolean concurrentReplanLeftWorkThisCallNeverSaw = stepRepository
                .findByWorkflowIdAndStatusNotOrderBySequence(workflowId, StepStatus.INVALIDATED).stream()
                .anyMatch(s -> s.getStatus() == StepStatus.PENDING || s.getStatus() == StepStatus.RUNNING
                        || s.getStatus() == StepStatus.WAITING_APPROVAL || s.getStatus() == StepStatus.RETRYING
                        || s.getStatus() == StepStatus.SAFE_STOPPED);
        if (concurrentReplanLeftWorkThisCallNeverSaw) {
            // Leave the instance exactly as it is (not COMPLETED) — a replan() that introduced
            // this work always resumes it via its own trailing runFrom() call once it publishes;
            // an operator can also always trigger a fresh resume for the same reason submit()'s
            // own restart-recovery story already relies on repeated runFrom() calls being safe.
            return;
        }

        // Codex REQUEST AMENDMENTS item 7: this is the write the concurrency test targets most
        // directly — a concurrent decision on a different sibling gate must never have this call
        // overwrite its (already-committed) FAILED status back to COMPLETED.
        if (!applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.COMPLETED))) {
            return; // a concurrent decision already resolved this workflow terminally
        }
        // actorType "orchestrator" (not "engine"): openapi.yaml's AuditEventResponse.actorType
        // enum is {agent, human, orchestrator} — the engine completing a workflow on its own
        // initiative is exactly the "orchestrator" case.
        recordEvent(workflowId, "orchestrator", "workflow-engine", "workflow_completed", null, "OK", null);
    }

    // Codex REQUEST AMENDMENTS item 7: applies a mutation to the workflow instance inside a short,
    // independently pessimistic-locked transaction — a fresh read, not the (possibly stale)
    // in-memory `instance` object callers in runFrom hold. Returns false (mutation abandoned,
    // nothing written) if the instance is already terminal (FAILED/COMPLETED) by the time the lock
    // is acquired; true if the mutation was applied and saved.
    private boolean applyInstanceUpdate(String workflowId, Consumer<WorkflowInstanceEntity> mutator) {
        Boolean applied = transactionTemplate.execute(status -> {
            WorkflowInstanceEntity fresh = instanceRepository.findWithLockById(workflowId).orElseThrow();
            if (fresh.getStatus() == WorkflowStatus.FAILED || fresh.getStatus() == WorkflowStatus.COMPLETED) {
                return Boolean.FALSE;
            }
            mutator.accept(fresh);
            instanceRepository.save(fresh);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(applied);
    }

    // Starting from batchStartArtifacts (the pre-batch baseline) and merging in ONLY the (key,
    // value) writes captured by ctx.writtenBy(...) for stages whose FINAL status proves their own
    // handler completed (SUCCEEDED, WAITING_APPROVAL, or SAFE_STOPPED/POLICY_BLOCKED) — never
    // COMPENSATED (its handler never actually succeeded; compensation acts on the stage's RESULT,
    // not on ctx) and never re-reading a value from the live, still-mutable ctx.artifacts() map.
    // Shared by BOTH runFrom call sites that persist a batch's artifacts: the SafeStopException
    // catch (a sibling failed) and the success-path persist (every dispatched stage succeeded or
    // was compensated) — 4th independent-review finding (Section 17 follow-up review, Finding A):
    // these two paths previously used different logic, and the success path persisted the raw,
    // unfiltered map, letting a COMPENSATED stage's own pre-failure writes flow forward into the
    // NEXT batch's baseline even though the exact same rule already excluded them on the
    // safe-stop path.
    private Map<String, Object> mergeTrustworthyArtifacts(String workflowId,
            List<BatchMember> members, WorkflowContext ctx, Map<String, Object> batchStartArtifacts) {
        Map<String, Object> safeArtifacts = new LinkedHashMap<>(batchStartArtifacts);
        for (BatchMember member : members) {
            Map<String, Object> written = ctx.writtenBy(member.stage().name());
            if (written.isEmpty()) {
                continue; // this stage's handler never ran, or wrote nothing new
            }
            // A sibling with non-empty writtenBy(...) must already have run its handler to
            // completion (writesByStage is only ever populated by a real addArtifact call), and
            // REQ-018 forbids replan() from ever invalidating a stage that has already started — so
            // member.row() is provably non-null here. Read by the id this batch already resolved it
            // to (Task 35 remediation, round 6), not a fresh by-name lookup, which is what let a
            // stale run resolve a same-named replacement row in the first place.
            WorkflowStepEntity siblingStep = member.row() == null ? null : activeStepById(member.row().getId());
            StepStatus st = siblingStep == null ? null : siblingStep.getStatus();
            boolean trustworthy = st == StepStatus.SUCCEEDED || st == StepStatus.WAITING_APPROVAL
                    || (st == StepStatus.SAFE_STOPPED
                        && siblingStep.getSafeStopReason() == SafeStopReason.POLICY_BLOCKED);
            if (trustworthy) {
                safeArtifacts.putAll(written);
            }
        }
        return safeArtifacts;
    }

    // Independent-review finding (post-item-7 follow-up): two concurrent runFrom() calls — e.g.
    // one thread per approve() call on DIFFERENT sibling exit gates in the same batch — each
    // independently recompute `pending` by reading step status, and both can observe the SAME
    // dependent stage as PENDING before either has dispatched it, double-executing its handler.
    // Every fix already in this file (applyInstanceUpdate, the per-iteration terminal re-check)
    // protects the WORKFLOW INSTANCE's status; none of them claim the STEP itself, which is what
    // this closes: only a thread that wins the atomic PENDING -> RUNNING transition below actually
    // runs the stage's handler. The loser sees a non-PENDING status and skips it as a no-op — the
    // winner's execution already covers it, and future.get() below only ever waits on the threads
    // that actually claimed a stage.
    private void runBatch(String workflowId, List<BatchMember> pending, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        // Task 35 remediation (Codex round 6): claim by the row id this batch already resolved —
        // never re-derive "the current row for this name" here. See claimStage's own comment.
        List<BatchMember> claimed = pending.stream()
                .filter(member -> claimStage(member.row().getId(), inputsSnapshot))
                .toList();
        if (claimed.isEmpty()) {
            return; // every stage in this batch was already claimed by a concurrent runFrom() call
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, claimed.size()));
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (BatchMember member : claimed) {
                long stepId = member.row().getId();
                StageDefinition stage = member.stage();
                futures.add(pool.submit(() -> runStage(workflowId, stepId, stage, ctx, inputsSnapshot)));
            }
        } finally {
            pool.shutdown();
        }

        // Independent-review finding (5th pass, post-cd797f0 follow-up): await EVERY claimed
        // stage's future before this method returns or throws — even once one has already
        // failed. The previous form propagated the FIRST failure immediately (via future.get()
        // inside the loop, which throws on the first exception it meets), abandoning the
        // remaining futures unjoined. Their handlers kept running on the pool's threads
        // regardless — pool.shutdown() does not interrupt in-flight tasks — but nothing was
        // left to notice when they finished: every "is anything still RUNNING" check in
        // runFrom only ever runs inside a thread that returned from THIS method, and this
        // method had already returned. A batch with an orphaned sibling still executing could
        // then get "resolved" (e.g. by a retrySafeStopped call landing mid-orphan) with no
        // thread left to observe the orphan's eventual completion, permanently stranding the
        // instance RUNNING. Joining every future here — regardless of order or which one
        // fails first — guarantees that by the time this method returns, every stage IT
        // dispatched has actually finished, restoring the invariant runFrom's per-batch check
        // depends on.
        Throwable firstSafeStop = null;
        Throwable firstOther = null;
        boolean interrupted = false;
        for (Future<?> future : futures) {
            // Codex REQUEST AMENDMENTS follow-up (Section 17 review): an InterruptedException
            // from future.get() means THIS thread was interrupted while waiting, not that the
            // future itself failed — the handler it's waiting on is still running. Moving on to
            // the next future without re-awaiting this one is exactly the orphan-thread gap the
            // Round 5 fix (join every future unconditionally) was meant to close, just reachable
            // via a different trigger. Retry get() on the SAME future — java.util.concurrent
            // clears a thread's interrupt status the moment a blocking call consumes it, so the
            // retry does not spin or return immediately unless re-interrupted — and only restore
            // the interrupt flag once every future has actually been joined.
            boolean joined = false;
            while (!joined) {
                try {
                    future.get();
                    joined = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    // Task 28: a SafeStopException from a mandatory policy failure must propagate
                    // as itself — not wrapped in a generic RuntimeException — so runFrom's catch
                    // above can distinguish "safe-stop the workflow" from "an unexpected handler
                    // error."
                    Throwable cause = e.getCause();
                    if (cause instanceof SafeStopException && firstSafeStop == null) {
                        firstSafeStop = cause;
                    } else if (firstOther == null) {
                        firstOther = cause;
                    }
                    joined = true;
                }
            }
        }
        // Codex REQUEST AMENDMENTS follow-up (Section 17 review): restore the interrupt flag so a
        // caller that cares can observe it — but do NOT synthesize an exception purely because
        // this thread was interrupted while waiting. Every future was still joined to its REAL
        // outcome above (that's the whole point of retrying get() on the same future rather than
        // abandoning it), so if every stage actually succeeded, this method returning normally is
        // the correct behavior; throwing here would misrepresent a fully successful batch as
        // failed merely because the waiting thread was interrupted partway through.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (firstSafeStop != null) {
            throw (SafeStopException) firstSafeStop;
        }
        if (firstOther != null) {
            throw new RuntimeException(firstOther);
        }
    }

    // Atomically transitions a step PENDING -> RUNNING under the step's existing pessimistic row
    // lock (the same lock recordDecisionInTransaction uses), so exactly one concurrent caller
    // dispatches this stage's handler. Sets startedAt/inputsSnapshot here — the values a losing
    // caller would otherwise have raced to write — so runStage's own read afterward is guaranteed
    // fresh rather than reused from a stale in-memory reference.
    // Task 35 remediation (Codex round 6): claims by the row's own durable id, never by
    // (workflowId, name) — see WorkflowStepJpaRepository.findWithLockById's own comment for why a
    // name-based claim let a stale run claim and execute a same-named REPLACEMENT row a concurrent
    // replan() had already inserted, running the OLD StageDefinition's handler against it. Binding
    // to the id a batch already resolved this stage to means a concurrent replan() that invalidates
    // THIS EXACT row is correctly seen as "no longer PENDING" here, never silently swapped for
    // whatever OTHER row now happens to share its name.
    private boolean claimStage(long stepId, Map<String, Object> inputsSnapshot) {
        Boolean claimed = transactionTemplate.execute(status -> {
            // A concurrent replan() legitimately invalidates a PENDING stage (REQ-018 permits
            // invalidating not-yet-started work) in the window between this stage's own status
            // being read as PENDING elsewhere and this claim attempt. That is not a bug to crash
            // on — it is exactly "someone else already decided this stage isn't running," the same
            // outcome as losing the claim race to a concurrent runFrom.
            WorkflowStepEntity step = stepRepository.findWithLockById(stepId).orElse(null);
            if (step == null || step.getStatus() != StepStatus.PENDING) {
                return Boolean.FALSE;
            }
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(Instant.now());
            step.setInputsSnapshot(inputsSnapshot);
            stepRepository.save(step);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(claimed);
    }

    public void approve(String workflowId, String stepName, String decision, String actor, String reason) {
        // Round-6 correction (Codex REQUEST AMENDMENTS item 1): validate the decision value itself
        // before touching any state — a decision that is neither "approved" nor "rejected" must
        // never be silently treated as an implicit approval.
        if (!"approved".equals(decision) && !"rejected".equals(decision)) {
            throw new InvalidApprovalDecisionException(
                    "decision must be 'approved' or 'rejected', got: " + decision);
        }
        // Round-6c correction: actor/reason are REQ-013's "recorded with rationale" and
        // data-model.md's decided_by fields — required for any row this engine actually inserts
        // (see ApprovalEntity's Round-6c note: nullable at the schema level only to allow a future
        // pending-row-before-decision pattern this engine doesn't use).
        if (actor == null || actor.isBlank()) {
            throw new InvalidApprovalDecisionException("actor must be present and non-blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidApprovalDecisionException("reason must be present and non-blank");
        }
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new InvalidApprovalDecisionException(
                    "reason must be at most " + REASON_MAX_LENGTH + " characters, got " + reason.length());
        }

        // Round-6c correction (item 1, atomic/one-shot decision recording): the read-check-write
        // sequence that decides whether THIS call wins the gate runs inside one transaction, with
        // the target step's row pessimistically locked, so two concurrent approve() calls on the
        // SAME step instance are fully serialized — the loser re-reads the step under lock only
        // after the winner has committed, sees the already-decided status, and is rejected exactly
        // like any other "no pending gate" case. The step_instance_id unique constraint on
        // ApprovalEntity is a second, independent backstop against a duplicate insert.
        DecisionOutcome outcome = recordDecisionAtomically(workflowId, stepName, decision, actor, reason);
        if (!outcome.rejected()) {
            runFrom(workflowId);
        }
    }

    private DecisionOutcome recordDecisionAtomically(String workflowId, String stepName, String decision,
                                                       String actor, String reason) {
        try {
            return recordDecisionInTransaction(workflowId, stepName, decision, actor, reason);
        } catch (PessimisticLockingFailureException e) {
            // A concurrent approve() call on the SAME step is holding the row lock past H2's lock
            // timeout: rather than let that surface as a 500, treat it the same as losing the race
            // outright — by the time this caller could act, the winner has (or is about to have)
            // already decided this gate.
            throw new NoPendingApprovalException(
                    "no approval currently pending on step " + stepName + " (contended by a concurrent decision)");
        }
    }

    private DecisionOutcome recordDecisionInTransaction(String workflowId, String stepName, String decision,
                                                          String actor, String reason) {
        return transactionTemplate.execute(status -> {
            // Codex REQUEST AMENDMENTS item 7: pessimistic row lock on the WORKFLOW instance
            // itself, acquired before the step-level lock below. This closes the previously
            // disclosed cross-sibling race: two DIFFERENT exit-gated siblings decided truly
            // simultaneously (reject one, approve the other) can no longer both pass the
            // terminal-status check below before either commits — the second caller now blocks
            // here until the first's transaction commits or rolls back, then re-reads the
            // now-committed instance status.
            WorkflowInstanceEntity instance = instanceRepository.findWithLockById(workflowId)
                    .orElseThrow(() -> new NoPendingApprovalException("unknown workflow: " + workflowId));

            // Round-6b correction (independent-review finding): a terminal workflow accepts no
            // further decisions.
            if (instance.getStatus() == WorkflowStatus.FAILED || instance.getStatus() == WorkflowStatus.COMPLETED) {
                throw new NoPendingApprovalException(
                        "workflow " + workflowId + " is already terminal (" + instance.getStatus() + ")");
            }
            int planVersion = instance.getPlanVersion();

            StageRegistration registration = registry.get(workflowId);
            StageDefinition stage = registration == null ? null : registration.stages().stream()
                    .filter(s -> s.name().equals(stepName)).findFirst().orElse(null);
            if (stage == null) {
                throw new NoPendingApprovalException("unknown step: " + stepName);
            }

            // Pessimistic row lock (SELECT ... FOR UPDATE): a concurrent approve() call targeting
            // this same step blocks here until this transaction commits or rolls back, then re-reads
            // the now-committed status — never a stale, pre-decision read.
            WorkflowStepEntity step = stepRepository
                    .findWithLockByWorkflowIdAndNameAndStatusNot(workflowId, stepName, StepStatus.INVALIDATED)
                    .orElseThrow(() -> new NoPendingApprovalException("unknown step: " + stepName));

            // Task 35 remediation (Codex round 7): same generation check runFrom() applies via
            // resolveIfCurrentGeneration(). `stage` above was resolved from the caller's own
            // (possibly stale) in-memory registration by name alone; the row just locked above was
            // independently resolved by (workflowId, name, status != INVALIDATED). A concurrent
            // replan() that invalidates the old "stepName" row and inserts a fresh one under the
            // SAME name between those two resolutions would otherwise let this decision be recorded
            // — and, worse, dispatched — against the NEW row using the OLD StageDefinition's
            // entryApproval/exitApproval flags and handler. Reject as unknown rather than silently
            // deciding across a generation boundary.
            Integer expectedPlanVersion = registration.planVersionByName().get(stepName);
            if (expectedPlanVersion == null || step.getPlanVersion() != expectedPlanVersion) {
                throw new NoPendingApprovalException("unknown step: " + stepName);
            }

            // Reject premature (step never reached its gate), duplicate (step already decided), and
            // non-gate (stage has neither entryApproval nor exitApproval) approval attempts alike: a
            // step only ever carries WAITING_APPROVAL while a gate genuinely awaits a decision on it.
            if (step.getStatus() != StepStatus.WAITING_APPROVAL) {
                throw new NoPendingApprovalException(
                        "no approval currently pending on step " + stepName + " (status: " + step.getStatus() + ")");
            }

            ApprovalEntity approval = new ApprovalEntity();
            approval.setWorkflowId(workflowId);
            approval.setPlanVersion(planVersion);
            approval.setStepInstanceId(step.getId());
            approval.setStepName(stepName);
            approval.setDecidedAt(Instant.now());
            approval.setDecision(decision);
            approval.setDecidedBy(actor);
            approval.setReason(reason);
            try {
                approvalRepository.saveAndFlush(approval);
            } catch (DataIntegrityViolationException e) {
                // Backstop for the DB unique constraint on step_instance_id: the pessimistic lock
                // above should already make this unreachable, but if it's ever hit, a second
                // decision on an already-decided step is rejected the same way a duplicate is.
                throw new NoPendingApprovalException(
                        "no approval currently pending on step " + stepName + " (already decided)");
            }
            recordEvent(workflowId, "human", actor, "approval_" + decision + ":" + stepName, stepName, "OK", reason);

            // Codex REQUEST AMENDMENTS item 8: an exit-gated step's post-approval status now
            // restores from preGateStatus (captured by enterPendingApproval right before it
            // overwrote the step to WAITING_APPROVAL) instead of hardcoding SUCCEEDED — a step
            // that reached this gate via COMPENSATED (Task 33) stays COMPENSATED through approval
            // or rejection alike, matching the pre-existing symmetry ("the handler's own
            // completion stands; only downstream is blocked via instance FAILED on rejection").
            // preGateStatus falls back to SUCCEEDED for rows persisted before this column existed.
            StepStatus exitGateOutcome = step.getPreGateStatus() != null ? step.getPreGateStatus() : StepStatus.SUCCEEDED;
            boolean rejected = "rejected".equals(decision);
            if (rejected) {
                step.setStatus(stage.entryApproval() ? StepStatus.FAILED : exitGateOutcome);
            } else {
                step.setStatus(stage.entryApproval() ? StepStatus.PENDING : exitGateOutcome);
            }
            step.setPreGateStatus(null);
            stepRepository.save(step);
            instance.setStatus(rejected ? WorkflowStatus.FAILED : WorkflowStatus.RUNNING);
            instanceRepository.save(instance);
            return new DecisionOutcome(rejected);
        });
    }

    private record DecisionOutcome(boolean rejected) { }

    // Round-6 correction (Codex REQUEST AMENDMENTS item 1): stepInstanceId — not stepName — is the
    // authoritative key for "has THIS step instance been approved".
    private boolean isApproved(WorkflowStepEntity step) {
        return !approvalRepository.findByStepInstanceIdAndDecision(step.getId(), "approved").isEmpty();
    }

    private boolean hasAnyDecision(WorkflowStepEntity step) {
        return approvalRepository.existsByStepInstanceId(step.getId());
    }

    private void enterPendingApproval(WorkflowStepEntity step) {
        // Codex REQUEST AMENDMENTS item 8: capture the status a gate is about to overwrite, so
        // approve() can restore it afterward instead of hardcoding SUCCEEDED — the only caller of
        // this method with anything other than PENDING/SUCCEEDED as the pre-gate status is an
        // exit-gated step that reached COMPENSATED (Task 33), which must stay COMPENSATED through
        // approval, not silently read as a clean success.
        step.setPreGateStatus(step.getStatus());
        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);
    }

    // Task 35 remediation (Codex round 6): dispatched with the EXACT row id claimStage already
    // claimed, never re-resolved by (workflowId, stage.name()) — that re-resolution is exactly what
    // let a stale StageDefinition's handler run against a same-named replacement row a concurrent
    // replan() had already inserted.
    private void runStage(String workflowId, long stepId, StageDefinition stage, WorkflowContext ctx,
                           Map<String, Object> inputsSnapshot) {
        // status/startedAt/inputsSnapshot are already committed here — claimStage (in runBatch)
        // performed the atomic PENDING -> RUNNING transition before this call was ever dispatched.
        // A fresh read, not a reused reference, so this reflects that committed state.
        WorkflowStepEntity step = requireStepById(stepId);

        // Codex REQUEST AMENDMENTS item 5: actually wire T030's RetryExecutor (bounded exponential
        // backoff) around T031's TimeoutExecutor, rather than a hand-rolled loop that never slept
        // between attempts. Classification comes from the handler itself: PermanentStageException
        // never retries; TransientStageException — which StageTimeoutException now IS, by
        // inheritance — retries up to stage.maxRetries() additional attempts, with real
        // exponential backoff between them. onRetry fires only when a retry is actually about to
        // happen (never on the final exhausting failure), so step.retryCount keeps tracking
        // retries actually PERFORMED, bounded by maxRetries — same semantics as before, now sitting
        // on the real T030 backoff/retry machinery instead of duplicating its math inline. Every
        // existing caller is unaffected: StageDefinition.of(...) defaults maxRetries=0/
        // timeoutSeconds=30, so a normal non-throwing handler still runs exactly once, inside a 30s
        // timeout window it never approaches, and proceeds through the unchanged policy-check/
        // SUCCEEDED path below.
        Map<String, Object> result = null;
        boolean succeeded = false;
        SafeStopReason failureReason = SafeStopReason.RETRY_EXHAUSTED;
        Throwable failureCause = null;
        // 6th/7th independent-review findings (Section 18 follow-up review, two rounds): resets
        // THIS stage's own writesByStage bucket at the start of EVERY attempt (including the
        // first), before the real handler runs, via WorkflowContext.beginAttempt — this call
        // happens INSIDE TimeoutExecutor's own dedicated per-attempt worker thread (it's the first
        // thing that thread does), not before that thread exists. An attempt that throws after
        // writing mandatory-policy evidence can never have that evidence survive into a LATER,
        // successful attempt's own evaluation, and (round 2, closing the gaps Codex's own review of
        // round 1 found) a straggler attempt whose worker thread keeps running past a timeout can
        // never have a write land in a newer attempt's bucket either — WorkflowContext makes the
        // generation-check-and-write atomic per stage and never lets a stage-attributed write touch
        // the shared `artifacts` map directly at all (see WorkflowContext's own comments). Scoped
        // per-stage throughout — concurrent siblings legitimately writing the same artifact key are
        // never touched by any of this.
        StageHandler perAttemptIsolated = attemptCtx -> {
            attemptCtx.beginAttempt(stage.name());
            return stage.handler().run(attemptCtx);
        };
        StageHandler timeoutBounded = TimeoutExecutor.withTimeout(perAttemptIsolated, stage.timeoutSeconds() * 1000L);
        StageHandler retryable = RetryExecutor.withBoundedRetry(timeoutBounded, stage.maxRetries(),
                RETRY_BACKOFF_BASE_MILLIS, attempt -> {
                    step.setRetryCount(step.getRetryCount() + 1);
                    stepRepository.save(step);
                });
        // Independent-review finding (2nd Section 17 follow-up review, D1 fix-of-a-fix):
        // attributes every ctx.addArtifact call this stage's handler makes (across all of its own
        // internal retry attempts — RetryExecutor runs them all on THIS thread, never a new one)
        // to this stage's name, via WorkflowContext's per-thread marker. Cleared in a finally so
        // it never leaks onto whatever this pool thread does next. See WorkflowContext's own
        // comment for why this replaces an earlier, racy before/after diff of the shared map.
        ctx.beginStageAttribution(stage.name());
        try {
            try {
                result = retryable.run(ctx);
                succeeded = true;
            } catch (PermanentStageException e) {
                // never retried; falls through to the exhaustion/compensation path below
                failureCause = e;
            } catch (TransientStageException e) {
                // retries exhausted (a plain transient failure or a StageTimeoutException — now
                // the same catch clause since StageTimeoutException extends TransientStageException)
                failureCause = e;
            } catch (RuntimeException e) {
                // Codex REQUEST AMENDMENTS follow-up (Section 17 review): neither RetryExecutor
                // nor TimeoutExecutor catches anything outside the Permanent/TransientStageException
                // hierarchy — an unexpected handler bug (NPE, IllegalStateException, etc.)
                // previously propagated straight out of runStage uncaught, leaving the step at
                // RUNNING (set by claimStage before the handler ran) and the workflow instance
                // untouched forever: no terminal write, no SafeStopException for runFrom's catch to
                // react to. Fail-closed: this falls into the same exhaustion path below a genuinely
                // exhausted retry takes, tagged with its own reason so the audit trail and
                // retrySafeStopped's resume logic (which already treats every non-POLICY_BLOCKED
                // reason identically — reset and genuinely re-run) can tell an unexpected bug apart
                // from ordinary exhaustion without any further branching needed there.
                failureReason = SafeStopReason.UNEXPECTED_FAILURE;
                failureCause = e;
            }
        } finally {
            // 7th independent-review finding (Section 18 follow-up review, round 2): seals this
            // stage's attempt bookkeeping the instant the retry loop as a whole concludes — success
            // or final exhaustion, doesn't matter which. Without this, the LAST attempt's
            // generation stays "open" forever (nothing ever calls beginAttempt again for this
            // stage), so a straggler from that final attempt could write into writesByStage at any
            // point in the future, including after writtenBy(stage.name()) below has already been
            // read for policy evaluation, or after the batch-level mergeTrustworthyArtifacts reads
            // it again later for persistence. Called before endStageAttribution (order between the
            // two doesn't matter — they touch disjoint state — but sealing first means a stray call
            // racing the very end of this finally block sees the stage already closed either way).
            ctx.sealAttempt(stage.name());
            ctx.endStageAttribution();
        }

        // Independent-review finding (Section 17 follow-up review, N3): everything from here to
        // the end of the method previously ran with NO fail-closed net at all — a RuntimeException
        // from the policy engine, a repository save, or recordEvent itself propagated straight out
        // of runStage uncaught, leaving the step RUNNING (or, mid-!succeeded-block, whatever
        // partial state it was in) exactly like the handler-invocation gap item 4 closed. The
        // SafeStopException paths below (compensation-failure, retry/unexpected-exhaustion,
        // policy-block) already fully persist their own terminal state before throwing — they
        // pass through the outer catch unchanged. Anything else lands in the same fail-closed
        // UNEXPECTED_FAILURE treatment, without attempting compensation: compensation is a
        // response to a genuine HANDLER failure with a real (possibly partial) result to react to,
        // not to a bug in the engine's own post-handler bookkeeping, which has no well-defined
        // "undo" and — per independent review — risks silently laundering an unrelated engine bug
        // into a normal COMPENSATED continuation.
        try {
            if (!succeeded) {
                // Design note (brief, Task 33): a compensated failure does NOT throw — the batch
                // and the workflow proceed normally toward COMPLETED. Compensation is "the failure
                // was handled," a controlled saga-style continuation, not a block. Only an
                // UNCOMPENSATED exhaustion, or the compensation itself failing, throws
                // SafeStopException, matching Task 28's policy-failure path.
                // 2nd independent-review finding (Section 17 follow-up review, D2): an EARLIER
                // version of this fix also skipped compensation whenever failureReason was
                // UNEXPECTED_FAILURE, on the theory that compensation shouldn't run for "a bug in
                // the engine's own bookkeeping." But failureReason is ONLY ever UNEXPECTED_FAILURE
                // here as a result of the HANDLER-INVOCATION catch above — i.e. exactly the
                // genuine-handler-failure case compensation exists for (a real, possibly
                // side-effecting handler threw something other than a StageException). The
                // engine-bug case that rationale actually describes is handled entirely by the
                // OUTER catch below, which never calls compensationRegistry at all — so the
                // condition here only ever fired for handler bugs, silently leaving a REAL side
                // effect un-compensated. Removed: compensation now runs for every !succeeded cause
                // uniformly, as it did before this reason code existed.
                boolean compensated = false;
                if (compensationRegistry != null) {
                    try {
                        compensated = compensationRegistry.run(stage.name(), result != null ? result : Map.of());
                    } catch (RuntimeException compensationFailure) {
                        // Codex REQUEST AMENDMENTS item 2: a compensation function that itself
                        // throws must not corrupt state or leave the workflow stuck RUNNING.
                        // Persist the same endedAt/result bookkeeping every other terminal path
                        // gets, mark the step SAFE_STOPPED, emit an audit event naming the failure,
                        // and propagate SafeStopException so runFrom's existing catch transitions
                        // the whole workflow to SAFE_STOPPED — the same mechanism every other
                        // safe-stop cause already uses.
                        step.setEndedAt(Instant.now());
                        step.setResult(result != null ? result : Map.of());
                        step.setStatus(StepStatus.SAFE_STOPPED);
                        step.setSafeStopReason(SafeStopReason.COMPENSATION_FAILED);
                        stepRepository.save(step);
                        recordEvent(workflowId, "orchestrator", "workflow-engine", "compensation_failed:" + stage.name(),
                                stage.name(), "FAILED", compensationFailure.getMessage());
                        throw new SafeStopException(
                                "compensation itself failed for stage " + stage.name() + ": " + compensationFailure.getMessage(),
                                SafeStopReason.COMPENSATION_FAILED);
                    }
                }
                step.setEndedAt(Instant.now());
                step.setResult(result != null ? result : Map.of());
                if (compensated) {
                    step.setStatus(StepStatus.COMPENSATED);
                    stepRepository.save(step);
                    // actorType "orchestrator": the engine deciding to run a compensation on the
                    // stage's behalf, not the stage's own agent-attributed action.
                    recordEvent(workflowId, "orchestrator", "workflow-engine", "compensation_ran:" + stage.name(),
                            stage.name(), "OK", null);
                    return; // handled: this batch and the workflow continue normally
                }
                step.setStatus(StepStatus.SAFE_STOPPED);
                step.setSafeStopReason(failureReason);
                stepRepository.save(step);
                // Independent-review finding (N4): the original exception's own detail (type +
                // message) was previously discarded entirely for UNEXPECTED_FAILURE — the one
                // reason code whose whole purpose is "a genuine bug happened here" carried zero
                // diagnostic payload anywhere. Folding it into THIS SafeStopException's own
                // message (rather than a separate recordEvent call here) means it reaches the
                // audit trail through the SAME single event runFrom's catch already emits for
                // every safe-stop cause ("safe_stop_triggered", reason = e.getMessage()) — no new
                // event, no risk of double-recording or mislabeling an ordinary retry exhaustion.
                String causeDetail = failureCause != null ? " (" + failureCause + ")" : "";
                String message = "stage " + stage.name() + " did not succeed and no compensation was"
                        + " registered (or compensation was not applicable): " + failureReason + causeDetail;
                throw new SafeStopException(message, failureReason);
            }

            // Task 28: policy enforcement runs right after the handler returns, before the step is
            // marked SUCCEEDED — a mandatory FAIL must be able to preempt success, not follow it.
            // Skipped entirely when no PolicyEngine is configured (the shared production bean
            // today).
            if (policyEngine != null) {
                // 3rd independent-review finding (Section 17 follow-up review, D-NEW-2): evaluating
                // against the live, shared ctx.artifacts() let a MANDATORY policy for THIS stage
                // pass on evidence a DIFFERENT, concurrently-running sibling in the SAME batch had
                // written — including a sibling that goes on to fail moments later — with no
                // dependency relationship between them at all (that's WHY they're in the same
                // batch). inputsSnapshot is this batch's own pre-batch baseline (everything already
                // trustworthy from fully-completed earlier batches); merging in only THIS stage's
                // own attributed writes (ctx.writtenBy) is the same trust boundary the safe-stop
                // merge above uses, applied here too so a sibling's in-flight, not-yet-validated
                // writes can never leak into this stage's own policy check.
                Map<String, Object> evaluationArtifacts = new HashMap<>(inputsSnapshot);
                evaluationArtifacts.putAll(ctx.writtenBy(stage.name()));
                List<PolicyCheckResult> policyResults = policyEngine.evaluate(stage.name(), evaluationArtifacts);
                boolean mandatoryFail = false;
                for (PolicyCheckResult pr : policyResults) {
                    PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
                    evaluation.setWorkflowId(workflowId);
                    evaluation.setStageName(stage.name());
                    evaluation.setPolicyId(pr.policyId());
                    evaluation.setPolicyVersion(policyEngine.version());
                    evaluation.setOutcome(pr.outcome());
                    evaluation.setReason(pr.reason());
                    policyEvaluationRepository.save(evaluation);
                    // A mandatory FAIL blocks unless an approved, unexpired exception covers this
                    // policy — see hasApprovedUnexpiredException below. Codex REQUEST AMENDMENTS
                    // item 3: EXCEPTION_REQUESTED blocks the same way FAIL does — it is still not
                    // a PASS, just a differently-labeled failure the audit trail can distinguish
                    // as "the stage explicitly flagged this, awaiting a decision" rather than a
                    // silent gap. An approved exception excuses either outcome identically.
                    if (pr.mandatory()
                            && (pr.outcome() == PolicyOutcome.FAIL || pr.outcome() == PolicyOutcome.EXCEPTION_REQUESTED)
                            && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                        mandatoryFail = true;
                    }
                }
                if (mandatoryFail) {
                    step.setStatus(StepStatus.SAFE_STOPPED);
                    step.setSafeStopReason(SafeStopReason.POLICY_BLOCKED);
                    step.setEndedAt(Instant.now());
                    step.setResult(result != null ? result : Map.of());
                    // 4th independent-review finding (Section 17 follow-up review, Finding B):
                    // persist the EXACT evaluationArtifacts view that produced this block, so
                    // reevaluatePolicyBoundary can re-run the SAME check later against the SAME
                    // evidence — never the live WorkflowContext, which by resume time may have a
                    // trustworthy SIBLING's evidence merged in for legitimate forward propagation
                    // to other, dependent stages. That evidence is real and correctly trusted for
                    // THOSE stages; it was never produced by THIS stage and must not clear THIS
                    // stage's own boundary.
                    step.setPolicyEvidenceSnapshot(evaluationArtifacts);
                    stepRepository.save(step);
                    throw new SafeStopException("mandatory policy failure at stage " + stage.name(),
                            SafeStopReason.POLICY_BLOCKED);
                }
            }

            step.setStatus(StepStatus.SUCCEEDED);
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            stepRepository.save(step);
            recordEvent(workflowId, "agent", stage.name(), "stage_succeeded:" + stage.name(), stage.name(), "OK", null);
        } catch (SafeStopException e) {
            throw e; // already fully persisted by the specific path above that threw it
        } catch (RuntimeException e) {
            // N3: anything else unanticipated here (a buggy PolicyEngine, a save() constraint
            // violation, recordEvent itself failing) must not strand the step/workflow RUNNING
            // either — same fail-closed treatment, no compensation attempt (see comment above).
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            step.setStatus(StepStatus.SAFE_STOPPED);
            step.setSafeStopReason(SafeStopReason.UNEXPECTED_FAILURE);
            stepRepository.save(step);
            // Folded into the SafeStopException message rather than a separate recordEvent call
            // here (consistent with the !succeeded path above): runFrom's catch already emits one
            // "safe_stop_triggered" event per safe-stop, with this message as its reason — no new
            // event type, and no risk of a second recordEvent call itself throwing right after the
            // one that may have caused this catch to fire in the first place.
            String message = "unexpected failure after stage " + stage.name() + "'s handler completed: " + e;
            throw new SafeStopException(message, SafeStopReason.UNEXPECTED_FAILURE);
        }
    }

    // Task 29: a mandatory FAIL is excused only by an exception that is BOTH recorded for this
    // exact (workflowId, policyId) pair AND not yet expired — a granted-but-expired exception
    // never silently resurrects (REQ-013-adjacent: approval is never inferred from something that
    // has lapsed). When no PolicyExceptionJpaRepository is configured (any caller still using the
    // Task 28-era 5/7-arg constructors), this stays the original always-false placeholder.
    private boolean hasApprovedUnexpiredException(String workflowId, String policyId) {
        if (policyExceptionRepository == null) return false;
        return policyExceptionRepository.findByWorkflowIdAndPolicyId(workflowId, policyId).stream()
                .anyMatch(exc -> exc.getExpiry().isAfter(Instant.now()));
    }

    // Task 29: records a human-approved, time-bounded deviation from a mandatory policy. Does NOT
    // itself resume a safe-stopped workflow — callers combine this with retrySafeStopped(...) (or
    // grant it ahead of time, before the stage that would otherwise fail ever runs).
    public void grantPolicyException(String workflowId, String policyId, String reason, String scope,
                                      String approvingAuthority, String compensatingControl, Instant expiry) {
        // Codex REQUEST AMENDMENTS item 4: validate before touching any state — same discipline
        // approve() already applies to its own inputs. Every field here maps directly to a
        // data-model.md-approved NOT NULL column (see PolicyExceptionEntity); this is the
        // application-level enforcement that guarantees this engine's own insert path can never
        // violate what the schema already requires.
        if (workflowId == null || !instanceRepository.existsById(workflowId)) {
            throw new InvalidPolicyExceptionRequestException("unknown workflow: " + workflowId);
        }
        if (policyId == null || policyId.isBlank()) {
            throw new InvalidPolicyExceptionRequestException("policyId must be present and non-blank");
        }
        if (policyEngine != null && !policyEngine.hasPolicy(policyId)) {
            throw new InvalidPolicyExceptionRequestException("unknown policy: " + policyId);
        }
        requireNonBlank(reason, "reason");
        requireNonBlank(scope, "scope");
        requireNonBlank(approvingAuthority, "approvingAuthority");
        requireNonBlank(compensatingControl, "compensatingControl");
        if (expiry == null) {
            throw new InvalidPolicyExceptionRequestException("expiry must be present");
        }
        if (!expiry.isAfter(Instant.now())) {
            throw new InvalidPolicyExceptionRequestException(
                    "expiry must be in the future, got: " + expiry + " (an exception is never granted already-expired)");
        }

        PolicyExceptionEntity exception = new PolicyExceptionEntity();
        exception.setWorkflowId(workflowId);
        exception.setPolicyId(policyId);
        exception.setReason(reason);
        exception.setScope(scope);
        exception.setApprovingAuthority(approvingAuthority);
        exception.setCompensatingControl(compensatingControl);
        exception.setApprovedAt(Instant.now());
        exception.setExpiry(expiry);
        policyExceptionRepository.save(exception);
        recordEvent(workflowId, "human", approvingAuthority, "policy_exception_granted:" + policyId,
                policyId, "OK", reason);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPolicyExceptionRequestException(fieldName + " must be present and non-blank");
        }
    }

    // Task 29: resets every SAFE_STOPPED step in the workflow's CURRENT plan version back to
    // PENDING and resumes runFrom() — no policy-recomputation logic is duplicated here. The
    // stage's handler already ran once (during the safe-stopped attempt) and will simply run
    // again; hasApprovedUnexpiredException now sees the just-granted exception, so the same
    // mandatory FAIL no longer blocks. If a DIFFERENT mandatory policy still fails with no
    // exception, the stage safe-stops again — same as any first attempt.
    //
    // Review correction (independent review): this method requires the workflow to actually be
    // SAFE_STOPPED before doing anything. An earlier version of this comment claimed unconditionally
    // resetting instance.status to RUNNING was harmless even with zero SAFE_STOPPED steps — that
    // claim was false in two reachable states: a FAILED workflow (a rejected entry-gate approval
    // left some step FAILED, not PENDING) would be resurrected to RUNNING and runFrom would silently
    // skip that step's batch and execute downstream stages that depend on a stage that never
    // actually succeeded; a PENDING_APPROVAL workflow with an unrelated entry-gated step still
    // WAITING_APPROVAL would similarly resurrect to RUNNING and runFrom could execute downstream
    // batches before that pending approval decision is ever made — an approval-gate bypass. Only the
    // exit-gate-pending case was genuinely idempotent as originally claimed.
    public void retrySafeStopped(String workflowId) {
        // Codex REQUEST AMENDMENTS follow-up (Section 17 review): the SAFE_STOPPED -> RUNNING
        // transition below is now claimed atomically under the instance's existing pessimistic
        // row lock, exactly like claimStage does for a step's PENDING -> RUNNING transition.
        // Before this, two concurrent retrySafeStopped(workflowId) calls could both read the
        // instance as SAFE_STOPPED (unlocked), both read the SAME steps list (unlocked), and both
        // proceed to reset the same non-POLICY_BLOCKED step — caller A's reset could be claimed
        // and run to completion by claimStage/runStage, then caller B's stale in-memory
        // WorkflowStepEntity (read before A's write) could still get saved back with status
        // PENDING and every OTHER field frozen at its pre-A values, undoing A's already-committed
        // progress and re-arming the step for a second execution. Claiming the instance FIRST
        // means only one caller ever reaches the per-step work below; a second concurrent caller
        // blocks on the instance lock and, once it acquires it, observes RUNNING (not
        // SAFE_STOPPED) and is rejected exactly like any other "not actually safe-stopped" call —
        // no step-level race is reachable at all.
        if (!claimSafeStoppedForRetry(workflowId)) {
            WorkflowInstanceEntity current = instanceRepository.findById(workflowId).orElseThrow();
            throw new IllegalStateException(
                    "workflow " + workflowId + " is not SAFE_STOPPED (status: " + current.getStatus() + ")");
        }
        // Independent-review finding (Section 17 follow-up review, N1): the claim above commits
        // SAFE_STOPPED -> RUNNING before any of the per-step work below. If anything after that
        // commit throws — including registry.get(workflowId) returning null after a process
        // restart, since the stage registry is in-memory while the workflow instance is
        // DB-persisted, exactly as WorkflowEngine.approve() already guards against at its own
        // registration lookup — nothing previously restored SAFE_STOPPED, and retrySafeStopped's
        // own precondition then refuses every future call: an unrecoverable RUNNING workflow.
        // Wrapping everything after the claim and reverting on ANY RuntimeException, rather than
        // only on the "nothing progressed" case already handled below, closes this without
        // weakening the intentional behavior of any success path (applyInstanceUpdate is already
        // a no-op once the instance is legitimately terminal, e.g. if runFrom below already
        // completed it before some unrelated later failure).
        try {
            WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
            StageRegistration registration = registry.get(workflowId);
            if (registration == null) {
                throw new IllegalStateException("workflow " + workflowId + " has no in-memory stage "
                        + "registration (process restarted?) — cannot resolve policy boundaries or resets");
            }
            // Task 35 remediation: every currently-ACTIVE row, not just rows at the instance's
            // current plan_version — a survivor from an earlier replan keeps its own original
            // plan_version forever (it is never re-stamped), so filtering by the instance's current
            // plan_version alone would silently skip a SAFE_STOPPED survivor untouched by any later
            // replan. A SAFE_STOPPED step's own StageDefinition is never affected by a concurrent
            // replan() either way — REQ-018 forbids invalidating an already-started (SAFE_STOPPED
            // counts) stage, and a surviving stage's StageDefinition object is carried forward
            // unchanged by identity — so no additional synchronization is needed reading
            // `registration` here.
            List<WorkflowStepEntity> steps =
                    stepRepository.findByWorkflowIdAndStatusNotOrderBySequence(workflowId, StepStatus.INVALIDATED);

            // Codex REQUEST AMENDMENTS item 6: a POLICY_BLOCKED step's handler already ran to
            // completion once — re-running it would risk double-executing whatever real side
            // effect it had. Resolve ONLY the policy boundary against the persisted (post-handler)
            // evidence, never touching stage.handler() again. A RETRY_EXHAUSTED,
            // COMPENSATION_FAILED, or UNEXPECTED_FAILURE step never actually completed, so it
            // genuinely needs to run again — reset to PENDING as before, letting runFrom's normal
            // machinery re-execute it.
            boolean anyProgress = false;
            for (WorkflowStepEntity step : steps) {
                if (step.getStatus() != StepStatus.SAFE_STOPPED) continue;
                if (step.getSafeStopReason() == SafeStopReason.POLICY_BLOCKED) {
                    StageDefinition stage = registration.stages().stream()
                            .filter(s -> s.name().equals(step.getName())).findFirst().orElseThrow();
                    if (reevaluatePolicyBoundary(workflowId, step, stage)) {
                        anyProgress = true;
                    }
                } else if (resetSafeStoppedStepToPending(workflowId, step.getName())) {
                    anyProgress = true;
                }
            }
            if (!anyProgress) {
                // Nothing actually changed — every SAFE_STOPPED step is still policy-blocked with
                // no (newly) approved exception. Reassert SAFE_STOPPED (this call's own claim
                // above already moved the instance to RUNNING) rather than leaving it resurrected
                // to RUNNING for no reason and letting runFrom silently march past a batch that
                // never actually cleared (mandatoryFail returning without throwing would otherwise
                // be invisible to runFrom's batch loop, which only reacts to a thrown
                // SafeStopException).
                applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.SAFE_STOPPED));
                return;
            }
            runFrom(workflowId);
        } catch (RuntimeException e) {
            applyInstanceUpdate(workflowId, i -> i.setStatus(WorkflowStatus.SAFE_STOPPED));
            throw e;
        }
    }

    // Atomically transitions the workflow instance SAFE_STOPPED -> RUNNING under its existing
    // pessimistic row lock — the same lock/helper shape as applyInstanceUpdate, specialized to a
    // compare-and-set so only ONE concurrent retrySafeStopped(workflowId) caller ever proceeds
    // past this point.
    private boolean claimSafeStoppedForRetry(String workflowId) {
        Boolean claimed = transactionTemplate.execute(status -> {
            WorkflowInstanceEntity fresh = instanceRepository.findWithLockById(workflowId).orElseThrow();
            if (fresh.getStatus() != WorkflowStatus.SAFE_STOPPED) {
                return Boolean.FALSE;
            }
            fresh.setStatus(WorkflowStatus.RUNNING);
            instanceRepository.save(fresh);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(claimed);
    }

    // Atomically transitions a SAFE_STOPPED (non-POLICY_BLOCKED) step to PENDING under the step's
    // existing pessimistic row lock — the same shape as claimStage, applied to the opposite
    // transition. A fresh, locked read-check-write instead of retrySafeStopped's caller reusing
    // its own earlier unlocked list read is what actually closes the stale-object-save race
    // described above; retrySafeStopped's own instance-level claim already ensures only one
    // caller reaches this per-workflow, but locking each step individually too costs nothing and
    // keeps this helper correct standing alone, matching claimStage's own defense-in-depth.
    private boolean resetSafeStoppedStepToPending(String workflowId, String stepName) {
        Boolean reset = transactionTemplate.execute(status -> {
            WorkflowStepEntity step = stepRepository
                    .findWithLockByWorkflowIdAndNameAndStatusNot(workflowId, stepName, StepStatus.INVALIDATED)
                    .orElseThrow();
            if (step.getStatus() != StepStatus.SAFE_STOPPED || step.getSafeStopReason() == SafeStopReason.POLICY_BLOCKED) {
                return Boolean.FALSE;
            }
            // Independent-review finding (Section 17 follow-up review, N6): a step reset back to
            // PENDING for a genuine re-run previously kept endedAt/result/retryCount from the
            // FAILED attempt — a re-claimed step could sit RUNNING with endedAt earlier than its
            // new startedAt, and a stale result/retryCount left over from the attempt that never
            // actually succeeded. claimStage sets a fresh startedAt/inputsSnapshot on claim, and
            // runStage always overwrites result/endedAt on its own next terminal transition — but
            // clearing them here too means nothing observing the step BETWEEN reset and claim (or
            // a retry-count assertion right after reset) sees leftover state from the run that
            // just failed.
            step.setStatus(StepStatus.PENDING);
            step.setSafeStopReason(null);
            step.setEndedAt(null);
            step.setResult(null);
            step.setRetryCount(0);
            stepRepository.save(step);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(reset);
    }

    // Codex REQUEST AMENDMENTS item 6: re-runs ONLY the policy check for a step whose handler
    // already succeeded, against the SAME context it succeeded against — never calls
    // stage.handler() again. Returns whether the boundary actually cleared (the step transitioned
    // to SUCCEEDED); if still blocked, the step is left exactly as it was (SAFE_STOPPED,
    // POLICY_BLOCKED) and this returns false.
    private boolean reevaluatePolicyBoundary(String workflowId, WorkflowStepEntity step, StageDefinition stage) {
        // 4th independent-review finding (Section 17 follow-up review, Finding B): evaluate
        // against THIS step's own persisted policyEvidenceSnapshot — captured at the moment it
        // originally blocked, from exactly the same batchStartArtifacts + own-writes view runStage
        // itself used — never the live WorkflowContext's artifacts(). By resume time that live map
        // may legitimately include a TRUSTWORTHY SIBLING's evidence, merged in on purpose so LATER,
        // DEPENDENT stages can see it — but that evidence was never produced by THIS stage, and
        // must not be able to clear THIS stage's own boundary with no new evidence and no granted
        // exception.
        Map<String, Object> evaluationArtifacts =
                step.getPolicyEvidenceSnapshot() != null ? step.getPolicyEvidenceSnapshot() : Map.of();
        List<PolicyCheckResult> policyResults = policyEngine.evaluate(stage.name(), evaluationArtifacts);
        boolean mandatoryFail = false;
        for (PolicyCheckResult pr : policyResults) {
            PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
            evaluation.setWorkflowId(workflowId);
            evaluation.setStageName(stage.name());
            evaluation.setPolicyId(pr.policyId());
            evaluation.setPolicyVersion(policyEngine.version());
            evaluation.setOutcome(pr.outcome());
            evaluation.setReason(pr.reason());
            policyEvaluationRepository.save(evaluation);
            if (pr.mandatory()
                    && (pr.outcome() == PolicyOutcome.FAIL || pr.outcome() == PolicyOutcome.EXCEPTION_REQUESTED)
                    && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                mandatoryFail = true;
            }
        }
        if (mandatoryFail) {
            return false; // still blocked; step stays exactly as it was
        }
        step.setStatus(StepStatus.SUCCEEDED);
        step.setSafeStopReason(null);
        stepRepository.save(step);
        recordEvent(workflowId, "orchestrator", "workflow-engine", "policy_boundary_cleared:" + stage.name(),
                stage.name(), "OK", null);
        return true;
    }

    private void recordEvent(String workflowId, String actorType, String actorId, String action,
                              String affectedArtifact, String result, String reason) {
        recordEvent(workflowId, actorType, actorId, action, affectedArtifact, result, reason, null);
    }

    // Task 35: payload-bearing overload — every prior 7-arg call site is unchanged, delegating here
    // with payload=null. Used so far only by replan()'s "replanned" event ({fromVersion, toVersion,
    // fromStage}), matching WorkflowEventEntity.payload's existing (already-present, previously
    // always-null) column.
    private void recordEvent(String workflowId, String actorType, String actorId, String action,
                              String affectedArtifact, String result, String reason, Map<String, Object> payload) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(workflowId);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setAction(action);
        event.setAffectedArtifact(affectedArtifact);
        event.setResult(result);
        event.setReason(reason);
        event.setPayload(payload);
        eventRepository.save(event);
    }

    // Task 35 remediation: null, not an exception, when no active row exists — a CONCURRENT
    // replan() can invalidate a stage between the moment a runFrom() call captured its (now-stale)
    // in-memory stage list and the moment it checks that stage's persisted status here. Used
    // exactly ONCE per batch member, when runFrom first resolves each BatchMember's row — every
    // LATER re-check within that same batch's processing goes through activeStepById, bound to the
    // specific row this resolution returned, never a second by-name lookup (Task 35 remediation,
    // round 6 — a second by-name lookup is exactly what let a stale run resolve a same-named
    // REPLACEMENT row a concurrent replan() had since inserted). Discovered via a genuine
    // concurrent replan/approve race (not a hypothetical): an earlier version of this method threw
    // a bare NoSuchElementException here, which — unlike every exception runStage's own try/catch
    // fail-closes — propagated straight out of runFrom uncaught. Contrast with requireStepById
    // below, used only where the row is known to still exist by invariant.
    private WorkflowStepEntity activeStepOrNull(String workflowId, String name) {
        return stepRepository.findByWorkflowIdAndNameAndStatusNot(workflowId, name, StepStatus.INVALIDATED)
                .orElse(null);
    }

    // Task 35 remediation (Codex round 7): the generation-aware resolution runFrom's BatchMember
    // construction actually needs. Proving "a live row exists for this name" is not the same as
    // proving "this row is the SAME logical stage `stage` was written for" — a replan() that
    // invalidates the old row and inserts a fresh one under the identical name produces a row whose
    // plan_version has moved on; `registration`'s own tag for that name (planVersionByName) has
    // not, if this call's registration snapshot predates that replan's publish. Returns null (never
    // the mismatched row) whenever the two disagree, or when the registration has no tag at all for
    // this name (should not happen for any stage actually in `registration.stages()`, but treated
    // as "not resolvable" rather than an NPE if it ever did).
    private WorkflowStepEntity resolveIfCurrentGeneration(String workflowId, StageDefinition stage,
                                                            StageRegistration registration) {
        WorkflowStepEntity row = activeStepOrNull(workflowId, stage.name());
        if (row == null) {
            return null;
        }
        Integer expectedPlanVersion = registration.planVersionByName().get(stage.name());
        if (expectedPlanVersion == null || row.getPlanVersion() != expectedPlanVersion) {
            return null;
        }
        return row;
    }

    // Task 35 remediation (Codex round 6): a FRESH read of a row a caller has ALREADY resolved and
    // is bound to by its durable id — never a re-resolution by name. Used everywhere runFrom needs
    // this batch's own snapshot's up-to-date status (post-dispatch, mid-gate-scan, etc.) without
    // risking that name now resolving to a DIFFERENT row a concurrent replan() introduced in the
    // meantime. Unlike activeStepOrNull, this deliberately does NOT filter by INVALIDATED status —
    // a row this call is already bound to never needs "is there a live row for this name" answered
    // again, only "what is THIS row's current state."
    private WorkflowStepEntity activeStepById(long stepId) {
        return stepRepository.findById(stepId).orElse(null);
    }

    // Strict variant for the one call site (runStage, on the row claimStage just transitioned to
    // RUNNING moments earlier in the same call) where the row is guaranteed to still exist.
    private WorkflowStepEntity requireStepById(long stepId) {
        WorkflowStepEntity step = activeStepById(stepId);
        if (step == null) {
            throw new IllegalStateException(
                    "step id " + stepId + " does not exist — this should be unreachable (invariant violation)");
        }
        return step;
    }

    // Extracted from runFrom (Codex REQUEST AMENDMENTS item 6): reused by retrySafeStopped/
    // reevaluatePolicyBoundary so a POLICY_BLOCKED resume reconstructs the exact same context
    // shape runFrom itself uses, from the same persisted contextJson.
    @SuppressWarnings("unchecked")
    private WorkflowContext buildContext(WorkflowInstanceEntity instance) {
        List<Map<String, Object>> priorDecisions =
                (List<Map<String, Object>>) instance.getContextJson().getOrDefault("decisions", new ArrayList<Map<String,Object>>());
        Map<String, Object> priorArtifacts =
                (Map<String, Object>) instance.getContextJson().getOrDefault("artifacts", new HashMap<String,Object>());
        return new WorkflowContext(instance.getCorrelationId(), priorDecisions, priorArtifacts);
    }

    // Task 35 remediation (Codex round 7): `planVersionByName` tags every StageDefinition in
    // `stages` with the plan_version its CURRENT persisted row is expected to carry. This is what
    // makes a stale (pre-replan) registration DETECTABLY stale for a specific name, not just "old" —
    // resolving a row by (workflowId, name, status != INVALIDATED) alone proves "this is the ONLY
    // currently-live row for this name," never "this row is the SAME logical stage the caller's own
    // StageDefinition object was written for." A replan() that invalidates the old row and inserts a
    // fresh one under the SAME name produces a row whose plan_version has advanced; a stale
    // registration's own tag for that name has not. Comparing the two at resolution time (see
    // runFrom's BatchMember construction, and approve()'s own resolution) is what lets a stale run
    // recognize "the row this name currently points to is not the stage I have a StageDefinition
    // for" and refuse to touch it, rather than claiming and executing the wrong handler against the
    // right row. Survivors keep their EXISTING tag forever (never re-stamped, matching their row's
    // own immutable plan_version); only replan()'s own newStages get a fresh tag.
    private record StageRegistration(List<StageDefinition> stages, DependencyGraph graph,
                                      Map<String, Integer> planVersionByName) { }

    // Task 35 remediation (Codex round 6): binds a StageDefinition to the SPECIFIC persisted row
    // resolved for it at ONE moment in time (the top of a single batch's processing in runFrom) —
    // `row` is null if no active (non-invalidated) row existed for this name at that moment. Every
    // later step of processing this batch member (entry-gate check, dispatch/claim/execute,
    // post-dispatch gate scans) uses `row`'s id, via activeStepById/requireStepById, never a fresh
    // by-name lookup — see WorkflowStepJpaRepository.findWithLockById's own comment for why a
    // second by-name resolution mid-batch is exactly what let a stale StageDefinition's handler run
    // against a same-named row a concurrent replan() had already swapped in.
    private record BatchMember(StageDefinition stage, WorkflowStepEntity row) { }
}
