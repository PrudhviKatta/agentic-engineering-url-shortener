# Ambiguous-Requirement Scenario (SCN-C) Quality Checklist

**Category 14 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `spec.md` Section 4 (SCN-C), `plan.md` Section 11.3, the `"add rate limiting"`
example, `AmbiguousScenarioTest`, `docs/scenarios/ambiguous.md`.
**Constitution principles:** I, II, III, VIII, X.
**Status:** Final review: **PASS**. SCN-C execution and current run evidence are recorded in
`docs/scenarios/ambiguous.md` and `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] AMB-CHK-001** — Verify Stage 3 (Ambiguity detection) for the `"add rate limiting"`
  example correctly returns `ambiguityDetected=true` with a stated reason (no numeric target given —
  the same residual gap CLR-11 names), and that this is treated as the handler *succeeding* (not an
  error) per `plan.md` Section 11.3. **Fail if**: the requirement's underspecification is not
  detected, or is detected but reported as a stage failure rather than a successful
  ambiguity-flagging outcome. **Cites**: spec.md Section 4 (SCN-C), plan.md Section 11.3.

- **[MANDATORY] AMB-CHK-002** — Verify Decomposition (Stage 5) is hard-blocked — not merely delayed
  — while Stage 3's outcome is "ambiguous": its entry criterion ("Stage 3 clear or Stage 4
  approved") must be objectively unmet, and no downstream node may proceed on the unclear input.
  **Fail if**: any test shows Decomposition beginning, even partially, before Stage 4 resolves.
  **Cites**: spec.md Section 4 (SCN-C — "hard fan-out block, not a delay"), Principle I.

- **[MANDATORY] AMB-CHK-003** — Verify the run sits in a recorded safe-stop
  (`PENDING_APPROVAL`/`WAITING_APPROVAL`) state while Stage 4 (Human Clarification) is pending — not
  a silent hang indistinguishable from a crashed process. **Fail if**: querying the run's state
  while clarification is pending returns no distinguishable "waiting on human" status. **Cites**:
  spec.md Section 4 (SCN-C), REQ-016.

- **[MANDATORY] AMB-CHK-004 [ANTI-PATTERN: approval inferred from silence]** (scenario
  instantiation) — Verify no timeout on the Stage 4 clarification gate resolves to an implicit
  decision — the run remains blocked indefinitely until an explicit human decision (e.g., "rate
  limit set to 100 req/min/IP") is recorded. **Fail if**: a timeout-simulation test shows the
  workflow resuming decomposition without an explicit recorded decision. **Cites**: Principle III,
  REQ-013; primary detection in `approvals.md` CHK-APPR-003.

- **[MANDATORY] AMB-CHK-005 [ANTI-PATTERN: missing dynamic replanning]** (scenario instantiation) —
  Verify the clarification outcome triggers a real replan (`decomposition→decomposition_v2`) that is
  registered *before* `approve(requirement)` is called (the fixed replan-before-approve ordering,
  `plan.md` Section 3.4/11.3), since `approve()` synchronously resumes execution. **Fail if**: a race
  test shows `approve()` resuming execution against a graph not yet containing
  `decomposition_v2`. **Cites**: plan.md Section 11.3, REQ-018, REQ-013.

- **[MANDATORY] AMB-CHK-006** — Verify an impact analysis runs over any artifacts that already
  existed before the ambiguity was detected (e.g., a prior partial decomposition from an earlier,
  related requirement), identifying what those artifacts must change, and that those artifacts are
  then actually updated — not merely flagged. **Fail if**: a prior artifact affected by the
  clarification's outcome is left stale with no update or explicit "no change needed" record.
  **Cites**: spec.md Section 4 (SCN-C), REQ-018.

- **[MANDATORY] AMB-CHK-007** — Verify the graph resumes at Decomposition (the correct suspended
  node) — not by restarting the whole run from Requirement ingestion — and that `AmbiguousScenarioTest`
  asserts the audit-event ordering `ambiguity_detected < replanned < approval_approved`. **Fail if**:
  the event order in a real run's audit trail does not match this sequence, or the run restarts from
  Stage 1. **Cites**: spec.md Section 4 (SCN-C), plan.md Section 11.3, REQ-017.

- **[MANDATORY] AMB-CHK-008** — Verify the audit trail of Stages 1–3 as they already executed is
  preserved unchanged by the replan — not erased or rewritten — alongside the new replan event
  (REQ-018 negative criterion). **Fail if**: a post-replan audit-trail query shows Stage 1–3 records
  altered or missing. **Cites**: REQ-018 (negative criterion), spec.md Section 4 (SCN-C).

- **[MANDATORY] AMB-CHK-009** — Verify `docs/scenarios/ambiguous.md` contains all 17 evidence points
  `plan.md` Section 11.3 enumerates (original ambiguous input, detected ambiguity,
  classification/severity, affected requirements, workflow state before/after, clarification request
  text, recorded decision, updated assumption, downstream impact analysis, replanning event,
  invalidated/regenerated artifacts, resumed state, final validation, audit trail excerpt, terminal
  outcome, plus the two implied by "before/after" being counted separately) — each backed by real run
  data. **Fail if**: any of the 17 points is absent or is narrative invention rather than
  extracted-from-a-real-run evidence. **Cites**: plan.md Section 11.3, Principle XI.

- **[MANDATORY] AMB-CHK-010** — Verify the scenario's terminal outcome is `COMPLETED` only if the
  actual executed run reached that state with a genuinely resolved ambiguity and a genuinely
  executed replan — not asserted as the "expected" outcome in documentation without the run itself
  having produced it. **Fail if**: `docs/scenarios/ambiguous.md` states a `COMPLETED` outcome with
  no corresponding real run's terminal-status evidence. **Cites**: Principle XI, plan.md Section 11.3.

- **[OPTIONAL] AMB-CHK-011** — Verify the chosen ambiguous example (`"add rate limiting"`) is
  genuinely ambiguous by the same Stage 3 quality checks used in the greenfield scenario (not
  ambiguous merely by scenario-author assertion) — i.e., that Stage 3's completeness/consistency/
  testability/policy-boundary checks, run against this input, actually and correctly flag it. **Fail
  if**: Stage 3's checks pass this input as clear and a human has to manually force the ambiguous
  branch for the demo. **Cites**: spec.md Section 3.1 (Stage 3), plan.md Section 11.3.
