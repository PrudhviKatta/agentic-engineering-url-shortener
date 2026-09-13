# ADR-004: Orchestration Model

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D4 already recorded as "Proposed for ADR at Task 7." Reviewed and accepted by the
human candidate (Prudhvidhar Reddy Katta) on 2026-09-13.

## Context

Constitution Principle II requires the orchestration model be more than sequential chaining:
explicit dependency graph, sequential and parallel paths, synchronization, branching, dynamic
replanning, and interruption/resumption. `spec.md` Section 3.1 defines a 12-stage baseline graph
with conditional branches (Stage 3→4), fan-out (Stage 6→{7,9}, Stage 7→{8,10}), and a
synchronization join (Stage 11 from Stages 8, 9, 10). REQ-011 through REQ-018 collectively require:
explicit dependency-graph instantiation with defined failure behavior per stage (REQ-011), full
state inspectability from persisted state alone (REQ-012), human-owned Approval Gates that block or
release a dependent branch (REQ-013), bounded classified retry (REQ-014), rollback/compensation
(REQ-015), safe-stop (REQ-016), resumption from persisted state without re-execution or skipping
(REQ-017), and dynamic replanning that preserves prior history and governance (REQ-018). This ADR
decides what concrete mechanism executes that graph and enforces those properties. ADR-005
(companion decision) separately evaluates whether that mechanism should be a third-party
orchestration product instead of a custom engine.

## Decision Drivers

- Must express explicit dependencies, sequential/parallel paths, synchronization, conditional
  branching, entry/exit gates, retry/rollback/compensation/safe-stop, resumption, and dynamic
  replanning (`spec.md` Section 3) as first-class, testable mechanisms — not emergent behavior of
  a general-purpose scheduler.
- Must not add infrastructure disproportionate to a 2–3 day local prototype (this is the shared
  driver with ADR-005).
- Must be fully unit-testable in isolation from the web layer — the engine's correctness must not
  require booting the full Spring context for every test.
- Must be auditable by a reviewer reading Java source end-to-end, not a vendor's opaque runtime
  (constitution Principle IX, audit-grade evidence).
- Must persist state after every batch (not only at terminal states) so `GET /workflows/{id}`
  (REQ-012) always reflects real, currently-durable state.

## Options Considered

### Option A: Custom in-process `WorkflowEngine` over an explicit `DependencyGraph` (selected)
- **Approach:** a plain, constructor-injected Java class (not a Spring-managed singleton, so it is
  testable without booting the full Spring context) executes a `DependencyGraph` of
  `StageDefinition` records — `(name, dependsOn, handler, entryApproval, exitApproval, maxRetries,
  timeoutSeconds, parallelGroup)`. `DependencyGraph.topologicalBatches()` resolves the graph into
  ordered batches of concurrently runnable stages; `validate()` rejects cycles (`CycleException`)
  and dangling dependencies (`UnknownDependencyException`) before any persistence write happens.
  State (`WorkflowInstanceEntity`/`WorkflowStepEntity`/`WorkflowEventEntity`) persists via Spring
  Data JPA after every batch.
- **Advantages:** satisfies every explicit orchestration characteristic `spec.md` Section 3 names
  without adopting distributed-systems infrastructure; every transition is Java source a reviewer
  can read end-to-end; constructor injection of repositories keeps the engine testable in plain
  JUnit without a Spring context; no vendor black box between "what the code says happens" and
  "what actually happened."
- **Disadvantages:** the engine's own correctness (cycle detection, topological batching, gate
  semantics, replan reachability) becomes this project's own thing to get right and test — there
  is no vendor test suite to lean on.
- **Risks:** hand-rolled engines are a common source of subtle state-machine bugs (e.g., a replan
  racing an in-flight approval).
- **Implementation impact:** requires building `DependencyGraph`, `WorkflowEngine`,
  `StageDefinition`, and the associated persistence entities from scratch (Tasks 20–41 per the
  runbook), plus a dedicated test suite covering execution, approval, lineage, reliability,
  safe-stop, and replanning.
- **Assessment implications:** this is the architectural core of the differentiator constitution
  Principle II names — its correctness is the single most load-bearing engineering claim in this
  project.

### Option B: An embedded workflow library (e.g., a lightweight Java state-machine/workflow library)
- **Approach:** adopt a pre-built embedded library to express the stage graph rather than writing
  `DependencyGraph`/`WorkflowEngine` from scratch.
- **Advantages:** less code to write and test for the core scheduling/topological-batching
  mechanics.
- **Disadvantages:** most embedded Java workflow libraries do not natively express this project's
  specific combination of independent `entryApproval`/`exitApproval` gate semantics, JPA-persisted
  step state per batch, and reachability-based dynamic replanning (Section 3.3/3.4) — adapting one
  to fit would likely require as much custom glue code as writing the engine directly, while adding
  a dependency a reviewer must also learn to audit (a library's internal state model is not
  necessarily as transparent as this project's own Java source).
- **Risks:** a library not designed for this exact gate/replan/compensation combination could hide
  subtle semantic mismatches (e.g., its own notion of "resume" might not match REQ-017's negative
  criteria) until late in implementation.
- **Implementation impact:** unclear/variable — depends entirely on how well a given library's
  primitives map onto this project's specific requirements, which is itself a research cost this
  timebox cannot fully absorb.
- **Assessment implications:** does not clearly reduce implementation risk relative to Option A,
  and reduces auditability (Principle IX) since correctness partly depends on a third party's code.

### Option C: An external orchestration product (Temporal, Camunda, Activiti)
- **Approach:** run the stage graph on a dedicated orchestration server/runtime.
- **Advantages:** production-grade durability guarantees (e.g., crash-consistent replay) beyond
  what a custom engine provides.
- **Disadvantages:** requires a separate server process and its own persistence/operational
  learning curve — infrastructure disproportionate to a local, reviewer-run 2–3 day prototype; the
  product's own runtime would itself need independent auditing to satisfy Principle IX, on top of
  this project's own code.
- **Risks/Rejected reasoning:** fully evaluated and rejected in its own dedicated ADR-005, since
  the rejection rationale is substantial enough to warrant a standalone record rather than a
  sub-bullet here.
- **Implementation impact:** highest of the three options; out of proportion to this assessment's
  scope.
- **Assessment implications:** see ADR-005.

## Decision

**A custom, in-process `WorkflowEngine`** — a plain, constructor-injected Java class over an
explicit `DependencyGraph` of `StageDefinition`s, with independent `entryApproval`/`exitApproval`
flags per stage, state persisted via Spring Data JPA after every batch.

## Rationale

Option A is the only option that satisfies every explicit orchestration characteristic `spec.md`
Section 3 and constitution Principle II name — explicit dependency graph, parallel fan-out with
synchronization, entry/exit gates, retry/rollback/compensation/safe-stop, resumption, and dynamic
replanning — as first-class, independently testable mechanisms, without introducing either an
unproven-fit third-party library (Option B) or disproportionate infrastructure (Option C, see
ADR-005). Building it in-house means every transition a reviewer needs to audit is Java source they
can read directly, which is itself evidence toward Principle IX (audit-grade evidence) and
Principle X (traceability): there is no vendor black box between "what the code says happens" and
"what actually happened." Constructor-injecting repositories rather than making `WorkflowEngine` a
Spring-managed singleton keeps the engine's correctness testable in plain JUnit, isolated from the
web layer, which is essential given how much of this project's evidence burden falls on the
engine's own test suite.

**`entryApproval` vs. `exitApproval` (the key design distinction, not a naming detail):**
`entryApproval=true` blocks the stage's handler from running at all until a human approves —
correct for a destructive/irreversible action that must not even be attempted without sign-off.
`exitApproval=true` lets the handler run and produce output first, then blocks only that stage's
*dependents* until the produced output (a design, a detected ambiguity, a release-readiness report)
is reviewed — correct whenever there is something concrete to review, not merely a proposal to
approve. Approving an `exitApproval` gate does not re-run the stage; it releases already-produced
output to its dependents. A stage may set neither, either, or (rarely) both flags. This distinction
is the concrete mechanism that resolves what `spec.md` names only abstractly as "Approval Gates"
(REQ-013) into two operationally distinct behaviors — the choice of which gate type applies to
which stage in Section 3.2's node table is itself part of this design, not an incidental detail.
ADR-006 (human approval mechanism) covers how a human actually records the decision against either
gate type; this ADR covers what the gate blocks and when.

## Consequences

**Positive:**
- Every orchestration behavior required by Principle II and `spec.md` Section 3 is a directly
  testable, directly readable Java mechanism.
- `DependencyGraph.topologicalBatches()` is the literal, singular mechanism realizing both
  "sequential paths" and "parallel paths" — there is exactly one code path deciding batch
  membership, not separate sequential/parallel handling that could drift out of sync.
- State persisted after every batch (not only at terminal states) makes REQ-012 (inspection) and
  REQ-017 (resumption) hold against the same underlying data, rather than requiring a separate
  "resumption snapshot" mechanism.

**Negative:**
- No vendor test suite to lean on; the engine's own correctness (cycle detection, topological
  batching, gate semantics, replan reachability) is entirely this project's burden.
- More implementation and test surface than adopting an existing library or product, if either had
  fit cleanly (they did not — see Options B/C).

**Operational:**
- No additional infrastructure beyond the JVM process and H2 (ADR-002) — the engine runs in the
  same process as the web layer, with no separate orchestration server to deploy, monitor, or
  operate.
- `WorkflowEngine.runFrom` is the single place that derives workflow-level `WorkflowStatus` from
  aggregate step-level `StepStatus` — a deliberate design constraint (`plan.md` Section 3.1) so the
  two never drift out of sync, which is itself an operational simplification (one code path to
  reason about during an incident, not two).

**Testing:**
- `DependencyGraphTest`, `WorkflowEngineExecutionTest`, `WorkflowEngineApprovalTest`,
  `WorkflowEngineContextLineageTest`, `WorkflowEngineReliabilityIntegrationTest`,
  `WorkflowEngineSafeStopTest`, `WorkflowEngineReplanningTest` (Phase 4/6/7 test suite,
  `plan.md` Section 10) collectively carry the correctness burden Option A's "no vendor test suite"
  negative consequence creates.

**Governance:**
- This ADR is proposed pending the human candidate's explicit accept/reject at Task 7. Because this
  is the architectural core of the project's own differentiator (Principle II), reversing it later
  would be substantial (see Reversibility) — the human candidate's review of this specific ADR
  carries more downstream weight than most of the other seven.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Hand-rolled engines are a common source of subtle state-machine bugs (e.g., a replan racing an in-flight approval) | Fixed ordering rule: `replan()` is always called before `approve()` when a gate's resolution is "replace what comes next," because `approve()` synchronously resumes execution — the replacement stages must already be registered first (`plan.md` Section 3.4); dedicated tests for exactly this race (Task 35/41) |
| `WorkflowEngine`'s correctness has no external test suite to fall back on | Phase 4/6/7's dedicated test suite (above) exists specifically to carry that burden; Task 34's convergence check specifically proves there is no third, divergent safe-stop path |
| Workflow-level and step-level status could drift out of sync if derived in more than one place | Design constraint: exactly one place (`WorkflowEngine.runFrom`) decides workflow-level status from step-level status (`plan.md` Section 3.1) |

## Reversibility

**Low.** This is the architectural core of the differentiator constitution Principle II names;
replacing it later (with a library or an external product) would be a substantial rewrite of the
orchestration layer, not a swap or a configuration change. This is precisely why it is flagged for
explicit ADR review at Task 7 rather than left as an unexamined default carried over from the
runbook's scheduling table.

## Traceability

- Requirements: REQ-011 (workflow creation), REQ-012 (workflow inspection), REQ-013 (approvals and
  rejection), REQ-014 (retries and timeouts), REQ-015 (rollback or compensation), REQ-016
  (safe-stop), REQ-017 (workflow resumption), REQ-018 (dynamic replanning).
- Specification sections: `spec.md` Section 3 (Primary Capability: Agentic SDLC Orchestration),
  Section 3.1 (baseline stage graph), Section 3.2 (behavioral characteristics → requirement
  mapping).
- Plan sections: `plan.md` Section 3 (Agentic Orchestration Architecture, 3.1–3.4), Section 12
  (Technology Decisions, ADR-004 row), Section 13 (Traceability, REQ-011/012/018/024 rows citing
  ADR-004), `research.md` D4.
- Expected task identifiers: Tasks 20–41 (runbook orchestration-engine implementation and test
  tasks).

## Validation

Validated through the dedicated Phase 4/6/7 test suite: `DependencyGraphTest` (cycle/dangling-
dependency detection, topological batching), `WorkflowEngineExecutionTest` (sequential/parallel
execution), `WorkflowEngineApprovalTest` (entry/exit gate semantics), `WorkflowEngineContextLineage
Test` (decision/artifact preservation), `WorkflowEngineReliabilityIntegrationTest` (retry/rollback/
compensation), `WorkflowEngineSafeStopTest` (convergent safe-stop path), and
`WorkflowEngineReplanningTest` (reachability-based invalidation and the replan-before-approve
ordering rule). Each test targets a specific negative criterion named in REQ-011–REQ-018 above, not
only the positive/happy path.
