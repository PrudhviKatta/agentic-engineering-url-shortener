# Architecture Decision Record (ADR) Quality Checklist

**Category 3 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `docs/adr/ADR-001..010*.md` (all 10, all currently Accepted) and any future ADR.
**Constitution principles:** VII (Architecture and Maintainability), III (Human Governance), VI
(Compliance and Change Control), X (Traceability).
**Status:** Final review: **PASS** for ADR-001 through ADR-010. See
`docs/assessment/final_checklist_status.md`; this remains an ongoing drift check for future ADRs.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] ADR-CHK-001** — Verify every ADR (ADR-001..010) has a `## Status` line stating
  Accepted/Proposed/Rejected/Superseded and the accepting human's name and date, per the project's
  own guardrail ("only the human candidate may mark an ADR Accepted," ADR-001's Status section).
  **Fail if**: an ADR is treated as in effect (cited by plan.md/code) while its Status line reads
  Proposed or is missing an approver name. **Cites**: Principle III, ADR-001 Status precedent.

- **[MANDATORY] ADR-CHK-002 [ANTI-PATTERN: architecture, schema, security, or workflow change
  without formal approval]** — Verify no architecture, schema, security-control, or workflow-state
  change lands in the codebase without a corresponding Accepted ADR (or an ADR amendment) predating
  or accompanying the change. **Fail if**: a commit changes `WorkflowStatus`'s enumerated states, the
  persistence engine, the approval mechanism, or a security control (scheme allow-list, SSRF guard)
  with no ADR reference in the commit message or an existing/updated ADR covering it. **Cites**:
  Principle VI ("any change to already-approved... architecture... must go through formal impact
  analysis and change approval"), Principle III.

- **[MANDATORY] ADR-CHK-003** — Verify each ADR's "Options Considered" section documents at least
  two real alternatives (not one option and a straw-man), each with advantages, disadvantages,
  risks, and assessment implications, per the pattern ADR-001..010 already establish. **Fail if**: a
  future ADR lists only the chosen option, or lists alternatives with no stated disadvantage (a
  decision with no visible counter-argument reads as undeliberated per Principle VII). **Cites**:
  Principle VII (third bullet).

- **[MANDATORY] ADR-CHK-004** — Verify each ADR's "Decision" section states a single, unambiguous
  choice (not "we will consider X or Y depending on..."), and that the "Rationale" section
  explicitly explains why the rejected options were rejected relative to the decision drivers, per
  ADR-004/ADR-005/ADR-009's pattern of naming exactly which driver each rejected option fails.
  **Fail if**: a Decision section leaves the actual choice conditional/open. **Cites**: Principle
  VII, Principle I (non-ambiguity in downstream artifacts).

- **[MANDATORY] ADR-CHK-005** — Verify each ADR's "Traceability" section cites real REQ IDs,
  `spec.md` section numbers, and `plan.md` section numbers that actually exist (spot-check against
  `spec.md`/`plan.md`), not invented or stale references. **Fail if**: an ADR's Traceability section
  cites a REQ ID absent from `spec.md`, or a plan.md section number that does not correspond to the
  claimed content. **Cites**: Principle X.

- **[MANDATORY] ADR-CHK-006** — Verify each ADR's "Validation" section names concrete, real test
  class names (e.g. ADR-004's `DependencyGraphTest`, `WorkflowEngineSafeStopTest`) that are expected
  to exist once implementation runs, and that — once Tasks 11+ complete — those test classes
  actually exist and pass. **Fail if**: an ADR's Validation section is vague ("will be tested
  thoroughly") rather than naming specific test classes/methods, or the named tests never
  materialize in the codebase. **Cites**: Principle IV, Principle XI.

- **[MANDATORY] ADR-CHK-007** — Verify ADR-001's disclosed process gap (the original Python stack
  was adopted before any ADR existed, "pre-decided... for scheduling purposes") is not repeated for
  any future technology choice — i.e., verify no new technology/library is adopted in code before an
  ADR proposing it exists, or that if it is (for legitimate scheduling reasons), the same disclosure
  pattern ADR-001 used is followed rather than silently omitted. **Fail if**: a new dependency
  appears in `pom.xml` with no ADR and no disclosed rationale anywhere. **Cites**: ADR-001 (Process
  note), Principle I.

- **[MANDATORY] ADR-CHK-008** — Verify each ADR's "Reversibility" section makes an honest claim
  (e.g. ADR-004's "Low" for the orchestration engine, reflecting real rewrite cost) rather than
  uniformly claiming high reversibility to minimize scrutiny. **Fail if**: an ADR whose decision
  would require a substantial rewrite to reverse (per its own Consequences section) nonetheless
  claims "High" reversibility with no supporting reasoning. **Cites**: Principle X (honest
  self-assessment), Principle XI.

- **[OPTIONAL] ADR-CHK-009** — Verify each ADR's "Risks and Mitigations" table names a concrete
  mitigation mechanism (a specific test, a specific design rule) rather than a vague intention ("we
  will be careful"), per ADR-004's "Fixed ordering rule... dedicated tests for exactly this race"
  pattern. **Fail if**: a Risk row's Mitigation column is non-actionable prose with no verifiable
  artifact behind it. **Cites**: Principle VIII, Principle IV.

- **[MANDATORY] ADR-CHK-010** — Verify no ADR selects a technology or design that contradicts a
  requirement's confirmed acceptance criterion (spot-check ADR-002's H2 choice against REQ-008/CLR-9's
  transactional-isolation requirement; ADR-007's YAML policy format against REQ-030/Principle VI's
  versioned-policy-evidence requirement). **Fail if**: an ADR's Decision cannot actually satisfy the
  REQ(s) it claims to in its Traceability section. **Cites**: Principle I, Principle VII.

- **[OPTIONAL] ADR-CHK-011** — Verify ADR numbering is never reused or reassigned to a different
  decision, even if a decision is later superseded (a superseded ADR gets a new ADR number that
  references and supersedes the old one, per the numbering-stability convention `spec.md` Section 0
  establishes for REQ IDs and this project's own stated practice, e.g. plan.md Section 9's
  "architecture decisions are ADR-numbered and superseded, not edited in place"). **Fail if**: an
  existing ADR file is edited in place to reverse its Decision without a superseding ADR number.
  **Cites**: Principle X, plan.md Section 9.
