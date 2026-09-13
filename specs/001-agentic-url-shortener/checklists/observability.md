# Observability and Auditability Quality Checklist

**Category 10 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-019, REQ-025, REQ-026, `plan.md` Section 7 (Observability, MTTR mechanics),
`AuditRecorder`, `workflow_events`.
**Constitution principles:** IX (Observability and Auditability) — the primary principle this file
enforces.
**Status:** All items UNVERIFIED/PENDING — `MetricsService`, `AuditRecorder`, and correlation-ID
threading are not yet implemented.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] OBS-CHK-001** — Verify every orchestration run is assigned a `correlation_id` at
  the moment `submit()` is called, and that this identifier is immutable thereafter and attached to
  every `workflow_events` row's logical run. **Fail if**: any `workflow_events` row for a run lacks
  the run's `correlation_id`, or the identifier changes mid-run. **Cites**: Principle IX (first
  bullet), REQ-025.

- **[MANDATORY] OBS-CHK-002** — Verify 100% of audit-relevant events in a demonstration run carry
  the run/correlation identifier, matching REQ-025's proposed validation target, verified by a
  correlation-ID completeness check against captured run logs. **Fail if**: a completeness check
  finds any run-related log line or persisted artifact without the identifier attached. **Cites**:
  REQ-025 (proposed validation target and negative criterion).

- **[MANDATORY] OBS-CHK-003 [ANTI-PATTERN: incomplete audit evidence]** — Verify every audit record
  captures all six required fields: actor, action, timestamp, artifact/state affected, result, and
  reason. Verify a `workflow_events` row missing any of the six fields is rejected/impossible to
  persist, not merely discouraged by convention. **Fail if**: any audit-trail schema-conformance
  test finds a persisted event with a null/absent required field. **Cites**: Principle IX
  ("Audit-grade evidence" bullet), REQ-026 (negative criterion).

- **[MANDATORY] OBS-CHK-004** — Verify schema completeness is verified against 100% of events in a
  demonstration run exercising every event type (state transitions, decisions, approvals/
  rejections, retries, failures, replans), matching REQ-026's proposed validation target. **Fail
  if**: any event type produced by the engine is not covered by the schema-conformance test. **Cites**:
  REQ-026 (proposed validation target).

- **[MANDATORY] OBS-CHK-005** — Verify a named human reviewer, given only the persisted evidence
  trail (logs, `workflow_events`, `workflow_steps`, `approvals`, `policy_evaluations`) and no other
  context, can answer: what happened, in what order, who/what caused it, when it happened, and what
  the result was — without needing to trust an agent's narrative summary. **Fail if**: reconstructing
  any single event in the trail requires reading a chat transcript or an agent's self-report rather
  than the persisted record. **Cites**: Principle IX ("Audit-grade evidence" — the reviewer test),
  REQ-019.

- **[MANDATORY] OBS-CHK-006** — Verify `workflow_events` rows are never updated or deleted by any
  code path — spot-checked by confirming no update/delete method exists on
  `WorkflowEventJpaRepository`'s usage anywhere in the codebase (a disclosed code-review-level
  guarantee, not a database trigger, per `plan.md` Section 8/`data-model.md` Section 5). **Fail
  if**: any code path calls a delete or update operation against a `workflow_events` row. **Cites**:
  REQ-026 (negative criterion: "never allows an existing audit record to be edited or deleted"),
  plan.md Section 8.

- **[MANDATORY] OBS-CHK-007** — Verify retry attempts, fallback actions, compensation actions,
  recovery outcomes, and latency are all captured as part of the run's audit trail, not
  reconstructed after the fact from memory or logs not designed to support it. **Fail if**: a
  recovery event (retry, compensation) occurred during a test run but left no corresponding
  `workflow_events`/`RecoveryEvent` record. **Cites**: Principle VIII (closing paragraph), Principle
  IX, plan.md Section 7.2.

- **[MANDATORY] OBS-CHK-008** — Verify `GET /metrics` / `--metrics-report` output is always tagged
  `isDemonstrationData: true` and is never presented, in any report or dashboard, in a way that
  could be mistaken for a production-grade measurement. **Fail if**: any metrics output omits the
  demonstration-data tag, or a report/README presents a demonstration-run number as if it were a
  production statistic. **Cites**: Principle IX (metrics-labeling bullet), plan.md Section 7.1.

- **[MANDATORY] OBS-CHK-009** — Verify `MttrReport.mttrSeconds` is `null` (not `0`) when zero
  events have recovered, and that `MttrReport.unrecoveredCount` reports unrecovered failures
  separately rather than folding them into the average. **Fail if**: the MTTR computation returns
  `0` for a zero-recovered-events population, or unrecovered failures are silently included in the
  denominator. **Cites**: plan.md Section 7.2 (exact MTTR mechanics), Principle IX.

- **[MANDATORY] OBS-CHK-010** — Verify `MttrReport.populationNote` (or equivalent) states, in the
  response itself, that the measured population is a small, non-statistically-representative sample
  of demonstration runs — not only documented in `plan.md` but surfaced at the point of use. **Fail
  if**: the metrics response contains a number with no accompanying population-size caveat. **Cites**:
  plan.md Section 7.2, Principle IX.

- **[MANDATORY] OBS-CHK-011** — Verify the reliability summary's computed measurements (success
  rate, failure rate, retry frequency, rollback/compensation frequency, end-to-end latency) are
  each derived from real persisted `workflow_instances`/`workflow_steps` state, not from a
  hand-maintained or hardcoded report. **Fail if**: a metrics value cannot be reproduced by querying
  persisted state independently of the reporting code. **Cites**: plan.md Section 7.1, Principle XI.

- **[OPTIONAL] OBS-CHK-012** — Verify structured log lines are consistently correlated by
  `correlation_id`/`workflow_id` in a machine-parseable format (not free-text prose only), so a
  reviewer or tool can filter a single run's logs mechanically. **Fail if**: log lines for the same
  run use inconsistent or absent structured correlation fields. **Cites**: plan.md Section 7, REQ-025.
