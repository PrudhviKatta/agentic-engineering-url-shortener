# ADR-006: Human Approval Mechanism

## Status

**Proposed** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D6 already recorded as "Proposed for ADR at Task 7." Not yet reviewed by the human
candidate. Per the project's guardrail (guide Section 29), this ADR may not be self-accepted by the
controlling agent.

## Context

Constitution Principle III requires that humans own requirements, architecture, security-sensitive
decisions, exceptions, destructive changes, release readiness, and final quality, and that approval
is never inferred from silence. REQ-013 makes this a functional requirement: the system MUST
support human-owned Approval Gates that block or release a dependent branch based on a recorded
human decision, MUST never treat an unanswered gate as approved (no default-to-yes on timeout), and
MUST never allow an agent to resolve a human-owned gate on its own authority. ADR-004 already
establishes *what* a gate blocks — the `entryApproval`/`exitApproval` distinction on a
`StageDefinition` — but not *how* a human actually records the decision against either gate type.
This ADR is that mechanism decision. It is more subtle than "there is an approval endpoint": the
mechanism must correctly interact with both gate types, with `WorkflowEngine.replan`, and with the
possibility that a batch containing an unresolved gate must remain halted rather than silently
proceeding on partial batch success.

## Decision Drivers

- No UI is in scope for this assessment (`spec.md` Section 9.6 exclusion) — the mechanism must not
  require building one.
- The decision must be an explicit, auditable, non-inferred act (REQ-013) — never a default, never
  a timeout-implied approval.
- The mechanism must correctly implement both gate semantics ADR-004 defines:
  `entryApproval=true` (blocks the handler from running at all) and `exitApproval=true` (lets the
  handler run and produce output first, then blocks only that stage's dependents from consuming
  it).
- The mechanism must interact correctly with dynamic replanning (REQ-018): `WorkflowEngine.replan`
  must be callable, and completed, before `approve()` is called when a gate's resolution is
  "replace what comes next," because `approve()` synchronously resumes execution.
- A batch containing one or more stages still blocked on an unresolved gate must not be treated as
  "the batch succeeded, proceed to the next batch" — the workflow must correctly re-halt on the
  still-blocked portion rather than silently advancing past it.

## Options Considered

### Option A: Synchronous REST endpoint (selected)
- **Approach:** `POST /workflows/{id}/approvals/{step}` accepts an explicit `approved`/`rejected`
  decision, an `actor`, and a `reason`. A human (or, for this prototype, the reviewer standing in
  for a named role such as "release owner") calls it directly. The workflow blocks in
  `WAITING_APPROVAL` (step-level, per `plan.md` Section 3.1's `StepStatus` vocabulary) /
  `PENDING_APPROVAL` (workflow-level `WorkflowStatus`) state until this endpoint is called.
- **Advantages:** simplest mechanism that satisfies REQ-013's explicit/non-silent requirement
  without building UI the assignment explicitly excludes from scope; a single, auditable HTTP call
  is trivially recorded as an `approvals` row and a `workflow_events` entry; testable with
  `TestRestTemplate`/`@WebMvcTest` without any additional infrastructure.
- **Disadvantages:** a real deployment would need real authentication on this endpoint specifically
  — it is the single most security-sensitive endpoint in the system, since it is the only path that
  releases a blocked branch (this is the cross-reference ADR-010 formalizes).
- **Risks:** without authentication (ADR-010), `actor`/`decidedBy` is self-asserted, not verified.
- **Implementation impact:** one controller endpoint, one `WorkflowEngine.approve(...)` method, one
  `approvals` table row per decision; must correctly resume only the previously blocked branch, not
  re-run the entire workflow.
- **Assessment implications:** demonstrates the required property (explicit, auditable, human-owned
  decision) with the minimum mechanism proportionate to a no-UI prototype.

### Option B: Message-queue-based asynchronous approval channel
- **Approach:** publish a "gate pending" message; a human (or a downstream consumer) publishes a
  decision message back, which a listener applies to the workflow.
- **Advantages:** decouples the approval transport from the web layer; would scale to a real
  multi-consumer approval workflow.
- **Disadvantages:** requires standing up a message broker (Kafka/RabbitMQ/etc.) — infrastructure
  disproportionate to a local 2–3 day prototype, and inconsistent with this project's
  no-external-infrastructure stance applied elsewhere (ADR-002, ADR-005). Also adds an asynchronous
  delivery/ordering concern (could a decision message arrive and be applied out of order relative to
  a concurrent replan?) that Option A's synchronous call avoids by construction.
- **Risks:** message ordering/delivery-guarantee bugs would directly threaten the fixed
  "replan-before-approve" ordering rule ADR-004 depends on.
- **Implementation impact:** highest of the three options — a new infrastructure dependency plus a
  consumer/listener component.
- **Assessment implications:** would demonstrate messaging-integration skill, not the actual
  property REQ-013 asks for (an explicit, human-owned, auditable decision).

### Option C: Built reviewer UI/dashboard
- **Approach:** a web UI listing pending gates with approve/reject controls.
- **Advantages:** most natural human experience for a real reviewer.
- **Disadvantages:** explicitly out of scope per `spec.md` Section 9.6; would consume timebox on
  frontend work with no corresponding requirement credit, since REQ-013 is satisfied by the
  underlying mechanism (an explicit, recorded decision), not by the existence of a UI.
- **Risks:** scope creep into an explicitly excluded deliverable.
- **Implementation impact:** highest of the three in terms of net-new surface area for no
  requirement benefit given the explicit exclusion.
- **Assessment implications:** would misallocate timebox against the assignment's own stated scope
  boundary.

## Decision

**Option A: a synchronous `POST /workflows/{id}/approvals/{step}` REST endpoint.** The workflow
blocks in `WAITING_APPROVAL`/`PENDING_APPROVAL` state until this endpoint is called with an
explicit `approved`/`rejected` decision, an `actor`, and a `reason`; there is no timeout that
resolves to implicit approval.

## Rationale

Option A is the only option that satisfies REQ-013's explicit/non-silent requirement without either
building UI the assignment excludes (Option C) or introducing infrastructure disproportionate to a
local prototype and inconsistent with this project's own repeatedly-applied "no external
infrastructure" standard (Option B, ADR-002, ADR-005). A synchronous call is also the simplest
mechanism to reason about correctly alongside `WorkflowEngine.replan` — there is exactly one thread
of control per decision, with no asynchronous delivery/ordering question layered on top of the
already load-bearing "replan before approve" ordering rule (ADR-004, `plan.md` Section 3.4).

**Gate-semantics interaction (the genuinely subtle part of this decision, not merely "there's an
approval endpoint"):** the same `approve()`/`reject()` contract serves both `entryApproval` and
`exitApproval` gates, but its effect differs by gate type:

- Against an **`entryApproval`** gate (reserved for a stage whose handler itself performs a
  destructive/irreversible action — in this domain's current stage set, this reduces to nothing
  beyond the redirect-serving action itself, per `plan.md` Section 5's Human-in-the-Loop table,
  which is why CLR-14's compensation-only rule exists for that action instead of an entry gate on
  it), approval releases the handler to run for the first time. Rejection means the handler never
  runs at all — there is no "partial output" to reason about, because none was produced.
- Against an **`exitApproval`** gate (used on `design` (Stage 6) and `release_readiness` (Stage
  11), per `plan.md` Section 5), the handler has already run and produced real output (a design
  artifact, a release-readiness report) before the gate exists. Approval does **not** re-run the
  stage — it releases the already-produced output to the stage's *dependents*. Rejection leaves
  that output persisted (for audit, REQ-019) but permanently blocks the dependent branch for that
  plan version; the only path forward is a human-authorized replan (`plan.md` Section 3.4) or
  accepting the workflow's terminal `FAILED`/`SAFE_STOPPED` state.

This distinction matters operationally, not just semantically: an `approve()` call against an
`exitApproval` gate must not trigger `WorkflowEngine` to re-invoke the stage handler (which would
violate REQ-017's "never re-executes a stage whose effect already durably completed" negative
criterion by a different route — approval-triggered re-execution, not resumption-triggered
re-execution) — it must instead unblock scheduling of the dependent stages that were waiting on
that already-persisted output.

**The "blocked-batch" re-halt guard (also load-bearing, not incidental):** because
`DependencyGraph.topologicalBatches()` groups same-batch stages for concurrent execution
(ADR-004/ADR-008), a batch can contain one stage that completes normally alongside another that
enters `WAITING_APPROVAL`. `WorkflowEngine.runFrom` must not treat that batch as "succeeded, advance
to the next batch" — any stage still gated must keep its dependents unscheduled, and the workflow-
level status must reflect `PENDING_APPROVAL` for as long as any step in the just-processed batch is
`WAITING_APPROVAL`, even if sibling stages in the same batch succeeded. This is the same "no default
-to-yes, no silent advance" property REQ-013's negative criteria require, applied at the batch level
rather than only the single-stage level: a batch is only fully "done" when every one of its stages
is in a terminal-for-this-batch state (`SUCCEEDED`, or blocked awaiting the gate), never when only
some of them are. Concretely, this means the engine re-checks gate state on every `runFrom` call
rather than caching a "batch complete" flag from a prior pass — a workflow that was left
`PENDING_APPROVAL` and is later invoked again (e.g., via resumption, REQ-017) must re-derive that it
is still blocked from the persisted step statuses, not from a stale in-memory assumption that the
batch had already advanced. Task 23/35/41 in the runbook test this interaction explicitly (a
sibling stage completing does not spuriously unblock a still-gated stage's dependents, and a replan
targeting a gated stage's downstream correctly reaches only the not-yet-started portion).

## Consequences

**Positive:**
- A single, uniform HTTP contract (`POST /workflows/{id}/approvals/{step}`) serves every gate in
  the system regardless of gate type, keeping the API surface small and the audit trail uniform (one
  `approvals` row shape for every kind of decision).
- No UI development needed, matching the assignment's explicit scope boundary.
- The synchronous call model keeps "replan before approve" a simple, single-threaded ordering rule
  rather than a distributed-ordering problem.

**Negative:**
- A real deployment would need real authentication on this specific endpoint (see ADR-010) — it is
  the single highest-severity unauthenticated endpoint in the system, since it is the only path that
  releases a blocked branch.
- `actor`/`decidedBy` is self-asserted in the request body, not verified against any identity
  system, in this iteration.

**Operational:**
- No additional infrastructure — the endpoint runs in the same process as every other controller.
- A human (or the reviewer, standing in for a named role) must know to call this endpoint; there is
  no notification/paging mechanism in scope for this iteration (a disclosed simplification, not a
  hidden gap — REQ-012's inspection endpoint is how a human discovers a pending gate).

**Testing:**
- `WorkflowEngineApprovalTest` covers both gate types' distinct release semantics (entry blocks the
  handler; exit blocks only dependents) and the negative criteria (no timeout-implied approval, no
  agent self-approval).
- `WorkflowControllerContractTest` covers the REST contract itself (approve, reject, and the
  still-pending/unanswered state).
- The blocked-batch re-halt guard is exercised by Task 23/35/41's tests, specifically proving a
  sibling stage's success does not spuriously release a still-gated stage's dependents, and that
  resumption re-derives blocked status from persisted state rather than a cached flag.

**Governance:**
- This ADR is proposed pending the human candidate's explicit accept/reject at Task 7. It directly
  operationalizes constitution Principle III's "approval is never inferred from silence" and REQ-
  013's negative criteria — a rejection or amendment of this ADR would need to preserve those
  properties under whatever alternative mechanism is proposed instead.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Unauthenticated approval endpoint means `actor`/`decidedBy` is self-asserted, not verified | Explicitly named as the top authentication-backlog priority if ADR-010 is ever revisited; disclosed in `data-model.md`'s `approvals`/`policy_exceptions` table notes, not hidden |
| A sibling stage's success in the same batch could be mistaken for "batch complete," spuriously releasing a still-gated stage's dependents | `WorkflowEngine.runFrom` re-derives per-stage gate state from persisted `workflow_steps.status` on every invocation rather than caching a batch-complete flag; Task 23/35/41 tests this explicitly |
| A replan racing an in-flight approval could apply in the wrong order | Fixed ordering rule: `replan()` always called before `approve()` when the resolution is "replace what comes next" (ADR-004, `plan.md` Section 3.4); Task 35/41 tests this race directly |
| `approve()` called against an `exitApproval` gate could be mistakenly implemented to re-run the stage handler | Explicitly documented in this ADR's Rationale that approval releases already-produced output to dependents, never re-invokes the handler; covered by `WorkflowEngineApprovalTest` |

## Reversibility

**High for the transport, low for the underlying semantics.** Swapping the transport (REST → queue
→ UI) does not change `WorkflowEngine.approve()`'s contract or the entry/exit gate-release semantics
this ADR documents — those live in ADR-004 and would need to be preserved under any transport. The
transport choice itself (Option A) could be replaced with Option B or C later at moderate cost,
since the controller layer is a thin adapter over `WorkflowEngine.approve()`.

## Traceability

- Requirements: REQ-013 (approvals and rejection), REQ-017 (workflow resumption — the blocked-batch
  re-halt guard's interaction with resumption), REQ-018 (dynamic replanning — the replan-before-
  approve ordering rule), REQ-020 (release-readiness — the `exitApproval` gate on Stage 11).
- Specification sections: `spec.md` Section 6.2 (REQ-013), Section 9.6 (no-UI exclusion).
- Plan sections: `plan.md` Section 3.1 (gate semantics, `entryApproval`/`exitApproval`), Section 3.3
  (fan-out/join), Section 3.4 (replan ordering rule), Section 5 (Human-in-the-Loop Controls table),
  Section 12 (Technology Decisions, ADR-006 row), `research.md` D6.
- Related ADR: ADR-004 (Orchestration Model — defines the gate types this ADR's mechanism resolves).
- Expected task identifiers: Tasks 23, 35, 41 (runbook tests for gate-release correctness, the
  blocked-batch guard, and the replan/approve race).

## Validation

`WorkflowEngineApprovalTest` validates both gate types release correctly (entry gate blocks handler
invocation entirely until approved; exit gate releases already-produced output to dependents without
re-invoking the handler) and validates the negative criteria (no timeout-implied approval, rejection
permanently blocks the branch for that plan version). `WorkflowControllerContractTest` validates the
REST contract end-to-end via `TestRestTemplate`. Tasks 23/35/41 specifically validate the
blocked-batch re-halt guard (a sibling stage's success does not spuriously release a still-gated
stage's dependents) and the replan-before-approve ordering rule under a simulated race.
