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
| Java/Spring Boot stack pivot | `docs/adr/ADR-001-language-and-framework.md`, `docs/adr/ADR-002-persistence-h2.md` | N/A — ADRs drafted ahead of the formal Task 7 ADR gate, at the human candidate's explicit request for a stack change | **Requested by the human candidate directly** (not a proposal the agent originated); formal ADR + impact analysis drafted after the fact per external-review feedback, pending explicit accept/reject — see the ADR files' own Status field for current state. | Prudhvidhar Reddy Katta (human candidate) | 2026-09-12 (requested) / see ADR for acceptance date |

## What this log does *not* replace

- The full task-by-task review trail (implementer report + reviewer verdict + fix-loop history for every one of the 48 plan tasks) still lives in `.superpowers/sdd/2026-09-12-agentic-url-shortener-sdlc/progress.md` during active development. That ledger is process scratch by design (per the `superpowers:subagent-driven-development` skill) and is deleted once the final whole-branch review is clean.
- Before that deletion happens, the curated, durable version of that evidence — review outcomes, findings and their disposition, test commands and results, replanning records, known process incidents — must be migrated into tracked documents (this file for gate-level decisions; `docs/assessment/convergence_report.md` and `docs/assessment/final_engineering_summary.md`, per plan Tasks 46–47, for the full evidence trail). This is a standing obligation, not yet fully discharged as of 2026-09-13 — noted here so it isn't forgotten.
