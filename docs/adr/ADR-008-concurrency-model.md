# ADR-008: Concurrency Model for Parallel Stage Fan-Out

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D8 already recorded as "Proposed for ADR at Task 7." Reviewed and accepted by the
human candidate (Prudhvidhar Reddy Katta) on 2026-09-13.

## Context

Constitution Principle II and `spec.md` Section 3 require the orchestration engine support genuine
parallel paths and synchronization, not just sequential chaining — concretely realized in Section
3.1's stage graph as Stage 7 (Implementation) fanning out to Stages 8 (Testing) and 10 (Security),
which run concurrently and are joined at Stage 11 (Release-readiness). ADR-004 established that
`DependencyGraph.topologicalBatches()` groups same-batch stages for concurrent execution; this ADR
decides the concrete Java concurrency mechanism that actually runs those same-batch stages in
parallel and joins them before the next batch proceeds.

## Decision Drivers

- Stage handlers in this prototype are short-lived and I/O-light (no real external network calls;
  they operate against the in-process H2 database and in-memory context) — the concurrency
  mechanism does not need to optimize for high-latency I/O multiplexing.
- Must prove genuine parallel fan-out and synchronization (spec.md Section 3's explicit
  requirement) — a test must be able to observe stages actually overlapping in time, not merely
  appear to via sequential execution disguised as "parallel."
- Must not force a reactive rewrite of the whole codebase — ADR-001 already selected Spring Web MVC
  (blocking/servlet style) over WebFlux for the web layer; introducing reactive programming only for
  the orchestration engine's internals would fragment the codebase's programming model without a
  corresponding requirement driving it.
- Must integrate cleanly with `WorkflowEngine.runFrom`'s per-batch execution model (ADR-004) and
  with each stage's own timeout/retry wrapping (`TimeoutExecutor`/`RetryExecutor`).

## Options Considered

### Option A: `java.util.concurrent.ExecutorService` (fixed thread pool) + `Future.get()` per stage (selected)
- **Approach:** `Executors.newFixedThreadPool(...)` submits one `Callable`/`Future` per stage
  within a `DependencyGraph.topologicalBatches()` batch; the batch's overseeing code calls
  `Future.get()` on every submitted stage to join the batch before advancing to the next.
- **Advantages:** minimal additional complexity — a well-understood, standard-library mechanism
  requiring no new dependency; proves real fan-out/synchronization directly (a test can assert
  overlapping execution windows via timestamps or a shared countdown latch); `Future.get()`
  naturally propagates a stage handler's exception synchronously to the batch-joining code, which
  integrates cleanly with the existing retry/safe-stop exception model (`TransientStageException`/
  `PermanentStageException`) rather than requiring a separate reactive error-handling model.
- **Disadvantages:** thread-pool sizing is a fixed constant, not adaptive to load.
- **Risks:** H2 file-mode locking under concurrent writes from parallel stages is the specific
  interaction Phase 6's reliability tests target.
- **Implementation impact:** one `ExecutorService` field on `WorkflowEngine` (or a request-scoped
  equivalent), a `submit`/`Future.get()` loop per batch — small, self-contained, and unit-testable
  without any web-layer dependency.
- **Assessment implications:** sufficient to demonstrate the required orchestration characteristic
  (parallel fan-out + synchronization) without adopting a programming model the rest of the codebase
  does not use.

### Option B: Reactive/WebFlux end-to-end
- **Approach:** rebuild the orchestration engine (and potentially the web layer) around Project
  Reactor's `Mono`/`Flux` composition for stage execution and joining.
- **Advantages:** reactive composition operators (`Flux.merge`, `Mono.zip`) natively express
  fan-out/join patterns.
- **Disadvantages:** ADR-001 already selected Spring Web MVC (blocking/servlet style) specifically
  for parity with the original synchronous design and to avoid reactive-programming complexity
  across the whole codebase; introducing reactive types only inside the orchestration engine would
  create a two-programming-model codebase (blocking web layer, reactive orchestration core) with an
  awkward boundary between them, and would complicate the straightforward exception-based
  retry/safe-stop model already designed around synchronous calls.
- **Risks:** reactive debugging (stack traces across asynchronous boundaries) is a well-known
  usability cost that would work against this project's own auditability goals (Principle IX) for
  a team/reviewer not already fluent in Reactor.
- **Implementation impact:** highest of the three options — effectively a rewrite of the
  orchestration engine's execution model, directly re-litigating ADR-001's already-settled
  blocking-vs-reactive decision for the web layer.
- **Assessment implications:** disproportionate to a prototype whose stage handlers are already
  short-lived and I/O-light — the workload profile reactive programming is designed to help with
  (many concurrent slow I/O operations) does not describe this system.

### Option C: Virtual threads (Java 21 preview feature at time of writing)
- **Approach:** run each stage handler on its own virtual thread (`Thread.ofVirtual()`) instead of
  a platform-thread pool.
- **Advantages:** would remove the fixed-pool-sizing concern (Option A's negative consequence) and
  scale trivially to a large number of concurrent stages without platform-thread overhead.
- **Disadvantages:** virtual threads were a preview feature in the Java 21 timeframe relevant to
  this project's tooling choice (ADR-001 selected Java 21 LTS specifically for stability); depending
  on a preview feature would require an explicit `--enable-preview` build-time flag, undermining the
  "stable, well-documented target" rationale ADR-001 gave for choosing an LTS release in the first
  place. At this prototype's actual concurrency scale (a handful of parallel stages per batch), the
  scalability benefit virtual threads provide has no material use case to demonstrate.
- **Risks:** a preview-feature dependency is a build-stability risk disproportionate to a benefit
  this project's actual workload does not need.
- **Implementation impact:** would require enabling preview features project-wide for a benefit
  confined to one internal component.
- **Assessment implications:** would introduce build-tooling risk for a capability advantage this
  prototype's scale does not exercise.

## Decision

**Option A: `java.util.concurrent.ExecutorService` with a fixed thread pool
(`Executors.newFixedThreadPool(...)`), one `Future` per stage within a
`DependencyGraph.topologicalBatches()` batch, joined via `Future.get()` before the next batch
begins.**

## Rationale

Option A is the only option that proves genuine parallel fan-out and synchronization
(`spec.md` Section 3's explicit requirement) using a standard, stable, already-available
JDK 21 mechanism, without either forcing a reactive rewrite that would contradict ADR-001's
already-settled blocking-web-layer decision (Option B) or depending on a preview-feature capability
disproportionate to this prototype's actual concurrency scale (Option C). `Future.get()`'s
synchronous exception propagation integrates directly with the existing `TransientStageException`/
`PermanentStageException` classification and `RetryExecutor`/`TimeoutExecutor` wrapping (`plan.md`
Section 6) — a stage's failure surfaces to the batch-joining code exactly the same way a
sequentially-executed stage's failure would, keeping one exception-handling model for the whole
engine regardless of whether a given stage ran sequentially or in a parallel batch.

## Consequences

**Positive:**
- Proves real parallel fan-out and synchronization directly testable via `Future.get()` join
  timing or a shared coordination primitive (e.g., a `CountDownLatch` in the test harness) —
  satisfying `spec.md` Section 3's explicit non-sequential-chaining requirement with a mechanism a
  reviewer can read and verify directly.
- One exception-handling model (checked/unchecked exception propagation) applies uniformly whether
  a stage ran sequentially or as part of a parallel batch — no separate reactive error-handling
  vocabulary to reason about.
- No new dependency; `java.util.concurrent` is part of the JDK already required by ADR-001.

**Negative:**
- Thread-pool sizing is a fixed constant, not adaptive — acceptable at demonstration concurrency
  levels (REQ-023's own scope framing), but would need revisiting for real production-scale
  concurrent workflow execution.

**Operational:**
- The fixed thread pool's lifecycle (creation, shutdown) must be managed alongside
  `WorkflowEngine`'s own lifecycle — since `WorkflowEngine` is a plain, constructor-injected class
  (ADR-004), the pool is either owned by `WorkflowEngine` itself or supplied to it, keeping the
  engine testable without depending on Spring's own thread-pool bean lifecycle.
- H2 file-mode locking (ADR-002) under concurrent writes from parallel stages is the specific
  interaction Phase 6's reliability tests target; a finding there is a reliability-test finding
  against ADR-002/ADR-008's interaction, not a re-litigation of either decision individually.

**Testing:**
- The runbook's Task 22 parallel-execution test asserts stages within the same batch actually
  execute concurrently (not merely appear parallel due to fast sequential execution) via a
  synchronization primitive in the test harness.
- Phase 6's concurrency-focused reliability tests exercise the H2-locking interaction under
  concurrent parallel-stage writes.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13, per the same
  governance standing as the other seven ADRs — see `docs/governance/human-gates-log.md`.
  **Corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): this bullet
  previously read "is proposed pending... accept/reject at Task 7" after Task 7 had already run and
  this ADR's own Status header above had already moved to Accepted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Fixed thread-pool size does not adapt to load | Acceptable at demonstration concurrency levels (REQ-023); documented as a production-scale consideration, not silently assumed to hold at any scale |
| H2 file-mode locking behavior under concurrent parallel-stage writes could prove inadequate | Phase 6's reliability tests target exactly this interaction; a finding there is a reliability-test finding for the plan's own reliability-testing tasks to surface (`research.md` D8), not something this ADR can pre-certify |
| A stage handler that blocks longer than expected could starve the fixed pool, delaying sibling stages in the same batch | Each stage handler is wrapped by `TimeoutExecutor.withTimeout` (ADR-004/`plan.md` Section 6) independently of the thread-pool mechanism, bounding how long any single stage can occupy a pool thread |

## Reversibility

**High.** The concurrency mechanism is an internal implementation detail entirely behind
`WorkflowEngine.runFrom` — not part of any external contract (REST API, persisted entity shape, or
`StageDefinition` record). Swapping it later (e.g., to virtual threads once they graduate from
preview, or to a different executor configuration) would not require changing any stage handler,
any test's assertions about *what* happened (only possibly *how fast*), or any consumer of
`WorkflowEngine`'s public methods.

## Traceability

- Requirements: REQ-011 (workflow creation — parallel/sequential path support), REQ-022
  (reliability), REQ-023 (scalability — the "demonstration concurrency levels" scope framing this
  ADR relies on).
- Specification sections: `spec.md` Section 3 (orchestration behavioral characteristics — "Sequential
  and parallel activity support," "Synchronization of dependent activities"), Section 7 (REQ-023).
- Plan sections: `plan.md` Section 3.1 (topological batching), Section 3.3 (fan-out/join examples:
  Stage 6→{7,9}, Stage 7→{8,10}, Stage 11 join), Section 12 (Technology Decisions, ADR-008 row),
  `research.md` D8.
- Related ADR: ADR-004 (Orchestration Model — defines the batches this ADR's mechanism executes
  concurrently), ADR-002 (Persistence — the H2 locking interaction under concurrent writes).
- Expected task identifiers: Task 22 (parallel-execution test), Phase 6 (concurrency-focused
  reliability tests).

## Validation

The runbook's Task 22 test validates genuine concurrent execution of same-batch stages (e.g., via a
shared `CountDownLatch` or timestamp-overlap assertion, proving stages actually ran simultaneously
rather than merely completing quickly in sequence). Phase 6's concurrency-focused reliability tests
validate the interaction between this concurrency model and H2's file-mode locking behavior
(ADR-002) under concurrent parallel-stage writes, confirming REQ-008's per-write transactional
isolation guarantee holds even when writes originate from concurrently executing stage handlers, not
only from concurrent external API requests.
