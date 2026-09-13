# Assessment Submission Quality Checklist

**Category 19 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** the Task 47 Final Engineering Summary and the submission as a whole (constitution
Section 1's ten reviewer dimensions).
**Constitution principles:** XI (Evidence-Based Completion), X (Traceability and Repository
Integrity), III (Human Governance — final sign-off).
**Status:** All items UNVERIFIED/PENDING — the Final Engineering Summary does not exist yet (Task
47).

## How to read this checklist
- **[MANDATORY]** blocks release readiness/submission if failed/unverified. **[OPTIONAL]** does not,
  but must be disclosed.

## Items

- **[MANDATORY] SUB-CHK-001** — Verify the Final Engineering Summary is compiled from persisted
  evidence (workflow run state, audit trail, test results, ADRs, policy evaluations) — not from an
  agent's narrative recollection of what it believes it did. **Fail if**: a claim in the summary has
  no corresponding persisted artifact a reviewer can independently open. **Cites**: spec.md Section
  3.1 (Stage 12 exit criterion), Principle XI.

- **[MANDATORY] SUB-CHK-002 [ANTI-PATTERN: unsupported completion claims]** — Verify every "done"/
  "complete"/"passing" claim anywhere in the submission (task reports, the final summary, commit
  messages) satisfies all ten Principle XI conditions: requirements identified and traced,
  decisions/assumptions recorded with rationale, acceptance criteria actually (not approximately)
  satisfied, required tests executed and observed passing in this environment, security/reliability
  checks clean or exception-approved, documentation updated to match, traceability intact, residual
  risk disclosed, required approvals actually recorded, and all evidence assembled and available.
  **Fail if**: any completion claim is missing evidence for one or more of the ten conditions.
  **Cites**: Principle XI (all ten sub-conditions).

- **[MANDATORY] SUB-CHK-003** — Verify the submission addresses all ten reviewer dimensions named in
  constitution Section 1: working behavior, architecture fitness, engineering-decision quality and
  reasoning, AI-assisted process discipline, task decomposition, testing discipline (including
  whether tests preceded implementation and were executed), governance substance vs. theater,
  traceability, commit-history honesty, and documentation currency/judgment under ambiguity. **Fail
  if**: the Final Engineering Summary is silent on any of the ten dimensions. **Cites**: constitution
  Section 1.

- **[MANDATORY] SUB-CHK-004** — Verify every disclosed process incident (e.g. the `12f1353`
  commit-bundling incident) is named explicitly in the Final Engineering Summary's process-honesty
  section, with its cause, its impact assessment (no data loss/corruption), and the ruling made
  (disclose, don't rewrite history) — not omitted because it is embarrassing. **Fail if**: a known,
  previously-disclosed incident is absent from the final summary. **Cites**: Principle X, Principle
  XI (#8); cross-reference `github-evidence.md` CHK-GH-005.

- **[MANDATORY] SUB-CHK-005** — Verify every non-waivable principle (I, III, IV, XI) is explicitly
  addressed in the submission's self-assessment against the constitution, with evidence, not a bare
  "complied" assertion. **Fail if**: the final self-check omits any of the four non-waivable
  principles. **Cites**: constitution Section 4.1, Section 4.3.

- **[MANDATORY] SUB-CHK-006** — Verify all three required scenarios (SCN-A, SCN-B, SCN-C) have
  actually been executed (not merely designed) with their generated evidence documents
  (`docs/scenarios/{greenfield,brownfield,ambiguous}.md`) present and each backed by a real run's
  persisted data, before the submission claims scenario coverage. **Fail if**: a scenario's evidence
  document exists but cannot be traced to an actual executed workflow run's `correlation_id`.
  **Cites**: Principle XI, `greenfield.md`/`brownfield.md`/`ambiguous.md` (full checklists).

- **[MANDATORY] SUB-CHK-007** — Verify the submission's release-readiness outcome (per
  `release-readiness.md`) is attached and current at the time of submission — not a stale snapshot
  from an earlier point when fewer checks applied. **Fail if**: the submitted release-readiness
  report predates a later code/policy change that would have altered its outcome. **Cites**: RR-CHK-007,
  constitution Section 4.6.

- **[MANDATORY] SUB-CHK-008** — Verify a human has recorded final sign-off on the submission as a
  whole, distinct from any individual gate's approval — the decision to submit is itself a
  human-owned decision (Principle III: "final quality sign-off"). **Fail if**: the submission is
  transmitted/presented with no recorded human sign-off event. **Cites**: Principle III.

- **[MANDATORY] SUB-CHK-009** — Verify the submission discloses every open/deferred item still
  outstanding at submission time (e.g. AMB-7's real retention figure still pending real compliance
  input, the CLR-11 rate-limit threshold never approved) rather than presenting the project as if
  every question were fully closed. **Fail if**: a still-open item from `spec.md` Section 13.2 is
  absent from the submission's disclosed-limitations section. **Cites**: Principle XI (#8), spec.md
  Section 13.2.

- **[OPTIONAL] SUB-CHK-010** — Verify the submission includes a short "how to independently verify
  this" guide (which commands to run, which files to open first) so a reviewer does not have to
  reverse-engineer the evidence trail's structure from scratch. **Fail if**: no such guide exists and
  the evidence trail's navigation is not self-evident from the repo layout alone. **Cites**:
  Principle IX (audit-grade evidence — the "named human reviewer, with no other context" test).

- **[MANDATORY] SUB-CHK-011** — Verify the submission does not claim architectural or process
  differentiation (e.g., "non-linear orchestration," "governed autonomy") that the actual executed
  evidence does not support — every superlative/differentiating claim in the summary must be
  traceable to a specific test or audit-trail excerpt proving it, not asserted on the strength of the
  design documents alone. **Fail if**: a claim like "the system supports dynamic replanning" has no
  corresponding passing `WorkflowEngineReplanningTest`/`AmbiguousScenarioTest` evidence attached.
  **Cites**: Principle XI, Principle X (fabrication is severity-one).
