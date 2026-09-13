# Reliability and Recovery Quality Checklist

**Category 9 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-014..017, REQ-022, REQ-028, `plan.md` Section 6, ADR-008, ADR-009, CLR-12/13/14.
**Constitution principles:** VIII (Reliability and Recovery) — the primary principle this file
enforces.
**Status:** All items UNVERIFIED/PENDING — `RetryExecutor`, `TimeoutExecutor`,
`CompensationRegistry`, and safe-stop handling are not yet implemented.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] REL-CHK-001 [ANTI-PATTERN: unlimited retries]** — Verify every retryable operation
  has a declared, finite `maxRetries` (per `StageDefinition`) and that `RetryExecutor` rethrows
  immediately once that bound is exhausted, with no code path capable of retrying indefinitely.
  **Fail if**: a fault-injection test shows an operation attempting more than its declared
  `maxRetries`, or any stage/operation has no declared maximum at all. **Cites**: Principle VIII
  ("always bounded by an explicit maximum attempt count"), REQ-014 (negative criterion).

- **[MANDATORY] REL-CHK-002** — Verify failures are classified transient vs. permanent at the point
  they are handled (`TransientStageException` vs. `PermanentStageException`), and that only
  transient failures are retried. Verify validation failures and policy failures are always
  classified permanent and never retried, regardless of remaining retry budget (CLR-12). **Fail
  if**: a validation error or a policy `FAIL` is retried even once. **Cites**: REQ-014, CLR-12,
  Principle VIII.

- **[MANDATORY] REL-CHK-003** — Verify retries follow a declared backoff (`backoffMillis`) and a
  declared per-attempt timeout (`TimeoutExecutor.withTimeout`), readable in advance by an operator —
  i.e., the retry/backoff/timeout parameters are declared configuration, not implicit/hardcoded
  values a reviewer must reverse-engineer from behavior. **Fail if**: the backoff or timeout value
  for a given stage cannot be determined by reading `StageDefinition`/configuration without running
  the code. **Cites**: Principle VIII, REQ-014.

- **[MANDATORY] REL-CHK-004** — Verify retryable operations are idempotent — a retried operation
  does not double-apply its effect (e.g., does not create the same short link twice, does not
  double-increment an analytics counter beyond the single logical event). **Fail if**: a retry
  fault-injection test shows a duplicated side effect from a single logical operation being retried.
  **Cites**: Principle VIII ("Operations that may be retried are designed to be idempotent"),
  REQ-014, REQ-008.

- **[MANDATORY] REL-CHK-005 [ANTI-PATTERN: invalid rollback claims]** — Verify recovery never
  attempts to "roll back" an effect that has already been externally observed. Specifically: once a
  redirect has been served to a caller, no code path attempts a rollback against that delivery —
  only compensation (CLR-14) is used. **Fail if**: any recovery-path code or test description
  characterizes an already-served redirect, or any other already-externally-observed effect, as
  "rolled back." **Cites**: Principle VIII ("Rollback vs. compensation"), CLR-14, REQ-015 (negative
  criterion).

- **[MANDATORY] REL-CHK-006 [ANTI-PATTERN: missing compensation]** — Verify `CompensationRegistry`
  provides a registered compensating action for every stage handler that produces an
  externally-observed effect (the redirect-serving action being the canonical case), and that a
  stage failing after such an effect always reaches either rollback or compensation — never neither.
  Verify the documented fail-safe behavior holds: a stage handler with an externally-visible effect
  but no registered compensating action falls through to safe-stop (not silent no-op) when
  compensation would otherwise be needed. **Fail if**: a fault-injection test shows a stage with a
  committed external effect and a subsequent failure leaving neither a rollback nor a compensation
  record, and also not a safe-stop. **Cites**: Principle VIII ("never leaves a partially applied
  effect with neither rollback nor compensation"), REQ-015 (negative criterion), ADR-009.

- **[MANDATORY] REL-CHK-007** — Verify the rollback/compensation distinction is tested with at least
  one rollback-eligible scenario (an in-transaction, not-yet-committed effect) and one
  compensation-eligible scenario (an already-externally-observed effect), per REQ-015's Evidence
  pointer and `CompensationRegistryTest`/`WorkflowEngineReliabilityIntegrationTest`. **Fail if**:
  only one of the two scenario types is tested. **Cites**: REQ-015 (Evidence), ADR-009.

- **[MANDATORY] REL-CHK-008 [ANTI-PATTERN: missing safe-stop]** — Verify a workflow enters
  safe-stop exactly when (a) a mandatory policy check resolves `FAIL` without an approved exception,
  or (b) bounded retries are exhausted with no applicable fallback path (CLR-13's closed
  enumeration) — and that no third, undocumented path to a stuck/inconsistent state exists. **Fail
  if**: a fault-injection test produces a workflow that is neither progressing nor in a recorded
  `SAFE_STOPPED` state (e.g., it silently hangs). **Cites**: REQ-016, CLR-13, Principle VIII
  ("Deterministic terminal outcomes").

- **[MANDATORY] REL-CHK-009** — Verify safe-stop preserves already-completed work and its evidence
  intact — entering safe-stop never discards or corrupts upstream-completed stage outputs. **Fail
  if**: a safe-stop test shows a previously `SUCCEEDED` step's persisted result altered or removed
  after a sibling/downstream stage safe-stops. **Cites**: REQ-016 (negative criterion), Principle
  VIII.

- **[MANDATORY] REL-CHK-010 [ANTI-PATTERN: missing resume behavior]** — Verify an interrupted run
  (simulated process restart, safe-stop, or delayed approval) resumes from persisted state without
  re-executing already-`SUCCEEDED` steps and without silently skipping incomplete/pending ones, and
  that the resumption itself is recorded as an auditable event. **Fail if**: an interrupt-and-resume
  test shows a completed step re-executed (a non-idempotent double-execution), or a pending step
  dropped with no trace, or no resumption event recorded. **Cites**: REQ-017 (negative criterion),
  REQ-028, Principle VIII ("Controlled resumption").

- **[MANDATORY] REL-CHK-011** — Verify a partial-failure batch (some stages `SUCCEEDED`, one
  `SAFE_STOPPED`) leaves the succeeded stages' results persisted and undiscarded, with only the
  affected stage(s)/their dependents halted. **Fail if**: a partial-batch-failure test shows a
  succeeded sibling stage's result also rolled back or discarded. **Cites**: plan.md Section 6
  ("Partial failure" row), REQ-016.

- **[MANDATORY] REL-CHK-012** — Verify every stage and every orchestration run resolves to one of a
  small, explicitly enumerated set of terminal outcomes (`COMPLETED`, `FAILED`, `SAFE_STOPPED`) —
  never an "it just stopped and nobody knows why" state. **Fail if**: any run in the test evidence
  ends in a status not in this enumerated set, or ends with no terminal status at all. **Cites**:
  Principle VIII ("Deterministic terminal outcomes"), REQ-022.

- **[MANDATORY] REL-CHK-013** — Verify 100% of fault-injection test scenarios resolve to an
  enumerated terminal outcome (success / permanent failure / safe-stop), matching REQ-022's proposed
  validation target, measured in the demonstration test environment and explicitly labeled as such
  (not a production SLA claim). **Fail if**: any fault-injection scenario in the test suite is left
  unresolved or unlabeled. **Cites**: REQ-022 (proposed validation target), Principle IX.

- **[MANDATORY] REL-CHK-014** — Verify fallback (a real, distinct mechanism per constitution
  Principle VIII — corrected 2026-09-13; `retry-then-safe-stop` was an earlier mislabeling of a
  different, already-real mechanism, not fallback) is distinct from retry and from
  rollback/compensation in both design and test evidence: a fallback is only attempted after retry
  exhaustion, only when a `StageCatalog`-registered fallback handler exists for that stage
  (`docs/engineering-runbook.md` Task 50), produces `SUCCEEDED_WITH_FALLBACK` with
  `fallback_started`/`fallback_succeeded`/`fallback_failed` audit events, and states explicitly what
  capability/guarantee is reduced relative to the primary path (`ReleaseReadinessStage` discloses
  this). **Fail
  if**: a fallback outcome is presented as equivalent to a primary-path success without disclosing
  the reduction. **Cites**: Principle VIII ("Fallback behavior"), REQ-014.

- **[MANDATORY] REL-CHK-015** — Verify REQ-009's persistence-failure handling never reports a
  creation or redirect as successful when the underlying write/read did not durably succeed, under
  a persistence-outage failure-injection test. **Fail if**: a simulated H2 outage during creation
  results in an HTTP success response with no durable row written. **Cites**: REQ-009 (negative
  criterion), Principle VIII.

- **[OPTIONAL] REL-CHK-016** — Verify a recovery-time objective for resuming a demonstration-scale
  interrupted run is proposed and (if approved) recorded, per REQ-028's proposed validation target,
  rather than left entirely unspecified. **Fail if**: no RTO figure or explicit "not yet proposed"
  disclosure exists. **Cites**: REQ-028 (proposed validation target).
