# Agentic Orchestration Quality Checklist

**Category 4 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** the `WorkflowEngine`/`DependencyGraph`/`StageDefinition` implementation (ADR-004),
`plan.md` Sections 3–4, and REQ-011..018.
**Constitution principles:** II (Explicit Agentic Orchestration) — the primary principle this file
exists to enforce — plus IX (Observability/Auditability) and X (Traceability) for lineage checks.
**Status:** Final review: **PASS WITH LIMITATION**. All mandatory runtime mechanics have named
executable evidence; generic persisted step metadata is narrower than the complete plan-level node
contract. See `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] ORCH-CHK-001 [ANTI-PATTERN: linear chaining disguised as orchestration]** — Verify
  the SDLC stage graph is represented as explicit, inspectable data (`DependencyGraph` of
  `StageDefinition` records with a `dependsOn` field per ADR-004), not as an implicit sequence baked
  into a driver method's call order or an agent prompt. **Fail if**: stage order is discoverable
  only by reading the source order of sequential method calls (e.g. `runRequirement();
  runDecomposition(); runDesign();...`) rather than from a graph structure a test can query
  independently of execution order. **Cites**: Principle II, REQ-011, ADR-004.

- **[MANDATORY] ORCH-CHK-002 [ANTI-PATTERN: missing dependency representation]** — Verify every
  stage declares its predecessor(s) explicitly (`StageDefinition.dependsOn`) and that
  `DependencyGraph.validate()` rejects a graph containing a stage with an undeclared or dangling
  dependency (`UnknownDependencyException`) before any persistence write happens. **Fail if**: a
  stage can execute without its declared predecessor(s) having reached a terminal state, or the
  engine silently ignores an unknown dependency reference instead of raising
  `UnknownDependencyException`. **Cites**: Principle II, REQ-011 (negative criterion), ADR-004.

- **[MANDATORY] ORCH-CHK-003** — Verify `DependencyGraph.validate()` detects and rejects a cyclic
  graph (`CycleException`) before the graph is ever scheduled. **Fail if**: a workflow with a cyclic
  stage dependency is accepted and either hangs, infinite-loops, or silently executes an arbitrary
  subset of stages. **Cites**: Principle II, ADR-004, REQ-011.

- **[MANDATORY] ORCH-CHK-004 [ANTI-PATTERN: missing synchronization]** — Verify Stage 11
  (Release-readiness) genuinely waits for the join of Stages 8, 9, and 10 (`spec.md` Section 3.1)
  before evaluating, and that a test demonstrates this join by observing actual concurrent execution
  overlap (e.g., via `Future.get()` timing or a `CountDownLatch`), not sequential execution that
  merely looks parallel because each stage individually finishes fast. **Fail if**: Stage 11 can
  begin before all three of Stages 8/9/10 report a terminal-for-this-batch status, or the
  "parallel" test cannot demonstrate actual time-overlap. **Cites**: Principle II, REQ-011, ADR-008.

- **[MANDATORY] ORCH-CHK-005** — Verify Stages 7 (Implementation) and 9 (Documentation) both depend
  only on Stage 6 (Design) and run as a true parallel fan-out — i.e., Documentation does not
  spuriously wait on Implementation's completion, per `spec.md` Section 3.1's explicit design.
  **Fail if**: `DependencyGraph.topologicalBatches()` places Stage 9 in a later batch than Stage 7
  when both share the same unmet-predecessor state. **Cites**: spec.md Section 3.1, ADR-004.

- **[MANDATORY] ORCH-CHK-006 [ANTI-PATTERN: missing persistent state]** — Verify
  `WorkflowInstanceEntity`/`WorkflowStepEntity`/`WorkflowEventEntity` state is persisted via Spring
  Data JPA after every batch (not only at terminal states), so `GET /workflows/{id}` (REQ-012)
  reflects real, currently-durable state at any point mid-run. **Fail if**: querying a workflow's
  state mid-run (between batches) returns stale or absent data that only becomes correct once the
  whole run terminates, or state is held only in an in-memory field of a request-scoped object.
  **Cites**: Principle II ("State persistence"), REQ-012, ADR-004.

- **[MANDATORY] ORCH-CHK-007** — Verify a killed/restarted orchestrator process can reconstruct a
  run's full state (which stages started/finished/failed/waiting, each stage's output, pending human
  decisions) from persisted storage alone, with no dependency on any single agent's transient
  context or conversation memory. **Fail if**: resuming a workflow after a simulated process
  restart requires re-reading a chat transcript or loses any already-recorded decision. **Cites**:
  Principle II ("State persistence"), REQ-017, REQ-028.

- **[MANDATORY] ORCH-CHK-008 [ANTI-PATTERN: missing decision lineage]** — Verify
  `WorkflowContext.recordDecision`/`context_json.decisions[]` captures, for every branch taken
  (e.g., Stage 3's clear-vs-ambiguous outcome, an approval gate's approve/reject), the actor, the
  decision, and the rationale — not merely the resulting state transition with no "why." **Fail
  if**: a workflow's persisted history shows a stage transitioned but no accompanying decision
  record explains why that branch was taken. **Cites**: Principle II ("decision lineage... is part
  of this persisted state"), Principle IX, REQ-012, REQ-019.

- **[MANDATORY] ORCH-CHK-009** — Verify every `StageDefinition` declares a failure behavior
  (`maxRetries`, `timeoutSeconds`, or an equivalent explicit policy) before it may be scheduled, and
  that `WorkflowEngine`/graph construction rejects instantiating a workflow containing a stage with
  no defined failure behavior. **Fail if**: a stage with an unset/undefined failure behavior is
  accepted into a running workflow instance. **Cites**: Principle II ("A stage without a defined
  failure behavior is an incomplete stage definition and may not be scheduled"), REQ-011.

- **[MANDATORY] ORCH-CHK-010 [ANTI-PATTERN: missing dynamic replanning]** — Verify
  `WorkflowEngine.replan()` exists and is exercised by at least one test (`WorkflowEngineReplanningTest`)
  that alters only not-yet-started stages, preserves the audit trail of already-executed stages
  unchanged (no deletion/rewrite of prior `workflow_events`/`workflow_steps` rows), and records the
  replan itself as a new auditable `workflow_events` row (`replanned` tag) with what changed, why,
  and under whose authority. **Fail if**: a replan test shows an already-`SUCCEEDED` step's row
  mutated or deleted, or no new `replanned` event is recorded. **Cites**: Principle II ("Dynamic
  replanning"), REQ-018.

- **[MANDATORY] ORCH-CHK-011** — Verify replanning uses `workflow_steps.status=INVALIDATED` (not
  deletion) for superseded steps, and bumps `workflow_instances.plan_version`, per `plan.md`
  Section 4's state-lineage table. **Fail if**: a replan removes a row instead of marking it
  `INVALIDATED`, breaking the "given any one link in the chain, a reviewer should be able to walk to
  the others" property (Principle X). **Cites**: plan.md Section 4, REQ-018, Principle X.

- **[MANDATORY] ORCH-CHK-012** — Verify the "replan-before-approve" ordering rule (ADR-004,
  `plan.md` Section 3.4) is enforced: when a gate's resolution is "replace what comes next,"
  `replan()` completes and registers the replacement stages before `approve()` is called (since
  `approve()` synchronously resumes execution). **Fail if**: a test can trigger `approve()` racing
  ahead of a not-yet-completed `replan()`, resuming execution against a graph that does not yet
  contain the intended replacement stages. **Cites**: ADR-004 (Risks and Mitigations), REQ-018,
  REQ-013.

- **[MANDATORY] ORCH-CHK-013** — Verify a batch containing one stage that completes normally and
  another still `WAITING_APPROVAL` is never treated as "batch succeeded, advance" — the
  workflow-level status must reflect `PENDING_APPROVAL` until every step in that batch reaches a
  terminal-for-this-batch state, and this is re-derived from persisted step statuses on every
  `runFrom` call (not cached). **Fail if**: a sibling stage's completion spuriously unblocks a
  still-gated stage's dependents, or a stale in-memory "batch complete" flag survives a resumption.
  **Cites**: ADR-006 ("blocked-batch re-halt guard"), REQ-013, REQ-017.

- **[MANDATORY] ORCH-CHK-014** — Verify exactly one code path (`WorkflowEngine.runFrom`, per
  ADR-004's Operational consequences) derives workflow-level `WorkflowStatus` from aggregate
  step-level `StepStatus` — i.e., no second, divergent status-computation path exists elsewhere in
  the codebase that could disagree with `runFrom`'s derivation. **Fail if**: a second method
  computes/overrides workflow status independently of `runFrom`. **Cites**: ADR-004, Principle II.

- **[MANDATORY] ORCH-CHK-015** — Verify every agent action recorded during a stage's execution is
  attributable to the specific stage it was taken under, is validated against that stage's declared
  exit criteria before being accepted, and is observable in persisted run state. **Fail if**: an
  audit trail shows an action with no attributable owning stage, or a stage's output is accepted as
  satisfying exit criteria without an explicit check having run. **Cites**: Principle II (closing
  paragraph), REQ-031; cross-reference `autonomy.md` CHK-AUTO-001.

- **[OPTIONAL] ORCH-CHK-016** — Verify the engine exposes a way to inspect the full instantiated
  graph (nodes, edges, parallel groups, gate flags) independent of any single run, so a reviewer can
  confirm the graph matches `spec.md` Section 3.1 without inferring it from execution traces alone.
  **Fail if**: the only way to see the graph's shape is to run a workflow and observe which stages
  executed. **Cites**: REQ-011, REQ-012, Principle IX.

- **[MANDATORY] ORCH-CHK-017** — Verify conditional branching (Stage 3 → 4, only when Stage 3's
  exit outcome is "ambiguous") is implemented as a real runtime branch evaluated from Stage 3's
  actual output — not a flag an agent sets by convention with no enforcement that Decomposition
  (Stage 5) actually cannot start until the branch resolves. **Fail if**: Stage 5 can begin while
  Stage 3 → 4's branch is unresolved. **Cites**: spec.md Section 3.1/4 (SCN-C), REQ-011, REQ-016.
