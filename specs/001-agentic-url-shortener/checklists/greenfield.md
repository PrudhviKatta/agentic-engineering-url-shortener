# Greenfield Scenario (SCN-A) Quality Checklist

**Category 12 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `spec.md` Section 4 (SCN-A), `plan.md` Section 11.1, `GreenfieldScenarioTest`,
`docs/scenarios/greenfield.md`.
**Constitution principles:** I, II, III, VIII, IX, X.
**Status:** Final review: **PASS**. SCN-A execution and current run evidence are recorded in
`docs/scenarios/greenfield.md` and `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] GF-CHK-001 [ANTI-PATTERN: linear chaining disguised as orchestration]** (scenario
  instantiation) — Verify the executed greenfield run's audit trail (`GET /workflows/{id}/audit`)
  shows genuine parallel fan-out — Design → {Implementation, Documentation} and
  Implementation/Documentation → {Testing, Security} — with the join at Release-readiness, not a
  sequential 12-step walk that merely lists the same 12 stage names in order. **Fail if**: the
  audit trail's timestamps show every stage starting only after the previous one's completion, with
  no overlap in stages the graph declares parallel. **Cites**: spec.md Section 4 (SCN-A), Principle
  II; primary detection in `orchestration.md` CHK-ORCH-001/004.

- **[MANDATORY] GF-CHK-002** — Verify Stage 3 (Ambiguity detection) records the specific quality
  checks performed (complete / consistent / testable / in-policy-and-architecture-bounds) and the
  explicit reason clarification was skipped on the initial pass — not merely a boolean "no
  clarification needed" with no rationale. **Fail if**: `GreenfieldScenarioTest`'s assertion on the
  early decision record finds no skip-rationale text. **Cites**: spec.md Section 4 (SCN-A), Principle
  I.

- **[MANDATORY] GF-CHK-003** — Verify that if a material ambiguity is discovered mid-Implementation
  or mid-Testing, only the affected branch suspends (via `replan()` targeting that branch), while
  any unaffected, already-in-flight Documentation work is not discarded. **Fail if**: a mid-run
  ambiguity injection test shows the entire workflow halting rather than only the affected branch,
  or shows already-produced Documentation output discarded. **Cites**: spec.md Section 4 (SCN-A),
  REQ-018 (negative criterion).

- **[MANDATORY] GF-CHK-004** — Verify the governed clarification sub-flow invoked mid-run produces
  an impact analysis over artifacts already built, and that the graph resumes at the suspended node
  (not by restarting from Requirement ingestion) only after recorded human approval. **Fail if**: a
  resumption after mid-run clarification re-executes Stage 1/2 rather than resuming at the suspended
  node. **Cites**: spec.md Section 4 (SCN-A), REQ-017, REQ-018.

- **[MANDATORY] GF-CHK-005** — Verify `GreenfieldScenarioTest` asserts a terminal `COMPLETED`
  outcome and that the first (or an early) recorded decision documents the no-clarification-required
  rationale, matching `plan.md` Section 11.1's stated validation approach. **Fail if**: the test
  passes without actually asserting on the decision content (i.e., it only checks the terminal
  status). **Cites**: plan.md Section 11.1.

- **[MANDATORY] GF-CHK-006** — Verify `docs/scenarios/greenfield.md` is generated from an actual
  executed run's real `GET /workflows/{id}/audit` JSON output — not hand-written narrative text
  describing what the run would show. **Fail if**: the scenario evidence document contains no
  reference to, or extract from, a real API response, or its content cannot be reproduced by
  re-querying a persisted run. **Cites**: plan.md Section 11.1 ("the actual... JSON from a real
  run"), Principle X ("No approval, test result, execution log... is ever fabricated").

- **[MANDATORY] GF-CHK-007** — Verify the scenario's expected terminal outcome (`COMPLETED`,
  release-readiness `ready`) is only claimed if every mandatory policy check in the actual run
  resolved `PASS`/`NOT_APPLICABLE` and every test in the actual run passed — not asserted as an
  expected/aspirational outcome without the underlying evidence. **Fail if**: `docs/scenarios/
  greenfield.md` claims a "ready" outcome with no corresponding policy/test evidence attached.
  **Cites**: REQ-020, Principle XI.

- **[MANDATORY] GF-CHK-008** — Verify the primary requirements SCN-A claims to exercise
  (REQ-001–010, 011, 012, 013, 016, 017, 018, 019, 020 per `spec.md` Section 4) are each
  identifiably touched by the actual executed run's evidence — not merely named in the scenario
  narrative with no corresponding test/audit-trail proof. **Fail if**: any claimed REQ ID has no
  discoverable evidence in the run's audit trail or test results. **Cites**: spec.md Section 4,
  Principle XI, REQ-029.

- **[OPTIONAL] GF-CHK-009** — Verify the greenfield scenario's chosen example requirement
  (`"add-expiration-endpoint"` per `plan.md` Section 11.1) is itself a real, well-formed requirement
  that plausibly would pass Stage 3's quality checks — not a strawman engineered to trivially avoid
  triggering clarification. **Fail if**: the example requirement is so vague it should have
  triggered ambiguity detection but did not. **Cites**: spec.md Section 3.1 (Stage 3), Principle I.
