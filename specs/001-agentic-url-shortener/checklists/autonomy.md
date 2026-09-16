# Controlled Autonomy Quality Checklist

**Category 5 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** every agent-executed stage handler in the orchestration engine; REQ-031.
**Constitution principles:** II (closing paragraph: "Agent autonomy inside this graph is bounded by
construction, not by good behavior"), III (Human Governance).
**Status:** Final review: **PASS WITH LIMITATION** (deterministic demonstration handlers and no
deep immutability for nested artifact values). See `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] AUTO-CHK-001** — Verify an agent's action during stage execution is structurally
  confined to the stage it currently owns — i.e., a stage handler has no code path that writes to
  another stage's `workflow_steps` row or mutates another stage's declared outputs. **Fail if**: a
  stage handler for stage N can be shown to write state attributed to stage M ≠ N. **Cites**:
  REQ-031, Principle II.

- **[MANDATORY] AUTO-CHK-002** — Verify a stage's output is validated against that stage's declared
  exit criteria (`StageDefinition`) before it is accepted as complete, and that a stage cannot mark
  itself `SUCCEEDED` when its exit criteria are objectively unmet. **Fail if**: a stage transitions
  to `SUCCEEDED` status without an exit-criteria check having executed and passed, verifiable via a
  stage-boundary violation test in which an out-of-scope or criteria-failing action is rejected.
  **Cites**: REQ-031 (negative criterion), Principle II.

- **[MANDATORY] AUTO-CHK-003 [ANTI-PATTERN: missing approval enforcement]** (autonomy angle) —
  Verify no agent code path can call the approval decision (`approve()`/`reject()`) on its own
  authority for a gate the constitution assigns to a human, regardless of any "autonomy level"
  configuration. **Fail if**: any internal method allows a stage handler or the orchestrator itself
  to resolve a human-owned Approval Gate programmatically. **Cites**: Principle III, REQ-013
  (negative criterion), REQ-031; primary detection lives in `approvals.md` CHK-APPR-002.

- **[MANDATORY] AUTO-CHK-004** — Verify 100% of agent actions in a demonstration run are
  attributable to a declared stage owner (an agent role, per `StageDefinition.owner`, or the literal
  string "human" for human-owned stages) and pass that stage's exit-criteria check, verified via
  audit-trail review — matching REQ-031's own proposed validation target. **Fail if**: an
  audit-trail review of a completed demonstration run finds any action with no attributable stage
  owner. **Cites**: REQ-031 (proposed validation target), Principle IX.

- **[MANDATORY] AUTO-CHK-005** — Verify a security-sensitive decision, a destructive/irreversible
  change, or a release-readiness sign-off is never resolved by unsupervised agent judgment — these
  categories remain human-owned per constitution Principle III regardless of what any per-stage
  "autonomy setting" might otherwise allow. **Fail if**: a stage classified as touching one of these
  categories has `entryApproval=false` and `exitApproval=false` with no human-owned gate anywhere in
  its path. **Cites**: Principle III (human-ownership list), REQ-031, REQ-016.

- **[MANDATORY] AUTO-CHK-006** — Verify a stage-boundary violation test exists and passes: an
  attempt by an out-of-scope agent action (e.g., a stage-6 handler trying to write stage-8's result)
  is rejected, not silently accepted. **Fail if**: no such test exists once implementation is
  underway, or the test exists but the violation is accepted rather than rejected. **Cites**:
  REQ-031 (Evidence pointer).

- **[OPTIONAL] AUTO-CHK-007** — Verify any future "autonomy level" configuration (e.g., a setting
  that widens or narrows what an agent may do without a human gate) is itself change-controlled
  (REQ-030) — a change to autonomy scope is an architecture/workflow-state-definition change under
  Principle VI, not a runtime toggle an agent can flip unilaterally. **Fail if**: an autonomy-scope
  setting can be changed by an agent action with no recorded human approval. **Cites**: Principle
  VI, REQ-030, REQ-031.

- **[MANDATORY] AUTO-CHK-008** — Verify controlled autonomy is tested negatively, not only
  positively: at least one test demonstrates that an agent attempting to act beyond its stage's
  bounds is blocked, in addition to tests demonstrating in-bounds actions succeed. **Fail if**: the
  test suite for REQ-031 contains only positive-path assertions. **Cites**: REQ-031 (negative
  criterion), Principle IV.
