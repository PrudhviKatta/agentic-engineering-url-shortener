# Pre-Implementation Consistency Analysis (`/speckit.analyze`) + Independent Reviewer Gate

**Stage:** Task 10 of the controller's 48-task runbook — the last gate before Phase 2 implementation.
**Performed:** 2026-09-13, as a strict, read-only cross-artifact analysis. **No artifact other than
this file was modified to produce this report**, per the `/speckit.analyze` rule ("Do not modify
artifacts... Correct issues at their proper upstream source and re-run `/speckit.analyze`") and this
task's explicit process constraint: findings are reported precisely, with the correct upstream
document/section named, but not edited here.

**Checked against:** `docs/governance/constitution.md` v1.0.0 (Ratified 2026-09-13);
`specs/001-agentic-url-shortener/{spec,plan,research,data-model}.md`; `contracts/openapi.yaml`;
`docs/adr/ADR-001..010*.md` (all 10, all Accepted); `specs/001-agentic-url-shortener/checklists/*.md`
(19 files); `specs/001-agentic-url-shortener/tasks.md` (T011–T048); `docs/governance/human-gates-log.md`;
and, for ground-truth tracing, `docs/engineering-runbook.md`
(the controller's execution runbook, outside this repo).

---

## 1. Executive Finding

The artifact set is unusually disciplined for a pre-implementation gate: 31 requirements each carry
positive/negative acceptance criteria, 17 clarifications are individually rationale-bearing and
attributed to a named human decision, all 10 ADRs document real rejected alternatives and honest
reversibility assessments, and the 38-task breakdown maps cleanly onto 12 orchestration stages with
TDD sequencing, explicit parallel-safety notes, and 7 tasks correctly flagged for human review under
Principle III. No evidence of fabricated results, fabricated approvals, or silently-resolved
ambiguity was found anywhere in the reviewed set — a genuinely rare property for this stage of a
project.

However, **one CRITICAL governance gap** blocks a clean PROCEED verdict: `spec.md` itself declares
Human Gate 2 (Section 12, approval of the specification as a whole) **"Pending — Not Yet
Completed,"** with every checklist box unchecked, yet `plan.md`'s own Authority section asserts
`spec.md` is "approved with clarifications CLR-1..17," and every ADR and every one of the 38 tasks
builds on that assertion. This is not a paperwork nicety — constitution Principle I is **non-waivable**
("No production implementation may begin until a specification for that unit of work has been
written and approved through the applicable human gate") and this project's own checklists
(`requirements.md` REQ-CHK-013, `approvals.md` APPR-CHK-011) already name this exact check as
**MANDATORY**, to be verified at exactly this gate, before Tasks 11+ begin. It has not been
satisfied.

Two further HIGH findings (a plan-mandated contract obligation with no implementing task, and a
constitution/checklist-mandated test with no implementing task) and one MEDIUM finding (partial
policy-domain disclosure) are reported below. One numeric inconsistency inside the controller's own
runbook (already surfaced by a prior task review as "Minor") was traced to its exact origin and is
independently confirmed here as genuinely trivial — Minor-tier, not CRITICAL/HIGH.

**No CRITICAL or HIGH finding requires a design change.** All are closeable by: (a) a human
recording Human Gate 2 approval, (b) adding two small tasks to `tasks.md`, (c) adding one test task
to `tasks.md`, and (d) one disclosure sentence in `plan.md` Section 9. None require touching
`spec.md`'s requirements, any Accepted ADR's decision, or any already-written code (none exists).

---

## 2. Critical Blockers

### CRITICAL-1 — Human Gate 2 (specification approval) was never recorded, but is treated as done

- **Where:** `specs/001-agentic-url-shortener/spec.md` lines 7–10, 1054–1079 (Section 12, "Human
  Gate 2 (Pending — Not Yet Completed)" — all nine checklist boxes `[ ]` unchecked) **versus**
  `specs/001-agentic-url-shortener/plan.md` line 15 ("`spec.md` (approved with clarifications
  CLR-1..17)") and every downstream artifact (all 10 ADRs' Traceability sections, `tasks.md`'s
  entire task list) that cites `spec.md` as settled ground truth.
- **Also:** `docs/governance/human-gates-log.md` — a document whose own stated purpose is "a
  tracked... record of every mandatory human-governance gate this project passes through" — has rows
  for Human Gate 1 (constitution), individual CLR-15/AMB-7 reconfirmations, the ADR-001/002 stack
  pivot, and "Human Gate 4" (all 8 remaining ADRs), but **no row for Human Gate 2 at all**, and no
  row labeled "Human Gate 3" either (Human Gate 3's individual CLR decisions are recorded inline in
  `spec.md` Section 13.1 instead, which is a defensible design choice — but Human Gate 2 is a
  whole-document approval with its own Section 12 checklist, and nothing anywhere records it having
  happened).
- **Why it matters:** Constitution Principle I is one of four **non-waivable** principles (Section
  4.3): "No production implementation may begin until a specification for that unit of work has been
  written and approved through the applicable human gate. 'Approved' means a human has reviewed it,
  not that an agent generated it and moved on." Task 10 (this gate) is the last checkpoint before
  Phase 2 (`T011` onward) starts writing real code against `spec.md`. The project's own
  `requirements.md` checklist already anticipated exactly this failure mode: **REQ-CHK-013
  [MANDATORY]** — "Verify Human Gate 2 (`spec.md` Section 12) is recorded as completed, with the
  approving human named and dated, before any implementation task (Tasks 11+) claims to build
  against this specification. **Fail if**: implementation work references `spec.md` while Section
  12's checklist remains all unchecked and no approval row has been added." That is precisely the
  current state. **APPR-CHK-011 [MANDATORY]** makes the same demand for both Human Gate 2 and Human
  Gate 3.
- **Not a false positive:** the individual clarifications (CLR-1..17) *are* genuinely, individually
  human-attributed ("Owner: human candidate (via controlling session)," per-item date) — that is
  real evidence Human Gate 3 substantively happened. CLR-15 and AMB-7/CLR-17 were even explicitly
  *re*-confirmed by the human candidate on 2026-09-13 after being self-flagged as exceeding delegated
  scope — a genuinely good governance practice, and it is logged in `human-gates-log.md`. But none of
  that is a substitute for Human Gate 2's own checklist (technology-neutrality, non-silent-ambiguity,
  acceptance-criteria completeness, scenario non-linearity, approval-gate presence, recovery/safe-stop
  presence, testability, traceability) being walked and recorded as approved for the specification
  **as a whole** — `spec.md`'s own Section 12 text says exactly this ("Note (2026-09-12): Human Gate
  3... has been completed independently of this gate. Gate 3's completion... does not itself satisfy
  any Human Gate 2 checklist item above, which remains a separate, still-pending approval").
- **What the correct fix is (not performed here):** a human reviewer walks `spec.md` Section 12's
  nine checklist items, checks the boxes (or requests amendment), and records approval — name and
  date — in Section 12 itself and as a new row in `docs/governance/human-gates-log.md`, mirroring
  exactly how Human Gate 1 (constitution) was closed out (`constitution.md` Section 6, `human-gates-
  log.md` row 1). This is a fast, low-effort human action, not a redesign — but per Principle I it
  must happen **before** `T011` starts, not concurrently with it.

---

## 3. Coverage Matrix

### 3.1 REQ → task coverage (spec.md REQ-001..031 against tasks.md)

Every one of the 31 requirements has at least one `T0NN` task citing it in `tasks.md`'s
REQ/SCN/ADR field, cross-checked against `plan.md` Section 13's traceability table. No REQ ID has
**zero** task coverage. Two coverage weaknesses were found within otherwise-covered requirements
(detail in Section 7, "Unmapped Requirements," since they are partial, not total, gaps):

| REQ | Task coverage | Note |
|---|---|---|
| REQ-001–REQ-010 (domain) | T011–T019 | Full |
| REQ-011 | T020, T021, T022, T025, T026, T039 | **Partial** — the externally-reachable `POST /workflows` HTTP surface `plan.md` Section 2.1 says is needed to fully satisfy REQ-011 has no task (HIGH-1) |
| REQ-012–REQ-020 | T020–T029, T034–T039 | Full |
| REQ-021 | T015, T040 | Full |
| REQ-022–REQ-030 | T022, T030–T038, T045 | Full |
| REQ-031 | T039 | **Partial** — REQ-031's own named Evidence item ("a stage-boundary violation test") has no task (HIGH-2) |

### 3.2 Scenario → design/tasks/tests/docs/evidence (SCN-A/B/C)

| Scenario | Design (plan.md) | Task | Test | Doc | Evidence |
|---|---|---|---|---|---|
| SCN-A Greenfield | §11.1 | T039 | `GreenfieldScenarioTest` | `docs/scenarios/greenfield.md` | real `GET /workflows/{id}/audit` output (T039) |
| SCN-B Brownfield | §11.2 | T040 | `BrownfieldScenarioTest` + `UrlValidatorSsrfTest` | `docs/scenarios/brownfield.md` | two-commit split (fix, then wiring) |
| SCN-C Ambiguous | §11.3 | T041 | `AmbiguousScenarioTest` | `docs/scenarios/ambiguous.md` | 17-point evidence list |

All three are fully mapped end-to-end, cross-checked further by `T042` (cross-scenario review) and
`T043` (full traceability matrix). No gap found here.

### 3.3 Orchestration capability → implementation task (constitution Principle II / spec.md §3.2)

| Capability | Task(s) |
|---|---|
| Explicit dependency graph | T020 (`DependencyGraph`) |
| Sequential + parallel execution | T022, ADR-008 |
| Synchronization (join) | T022 (Stage 11 pattern), ORCH-CHK-004 |
| Persisted state | T021, T024 |
| Decision lineage | T021 (`WorkflowContext.recordDecision`) |
| Enforced approval gates | T023, T026 |
| Bounded retry / timeout | T030, T031, T033 |
| Rollback vs. compensation | T032, T033 |
| Safe-stop | T028, T033, T034 |
| Resumption | T023/T024 (`runFrom` reload) |
| Dynamic replanning | T035, T041 |
| Audit-grade evidence | T036 |

Full mapping confirmed; no orchestration capability named in `spec.md` §3.2 or constitution
Principle II is without an implementing task.

### 3.4 Human gate → state + task + evidence

`tasks.md`'s 7 human-approval-flagged tasks (T015, T026, T029, T040, T046, T047, T048) line up
sensibly against constitution Principle III's human-owned decision classes: T015 (security-control
design boundary), T026 (exposing the highest-risk unauthenticated endpoint), T029 (policy-exception
mechanism), T040 (security-sensitive fix + regression risk), T046 (release-readiness computation),
T047 (final quality sign-off), T048 (irreversible public submission). This is a coherent, correctly
-scoped set — no task appears to need the flag but lack it, and no flagged task appears
disproportionate.

**Gap (LOW, expected at this stage, not a defect):** `human-gates-log.md` has no placeholder rows
for these 7 future task-level gates. That is appropriate right now (none of T011–T048 has executed;
there is nothing yet to log), but it is worth naming as a forward obligation so it is not missed once
those tasks actually run — the log's own stated purpose commits it to capturing exactly this.

### 3.5 Accepted ADR → task mapping

All 10 ADRs' "Traceability" → "Expected task identifiers" lines were spot-checked against
`tasks.md`'s ADR references. All resolve to real, existing tasks (ADR-001/002 → T011+ generically;
ADR-003 → T016; ADR-004/005 → T020–T041; ADR-006 → T023/T026/T035/T041; ADR-007 → T027–T029;
ADR-008 → T022 + Phase 6; ADR-009 → T032–T034; ADR-010 → T015/T026/T040 by reference). No orphaned
ADR and no ADR citing a non-existent task were found.

### 3.6 NFR → verification method (REQ-021..031)

Every one of the 11 NFR requirements carries a "Proposed validation target (assumption — requires
approval)" in `spec.md` §7 and a named test class or check in `plan.md` §10 / `tasks.md`. All 11 have
an identified verification method; none is unfalsifiable as written. (Whether the *numeric* targets
themselves are ever formally approved is separately tracked as an open item in `spec.md` §13.2,
ASM-2 — correctly disclosed there as still open, not silently treated as approved.)

---

## 4. Consistency Issues

- **Terminology / identifiers / state names**: consistent throughout. `StepStatus`/`WorkflowStatus`
  vocabulary, REQ IDs, CLR IDs, and ADR numbers are used identically across `spec.md`, `plan.md`,
  `research.md`, `data-model.md`, the ADRs, the checklists, and `tasks.md`. No renumbering or
  redefinition found.
- **Retry semantics**: agree everywhere checked — "only transient failures are retryable; validation
  and policy failures are always permanent" (CLR-12) is stated identically in `spec.md` REQ-014,
  `plan.md` §6, ADR-004/ADR-008, and `tasks.md` T030/T033.
- **Rollback/compensation semantics**: agree everywhere — the CLR-14 served-redirect example is used
  identically in `spec.md`, `plan.md` §3.4/§6, ADR-009, and `tasks.md` T032/T033.
- **Safe-stop semantics**: agree — the CLR-13 closed two-condition enumeration is stated identically
  in `spec.md` REQ-016, `plan.md` §3.4, and `tasks.md` T028/T033/T034.
- **Approval behavior**: agree — the `entryApproval`/`exitApproval` distinction and the "no
  timeout-implied approval" rule are stated identically across ADR-004, ADR-006, `plan.md` §5, and
  the `approvals.md`/`orchestration.md` checklists.
- **Replanning behavior**: agree — the "replan-before-approve" fixed ordering rule is stated
  identically in ADR-004 (Risks), ADR-006 (Decision Drivers), `plan.md` §3.4, and `tasks.md`
  T035/T041.
- **Selected technologies vs. Accepted ADRs**: `plan.md` §12's technology table and `pom.xml`'s
  intended dependency set (Spring Boot, H2, Jackson) agree with ADR-001/002/003/004/006/007/008/009
  as currently accepted. No drift found (no code exists yet to drift against, but the *documents*
  agree with each other).
- **Tasks follow the approved architecture**: yes, with the two HIGH exceptions in Section 7 below
  (two contract-mandated endpoints and one constitution-mandated test have no task).
- **Three scenarios remain distinct**: confirmed — SCN-A's fan-out/join, SCN-B's impact-analysis
  insertion, and SCN-C's hard conditional block are structurally different graph shapes, not
  variations of the same demo (`plan.md` §11.1–11.3 each instantiate a materially different stage
  set, and `tasks.md` T041 explicitly notes its stage set is "not Task 39's... the graph shape itself
  differs").
- **Minor numeric inconsistency (traced, not newly discovered — see Section 9 for full disposition)**:
  the controller's runbook itself disagrees with itself about `WorkflowEngine`'s eventual constructor
  parameter count across two of its own passages (runbook line 3567: "9 positional parameters";
  runbook line 4750's Self-Review Notes: "8 positional params"). `tasks.md` T033 (lines 727–732)
  sides with the "8" figure. This is prose describing a not-yet-written constructor's exact arity —
  it has no effect on any requirement, ADR decision, or test, and will be settled by the actual code
  when T033 runs.

---

## 5. Constitutional Conflicts

| Principle | Status | Note |
|---|---|---|
| I — Specification Before Implementation | **Violated (in effect) at this gate** | See CRITICAL-1: `spec.md`'s Human Gate 2 is unrecorded while downstream artifacts treat it as approved. Non-waivable. |
| II — Explicit Agentic Orchestration | Satisfied (by design; unverified in execution, correctly, since no code exists) | ADR-004/005/008, `plan.md` §3, `tasks.md` T020–T041 |
| III — Human Governance | Satisfied for individual decisions (ADRs, CLRs); **the whole-specification gate is the CRITICAL-1 exception** | — |
| IV — Test-Driven Engineering | Satisfied by design (every task TDD:yes); non-waivable; cannot be verified until Tasks 11+ execute | `tdd.md` checklist correctly marks itself "UNVERIFIED/PENDING" |
| V — Security and Privacy by Design | Satisfied, with disclosed residual risks (no auth, no rate limiting, sequential short-code enumeration) — all explicitly named, not silent | ADR-010, `plan.md` §8 |
| VI — Compliance and Change-Control | Mostly satisfied; **MEDIUM gap** — policy-domain coverage disclosure incomplete (Section 7, MEDIUM-1) | `plan.md` §9, ADR-007 |
| VII — Architecture and Maintainability | Satisfied by design | `plan.md` §1–2, ADR-002/003/004/008 |
| VIII — Reliability and Recovery | Satisfied by design | `plan.md` §6, ADR-009 |
| IX — Observability and Auditability | Satisfied by design | `plan.md` §7, T036/T037 |
| X — Traceability and Repository Integrity | Satisfied, with one disclosed historical incident (`12f1353` commit-bundling, already logged and not hidden) | `github-evidence.md` |
| XI — Evidence-Based Completion | Not yet applicable (nothing is claimed complete pre-implementation); correctly framed throughout as "pending" | all 19 checklists |

---

## 6. ADR Conflicts

None found. All 10 ADRs (`ADR-001` through `ADR-010`) are internally consistent with each other, with
`plan.md`, and with `spec.md`'s confirmed requirements/clarifications:

- ADR-002 (H2)'s transactional-isolation claim is consistent with REQ-008/CLR-9's requirement.
- ADR-003 (base62 short codes) is consistent with CLR-3/CLR-4's confirmed code properties.
- ADR-004/005 (custom engine, reject external orchestrators) are mutually consistent (companion
  ADRs, cross-referenced correctly both ways).
- ADR-006 (approval mechanism) and ADR-010 (no auth) correctly name the same residual risk (the
  approval endpoint being unauthenticated) from both directions, without either one softening or
  contradicting the other.
- ADR-007 (YAML policy format) is consistent with REQ-030's change-traceability requirement.
- ADR-008 (thread-pool concurrency) is consistent with ADR-001's prior rejection of reactive/WebFlux.
- ADR-009 (rollback/compensation) is consistent with CLR-14's confirmed rollback-impossible case.

Every ADR's "Options Considered" section documents genuine, disadvantaged alternatives (not
straw-men), matching `adr.md` CHK-ADR-003's requirement. Every "Reversibility" section makes an
honest, non-uniform claim (ADR-004/005 correctly claim "Low"; ADR-008/009 correctly claim
"High"/"Moderate" for genuinely lower-cost decisions) — matching `adr.md` CHK-ADR-008.

---

## 7. Unmapped Requirements / Contract Obligations

### HIGH-1 — Two plan/contract-mandated endpoints have no implementing task

- **Where:** `specs/001-agentic-url-shortener/plan.md` lines 145–153 (Section 2.1): "Two endpoints
  not yet present in the runbook's reference implementation, added here as plan-level design
  decisions: `GET /links/{code}`... and `POST /workflows`... Both are specified in
  `contracts/openapi.yaml` with an explicit note on the implementation obligation they create for
  Task 9's task breakdown." Also `plan.md` lines 583–587 (Section 10): "**Contract-test obligation
  created by Section 2's two plan-level API additions**: since `GET /links/{code}` and `POST
  /workflows` are specified here but not yet implemented..., `tasks.md` (Task 9) must add their
  contract tests as first-class tasks, not assume they are already covered by the existing
  `LinkControllerContractTest`/`WorkflowControllerContractTest` names above."
- **Contract confirmation:** both paths exist in `contracts/openapi.yaml` — `GET /links/{code}`
  (line 83, `operationId: getLinkMetadata`) and `POST /workflows` (line 174, `operationId:
  createWorkflow`, with its own description explicitly telling "the implementing task" it "must
  instantiate the baseline 12-node stage graph... or reject with `501`... either outcome must be an
  explicit, documented choice at implementation time, not a silent gap").
- **Task check:** grepping `tasks.md`'s 38 tasks for either operation finds nothing. T017 implements
  `POST /links` (creation) only. T018 adds `GET /r/{code}` (the redirect-performing endpoint,
  explicitly distinct per `plan.md` from the metadata-only `GET /links/{code}`). T019 adds `GET
  /links/{code}/analytics`. T026 implements `GET /workflows/{id}`, `GET /workflows/{id}/audit`, and
  `POST /workflows/{id}/approvals/{step}` — **not** `POST /workflows` itself.
- **Compounding issue:** `tasks.md`'s own "API/schema deliverable group" cross-reference (lines
  50–58) asserts "Contract tests: `LinkControllerContractTest` (T017–T019), `WorkflowControllerContractTest`
  (T026)... [covering] the concrete endpoints" — this claim is incomplete; it does not cover the two
  endpoints named above, and nothing in `tasks.md` flags that gap the way `plan.md` itself did.
- **Requirement impact:** `plan.md` line 149 states plainly that the CLI-only creation path (via
  `ScenarioRunner` calling `WorkflowEngine.submit()` directly) "does not by itself satisfy REQ-011's
  'the system MUST allow a new Workflow Run to be created' as an externally reachable capability." So
  this is not merely a missing nice-to-have endpoint — by `plan.md`'s own stated reasoning, REQ-011's
  full satisfaction depends on it.
- **Correct fix (not performed here):** add two tasks to `tasks.md` (e.g., a `T026a`/renumbered slot,
  or folded into T026's scope with an explicit note) implementing `POST /workflows` and `GET
  /links/{code}`, each with its own contract test, sequenced after T017/T026 respectively. This is an
  addition to `tasks.md`, not a change to any ADR or to `spec.md`.

### HIGH-2 — REQ-031's own named evidence test has no implementing task

- **Where:** `spec.md` REQ-031's Evidence line: "audit-trail review; **a stage-boundary violation
  test** in which an out-of-scope agent action is rejected." Restated identically in `plan.md` line
  738 (traceability table): "REQ-031 | ... | Audit-trail review; **stage-boundary violation test**."
  Also required as **[MANDATORY]** by two separate checklists: `orchestration.md` ORCH-CHK-015 ("an
  audit trail shows an action with no attributable owning stage... cross-reference `autonomy.md`
  CHK-AUTO-001") and `autonomy.md` AUTO-CHK-006 ("Verify a stage-boundary violation test exists and
  passes: an attempt by an out-of-scope agent action... is rejected, not silently accepted. **Fail
  if**: no such test exists once implementation is underway").
- **Task check:** no task in `tasks.md` (T011–T048) creates a test with this shape. T039's REQ/SCN/ADR
  field cites REQ-031 ("each stage is attributable and exit-criteria-checked") but its Expected
  Artifact and Files list describe the greenfield scenario's normal happy-path stage classes, not a
  dedicated negative test proving an out-of-scope write is rejected.
- **Correct fix (not performed here):** add a task (or extend T039/T042's scope with an explicit,
  named sub-item) that adds a stage-boundary-violation test — e.g., a stage handler attempting to
  write another stage's `workflow_steps` row and observing rejection — before T046 (convergence)
  and T047 (final assessment) could otherwise be tempted to treat REQ-031 as fully evidenced without
  it.

**No other REQ was found with zero or materially incomplete task coverage.**

---

## 8. Unmapped Tasks

None found. Every one of the 38 tasks (T011–T048) cites at least one REQ, ADR, or scenario in its
own "REQ/SCN/ADR" field, satisfying `traceability.md` TRACE-CHK-003's anti-orphan-task check. No task
exists whose purpose cannot be traced to an upstream artifact.

---

## 9. Missing Tests (pre-implementation — what the task graph itself is missing, not what execution will reveal)

1. **Stage-boundary violation test** (REQ-031) — see HIGH-2 above. No task creates it.
2. **Contract tests for `GET /links/{code}` and `POST /workflows`** — see HIGH-1 above. No task
   creates them.
3. **T033 constructor-parameter-count note** — internally inconsistent prose in the source runbook
   (line 3567 vs. line 4750; `tasks.md` follows line 4750's "8"). This is **not** a missing test — it
   is a documentation nit about a not-yet-built method's signature shape. **Independent judgment on
   this task's specific ask:** I concur with the prior reviewer that this is Minor-tier, not
   CRITICAL/HIGH, but for a more precise reason than "it's just a typo somewhere" — I traced it to a
   genuine, pre-existing disagreement inside the runbook itself (not something `tasks.md` introduced
   or got wrong relative to a single authoritative source), it describes prose about a future
   parameter count with zero effect on any requirement, ADR decision, safety property, or test
   assertion, and the actual number will be dictated by the real code T033 writes and will be
   spot-checked at T047's convergence review regardless of which number the note currently says. It
   does not need a corrective task now; it is correctly left to the final whole-branch review's
   Minor-findings triage.

All other test-level obligations named in `plan.md` §10 and the 19 checklists (unit, integration,
contract, orchestration-transition, approval, retry, timeout, rollback/compensation, safe-stop,
resumption, replanning, concurrency, security, end-to-end) have an identified, named task and test
class. None is missing a task-level owner other than the two items above.

---

## 10. Missing Evidence

Nothing is "missing" in the sense of being expected-but-absent at this stage — no implementation has
started, and all 19 checklists correctly self-report `Status: UNVERIFIED/PENDING`, which is the
honest and expected state before Tasks 11+ run. The one evidence-related gap worth naming now, while
it is still cheap to fix, is:

**MEDIUM-1 — Policy-domain coverage disclosure is incomplete.**
- **Where:** constitution Principle VI (Section 3, "Compliance and Change-Control Policy
  Enforcement") names, "at minimum: security, compliance, privacy, audit retention, approved
  dependencies, software licensing, and change control" as the policy-guardrail domains a versioned
  policy set must cover. `plan.md` Section 9 and ADR-007/`research.md` D7 define exactly four
  concrete policies (`SEC-001`, `SEC-002`, `COMP-001`, `LIC-001`), explicitly labeled as covering
  three domain categories ("security," "compliance" [scoped narrowly to "audit-trail presence"], and
  "licensing").
- **Gap:** "privacy," "audit retention," "approved dependencies," and "change control" (as a
  PolicyEngine-evaluated category, as opposed to the separate REQ-030/`ImpactAnalysisStage`
  mechanism) have no corresponding `policy_evaluations`-producing check, and — unlike every other
  scope decision in this project — there is no single sentence anywhere in `plan.md`/`research.md`
  that says, explicitly, "these four Principle VI domains are intentionally not separate
  PolicyEngine checks in this iteration; they are addressed instead by [CLR-17's retention
  placeholder / a manual dependency scan / REQ-030's impact-analysis mechanism], and here is why
  that is an acceptable prototype-scope decision."
- **Why it matters:** `compliance.md` COMP-CHK-012 is **[MANDATORY]** and states exactly this
  requirement: "Verify... any gap... is explicitly disclosed as a scoped-down prototype decision,
  not a silent omission. **Fail if**: a named Principle VI policy domain has no corresponding policy
  entry and no disclosure explaining the gap." The underlying governance need is *substantively*
  met piecemeal (retention is disclosed via CLR-17; dependency scanning is named in `plan.md` §8;
  change control is REQ-030 in full) — this is not a design flaw — but the checklist's specific
  disclosure requirement (one place naming the mapping) is not yet satisfied.
- **Correct fix (not performed here):** add one paragraph to `plan.md` Section 9 explicitly mapping
  each of Principle VI's seven named domains to either a `PolicyEngine` check or the alternative
  mechanism that covers it, so `compliance.md` COMP-CHK-012 can be marked satisfied by inspection.

---

## 11. Explicit Failure Traps Scan (guide Section 13 / source-material Section "Explicit Failure Traps")

Each of the twenty named anti-patterns was checked against the current artifact set. Findings stated
explicitly, including confirmed-absent cases, per this task's instructions not to skip silently.

**Corrective note (Task 10 corrective batch, prompted by the external independent review,
the pre-implementation external review Finding #3):** the table below previously described several of these findings
using "Absent" language that read as a runtime/testing claim (e.g. "`X` exists," "`Y` is tested,"
"`Z` enforces..."), even though this is a pre-implementation, documentation-only gate — no Java
production code exists in this repository beyond the Spring Boot entry point, and nothing has
executed. The wording below has been corrected uniformly: every row now states either (a) the
anti-pattern is not exhibited **by the design as specified**, citing the exact upstream
design/ADR/plan artifact, or (b) the question is not yet assessable because no code exists yet to
assess. No row concludes that a mechanism "exists," "works," "is tested," or "is enforced" in a
runtime sense — that conclusion is only available once Tasks 11+ actually build and exercise the
code. The underlying design-level judgment (which artifact was checked, and whether *that document*
exhibits the anti-pattern) is unchanged from the original scan; only the runtime-implying phrasing
is corrected.

| # | Anti-pattern | Finding |
|---|---|---|
| 1 | Simple linear chaining | **Not yet assessable — no code exists.** The DESIGN does not exhibit this anti-pattern where specified: `plan.md` §3.1/§3.2 and ADR-004 specify `DependencyGraph`/`topologicalBatches()` with real fan-out (Stage 6→{7,9}, 7→{8,10}) and a join (Stage 11), and `orchestration.md` ORCH-CHK-001/004/005 name the tests that will have to prove this once T020/T022 are implemented. Whether the eventual code actually avoids linear chaining is unverifiable until then. |
| 2 | Missing parallel execution | **Not yet assessable — no code exists.** The DESIGN specifies genuine parallel execution: `ExecutorService` + `Future.get()` per batch (ADR-008, `plan.md` §3.1); T022's planned test is required to demonstrate genuine time-overlap, not fast-sequential disguise, once written. |
| 3 | Missing synchronization | **Not yet assessable — no code exists.** The DESIGN specifies Stage 11 as a true join of three branches (`plan.md` §3.3); ORCH-CHK-004 names the test this will require once T022 is implemented. |
| 4 | Missing persistent state | **Not yet assessable — no code exists.** The DESIGN specifies state persisted via JPA after every batch, not only at terminal states (ADR-004; `data-model.md` §3–4; ORCH-CHK-006). |
| 5 | Missing context propagation | **Not applicable to the design as sequenced** — this is a genuine, disclosed gap `plan.md`/`tasks.md` describe T022 as leaving open on purpose, to be closed by T024 (`context_json` reload across resume). The design discloses the gap and its planned closure explicitly; it is not a silent omission. Whether T022/T024's actual code closes it as designed is unverifiable until they are implemented. |
| 6 | Missing decision lineage | **Not yet assessable — no code exists.** The DESIGN specifies `WorkflowContext.recordDecision`/`context_json.decisions[]` (`plan.md` §4; ORCH-CHK-008) as the mechanism; T021 is the task that will implement it. |
| 7 | Unenforced approval | **Not yet assessable — no code exists.** The DESIGN does not exhibit this anti-pattern where specified: `entryApproval`/`exitApproval`, the blocked-batch re-halt guard, and the replan-before-approve ordering rule are specified as real, testable mechanisms (ADR-006; `plan.md` §3.1/§3.4; T023/T035). **Distinct, already-disclosed residual risk** (not this anti-pattern, and separately not a runtime claim): the approval endpoint is *designed* to be unauthenticated (ADR-010) — logically gated by workflow state, but not identity-verified. Already named as the project's single largest disclosed security caveat; not a silent gap. See Fix 5's addition to `plan.md` §8 for the precise wording this project now uses for that endpoint's posture. |
| 8 | Unbounded retry | **Not yet assessable — no code exists.** The DESIGN specifies a declared `maxRetries` and a `RetryExecutor` that rethrows on exhaustion (`plan.md` §6; REL-CHK-001); T030 is the implementing task. |
| 9 | Undefined timeout | **Not yet assessable — no code exists.** The DESIGN specifies `TimeoutExecutor.withTimeout` and `StageDefinition.timeoutSeconds` (default 30s), declared per stage (`plan.md` §3.2/§6); T031 is the implementing task. |
| 10 | Rollback without feasibility | **Not yet assessable — no code exists.** The DESIGN does not exhibit this anti-pattern where specified: CLR-14 explicitly designates the served-redirect case as rollback-infeasible → compensation-only, and ADR-009 explicitly rejects naive rollback-everything (Option A) for exactly this reason. Whether the eventual `CompensationRegistry` code honors this is unverifiable until T032/T033 are implemented. |
| 11 | Missing compensation | **Not yet assessable — no code exists.** The DESIGN specifies a `CompensationRegistry` (ADR-009; `plan.md` §3.4/§6) under which a stage lacking a registered action is designed to fall through to safe-stop (fail-safe, not silent no-op); T032/T033 are the implementing tasks. **Disclosed limitation** (not a runtime claim): the design as currently scoped has no task in T011–T048 that ever produces `StepStatus.ROLLED_BACK` — already named three times in `tasks.md` (T032, T033's evidence row, the closing Summary) as an intentional, disclosed scope decision, not an oversight. |
| 12 | Missing safe-stop | **Not yet assessable — no code exists.** The DESIGN specifies CLR-13's closed two-condition enumeration as the sole safe-stop trigger set; T034 is the planned dedicated convergence test intended to prove no third path exists once T028/T033/T034 are implemented. |
| 13 | Missing resume | **Not yet assessable — no code exists.** The DESIGN specifies `runFrom` reloading `context_json` and re-deriving from persisted step status (REQ-017; `plan.md` §3.4); T024 is the implementing task. |
| 14 | Missing replanning | **Not yet assessable — no code exists.** The DESIGN specifies `WorkflowEngine.replan()` (`plan.md` §3.4; T035), to be exercised again by T041 under a race condition once both are implemented. |
| 15 | Missing audit evidence | **Not yet assessable — no code exists.** The DESIGN specifies an `AuditRecorder`, a six-field event schema, and a `workflow_events` table (`data-model.md` §5; T036; OBS-CHK-003) as the mechanism; none of it has been built or exercised yet. |
| 16 | Claims without validation | **Found — see CRITICAL-1 and HIGH-1.** `plan.md`'s own Authority line claims `spec.md` is "approved" when Section 12 says otherwise; `tasks.md`'s own cross-reference table claims two endpoints are covered by tasks that do not implement them. Both are unvalidated claims already present in committed documents, not merely a risk to watch for. (This finding is itself about documentation-level claims, not runtime behavior, so it required no rewording.) |
| 17 | Weak brownfield analysis | **Not yet assessable as executed evidence, but the DESIGN does not exhibit this anti-pattern where specified.** SCN-B's design (`plan.md` §11.2) is grounded in a real, disclosed pre-existing defect (IPv6 ULA gap in `Inet6Address.isSiteLocalAddress()`), gated by a mandatory impact-analysis approval before implementation, covering all seven named impact dimensions per the `brownfield.md` checklist. Whether T040's actual implementation and `docs/scenarios/brownfield.md` deliver on this design is unverifiable until T040 runs. |
| 18 | Silent ambiguity resolution | **Absent — this is a documentation-level, already-executed finding, not a runtime claim.** All nine original ambiguities (AMB-1..9) plus eight newly-surfaced gaps have an explicit CLR resolution or an explicit, owned deferral (AMB-7, later dispositioned via CLR-17 with a named human approval), recorded directly in `spec.md`/`human-gates-log.md` today. Two resolutions (CLR-4, CLR-15) that exceeded the literal brief scope were self-flagged and separately human-reconfirmed — a stronger-than-required practice, not a weaker one. |
| 19 | Implementation tasks not preceded by tests | **Not yet verifiable (correctly so).** Every task in `tasks.md` is marked `TDD: yes` with an explicit "observed to fail first" expectation, and `tdd.md`'s checklist is honestly self-labeled `UNVERIFIED/PENDING` rather than pre-emptively claimed satisfied. This is the correct pre-implementation state, not a finding of absence or presence. |
| 20 | Tasks inconsistent with ADRs | **Absent — this is a documentation-level, already-executed finding, not a runtime claim.** The two HIGH exceptions in Section 7 are tasks-vs-*plan* gaps rather than tasks-vs-*ADR* gaps; `tasks.md`'s own "Task 7 reconciliation" section explicitly checked ADR alignment and found none needed. This conclusion is verifiable today by reading the documents directly — it draws no inference about code that does not yet exist. |
| — | Documentation disconnected from planned behavior | **Found — see HIGH-1.** `plan.md`/`contracts/openapi.yaml` describe two endpoints as planned, implementation-obligating behavior; `tasks.md` does not implement them and its own summary table asserts they are already covered. |

---

## 12. Corrective Sequence (what must happen, in order, before Phase 2 proceeds cleanly)

1. **(Blocking, CRITICAL-1)** A human reviewer walks `spec.md` Section 12's Human Gate 2 checklist,
   checks its boxes (or requests amendment), and records approval — name and date — in Section 12
   and as a new row in `docs/governance/human-gates-log.md`. This must precede `T011`.
2. **(Should precede or accompany Phase 4, HIGH-1)** Add two tasks (or extend existing ones with
   explicit sub-scope) to `tasks.md` implementing `POST /workflows` and `GET /links/{code}`, each
   with a contract test, and correct the "API/schema deliverable group" cross-reference table's claim
   accordingly.
3. **(Should precede T042/T046, HIGH-2)** Add a task (or extend T039/T042) creating the
   stage-boundary-violation test REQ-031 and `plan.md`'s own traceability table already promise.
4. **(Should precede T046 convergence, MEDIUM-1)** Add one disclosure paragraph to `plan.md` Section
   9 explicitly mapping constitution Principle VI's seven named policy domains to either a concrete
   `PolicyEngine` check or the alternative mechanism covering it.
5. **(No action required now, LOW)** Leave the T033 constructor-parameter-count prose inconsistency
   for the final whole-branch review's Minor-findings triage, as reasoned in Section 9.
6. Re-run this analysis (`/speckit.analyze`-equivalent) after items 1–4 land, to confirm PROCEED
   before `T011` begins in earnest — item 1 in particular is a hard constitutional gate, not a
   discretionary improvement.

---

# Independent Reviewer Gate — Pre-Implementation Principal Engineer Review

Adopting a fresh, skeptical Principal Engineer's stance, reviewing this repository as if submitted by
another candidate — not defending any prior decision because "I" produced it.

**1. Is this genuinely stateful orchestration or sophisticated linear chaining?**
Genuinely stateful, non-linear orchestration, as designed. `DependencyGraph.topologicalBatches()`
(ADR-004, `plan.md` §3.1) groups independent stages into concurrent batches, not a hardcoded call
sequence; state is persisted via JPA after every batch, not only at the end (`plan.md` §3.1,
`data-model.md` §3–4). This is a design claim, correctly labeled as such — no code exists yet to
independently confirm it in execution, and the project's own checklists (`orchestration.md`) know
this and mark themselves `UNVERIFIED/PENDING` rather than claiming otherwise.

**2. Where is parallel execution demonstrated?**
Not yet demonstrated (no code exists). Where it is *designed* to be demonstrated: Stage 6 fans out to
Stages 7 (Implementation) and 9 (Documentation); Stage 7 fans out to Stages 8 (Testing) and 10
(Security) — `spec.md` §3.1, `plan.md` §3.2–3.3, mechanized via `ExecutorService`+`Future.get()`
(ADR-008), with T022's test required to assert genuine time-overlap (`tasks.md` T022 Risk/guardrail:
"replace it with a `CountDownLatch`-based deterministic synchronization check" if timing proves
flaky) rather than accept fast-sequential execution as parallel by default.

**3. Where does synchronization occur?**
Stage 11 (Release-readiness) is designed as a synchronization join of Stages 8, 9, and 10
(`ReleaseReadinessStage.dependsOn = ["testing", "security", "documentation"]`, `plan.md` §3.3).
`orchestration.md` ORCH-CHK-004 requires a test proving actual concurrent overlap before this join,
not sequential completion that merely looks parallel.

**4. How is state persisted and resumed?**
Not yet implemented (no code exists). *Designed* to be: `WorkflowInstanceEntity`/
`WorkflowStepEntity`/`WorkflowEventEntity` via Spring Data JPA over H2 (ADR-002/004), persisted
after every batch. Resumption is designed as `WorkflowEngine.runFrom` reloading `context_json` and
re-deriving the next batch from persisted `workflow_steps.status`, never re-executing a `SUCCEEDED`
step (`plan.md` §3.4, T024). This design closes a gap T022 explicitly and disclosedly leaves open
on its own first pass, per `tasks.md` — a sign of honest incremental design, not a hidden defect.
Whether T021–T024's actual code delivers this is unverifiable until those tasks are implemented.

**5. How is decision lineage preserved?**
Not yet implemented (no code exists). *Designed* to be preserved via
`WorkflowContext.recordDecision(actor, decision, rationale)`, appended to `context_json.decisions[]`
and persisted with the workflow instance (`plan.md` §4, T021). `orchestration.md` ORCH-CHK-008
names the test that will be required, once T021 exists, to prove every branch taken carries a
decision record rather than merely a resulting state transition.

**6. Which actions require human approval?**
Enumerated explicitly in `plan.md` §5's Human-in-the-Loop table: unresolved ambiguity (Stage 3→4),
architecture/design approval (Stage 6 exit gate), security-sensitive policy exceptions, release
readiness (Stage 11 exit gate), and final submission sign-off (Task 47/48). Seven `tasks.md` tasks
(T015, T026, T029, T040, T046, T047, T048) are separately flagged for human review of the
*implementation* of these mechanisms, distinct from the SpecKit human gates that approve upstream
documents (`tasks.md` "Human approval" field legend, line 112–118).

**7. Can mandatory approval be bypassed?**
Not by design: `entryApproval`/`exitApproval` block scheduling/dependents respectively; no code path
is designed to call `approve()` on an agent's own authority (ADR-006 Rationale; `approvals.md`
APPR-CHK-006). **However**, the approval endpoint itself is unauthenticated (ADR-006/ADR-010,
explicitly disclosed as the project's single largest security caveat) — so while the *workflow logic*
cannot bypass approval, *any network caller* can currently supply an approval decision without being
verified as the intended human. This is disclosed, not hidden, but it is a real bypass vector for a
real deployment of this exact code, correctly named as a residual risk rather than a closed matter.

**8. Are retry boundaries explicit?**
Not yet implemented (no code exists). *Designed* to be explicit: `StageDefinition.maxRetries`,
declared per stage, with `RetryExecutor.withBoundedRetry` specified to rethrow immediately on
exhaustion (CLR-12, `plan.md` §6, `reliability.md` REL-CHK-001). T030 is the implementing task;
whether the built code honors this bound is unverifiable until then.

**9. Where is timeout behavior defined?**
Not yet implemented (no code exists). *Designed* as `StageDefinition.timeoutSeconds` (default 30s
per `StageDefinition.of`'s stated default), specified to be enforced per-attempt via
`TimeoutExecutor.withTimeout` independently of the retry loop (`plan.md` §6, T031). T031 is the
implementing task.

**10. Is rollback technically feasible?**
Not yet implemented (no code exists) — this is a design-feasibility judgment, not a runtime
observation. As *designed*, rollback is only claimed where genuinely reversible: an
in-transaction, not-yet-committed effect is specified to roll back for free via transaction abort
(ADR-009). Where it is not feasible — a served redirect, CLR-14's confirmed case — the design
correctly refuses to claim rollback and specifies compensation instead. This is the strongest part
of the reliability *design*: it names the *infeasible* case explicitly rather than papering over it
with a generic "rollback" claim. Whether ADR-009's `CompensationRegistry` code actually behaves this
way is unverifiable until T032/T033 are implemented.

**11. Where is compensation used instead?**
Not yet implemented (no code exists). *Designed* as `CompensationRegistry.register(stageName,
action)`/`run(...)`, to be invoked for externally-observed effects (the served-redirect case being
canonical, ADR-009). A stage with an externally-visible effect but no registered compensation is
specified to fall through to safe-stop — a disclosed fail-safe design, not a silent no-op — once
T032/T033 build it.

**12. What triggers safe-stop?**
Not yet implemented (no code exists). *Designed* as a closed, two-condition enumeration (CLR-13):
(a) a mandatory policy `FAIL` with no approved exception, or (b) bounded retries exhausted with no
applicable compensation. `tasks.md` T034 names a planned dedicated convergence test whose specific
job will be proving no third, divergent safe-stop path exists, once T028/T033/T034 are implemented.

**13. How does dynamic replanning avoid uncontrolled change?**
Not yet implemented (no code exists). *Designed* so that `replan()` only affects stages reachable
from a named point forward (never rewrites/deletes prior `workflow_events`/`workflow_steps` rows),
bumps `plan_version`, and records a `replanned` audit event with what changed, why, and under whose
authority (`plan.md` §3.4, ORCH-CHK-010/011). The replan-before-approve ordering rule is specified
as a fixed sequencing constraint intended to prevent a race where execution resumes against a
not-yet-updated graph (ADR-004 Risks) — a property T035/T041 are planned to test once written, not
one confirmed by any test that has run yet.

**14. Can every major claim be validated?**
Every claim reviewed cites a specific file/section/test-class name that will exist once Tasks 11+
run, rather than a vague assertion ("will be tested thoroughly"). The one place this discipline
currently *fails* is exactly CRITICAL-1: `plan.md`'s claim that `spec.md` is "approved" is not
validated by anything in the repository — `spec.md`'s own Section 12 and `human-gates-log.md` both
contradict it. That is a validation failure in a governance document, at this exact gate, not a
future-tense concern.

**15. Are accepted ADRs reflected in tasks?**
Yes, cross-checked in Section 3.5/6 above — no ADR is un-reflected, and no task contradicts an
Accepted ADR's decision.

**16. Are requirements independently testable?**
Yes for all 31 — every requirement carries both a positive and a negative acceptance criterion
(`spec.md` §0's own convention, verified present for REQ-001..031), and REQ-029 is itself the
requirement that this property hold, self-referentially satisfied by the document's own structure.

**17. Are tasks small and dependency-correct?**
Yes, with declared prerequisites and explicit parallel-safety notes per task (`tasks.md` "Prerequisites"/
"Parallel" fields), and a stated sequential-blocking-groups summary at the document's end matching the
individual task declarations. No task was found with an undeclared or incorrect dependency during
this review, other than the two coverage gaps in Section 7 (which are *missing* tasks, not
*incorrectly-sequenced* ones).

**18. Is the brownfield scenario credible?**
Yes — a genuine, disclosed pre-existing gap (`Inet6Address.isSiteLocalAddress()` only covering the
deprecated `FEC0::/10` range, missing RFC 4193 `fc00::/7`), not a contrived example, cross-referenced
consistently between `plan.md` §8/§11.2, ADR-010, `security.md` SEC-CHK-003, and `tasks.md` T040. The
impact-analysis-before-implementation gate is a real approval gate (`exitApproval`), not cosmetic.

**19. Does the ambiguous scenario truly suspend unsafe execution?**
As designed: Stage 5's entry criterion ("Stage 3 clear or Stage 4 approved") is a hard, structural
block, not a soft delay — `ambiguous.md` AMB-CHK-002 specifically requires a test proving Decomposition
cannot even partially begin before Stage 4 resolves. The chosen example (`"add rate limiting"` with no
numeric target) is genuinely ambiguous by construction, being the same gap CLR-11 already names as
unresolved — not a strawman engineered to trivially trigger the branch.

**20. Is the evidence strategy authentic?**
Yes, as designed: scenario docs are required to be generated from real `GET /workflows/{id}/audit`
output, not hand-written narrative (`greenfield.md` GF-CHK-006); metrics are required to be tagged
`isDemonstrationData: true` and never presented as production-grade (`observability.md` OBS-CHK-008);
a disclosed process incident (`12f1353`) is required to be named in the final summary rather than
omitted (`github-evidence.md` GH-CHK-005). The one place this same standard is **not** currently met
is CRITICAL-1 — an assertion (`spec.md` is "approved") stated as fact in `plan.md` without the
underlying evidence existing.

---

## Verdict

# PROCEED WITH CONDITIONS

## Critical Findings

- **CRITICAL-1**: `spec.md` Human Gate 2 (Section 12, whole-specification approval) has never been
  recorded — every checklist box is unchecked and the document's own status line reads "DRAFT —
  PENDING HUMAN APPROVAL" — yet `plan.md`'s Authority section and every downstream artifact treat
  `spec.md` as approved. This directly implicates non-waivable constitution Principle I and is
  independently flagged as MANDATORY by this project's own `requirements.md` REQ-CHK-013 and
  `approvals.md` APPR-CHK-011.

## Architectural Weaknesses

None found rising above the two HIGH coverage gaps already itemized (Section 7). The orchestration
architecture (ADR-004/005/008) is coherent, its trade-offs are honestly assessed, and no architectural
decision contradicts a confirmed requirement.

## Orchestration Weaknesses

None found in the *design*. The one real gap is evidentiary, not architectural: REQ-031's named
stage-boundary-violation test has no task (HIGH-2).

## Governance Weaknesses

- CRITICAL-1 (above) is fundamentally a governance-process weakness, not a technical one.
- MEDIUM-1: policy-domain coverage against constitution Principle VI's seven named domains is not
  explicitly disclosed as a scoped-down decision in one place (Section 10).

## Testing Weaknesses

- HIGH-1: no task implements or contract-tests `GET /links/{code}` or `POST /workflows`, both of
  which `plan.md`/`contracts/openapi.yaml` explicitly obligate.
- HIGH-2: no task implements the stage-boundary-violation test REQ-031 itself names as its evidence.

## Evidence Weaknesses

None beyond CRITICAL-1 (an unsubstantiated "approved" claim) and the two HIGH findings' downstream
consequence (evidence for REQ-011's externally-reachable creation path and REQ-031's negative
criterion will be incomplete unless the missing tasks are added before Tasks 11+ conclude).

## Required Corrections Before Implementation

1. **(Blocking)** Human reviewer records Human Gate 2 approval in `spec.md` Section 12 and
   `docs/governance/human-gates-log.md`, before `T011` starts.
2. Add tasks implementing `POST /workflows` and `GET /links/{code}` (with contract tests) to
   `tasks.md`, correcting its "API/schema deliverable group" cross-reference table.
3. Add a task creating the stage-boundary-violation test for REQ-031 to `tasks.md`.
4. Add one disclosure paragraph to `plan.md` Section 9 mapping Principle VI's seven policy domains to
   their covering mechanism.
5. No action needed on the T033 constructor-parameter-count prose inconsistency — correctly deferred
   to the final whole-branch review's Minor-findings triage.

Condition 1 is a hard constitutional gate and must close before Phase 2 begins in earnest. Conditions
2–4 should close before Phase 9 (scenario) and Phase 11 (convergence) tasks run, since those tasks
depend on the completeness these conditions supply, but do not block the earliest Phase 2/3/4 tasks
(T011–T026) from starting once Condition 1 is satisfied.
