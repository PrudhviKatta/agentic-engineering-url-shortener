# Requirements Quality Checklist

**Category 1 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `specs/001-agentic-url-shortener/spec.md` (REQ-001..031, SCN-A/B/C, CLR-1..17) and any
future requirement added under the same authority chain.
**Constitution principles:** I (Specification Before Implementation), X (Traceability), XI
(Evidence-Based Completion).
**Status:** All items below are UNVERIFIED/PENDING. No implementation exists yet (Tasks 11+ have not
run). This file is the instrument later gates (Task 10 analysis, task-group checkpoints, Task 46
convergence) use to check requirement quality — it is not itself a record that checking occurred.

## How to read this checklist
- **[MANDATORY]** items block release readiness (constitution Section 4.6) if failed or left
  unverified at the final gate.
- **[OPTIONAL]** items are enhancements; failing them must be disclosed but does not itself block
  release readiness.
- Each item states a pass condition and a fail condition. Where an anti-pattern from the Task 8
  source material is being detected, it is tagged **[ANTI-PATTERN: name]**.

## Items

- **[MANDATORY] REQ-CHK-001** — Verify every requirement ID actually referenced elsewhere in the
  repo (ADRs, plan.md, tasks, tests) resolves to a real, currently-defined requirement in `spec.md`
  Sections 6–7 (REQ-001..031). **Fail if**: any citation points to a REQ ID that does not exist in
  `spec.md`, or that exists only as a typo/renumbering. **Cites**: Principle X, REQ-029.

- **[MANDATORY] REQ-CHK-002** — Verify every requirement in `spec.md` Sections 6–7 carries both a
  *Positive* and a *Negative* acceptance criterion, per the document's own Section 0 convention and
  DER-3. **Fail if**: any REQ-001..031 entry has only a positive criterion, or a negative criterion
  that merely restates the positive one without describing a distinct failure/unsafe-behavior check.
  **Cites**: REQ-029, DER-3, Principle I.

- **[MANDATORY] REQ-CHK-003** — Verify every requirement's acceptance criteria are phrased so a test
  suite (not a human's subjective judgment) can determine pass/fail, per REQ-029 and Principle I's
  testability rule. **Fail if**: a requirement's criterion uses an undefined adjective (e.g.
  "reasonably fast," "sufficiently secure") with no accompanying concrete threshold or explicit
  "Proposed validation target" label. **Cites**: REQ-029, Principle I.

- **[MANDATORY] REQ-CHK-004** — Verify functional requirements (REQ-001..020) and non-functional
  requirements (REQ-021..031) remain tracked as distinguishable categories in every downstream
  artifact that consumes them (plan.md, tasks.md), not collapsed into one undifferentiated backlog.
  **Fail if**: a downstream artifact lists both categories interleaved with no way to tell which
  requirements are functional vs. non-functional. **Cites**: Principle I (second bullet).

- **[MANDATORY] REQ-CHK-005** — Verify every numeric target in `spec.md` Section 7 (REQ-021, 022,
  023, 025, 026, 027, 028, 030, 031) is labeled exactly "Proposed validation target (assumption —
  requires approval)" and is not, anywhere downstream, referenced as if it were an already-approved
  hard requirement before Human Gate 2/a later gate records its approval. **Fail if**: a task, ADR,
  or test asserts a numeric target (e.g. "p95 ≤ 200ms") as a confirmed requirement without a
  traceable approval record. **Cites**: spec.md Section 7, Section 9.3 (ASM-2), Principle I.

- **[MANDATORY] REQ-CHK-006** — Verify every one of the 9 originally flagged ambiguities (AMB-1..9,
  `spec.md` Section 9.5) has an explicit resolution (CLR-x) or an explicit, owned deferral recorded
  in Section 13, and that none was silently resolved by being quietly rewritten into requirement
  text without a CLR record. **Fail if**: a requirement's text changed in a way that resolves an
  ambiguity, but no corresponding CLR entry exists in Section 13.1/13.2. **Cites**: Principle I
  (ambiguity-escalation bullet), CLR-1..17.

- **[MANDATORY] REQ-CHK-007 [ANTI-PATTERN: orphan requirements]** — Verify every REQ ID in `spec.md`
  appears in the traceability matrix (`spec.md` Section 11) with at least one scenario, one user
  type, and one constitution principle, and — once tasks/implementation exist — in
  `traceability.md`'s bidirectional chain (requirement → task → implementation → test → evidence).
  **Fail if**: a REQ ID exists in Sections 6–7 with no Section 11 row, or (post-implementation) with
  no downstream task/test that exercises it. **Cites**: Principle X, REQ-029; see also
  `traceability.md` CHK-TRACE-001 for the full-chain version of this check.

- **[MANDATORY] REQ-CHK-008** — Verify REQ-007 (superseded by CLR-2) is marked `[SUPERSEDED IN
  PART]` in place, not deleted or renumbered, and that no downstream artifact treats REQ-007's
  original MUST-level idempotency obligation as still binding for this iteration's release-readiness
  outcome. **Fail if**: a task or test asserts idempotent-dedup behavior as a REQ-020 release
  blocker. **Cites**: REQ-007, CLR-2, Principle X (deprecated-in-place rule).

- **[MANDATORY] REQ-CHK-009** — Verify each of the three required scenarios (SCN-A, SCN-B, SCN-C)
  names the specific REQ IDs it exercises (`spec.md` Section 4's "Primary requirements exercised"
  lines) and that, once implementation exists, an end-to-end test for each scenario actually
  exercises every REQ ID it claims to. **Fail if**: a scenario's claimed REQ coverage is not matched
  by an identifiable test path once `GreenfieldScenarioTest` / `BrownfieldScenarioTest` /
  `AmbiguousScenarioTest` exist. **Cites**: spec.md Section 4, plan.md Section 10/11.

- **[MANDATORY] REQ-CHK-010** — Verify no requirement in Sections 6–7 selects implementation
  technology (language, framework, database, cloud provider, orchestration library), per Section 8's
  deferred-technology-decision rule. **Fail if**: a REQ's acceptance criterion or evidence pointer
  names a specific technology not yet ratified by an ADR. **Cites**: spec.md Section 8, Principle I,
  Principle VII.

- **[OPTIONAL] REQ-CHK-011** — Verify the derived-requirement list (DER-1..5, `spec.md` Section 9.2)
  is kept current if a later stage introduces a new elaboration not literally present in the source
  material, so a reviewer can distinguish "verbatim assignment requirement" from "agent-derived
  elaboration necessary for testability." **Fail if**: a new derived requirement is introduced
  without a DER-N entry explaining why it was necessary. **Cites**: Principle I (ambiguity/assumption
  recording bullet).

- **[MANDATORY] REQ-CHK-012** — Verify the exclusions list (`spec.md` Section 9.6) is still accurate
  once implementation exists — i.e., that no excluded capability (custom aliases, Link
  mutation/PATCH, rate limiting, authentication) was quietly implemented anyway without a recorded
  change-control decision reopening the exclusion. **Fail if**: code implements an explicitly
  excluded capability with no corresponding REQ-030 impact-analysis/approval record. **Cites**:
  spec.md Section 9.6, REQ-030.

- **[MANDATORY] REQ-CHK-013** — Verify Human Gate 2 (`spec.md` Section 12) is recorded as completed,
  with the approving human named and dated, before any implementation task (Tasks 11+) claims to
  build against this specification. **Fail if**: implementation work references `spec.md` while
  Section 12's checklist remains all unchecked and no approval row has been added. **Cites**:
  Principle I, Principle III, spec.md Section 12.

- **[OPTIONAL] REQ-CHK-014** — Verify requirement wording avoids "SHOULD" as a disguised MUST (per
  spec.md Section 0's own rule that SHOULD is not used for obligations). **Fail if**: a requirement
  uses "SHOULD" for something later treated as mandatory at release-readiness. **Cites**: spec.md
  Section 0.

- **[MANDATORY] REQ-CHK-015** — Verify the Key Entities (`spec.md` Section 2.1: Link, Short Code,
  Redirect Event, Workflow Run, Stage, Approval Gate, Policy Check Result, Audit Record, Exception)
  remain the vocabulary used consistently across plan.md, data-model.md, and any implementation —
  i.e., no entity is silently renamed or given different semantics downstream without an update to
  this section. **Fail if**: `data-model.md` or code introduces an entity with materially different
  semantics than Section 2.1 describes, with no recorded rationale. **Cites**: Principle X
  (documentation-currency rule), REQ-024.
