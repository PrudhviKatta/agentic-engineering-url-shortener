# Documentation Quality Checklist

**Category 15 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `docs/governance/constitution.md`, `specs/001-agentic-url-shortener/{spec,plan,
data-model,research}.md`, `docs/adr/*.md`, `docs/scenarios/*.md`, README/operational docs (once
written).
**Constitution principles:** X (Traceability and Repository Integrity — "Documentation is kept
current with implementation").
**Status:** Final review: **PASS**. Currency and remaining limitations are summarized in
`docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] DOC-CHK-001 [ANTI-PATTERN: documentation inconsistent with behavior]** — Verify
  every behavioral claim in `plan.md`/ADRs/scenario docs matches what the implemented code actually
  does, spot-checked at each task-group checkpoint (Task 10 analysis onward). **Fail if**: a
  document states a behavior (e.g., "expired and unknown codes are distinguishable") that the
  implemented code does not exhibit under test. **Cites**: Principle X ("a stale doc describing
  behavior the code no longer has is a defect to fix in the same change that caused the drift").

- **[MANDATORY] DOC-CHK-002** — Verify documentation affected by a change is updated in the *same*
  change that caused the drift — not deferred to a follow-up ticket. **Fail if**: a commit changes
  behavior described in `plan.md`/an ADR/a scenario doc without also updating that document in the
  same commit or PR. **Cites**: Principle X, Principle XI (#6).

- **[MANDATORY] DOC-CHK-003** — Verify every AI-generated document (constitution, spec, plan, ADRs,
  checklists, scenario docs) is subject to human review before being treated as approved, and that
  the review is recorded (a named human, a date) rather than assumed. **Fail if**: a document is
  cited downstream as "approved"/"ratified"/"accepted" with no recorded human reviewer. **Cites**:
  Principle X (fourth bullet), Principle III.

- **[MANDATORY] DOC-CHK-004** — Verify the Deferred Technology Decisions list (`spec.md` Section 8)
  is fully closed out by ADR-001..010 with no entry left silently unresolved once ADRs are Accepted
  — spot-check each Section 8 bullet against the ADR that resolves it. **Fail if**: any Section 8
  deferred decision has no corresponding Accepted ADR and is nonetheless assumed resolved elsewhere.
  **Cites**: spec.md Section 8, Principle I.

- **[MANDATORY] DOC-CHK-005 [ANTI-PATTERN: undocumented architecture drift]** — Verify
  `plan.md` Section 2.2 (component plan) and Section 12 (technology decisions) are updated whenever
  an ADR is added/amended, so a reviewer reading `plan.md` alone sees the current, not a stale,
  architecture. **Fail if**: `plan.md` and an Accepted ADR disagree on a technology/component
  decision with no note explaining which is authoritative. **Cites**: Principle X; primary detection
  in `architecture.md` CHK-ARCH-008.

- **[MANDATORY] DOC-CHK-006** — Verify security-relevant documentation (`plan.md` Section 8,
  ADR-010) discloses every accepted trade-off explicitly — undocumented trade-offs are
  indistinguishable from oversights, per constitution Principle V. **Fail if**: a security-relevant
  design choice (no rate-limiting, no authentication, sequential short-code enumeration) exists with
  no corresponding disclosure anywhere in the docs. **Cites**: Principle V, Principle X.

- **[MANDATORY] DOC-CHK-007** — Verify every numeric or scope-limiting placeholder (the CLR-17
  90-day retention figure, the CLR-11 unapproved 100 req/min/IP proposal) is labeled, wherever it
  reappears in later documentation, with the same qualifier it was given at its origin (e.g. "not
  yet approved," "approved temporary assumption") — not silently promoted to sound confirmed later.
  **Fail if**: a later document states one of these figures as if it were an already-approved
  requirement. **Cites**: CLR-11, CLR-17, Principle I.

- **[MANDATORY] DOC-CHK-008** — Verify residual risks and known limitations are disclosed
  explicitly in documentation rather than left for a reviewer to discover independently (Principle
  XI #8) — spot-check the security caveats list (`plan.md` Section 8) and the reliability
  disclosed-simplifications list (`plan.md` Section 6) for completeness against what the codebase
  actually contains. **Fail if**: a reviewer finds an undisclosed limitation by reading source code
  that has no counterpart disclosure in `plan.md`/an ADR. **Cites**: Principle XI (#8).

- **[MANDATORY] DOC-CHK-009** — Verify version history sections (constitution Section 5, `spec.md`'s
  clarification record, ADR statuses) are append-only — a new row is added for each change, and no
  prior row's content is edited to look as though history was different than it was. **Fail if**:
  a version-history table shows a prior entry rewritten rather than superseded by a new row. **Cites**:
  constitution Section 5 (closing note), Principle X.

- **[OPTIONAL] DOC-CHK-010** — Verify a top-level README (once written) accurately describes how to
  build, run, and exercise all three scenarios locally, matching what `plan.md` Section 14
  (Delivery Sequence) actually delivers — not an aspirational description of unimplemented
  capability. **Fail if**: README instructions reference a command/endpoint that does not exist.
  **Cites**: Principle X, REQ-024.

- **[MANDATORY] DOC-CHK-011** — Verify no documentation presents a demonstration-scale measurement
  (a load-test number from a laptop, a small-sample MTTR) in a way that could be mistaken for a
  production-grade claim — every such number carries its demonstration-scope label at the point it
  is stated, not only in a distant appendix. **Fail if**: a metric appears in prose with no
  demonstration-scope qualifier nearby. **Cites**: Principle IX (metrics-labeling bullet).
