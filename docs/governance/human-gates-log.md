# Human Gates Log

**Purpose:** a tracked (not gitignored), running record of every mandatory human-governance gate
this project passes through, distinct from task-review approval. This document exists because an
external review (2026-09-12/13, `codex-recommend.md`, not part of this repo) correctly identified
that a subagent's "spec compliant, approved" task review is **not** the same thing as the human
candidate clearing a SpecKit Human Gate — the two had been conflated in earlier session summaries.
This log is where that distinction is made auditable from the repository itself, without needing
access to the (gitignored) SDD execution ledger at `.superpowers/sdd/`.

Each row is added at the moment a real human decision is made — never backfilled to look earlier
than it happened, and never inferred from silence, per constitution Principle III.

## Gate Log

| Gate | Artifact | Task-review status (AI, not a governance gate) | Human decision | Approver | Date |
|---|---|---|---|---|---|
| Human Gate 1 — Constitution ratification | `docs/governance/constitution.md` v1.0.0 | Approved, clean (1 fix round: added missing "fallback behavior" principle, re-reviewed clean) | **Ratified as-is.** All 11 principles and the governance section (4.1–4.7) confirmed workable; no amendments required as a condition of approval. | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| CLR-15 re-confirmation | `specs/001-agentic-url-shortener/spec.md`, Section 2.1 / 13.1 | Approved, clean (spec.md as a whole; CLR-15 itself was self-flagged by the implementer as exceeding its delegated scope, pending this exact confirmation) | **Confirmed as correct.** Links remain immutable after creation; no update/PATCH capability. Not a rubber-stamp — reviewed the specific tradeoff (no update endpoint, simpler domain model, brownfield scenario redirected to a different real change) before confirming. | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| AMB-7 disposition (CLR-17) | `specs/001-agentic-url-shortener/spec.md`, Section 13.1/13.2 | N/A — AMB-7 was left genuinely open at the original Task 5 review, correctly not force-resolved by the agent | **Approved a disclosed temporary assumption**: 90-day retention placeholder for analytics/audit data, explicitly non-binding, to be replaced with a real compliance-derived figure before any production-scale deployment. | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Java/Spring Boot stack pivot — ADR-001 (language/framework) | `docs/adr/ADR-001-language-and-framework.md` | N/A — ADR drafted ahead of the formal Task 7 ADR gate, at the human candidate's explicit request for a stack change | **Accepted.** Requested by the human candidate directly (not a proposal the agent originated) on 2026-09-12; formal ADR + impact analysis drafted 2026-09-13 per external-review feedback, then reviewed and accepted. | Prudhvidhar Reddy Katta (human candidate) | Requested 2026-09-12 / Accepted 2026-09-13 |
| Java/Spring Boot stack pivot — ADR-002 (persistence: H2) | `docs/adr/ADR-002-persistence-h2.md` | N/A — downstream consequence of ADR-001, drafted alongside it | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-003 (short-code generation) | `docs/adr/ADR-003-short-code-generation.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-004 (orchestration model) | `docs/adr/ADR-004-orchestration-model.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-005 (why not Temporal/Camunda/Activiti) | `docs/adr/ADR-005-why-not-external-orchestration-engine.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-006 (human approval mechanism) | `docs/adr/ADR-006-human-approval-mechanism.md` | Approved, clean — reviewer specifically verified entryApproval/exitApproval semantics preserved | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-007 (policy engine format) | `docs/adr/ADR-007-policy-engine-format.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-008 (concurrency model) | `docs/adr/ADR-008-concurrency-model.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-009 (rollback vs. compensation) | `docs/adr/ADR-009-rollback-vs-compensation.md` | Approved, clean | **Accepted.** | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |
| Human Gate 4 — ADR-010 (no-authentication assumption) | `docs/adr/ADR-010-authentication-assumption.md` | Approved, clean — reviewer specifically flagged this as a real residual security risk needing genuine review, not a rubber stamp | **Accepted the risk.** Explicitly reviewed, not defaulted into — no authentication is enforced anywhere in this prototype, including the human-approval endpoint (ADR-006). | Prudhvidhar Reddy Katta (human candidate) | 2026-09-13 |

## Delegated Technical Reviews (Not Human-Gate Clearance)

| Review | Artifact | Decision | Reviewer | Date |
|---|---|---|---|---|
| Human Gate 2 readiness review | `specs/001-agentic-url-shortener/spec.md`, Section 12 | **REQUEST AMENDMENTS.** Safe-stop semantics, REQ-007's current acceptance criteria, and the disposition of proposed non-functional targets must be reconciled before human approval. Human Gate 2 remains open. | Codex (delegated technical reviewer; not a human approver) | 2026-09-13 |
| Technical-plan approval readiness review | `specs/001-agentic-url-shortener/plan.md`, Sections 1–14 | **REQUEST AMENDMENTS.** Graph/scenario fidelity, fallback semantics, replan versioning, API/audit contracts, and planned known metric defects must be corrected before human approval. The plan-approval gate remains open. | Codex (delegated technical reviewer; not a human approver) | 2026-09-13 |

## What this log does *not* replace

- The full task-by-task review trail (implementer report + reviewer verdict + fix-loop history for every one of the 48 plan tasks) still lives in `.superpowers/sdd/2026-09-12-agentic-url-shortener-sdlc/progress.md` during active development. That ledger is process scratch by design (per the `superpowers:subagent-driven-development` skill) and is deleted once the final whole-branch review is clean.
- Before that deletion happens, the curated, durable version of that evidence — review outcomes, findings and their disposition, test commands and results, replanning records, known process incidents — must be migrated into tracked documents (this file for gate-level decisions; `docs/assessment/convergence_report.md` and `docs/assessment/final_engineering_summary.md`, per plan Tasks 46–47, for the full evidence trail). This is a standing obligation, not yet fully discharged as of 2026-09-13 — noted here so it isn't forgotten.
