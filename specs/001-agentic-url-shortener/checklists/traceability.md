# Traceability Quality Checklist

**Category 16 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** the full requirement → scenario → decision → design → task → implementation → test
→ validation-evidence → documentation chain, per `spec.md` Section 11 and `plan.md` Section 13.
**Constitution principles:** X (Traceability and Repository Integrity) — the primary principle this
file enforces.
**Status:** Final review: **PASS WITH LIMITATION**. All 31 rows are complete; the optional
machine-generated traceability verifier is not implemented. See
`docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] TRACE-CHK-001** — Verify a live thread connects every REQ ID to: at least one
  scenario (`spec.md` Section 11), at least one ADR or clarification record where applicable, a
  design element (`plan.md`), at least one task (`tasks.md`, once it exists), at least one
  implementation artifact, at least one test, validation evidence (a passing test run), and — where
  behavior is user-visible — documentation. **Fail if**: given any REQ ID, a reviewer cannot walk to
  all of the above links. **Cites**: Principle X (first bullet).

- **[MANDATORY] TRACE-CHK-002 [ANTI-PATTERN: orphan requirements]** — Verify no REQ ID in `spec.md`
  Sections 6–7 is left with zero downstream artifacts once implementation begins — every REQ must
  eventually map to at least one task and one test. **Fail if**: after Tasks 11+ complete, a REQ ID
  has no task, no implementation, and no test referencing it anywhere in the repo. **Cites**:
  Principle X, REQ-029; cross-reference `requirements.md` CHK-REQ-007.

- **[MANDATORY] TRACE-CHK-003 [ANTI-PATTERN: orphan tasks]** — Verify no task in `tasks.md` (once it
  exists) exists with no traceable originating REQ ID, ADR, or scenario need — a task must be able
  to answer "why does this task exist" by pointing to an upstream artifact. **Fail if**: a task has
  no REQ/ADR/scenario citation anywhere in its description or commit message. **Cites**: Principle
  X, Principle I.

- **[MANDATORY] TRACE-CHK-004 [ANTI-PATTERN: orphan implementation]** — Verify no class, endpoint,
  or module in the implemented codebase exists with no traceable originating task or requirement —
  every production file should be attributable to at least one REQ ID or ADR decision. **Fail if**:
  a reviewer finds a source file whose purpose cannot be traced to any REQ/ADR/task. **Cites**:
  Principle X, Principle VII (complexity-must-be-justified rule).

- **[MANDATORY] TRACE-CHK-005 [ANTI-PATTERN: orphan tests]** — Verify no test class exists that
  cannot be traced to a REQ ID, an ADR's Validation section, or a scenario's evidence requirement —
  a test with no traceable purpose is either dead weight or evidence of an untracked requirement.
  **Fail if**: a test class's target behavior cannot be mapped to any REQ/ADR/scenario. **Cites**:
  Principle X, REQ-029.

- **[MANDATORY] TRACE-CHK-006** — Verify commits are small and logically coherent — one commit does
  one describable thing — such that a reviewer walking the traceability chain from a REQ ID to its
  implementation lands on a commit whose diff is actually about that REQ, not bundled with unrelated
  changes. **Fail if**: a commit touching a REQ's implementation also contains unrelated file
  deletions or changes with no shared rationale. **Cites**: Principle X (second bullet); primary
  detection and the concrete historical example live in `github-evidence.md` CHK-GH-001.

- **[MANDATORY] TRACE-CHK-007** — Verify commit messages state engineering intent (why the change
  exists) rather than only a mechanical description of the diff (e.g. "update files"). **Fail if**:
  a commit message is purely mechanical with no stated rationale traceable to a REQ/ADR/task.
  **Cites**: Principle X (second bullet).

- **[MANDATORY] TRACE-CHK-008** — Verify `spec.md` Section 11's preliminary traceability matrix
  (REQ → scenario → user type → constitution principle) is extended, not replaced, by later-stage
  traceability artifacts (a tasks-to-REQ matrix, a tests-to-REQ matrix) so the chain is cumulative
  and never has to be reconstructed from scratch at a later gate. **Fail if**: a later traceability
  artifact drops a REQ ID that Section 11 already covered, with no explanation. **Cites**: spec.md
  Section 11, Principle X.

- **[MANDATORY] TRACE-CHK-009** — Verify `plan.md` Section 13 (Traceability) correctly cites real
  REQ IDs, ADR numbers, and plan section numbers — spot-checked against the actual `spec.md`/ADR
  content, not merely internally self-consistent within `plan.md`. **Fail if**: a Section 13 row
  cites a REQ ID or ADR number that does not exist or does not match the claimed relationship.
  **Cites**: plan.md Section 13, Principle X.

- **[MANDATORY] TRACE-CHK-010** — Verify a changed upstream artifact (a spec amendment, an ADR
  superseding another) triggers a recorded re-evaluation of every downstream artifact that cited the
  prior version — the traceability chain is used operationally to find what needs re-checking, not
  merely kept as a static record. **Fail if**: a spec/ADR change occurs with no evidence that
  downstream artifacts citing the old version were re-evaluated. **Cites**: Principle I (fourth
  bullet), Principle VI, REQ-030.

- **[OPTIONAL] TRACE-CHK-011** — Verify a machine-generated or scripted traceability report (rather
  than a purely hand-maintained matrix) exists by the time of final convergence (Task 46), reducing
  the chance of a hand-maintained matrix silently drifting from the real repo state. **Fail if**: the
  final traceability matrix cannot be regenerated/verified against the current repo state by an
  independent script or query. **Cites**: Principle X, Principle XI (#10).
