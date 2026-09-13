# Feature Specification: Agentic Software Engineering System: URL Shortener

**Feature Branch**: `001-agentic-url-shortener`

**Created**: 2026-09-12

**Status**: **DRAFT — PENDING HUMAN APPROVAL (Human Gate 2).** This document was authored by an
agent acting under the project's SpecKit lifecycle. No requirement, scenario, assumption, or
proposed validation target in this document is "approved," "confirmed as a client requirement,"
or "in effect" until a human reviewer completes Human Gate 2 (Section 12) and records approval.
Until then, downstream artifacts (clarification records, plan, ADRs, tasks) that cite this
specification are citing a draft.

**Clarification status**: `/speckit.clarify` (Human Gate 3) was completed on 2026-09-12 — see
Section 13. Every material ambiguity flagged in the original Section 9.5 has been either resolved
by an explicit controller decision (recorded in Section 13.1) or carried forward as an explicitly
recorded deferred question with an owner (Section 13.2). **This does not substitute for Human Gate
2** (immediately below): the specification as a whole remains a DRAFT pending that separate,
broader approval. Human Gate 3's completion means the *ambiguities* are resolved or explicitly
deferred, not that the *specification* is approved.

**Authority**: This specification sits directly beneath `docs/governance/constitution.md`
(version 1.0.0, **Ratified** — Human Gate 1 closed 2026-09-13; see
`docs/governance/human-gates-log.md` for the approval record) in the project's authority
hierarchy. Nothing in this document may contradict any of the constitution's eleven principles;
where a tension was found while drafting, it is surfaced explicitly in Section 9 rather than
silently resolved. This specification does not choose a programming language, framework, database,
cloud provider, or agent-orchestration library — those are Architecture Decision Record (ADR) and
`/speckit.plan` stage decisions (see Section 10).

**Input**: `.superpowers/sdd/2026-09-12-agentic-url-shortener-sdlc/task-4-source-material.md`
("Prompt 2: Feature Specification"), interpreted at authoring time under
`docs/governance/constitution.md` v1.0.0, which was itself still in draft pending Human Gate 1 at
that moment — that gate has since closed and the constitution is now **Ratified** (see the
Authority line above; corrected by the Task 10 corrective batch, `codex-recommend.md` Finding
#12).

---

## 0. Document Conventions

- **Requirement IDs** (`REQ-001`–`REQ-031`) are stable and permanent once this draft is accepted
  into the repository. They are never renumbered or reused, even if a requirement is later
  deprecated (a deprecated requirement is marked `[DEPRECATED]` in place, not deleted or
  renumbered) — later stages (clarification, plan, ADRs, tasks, the traceability matrix) reference
  these IDs directly.
- **Scenario IDs** are `SCN-A` (Greenfield), `SCN-B` (Brownfield), `SCN-C` (Ambiguous Requirement),
  exactly as named in the source material.
- **MUST** denotes a confirmed, testable obligation. **SHOULD** is not used for obligations in this
  document — anything not a firm MUST is instead surfaced as an assumption or an open ambiguity, so
  nothing is quietly downgraded to optional.
- Every requirement in Sections 6 and 7 carries **both** a positive acceptance criterion (what
  observably proves the requirement is met) **and** a negative acceptance criterion (what
  observably proves the wrong or unsafe behavior does *not* happen). Neither is omitted for any
  requirement.
- Numeric performance/reliability-style targets appear **only** under a heading explicitly marked
  **"Proposed validation target (assumption — requires approval)."** No numeric target in this
  document is to be read as a confirmed client requirement; the assignment source material
  supplied no numeric targets, and none has been invented as if it had.
- Wherever this document was tempted to name a technology, language, framework, database, cloud
  service, or agent-orchestration library, it instead records a **deferred technology decision
  pointer** (Section 10) to the ADR/plan stage.

---

## 1. Business Objective

Demonstrate how a governed agentic execution system transforms software requirements into
reviewable engineering outcomes across the complete software development lifecycle (SDLC). The
system must demonstrate, and this specification must make testable: requirement understanding,
ambiguity detection, requirement normalization, decomposition, architecture and design, controlled
implementation, testing and validation, documentation, security and risk checks, release
readiness, human oversight, and final engineering reporting.

Per `docs/governance/constitution.md` Section 1, the URL shortener domain is deliberately simple.
Its simplicity is intentional: the thing actually under specification, and later under review, is
the governed, stateful, non-linear orchestration **process** — not the shortener as a product.

## 2. Demonstration Domain

The system must demonstrate a URL shortener service, treated as a production-oriented prototype
(not a toy), supporting the following capabilities. Each capability is implementation-independent
here; concrete interfaces, data models, and technology are ADR/plan-stage decisions.

- Short URL creation
- Short-code generation
- Redirect resolution
- Basic redirect analytics
- Expiration
- Validation (of submitted input, per constitution Principle V)
- Reliability controls (per constitution Principle VIII)
- Appropriate failure handling
- Operational health reporting

### 2.1 Key Entities (conceptual — no schema, storage engine, or field types implied)

- **Link (Short URL Mapping)**: an association between a system-issued or requested short
  identifier and a destination (long) URL, with a creation time and an optional expiration
  condition. Whether a Link is mutable after creation is an open ambiguity (Section 9, AMB-6).
  **[RESOLVED — CLR-15, Section 13.1; CONFIRMED by human candidate 2026-09-13]** A Link is
  immutable once created: neither its destination URL nor its expiration condition may be changed
  after creation in this iteration. There is no update/PATCH capability. This was originally an
  agent-inferred resolution (pattern-analogy, not one of the clarification brief's ten literal
  items) and was explicitly flagged for human review; the human candidate has now confirmed it as
  the correct design decision. The brownfield scenario (SCN-B) targets a different, non-mutating
  enhancement as a result — see Section 6/13.1 note.
- **Short Code**: the identifier portion of a Link that a caller presents to be resolved. Uniqueness
  of active Short Codes is a confirmed requirement (REQ-003); the generation strategy is not
  specified here. **[RESOLVED — CLR-3/CLR-4, Section 13.1]** Every Short Code is exactly 7
  characters drawn from the base62 character set (`[a-zA-Z0-9]`) and is always system-generated;
  caller-supplied ("custom alias") short codes are out of scope for this iteration. The concrete
  generation *algorithm* (e.g., random vs. counter-based) is still not decided here — it remains a
  Section 8 deferred technology/implementation decision pointing to ADR-003 at the plan stage; only
  the code's testable *properties* (length, character set, system-generated-only) are confirmed as
  requirements here.
- **Redirect Event**: a record that a resolution of a given Short Code occurred, used as the basis
  for analytics (REQ-006). Conceptually distinct from the Link itself — a Link may have zero, one,
  or many associated Redirect Events.
- **Workflow Run**: one execution instance of the orchestration graph (Section 3) against one
  ingested requirement, carrying a run identifier, persisted stage states, decision lineage, and a
  terminal outcome.
- **Stage**: one node in the orchestration graph (e.g., Decomposition, Testing) with declared
  inputs, outputs, entry criteria, exit criteria, an owner (an agent role or "human"), and a
  declared failure behavior.
- **Approval Gate**: a point in a Workflow Run where a human-owned decision (Section 5, "human
  reviewer or approver") is required before a dependent Stage may proceed.
- **Policy Check Result**: the outcome of evaluating a Workflow Run or a Stage against a versioned
  policy guardrail, always exactly one of `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, or
  `NOT-APPLICABLE` (constitution Principle VI).
- **Audit Record**: one entry in a Workflow Run's evidence trail capturing actor, action,
  timestamp, affected artifact/state, result, and reason (constitution Principle IX).
- **Exception**: a documented, bounded, time-limited, human-approved deviation from a policy or
  principle (constitution Section 4.2), never a default state.

## 3. Primary Capability: Agentic SDLC Orchestration

The orchestration system's differentiator, per constitution Principle II, is that it is **not** a
script calling agents one after another. It is represented here as an explicit dependency graph
over discrete stages, capable of sequential paths, parallel paths, synchronization points,
conditional branching, dynamic replanning, and interruption/resumption. The stage list below is
the same 12-item list named in the source material; it is presented as a directed graph — with
predecessors, parallel edges, and a synchronization point — specifically so it is not read as a
linear pipeline.

### 3.1 Orchestration Stage Graph (baseline — instantiated and possibly adapted per scenario, Section 4)

| # | Stage | Depends on (predecessor(s)) | Runs in parallel with | Owner | Illustrative entry criterion | Illustrative exit criterion | Failure behavior pointer |
|---|---|---|---|---|---|---|---|
| 1 | Requirement ingestion | — (graph root) | — | Agent (orchestrator) | A requirement input exists | Requirement is captured verbatim with provenance | Safe-stop if input is unreadable/absent (REQ-016) |
| 2 | Requirement normalization | 1 | — | Agent | Stage 1 complete | Requirement is restated in the project's normalized requirement form | Retry (transient parse failure) or safe-stop (REQ-014, REQ-016) |
| 3 | Ambiguity detection | 2 | — | Agent | Stage 2 complete | Requirement is scored complete/consistent/testable/in-policy, **or** flagged ambiguous | Safe-stop on inconclusive scoring (REQ-016) |
| 4 | Human clarification | 3 (**conditional**: only when Stage 3's exit outcome is "ambiguous") | Documentation of already-decided, unaffected parts (Stage 9) may continue for any *other* concurrently in-flight, unrelated requirement | Human | Stage 3 flagged ambiguity | Human decision recorded with rationale (Approval Gate) | Safe-stop while pending; no timeout-implied approval (REQ-013, Principle III) |
| 5 | Task decomposition | 3 (if exit = clear) **or** 4 (if Stage 4 was invoked and approved) | — | Agent, human-reviewable | Stage 3 (clear) or Stage 4 (approved) complete | Decomposition into reviewable units exists with acceptance criteria per unit | Safe-stop on incoherent decomposition (REQ-016) |
| 6 | Architecture and design | 5 | — | Agent, human-reviewable | Stage 5 complete | Design, API/schema impacts recorded | Safe-stop if design cannot satisfy decomposition (REQ-016) |
| 7 | Implementation | 6 | — | Agent | Stage 6 complete (and Approval Gate passed, if the design was material — REQ-013) | Code exists satisfying acceptance criteria, tests written first (Principle IV) | Bounded retry on transient tool failure; safe-stop on repeated failure (REQ-014, REQ-016) |
| 8 | Testing | 7 | Stage 10 (Security and risk validation) | Agent | Stage 7 complete | All applicable test levels (Principle IV) executed and observed passing | Safe-stop on unresolved failing test (REQ-016) |
| 9 | Documentation | 6 (needs design/API decisions, **not** Stage 7's completion) | Stages 7 and 8 (Implementation, Testing) | Agent | Stage 6 complete | Docs reflect decided design/API/schema | Retry/safe-stop as for other agent stages |
| 10 | Security and risk validation | 6, 7 (join) | Stage 8 (Testing) | Agent, human-reviewable for exceptions | Stages 6 and 7 complete | Policy checks (Principle VI) each resolve to `PASS`/`FAIL`/`EXCEPTION-REQUESTED`/`NOT-APPLICABLE` | `FAIL` blocks the dependent branch (REQ-016); `EXCEPTION-REQUESTED` requires Approval Gate (REQ-013) |
| 11 | Release-readiness determination | 8, 9, 10 (**synchronization point** — join of three branches) | — | Agent, human sign-off | Stages 8, 9, 10 complete | Deterministic ready/not-ready outcome derived from actual policy/test/exception state (REQ-020) | Safe-stop / not-ready outcome if any input is `FAIL` or unapproved `EXCEPTION-REQUESTED` |
| 12 | Final engineering summary | 11 | — | Agent, human-reviewed | Stage 11 complete | Summary compiled from persisted evidence, not narrative recollection | Safe-stop if evidence trail is incomplete (Principle IX, XI) |

This graph is the baseline; Section 4 shows how each required scenario instantiates, extends, or
takes conditional/parallel paths through it — none of the three scenarios is a straight walk from
node 1 to node 12.

### 3.2 Orchestration Behavioral Characteristics → Requirement Mapping

The orchestration characteristics named in the source material are not independent, free-floating
capabilities — each maps onto one or more of the orchestration functional requirements in Section
6.2, so nothing on this list is left untested:

| Orchestration characteristic (source material) | Realized primarily via |
|---|---|
| Explicit dependencies / equivalent state transitions | REQ-011 (Workflow creation) |
| Sequential and parallel activity support | REQ-011; Section 3.1 graph (Stages 8/9/10 parallel edges) |
| Synchronization of dependent activities | REQ-011; Section 3.1 Stage 11 join |
| Persisted workflow state | REQ-012 (Workflow inspection) |
| Preserved context and decision lineage | REQ-012, REQ-019 (Audit inspection) |
| Enforced stage entry/exit conditions | REQ-011; Section 3.1 per-stage criteria |
| Enforced human approval checkpoints | REQ-013 (Approvals and rejection) |
| Bounded retries | REQ-014 (Retries and timeouts) |
| Fallback support | REQ-014 |
| Rollback or compensation support | REQ-015 |
| Safe-stop when continuation is unsafe | REQ-016 |
| Safe resumption after recoverable interruption | REQ-017 (Workflow resumption) |
| Dynamic replanning on changed upstream outputs | REQ-018 (Dynamic replanning) |
| Preserved governance during replanning | REQ-018 |
| Audit-grade execution evidence | REQ-019 |
| Exposed reliability/orchestration measurements | REQ-025, REQ-026 (Observability, Auditability) |

## 4. Required Scenarios

Each scenario below reproduces the source material's exact scenario text (blockquoted, unedited),
followed by this specification's elaboration into a non-linear dependency structure over the
Section 3.1 graph. **The blockquoted arrow notation in the source material is a coarse
happy-path reading aid, not the authoritative execution model** — the authoritative model is the
dependency table and narrative beneath it, which is deliberately not a linear chain (parallel
branches, conditional branches, a join, and suspend/resume are all present).

### SCN-A — Greenfield

> Demonstrate a new capability moving through:
> A well-defined greenfield requirement must proceed without an artificial clarification gate when
> it is complete, consistent, testable, and within approved policy and architecture boundaries.
> The workflow must record the requirement-quality checks performed, the reason clarification was
> not required, the resulting decomposition, API and schema impacts, acceptance criteria, and
> traceable execution evidence.
> If material ambiguity emerges later, suspend only the affected path, invoke governed
> clarification and impact analysis, and resume from the correct state after explicit approval.
>
> Requirement → Decomposition → Design → Implementation → Testing → Documentation → Validation →
> Release Readiness

**Non-linear elaboration.** SCN-A instantiates the full Section 3.1 graph with Stage 4 (Human
Clarification) *not* triggered on the initial pass — Stage 3's exit outcome records the specific
quality checks performed (complete / consistent / testable / in-policy-and-architecture-bounds)
and the explicit reason clarification was skipped, per constitution Principle I. Decomposition (5)
and Design (6) proceed; from Design, Documentation (9) and Implementation (7) fan out in parallel
(Documentation does not wait on Implementation to finish); Testing (8) and Security validation (10)
run concurrently once Implementation completes; Release-readiness (11) is a synchronization point
that joins all three. If, **during Implementation (7) or Testing (8)**, a material ambiguity is
discovered (e.g., an edge case the original requirement did not address), only the affected branch
is suspended: the Implementation/Testing sub-path enters a safe-stop pending clarification while
any unaffected, already-decided Documentation work already in flight is not discarded. A governed
Human Clarification (4) sub-flow is invoked, an impact analysis is produced over the artifacts
already built, and — only after recorded human approval — the graph resumes at the suspended node
(not by restarting from Requirement ingestion), with the replan itself recorded as an auditable
event (REQ-018).

**Primary requirements exercised**: REQ-001–REQ-010 (domain), REQ-011, REQ-012, REQ-013, REQ-016,
REQ-017, REQ-018, REQ-019, REQ-020.

### SCN-B — Brownfield

> Demonstrate an enhancement, refactor, or defect correction against the existing URL shortener.
>
> Before changing code, the system must identify:
>
> - impacted components
> - impacted interfaces
> - impacted data flows
> - impacted tests
> - impacted documentation
> - regression risks
> - rollout or rollback considerations

**Non-linear elaboration.** SCN-B inserts a **Change Impact Analysis** stage-group between
Decomposition (5) and Design (6) (or, if the change is implementation-only, between Decomposition
and Implementation). The seven impact dimensions named above (components, interfaces, data flows,
tests, documentation, regression risk, rollout/rollback) are modeled as **parallel sibling
branches** that all depend on Decomposition and have no ordering constraint relative to each other
— they execute concurrently, not as seven sequential sub-steps — and all **join** into a single
Change Impact Report. That Report is a mandatory Approval Gate (REQ-013): Implementation (7) may
not begin until a human has reviewed and approved the joined Report, distinguishing this from
SCN-A precisely because existing behavior and existing consumers are now at risk. The rollout/
rollback branch's output becomes a required exit-criterion input to Release-readiness (11): a
brownfield change cannot reach a "ready" outcome (REQ-020) without a recorded rollback or
compensation plan (REQ-015). If regression tests (part of the impacted-tests branch, feeding
Testing at Stage 8) fail, the branch safe-stops (REQ-016) rather than proceeding to Documentation
or Release-readiness.

**Primary requirements exercised**: REQ-001–REQ-010 (as applicable to the changed capability),
REQ-011–REQ-013, REQ-015, REQ-016, REQ-019, REQ-020, REQ-030 (Change safety).

### SCN-C — Ambiguous Requirement

> Demonstrate incomplete, unclear, or conflicting input.
>
> The workflow must:
>
> - detect the ambiguity
> - prevent unsafe implementation
> - request human clarification
> - record the decision
> - identify downstream impact
> - update affected artifacts
> - resume at the correct state

**Non-linear elaboration.** SCN-C exercises the **conditional branch** at Stage 3→4 in Section 3.1
as its primary path, not as a side note: Ambiguity detection (3) exits with an "ambiguous" outcome,
which — by construction of the graph's entry criteria — makes Decomposition (5) unable to start (its
entry criterion, "Stage 3 clear or Stage 4 approved," is unmet). This is a **hard fan-out block**,
not a delay: no downstream node may silently proceed on an unclear input (constitution Principle
I). Human Clarification (4) is invoked as an Approval Gate; while it is pending, the run sits in a
recorded safe-stop state (REQ-016), not a silent hang. Once a human records a decision (approve
with an answer, or reject/redirect), that decision is recorded with rationale (REQ-013, REQ-019),
an impact analysis runs over **any artifacts that already existed** before the ambiguity was
detected (e.g., a prior partial decomposition from an earlier, related requirement) to identify
what those artifacts must now change, those artifacts are updated, and the graph then **resumes
Decomposition (5)** — the correct suspended node — rather than restarting the whole run from
Requirement ingestion (1). This is a dynamic replan (REQ-018) triggered specifically by the
clarification outcome, and it must preserve, not erase, the audit trail of Stages 1–3 as they
already executed.

**Primary requirements exercised**: REQ-011, REQ-013, REQ-016, REQ-017, REQ-018, REQ-019.

## 5. User Types & Journeys

Each journey is independently testable and references the requirement(s) it exercises.

### 5.1 API Consumer

- **Journey**: submits a long URL for shortening, later presents the returned short code to be
  redirected.
- **Given** a valid `https://` URL, **when** submitted for creation, **then** a resolvable short
  code is returned (REQ-001) and later resolves to the original destination until expiry (REQ-004,
  REQ-005).
- **Given** a malformed or disallowed-scheme URL, **when** submitted, **then** a clear rejection is
  returned and no mapping is created (REQ-002).

### 5.2 Software Engineer (acting under the orchestration system, or reviewing its output)

- **Journey**: a requirement is ingested on their behalf; they inspect decomposition, design, and
  generated implementation/test artifacts as the workflow proceeds.
- **Given** a workflow run in progress, **when** the engineer inspects it, **then** they see
  current stage status, decomposition, and design/API impact without needing an agent's narrative
  (REQ-012, REQ-019).

### 5.3 Human Reviewer / Approver

- **Journey**: is presented with an Approval Gate (a clarification question, a change-impact
  report, a policy exception request) and must approve, reject, or let it safe-stop.
- **Given** a pending Approval Gate, **when** the reviewer approves or rejects, **then** the
  recorded decision determines whether the dependent branch proceeds, with no default-approval on
  silence (REQ-013).

### 5.4 Release Owner

- **Journey**: consults the release-readiness determination before deciding whether to present the
  system for final review.
- **Given** a completed workflow run, **when** the release owner requests the release-readiness
  outcome, **then** they receive a deterministic ready/not-ready result with the specific
  policy/test/exception state that produced it — not a subjective claim (REQ-020).

### 5.5 Assessment Reviewer

- **Journey**: independently audits the whole submission — process and product — without taking
  any agent's self-report at face value.
- **Given** a completed run, **when** the assessment reviewer opens its audit trail, **then** they
  can reconstruct what happened, in what order, who/what caused it, and the result, for every
  stage, decision, retry, and replan (REQ-019, REQ-026), and can trace any single requirement
  through to its evidence (Section 11).

## 6. Functional Requirements

Every requirement below MUST hold. Each carries a positive acceptance criterion (proves correct
behavior occurs) and a negative acceptance criterion (proves incorrect/unsafe behavior does not
occur).

### 6.1 Domain (URL Shortener) Functional Requirements

**REQ-001 — Valid URL creation**
The system MUST accept a request to create a short URL for a syntactically valid, policy-permitted
long URL and return a resolvable short identifier.
- *Positive*: Given a well-formed, allow-listed-scheme long URL within policy bounds, when
  submitted for creation, then the system returns a unique short identifier and the mapping is
  subsequently resolvable (REQ-004).
- *Negative*: The system does not silently alter the destination URL beyond a documented,
  policy-approved normalization rule, and does not return a short identifier that collides with an
  existing, distinct active mapping (REQ-003).
- *Evidence*: creation API contract test; persisted-mapping inspection.

**REQ-002 — Invalid URL rejection**
The system MUST reject a request to shorten a URL that is malformed, empty, exceeds policy length,
or uses a scheme outside an explicit allow-list (constitution Principle V).
- *Positive*: Given input using a disallowed scheme (e.g., a scheme chosen specifically to enable
  abuse) or malformed syntax, when submitted, then the system rejects it with a distinguishable
  error and creates no mapping.
- *Negative*: The system does not create a mapping for rejected input under any circumstance, and
  does not "auto-correct" a disallowed scheme into an allowed one absent an explicit, documented,
  policy-approved rule for doing so.
- *Evidence*: rejection contract tests enumerating each disallowed input class; security test
  suite (Principle IV, V).
- **[RESOLVED — CLR-1 and CLR-5, Section 13.1]** The explicit scheme allow-list is exactly `http`
  and `https`; `javascript:`, `file:`, `data:`, and every other scheme are rejected. A long URL
  whose host resolves to a loopback, link-local, or private RFC1918 address MUST also be rejected
  at creation time (SSRF guard) — this is a confirmed requirement, not merely a "disallowed scheme"
  example. The maximum accepted long-URL length is 2048 characters; longer input is rejected the
  same way as a malformed URL. (These were previously open under AMB-5 and the undecided allow-list
  content implied by this requirement's own text.)

**REQ-003 — Short-code uniqueness**
The system MUST ensure every active short code is unique among currently active mappings at the
time of creation.
- *Positive*: Given N creation requests for distinct long URLs, when codes are generated, then
  every resulting active short code is distinct.
- *Negative*: The system does not issue the same active short code for two different destination
  URLs concurrently, and a detected collision never surfaces as a successful creation.
- *Evidence*: collision/concurrency test.

**REQ-004 — Redirect resolution**
The system MUST resolve a valid, active short code to its destination.
- *Positive*: Given an existing, non-expired short code, when resolution is requested, then the
  system returns/redirects to the original destination.
- *Negative*: The system does not redirect a request bearing an unknown, malformed, or
  never-issued short code to any destination — including a default or fallback destination that
  could be mistaken for a genuine mapping — and instead returns a distinguishable not-found
  outcome.
- *Evidence*: resolution contract tests, including the unknown-code case.

**REQ-005 — Expired-link behavior**
The system MUST refuse to redirect a short code once it has passed its defined expiration
condition.
- *Positive*: Given a short code past its expiration point, when resolution is requested, then the
  system does not redirect and returns a distinguishable expired outcome.
- *Negative*: The system does not redirect an expired code to its original destination under any
  circumstance. (Whether "expired" and "not found" must be distinguishable to an external caller,
  versus intentionally identical for information-disclosure reasons, is an open ambiguity — see
  AMB-2, Section 9 — and is not silently resolved here.)
- *Evidence*: expiration-boundary test (immediately before, at, and after expiry).
- **[RESOLVED — CLR-7, Section 13.1]** "Expired" and "unknown/never-issued" (REQ-004) MUST be
  distinguishable to the caller: an expired short code returns a distinguishable expired outcome
  (`410 Gone`-equivalent), while an unknown code returns a distinguishable not-found outcome
  (`404`-equivalent). Information-disclosure-by-indistinguishability was considered and explicitly
  rejected as the operative model for this iteration. (Originally AMB-2; the parenthetical above is
  preserved for history — this bullet is its resolution, not its replacement.)
- **[RESOLVED — CLR-6, Section 13.1]** Expiration is optional and caller-set at creation time via an
  `expires_at` condition; the default when omitted is **no expiration** (the Link never expires on
  its own). This was previously unspecified mechanism/default behavior underlying this requirement.

**REQ-006 — Analytics capture**
The system MUST record a basic analytics event for each successful redirect resolution.
- *Positive*: Given a successful redirect, when it occurs, then an analytics event (at minimum:
  which short code, when) is recorded without materially delaying the response returned to the
  caller.
- *Negative*: The system does not fail a redirect to the caller solely because analytics recording
  failed (analytics capture is not on the critical path for a successful redirect — see REQ-009),
  and does not record secret or disproportionate personal data in analytics beyond what is
  explicitly declared acceptable (constitution Principle V).
- *Evidence*: analytics-capture test; failure-injection test (analytics store unavailable, redirect
  still succeeds).
- **[RESOLVED — CLR-8, Section 13.1]** The confirmed analytics data model is: redirect count
  (a per-Link counter) and last-accessed timestamp. Consistency model: **eventually consistent** —
  an asynchronous, non-blocking increment relative to the redirect response is acceptable. This is
  recorded as a proposed validation target for the demonstration, not a hard production consistency
  guarantee (this requirement's critical-path/failure-isolation obligation above is unaffected and
  remains a hard MUST).

**REQ-007 — No creation-time deduplication (formerly "Duplicate request / idempotency behavior") [RESTATED 2026-09-13 — see CLR-2, Section 13.1; original text and criteria preserved below as historical/backlog, per this document's never-delete convention]**

**Current text (operative, post-CLR-2):** The system MUST treat every accepted creation request as
independent: no destination-URL match or payload match against a prior request causes the system
to return a previously-issued short code, reject the request as a duplicate, or otherwise alter
the request's outcome.
- *Positive (current, operative)*: Given two creation requests with identical destination URLs (or
  even a byte-for-byte identical full payload), when both are submitted, then each independently
  produces a distinct, new short code with its own database row — neither is treated as a repeat of
  the other.
- *Negative (current, operative)*: The system never returns a previously-issued short code in
  response to a new creation request, regardless of how closely that request matches an earlier
  one, and never rejects a creation request on the grounds that it duplicates a prior one.
- *Evidence*: a duplicate-URL creation test asserting two independently-created, distinct short
  codes for the same destination URL (replacing the original idempotency-replay test, which tested
  for behavior this iteration does not implement).
- *Status*: confirmed MUST for this iteration's release-readiness determination (REQ-020) — this is
  now what the system must actually do, not a downgraded/backlog item itself. What *is* backlog is
  the original idempotency-key/payload-based dedup behavior, preserved below.

**Original text and criteria (historical — NOT current behavior, preserved for traceability and
possible future revival, per CLR-2's resolution):** *The system MUST avoid creating functionally
duplicate mappings from a request the caller reasonably intended as a single retried operation.*
- *Positive (original, backlog)*: Given a creation request that is a retry of a prior request under
  the project's (to-be-clarified, see AMB-1) definition of "duplicate," when resubmitted, then the
  system returns a consistent, previously-issued result rather than creating a second independent
  mapping.
- *Negative (original, backlog)*: The system does not silently create two independent short codes
  for what was, by the applicable duplicate-detection rule, a single retried request, and does not
  leave the caller unable to determine whether their retry succeeded.
- **Resolution (CLR-2, Human Gate 3, 2026-09-12)**: AMB-1 is resolved by explicitly rejecting
  "identical destination URL" as a duplicate/dedup trigger. Per-request idempotency-key/payload-based
  deduplication is explicitly **not implemented in this iteration** and is backlog.
  - *Rationale*: simplest, most predictable behavior for a demonstration prototype; avoids
    committing to an idempotency-key design (header format, storage, TTL) that the source material
    never asked for. Full dedup/idempotency is explicitly named as backlog scope, not omitted by
    oversight.

**REQ-008 — Concurrent requests**
The system MUST process concurrent creation and resolution requests correctly and independently.
- *Positive*: Given multiple simultaneous creation and/or resolution requests, when processed
  concurrently, then each succeeds or fails correctly according to its own input, and short-code
  uniqueness (REQ-003) still holds afterward.
- *Negative*: The system does not corrupt, lose, or cross-assign a mapping under concurrent load,
  and does not hang or deadlock indefinitely (bounded by the retry/timeout model of REQ-014).
- *Evidence*: N-way concurrency test with per-request correctness assertions.
- **[RESOLVED — CLR-9, Section 13.1]** The persistence layer MUST provide per-write transactional
  isolation sufficient to guarantee REQ-003 (short-code uniqueness) holds under concurrent creation
  requests. For analytics increment operations specifically (REQ-006), **last-writer-wins**
  (eventually consistent) semantics are acceptable at prototype scale — analytics increments are
  not required to be strictly serializable against each other. (This confirms the *behavioral*
  concurrency model only; it does not select a persistence engine — see Section 8.)

**REQ-009 — Persistence failures**
The system MUST handle unavailability or transient errors from its persistence layer without
reporting a false success.
- *Positive*: Given the persistence layer is unavailable or returns a transient error during a
  creation or resolution operation, when detected, then the failure is classified (transient vs.
  permanent, constitution Principle VIII), bounded retry is applied where applicable, and a
  distinguishable error is returned if retries are exhausted.
- *Negative*: The system never reports a creation or redirect as successful when the underlying
  write/read did not durably succeed, and never retries a persistence failure indefinitely.
- *Evidence*: persistence-outage failure-injection test.

**REQ-010 — Operational health**
The system MUST expose an operational health/readiness signal reflecting the real state of its
critical dependencies.
- *Positive*: Given the service is running, when a health check is invoked, then the reported
  status reflects actual availability of dependencies critical to creation/resolution (at minimum,
  persistence).
- *Negative*: The system does not report "healthy" while a dependency required for core operations
  is actually unavailable.
- *Evidence*: health-check test under both nominal and degraded dependency states.

### 6.2 Orchestration Functional Requirements

**REQ-011 — Workflow creation**
The system MUST allow a new Workflow Run to be created from an ingested requirement, instantiating
the Section 3.1 stage dependency graph with explicit entry/exit criteria, owners, and failure
behavior per stage.
- *Positive*: Given a new requirement input, when a Workflow Run is created, then a run identifier
  is assigned and the full graph (including its parallel and conditional edges) is instantiated and
  persisted in an inspectable form.
- *Negative*: The system does not begin executing any stage whose declared entry criteria are
  unmet, and does not instantiate a Workflow Run containing a stage with no defined failure
  behavior (constitution Principle II).
- *Evidence*: workflow-creation test; persisted-graph snapshot.

**REQ-012 — Workflow inspection**
The system MUST make the full current state of any Workflow Run inspectable.
- *Positive*: Given an in-progress or completed run, when inspected, then the status of every
  stage (e.g., pending / running / awaiting approval / completed / failed / skipped), its
  inputs/outputs, and its decision lineage are retrievable from persisted state alone.
- *Negative*: The system does not lose or omit a stage's history from inspection once execution
  moves past it, and inspection never requires reconstructing state from an agent's chat
  transcript.
- *Evidence*: inspection-query test against persisted state.

**REQ-013 — Approvals and rejection**
The system MUST support human-owned Approval Gates that block or release a dependent branch based
on a recorded human decision.
- *Positive*: Given a stage reaches an Approval Gate, when a human records approval, then the
  dependent branch proceeds; when a human records rejection, then the dependent branch does not
  proceed, and the rejection with rationale is recorded.
- *Negative*: The system never treats an unanswered gate as approved (no default-to-yes on
  timeout — constitution Principle III), and never allows an agent to resolve a human-owned gate on
  its own authority.
- *Evidence*: approval-gate test covering approve, reject, and timeout/non-response paths.

**REQ-014 — Retries and timeouts**
The system MUST apply bounded, classified retry to transient stage-operation failures.
- *Positive*: Given a stage operation fails and is classified transient, when the retry policy
  applies, then the operation is retried up to a declared maximum attempt count with a declared
  backoff and timeout.
- *Negative*: The system does not retry a permanently classified failure (e.g., a validation
  error), and never retries beyond its declared bound.
- *Evidence*: retry/backoff test with fault injection distinguishing transient vs. permanent
  failures.
- **[RESOLVED — CLR-12, Section 13.1]** Retry eligibility rule (confirmed, newly surfaced during
  clarification — not previously a numbered ambiguity): only genuinely **transient** failures are
  retryable — e.g., a database lock/write-contention timeout, or a simulated transient stage
  failure injected for testing. **Validation failures and policy failures are always classified
  permanent and are never retried**, regardless of retry budget remaining.

**REQ-015 — Rollback or compensation**
The system MUST recover from a failed stage using rollback (undo of a reversible, not-yet-committed
effect) or compensation (a corrective, forward action for an already-committed external effect),
per the distinction in constitution Principle VIII, never conflating the two.
- *Positive*: Given a stage fails after a partial effect, when recovery runs, then a rollback is
  performed if the effect was reversible, or a compensation is performed if the effect was already
  externally observable.
- *Negative*: The system never attempts to "roll back" an effect that has already been externally
  observed (e.g., a delivered analytics event) by pretending it can be undone, and never leaves a
  partially applied effect with neither rollback nor compensation performed.
- *Evidence*: fault-injection test covering one rollback-eligible and one compensation-eligible
  scenario.
- **[RESOLVED — CLR-14, Section 13.1]** Confirmed rollback-impossible case (newly surfaced during
  clarification): once a redirect has been served to a caller, that delivery is a completed,
  externally observed effect and can never be rolled back. Recovery for a problem discovered after
  a redirect was served MUST use compensation (e.g., logging an incident/audit note, issuing a
  corrective follow-up action) — never a rollback attempt against the already-served redirect
  itself.

**REQ-016 — Safe-stop**
The system MUST halt forward progress on an affected branch, rather than guess, when continuation
would be unsafe.
- *Positive*: Given exhausted retries, a detected non-recoverable state, or an unresolved mandatory
  Approval Gate, when detected, then the orchestrator halts the affected branch, preserves
  already-completed work and its evidence, and surfaces the condition for human review.
- *Negative*: The system never executes a dependent stage past an unresolved safe-stop condition
  "to make progress," and never discards or corrupts already-completed upstream work upon entering
  safe-stop.
- *Evidence*: safe-stop trigger test with post-condition state inspection.
- **[RESOLVED — CLR-13, Section 13.1]** Confirmed safe-stop trigger conditions (newly surfaced
  during clarification): a Workflow Run enters safe-stop when (a) a mandatory policy check
  resolves `FAIL` without an approved exception, or (b) bounded retries (REQ-014) are exhausted with
  no applicable fallback path. This is the concrete, closed enumeration of "non-recoverable state /
  exhausted retries / unresolved mandatory gate" already named in this requirement's positive
  criterion above.

**REQ-017 — Workflow resumption**
The system MUST resume an interrupted Workflow Run from persisted state.
- *Positive*: Given a run was interrupted (process restart, safe-stop, delayed approval), when
  resumption is triggered, then execution continues from persisted state without re-executing
  already-completed stages and without skipping incomplete ones; the resumption itself is recorded.
- *Negative*: The system never re-executes a stage whose effect already durably completed (no
  non-idempotent double-execution), and never silently drops a pending stage on resume.
- *Evidence*: interrupt-and-resume test.

**REQ-018 — Dynamic replanning**
The system MUST support altering the not-yet-started portion of a Workflow Run's graph in response
to new information, while preserving governance and prior history.
- *Positive*: Given new information changes what remaining work is needed, when a replan is
  triggered, then only not-yet-started stages are altered, the existing audit trail is preserved
  unchanged, and the replan event itself (what changed, why, under whose authority) is recorded.
- *Negative*: The system never rewrites or deletes the record of stages that already executed as
  part of a replan, and never applies a replan without a recorded authorizing decision.
- *Evidence*: replan test asserting immutability of prior history plus a new audit entry for the
  replan itself.

**REQ-019 — Audit inspection**
The system MUST make every Workflow Run's evidence trail independently reconstructable.
- *Positive*: Given a completed or in-progress run, when an auditor inspects its evidence trail,
  then they can determine what happened, in what order, who/what caused it, when, and the result —
  for every state transition, decision, approval/rejection, retry, failure, and replan — without
  relying on an agent's narrative.
- *Negative*: The system never omits an actor, timestamp, action, or reason from an audit record,
  and never treats a narrative summary as a substitute for the underlying evidence.
- *Evidence*: audit-trail completeness test exercising every event type.

**REQ-020 — Release-readiness outcome**
The system MUST produce a deterministic release-readiness outcome derived from actual policy,
test, and exception state.
- *Positive*: Given a run reaches release-readiness determination, when evaluated, then it produces
  one of a small, enumerated set of outcomes (e.g., ready / not-ready-with-reasons), derived from
  the real state of every applicable policy check, test result, and open exception.
- *Negative*: The system never reports "ready" while a mandatory policy check is `FAIL` or an
  `EXCEPTION-REQUESTED` remains unapproved (constitution Principle VI, Section 4.6), and never
  produces a release-readiness outcome unsupported by an evidence trail.
- *Evidence*: release-readiness test covering both a passing run and a run blocked by an unresolved
  exception or failing policy.
- **[RESOLVED — CLR-16, Section 13.1]** "Release-readiness" is **one combined gate** covering both
  the orchestration engine and the URL shortener application as a single Workflow Run outcome — not
  two independent gates. This confirms ASM-4 as a requirement and resolves AMB-9; it is also the
  reading already implied by Section 3.1's Stage 11, which is modeled as a single synchronization
  node joining Stages 8/9/10, not two parallel release-readiness nodes.

## 7. Non-Functional Requirements

The assignment source material names eleven non-functional requirement areas but supplies no
numeric targets. Per this project's explicit rule (and the task brief), each area below states a
MUST-hold *verifiable capability*, and — only where the area implies a number — a clearly labeled
**Proposed validation target (assumption — requires approval)**. No number below is a confirmed
requirement.

**REQ-021 — Security**
The system MUST validate and normalize all externally supplied input against an explicit
scheme/format allow-list before use, and MUST treat named abuse classes as in-scope design threats.
- *Positive*: Given externally supplied input, when processed, then it is checked against an
  explicit allow-list before use, and security tests targeting open-redirect chaining,
  shortener-as-phishing-relay, short-code enumeration, and mass-creation denial-of-service each
  demonstrate safe failure.
- *Negative*: The system never accepts a disallowed scheme, never emits secrets or disproportionate
  personal data into logs/errors, and never ships with an insecure-by-default configuration.
- *Proposed validation target (assumption — requires approval)*: the security test suite includes
  at least the four abuse classes above, and a dependency-vulnerability scan and secret-scan are
  clean (or carry a recorded, approved exception) before any release-readiness "ready" outcome.
- *Evidence*: security test suite results; dependency/secret scan reports.
- **[RESOLVED — CLR-11, Section 13.1]** No rate-limiting/quota enforcement is in scope for this
  iteration (see CLR-11). Consequently, the mass-creation-denial-of-service abuse class named above
  is scoped, for this iteration's security test suite, to demonstrating that: (a) the absence of
  enforced rate-limiting is a documented, explicitly approved residual risk rather than a silent
  gap, and (b) input-size/format bounds (REQ-002's 2048-character limit, REQ-021's other allow-list
  checks) still hold correctly under high-volume submission. It does not attempt to demonstrate
  actual volumetric-abuse prevention, since no enforcement mechanism exists to test.
- **[RESOLVED — CLR-1, Section 13.1]** The SSRF guard (rejecting long URLs resolving to a loopback,
  link-local, or private RFC1918 address — see REQ-002) is a confirmed in-scope control for the
  open-redirect-chaining and shortener-as-phishing-relay abuse classes named above.

**REQ-022 — Reliability**
The system MUST resolve every injected transient failure in critical operations to a deterministic
terminal outcome without data corruption.
- *Positive*: Given transient failures are injected into critical operations, when the system
  responds, then failures are classified, bounded-retried per REQ-014, and resolved to one of the
  enumerated terminal outcomes.
- *Negative*: The system never silently swallows a failure without a recorded terminal outcome, and
  never exhibits unbounded retry or indefinite hang behavior.
- *Proposed validation target (assumption — requires approval)*: 100% of the fault-injection test
  scenarios in the test suite resolve to an enumerated terminal outcome (success / permanent
  failure / safe-stop) — measured in the demonstration test environment, explicitly not a
  production SLA.
- *Evidence*: reliability/failure-injection test suite results.

**REQ-023 — Scalability**
The system MUST preserve functional correctness under increasing concurrent load within the tested
range.
- *Positive*: Given an increasing number of concurrent creation/resolution requests, when load is
  applied, then correctness (REQ-008) holds even if latency increases.
- *Negative*: The system never produces an incorrect mapping, a duplicate active short code, or
  silent request loss purely as a function of increased concurrency within the tested range.
- *Demonstration acceptance target (binding for this iteration's three scenario demonstrations;
  explicitly NOT a production SLA — approved 2026-09-13, closing the deferral this line originally
  made to "the plan stage")*: correctness holds for **at least 20 concurrent creation/resolution
  requests** against this prototype's H2/laptop-scale execution environment, verified by a
  concurrent-request test producing zero incorrect mappings, zero duplicate active short codes, and
  zero silently-dropped requests. This number is binding for whether this iteration's demonstration
  passes REQ-023 — it is not a claim about production capacity at any different scale.
- *Evidence*: load/concurrency test results, labeled as demonstration-scale (constitution Principle
  IX).

**REQ-024 — Maintainability**
The system's structure MUST allow a change in one concern to be made without requiring changes to
an unrelated concern.
- *Positive*: Given a change is needed in one concern (e.g., persistence), when made, then it does
  not require touching an unrelated concern (e.g., input validation), consistent with the layering
  constitution Principle VII requires.
- *Negative*: The system's design never requires modifying domain/validation logic to change a
  persistence detail, or vice versa.
- *Proposed validation target (assumption — requires approval)*: a structural/dependency-direction
  check is defined at the plan/ADR stage; no numeric target is set here.
- *Evidence*: architecture review; ADR record of layering decisions.

**REQ-025 — Observability**
The system MUST correlate every event produced by a Workflow Run to a single run identifier.
- *Positive*: Given a run executes, when it does, then every stage transition, log line, and
  produced artifact carries the run/correlation identifier assigned at run start.
- *Negative*: The system never produces a run-related log line or artifact without that identifier
  attached.
- *Proposed validation target (assumption — requires approval)*: 100% of audit-relevant events in
  demonstration test runs carry the run/correlation identifier.
- *Evidence*: correlation-ID completeness check against captured run logs.

**REQ-026 — Auditability**
The system MUST capture, for every run event, the actor, action, timestamp, affected
artifact/state, result, and reason.
- *Positive*: Given any run event (state transition, decision, approval, retry, failure, replan),
  when recorded, then all six fields are present.
- *Negative*: The system never records an audit event missing any of the six fields, and never
  allows an existing audit record to be edited or deleted after the fact.
- *Proposed validation target (assumption — requires approval)*: schema completeness verified
  against 100% of events in a demonstration run exercising every event type.
- *Evidence*: audit-trail schema-conformance test.

**REQ-027 — Performance**
The system MUST serve a redirect-resolution request, under nominal conditions, within an
operationally reasonable bound for a redirect-class operation.
- *Positive*: Given a redirect-resolution request under nominal conditions, when served, then it
  completes within the bound proposed below (once approved).
- *Negative*: The system never degrades redirect latency silently, without a measurable metric
  surfacing the degradation.
- *Proposed validation target (assumption — requires approval)*: p95 redirect-resolution latency
  ≤ 200 ms measured in the demonstration environment — explicitly not a production SLA; the exact
  figure and measurement conditions are subject to human approval at the plan stage.
- *Evidence*: latency measurement from a load test, labeled as demonstration-scale.

**REQ-028 — Recoverability**
The system MUST allow a workflow interrupted mid-run to be resumed without loss of already-completed
work (ties to REQ-017).
- *Positive*: Given an interruption occurs mid-run (process restart, crash), when the system comes
  back up, then the affected Workflow Run resumes from persisted state with no loss of
  already-completed stage outputs.
- *Negative*: The system never loses a completed stage's output on restart, and recovery never
  requires manual reconstruction of run state from logs alone.
- *Demonstration acceptance target (binding for this iteration's three scenario demonstrations;
  explicitly NOT a production SLA — approved 2026-09-13, closing the deferral this line originally
  made to "the plan stage")*: a workflow interrupted mid-run resumes to its correct next state
  within **5 seconds** of the process restarting, for a demonstration-scale interrupted run against
  this prototype's H2/laptop-scale execution environment. This number is binding for whether this
  iteration's demonstration passes REQ-028 — it is not a claim about production recovery-time
  guarantees at any different scale or under real infrastructure failure modes.
- *Evidence*: crash/restart-and-resume test.

**REQ-029 — Testability**
Every requirement in this specification MUST be verifiable by an observable, automatable check
without subjective interpretation.
- *Positive*: Given any requirement in Sections 6 or 7, when a reviewer or test suite attempts to
  verify it, then the positive and negative acceptance criteria stated for it are sufficient to do
  so without further interpretation.
- *Negative*: This specification never contains a requirement lacking a concrete acceptance
  criterion (Sections 6–7 each supply both a positive and a negative one).
- *Evidence*: this document's own Section 6/7 acceptance criteria; the eventual test suite's mapping
  back to each REQ ID (Section 11).

**REQ-030 — Change safety**
An approved requirement, architecture decision, schema, orchestration state definition, security
control, or release criterion MUST NOT change without recorded impact analysis and approval.
- *Positive*: Given an already-approved artifact changes, when the change is introduced, then a
  recorded impact analysis and change approval precede it taking effect (constitution Principle
  VI).
- *Negative*: The system never allows such a change to be introduced silently as a side effect of
  an unrelated task, and work completed under a prior version is never treated as automatically
  still valid without re-evaluation.
- *Proposed validation target (assumption — requires approval)*: 100% of material post-approval
  changes have a corresponding recorded impact-analysis/change-approval artifact, verified by
  traceability audit at the plan/tasks stage.
- *Evidence*: change-log / impact-analysis records cross-referenced to affected artifacts.

**REQ-031 — Controlled autonomy**
An agent's actions MUST be bounded to the stage it currently owns and validated against that
stage's declared exit criteria.
- *Positive*: Given an agent is executing within a stage it owns, when it acts, then the action is
  attributable to that stage and validated against the stage's exit criteria before being accepted.
- *Negative*: The system never allows an agent to act outside the stage it currently owns, to
  bypass a mandatory human Approval Gate under any autonomy setting, or to mark a stage's exit
  criteria satisfied when they are not.
- *Proposed validation target (assumption — requires approval)*: 100% of agent actions in a
  demonstration run are attributable to a declared stage owner and pass that stage's exit-criteria
  check, verified via audit-trail review.
- *Evidence*: audit-trail review; a stage-boundary violation test in which an out-of-scope agent
  action is rejected.

## 8. Deferred Technology Decisions

The following are explicitly **not decided** in this specification, per constitution Principles I
and VII and the task brief's Step 3. Each is a placeholder pointing to the ADR/`/speckit.plan`
stage (Tasks 6–7 of the governing plan):

- Programming language(s) and runtime.
- Application framework(s) for the API surface.
- Persistence engine/database technology. (Clarification note, CLR-9: the *behavioral* concurrency
  requirement — per-write transactional isolation, last-writer-wins analytics increments — is now
  confirmed in REQ-008; the specific engine that satisfies it is still deferred here.)
- Cloud provider, hosting platform, or deployment target.
- Agent-orchestration framework or library used to implement the Section 3 dependency graph.
- Concrete short-code generation algorithm (only its *properties* — 7 characters, base62,
  system-generated-only, uniqueness per REQ-003 — are confirmed as requirements per CLR-3/CLR-4;
  the algorithm itself is a forward pointer to ADR-003 at the plan stage).
- Concrete authentication mechanism, if authentication is ever brought into scope in a future
  iteration (CLR-10 confirms none is enforced in this iteration; forward pointer to ADR-010 if
  revisited).
- Concrete data schema / storage layout for any Key Entity in Section 2.1.
- Specific numeric values for every "Proposed validation target" in Section 7, pending human
  approval.

## 9. Requirement Classification Summary

### 9.1 Confirmed Requirements

Every requirement area named explicitly in the assignment source material is confirmed to exist as
a requirement *area* — the specific acceptance-criteria wording, negative behavior, and evidence
requirement are this document's derived elaboration (Section 9.2) of that confirmed area.

| REQ ID(s) | Source area (verbatim from source material) |
|---|---|
| REQ-001 | valid URL creation |
| REQ-002 | invalid URL rejection |
| REQ-003 | short-code uniqueness |
| REQ-004 | redirect resolution |
| REQ-005 | expired-link behavior |
| REQ-006 | analytics capture |
| REQ-007 | duplicate request or idempotency behavior |
| REQ-008 | concurrent requests |
| REQ-009 | persistence failures |
| REQ-010 | operational health |
| REQ-011 | workflow creation |
| REQ-012 | workflow inspection |
| REQ-013 | approvals and rejection |
| REQ-014 | retries and timeouts |
| REQ-015 | rollback or compensation |
| REQ-016 | safe-stop |
| REQ-017 | workflow resumption |
| REQ-018 | dynamic replanning |
| REQ-019 | audit inspection |
| REQ-020 | release-readiness outcome |
| REQ-021 | security |
| REQ-022 | reliability |
| REQ-023 | scalability |
| REQ-024 | maintainability |
| REQ-025 | observability |
| REQ-026 | auditability |
| REQ-027 | performance |
| REQ-028 | recoverability |
| REQ-029 | testability |
| REQ-030 | change safety |
| REQ-031 | controlled autonomy |

### 9.2 Derived Requirements

Elaborations necessary for testability/consistency that are not themselves separately named bullet
areas in the source material, but were required to keep this specification whole and
constitution-consistent:

- **DER-1**: The conceptual Key Entity model (Section 2.1) — needed so requirements can reference a
  stable vocabulary without prescribing a schema.
- **DER-2**: "Deterministic terminal outcome" language in REQ-020 and REQ-022 is drawn from
  constitution Principle VIII ("Deterministic terminal outcomes") rather than stated verbatim in
  the assignment prompt, but is required for constitutional consistency.
- **DER-3**: A negative acceptance criterion for *every individual* requirement (not just a general
  instruction to "include negative acceptance criteria" as the source material states) — this
  document elaborates that instruction into 31 specific negative criteria.
- **DER-4**: The requirement that every stage in the orchestration graph declare a failure behavior
  in advance before it may be scheduled (folded into REQ-011) — drawn from constitution Principle
  II, not stated in the assignment's own bullet list.
- **DER-5**: The conceptual need for approval gates to be able to apply either before a stage
  begins or before its dependents may proceed (referenced abstractly in REQ-013 and the Section
  3.1 graph) — the concrete mechanism for this distinction is left to the plan/ADR stage; this
  specification only records that both shapes of gate must be expressible.

### 9.3 Assumptions (require approval)

- **ASM-1**: "No authentication" is the operative assumption for the API Consumer journey (Section
  5.1) at this assessment scope, mirroring the example the constitution itself gives (Principle V)
  — not a confirmed decision. If clarified otherwise, REQ-001/REQ-004 acceptance criteria will need
  an authorization dimension added. **[RESOLVED AND PROMOTED — CLR-10, Section 13.1, 2026-09-12]**
  "No authentication" is no longer merely an operative assumption — it is now a **confirmed
  decision** for this iteration: no authentication or authorization is enforced for any journey in
  Section 5. See CLR-10 for the resolution record and Section 8 for the forward ADR-010 pointer if
  a future iteration revisits this.
- **ASM-2**: All eleven "Proposed validation target" figures in Section 7 (REQ-021–REQ-031) are
  placeholder assumptions pending human approval, explicitly not confirmed client requirements.
  **Unchanged by this clarification pass** — these numeric targets remain deferred to the plan
  stage per this document's own design (Section 7's framing), except where a specific clarification
  above (CLR-8 analytics, CLR-11 rate-limiting) touched an individual target's scope.
- **ASM-3**: The demonstration/test execution environment is assumed to be a single-machine or
  small-CI-scale environment (not a production cluster), which is why scalability/performance
  targets in Section 7 are scoped as "demonstration-scale." **Unchanged by this clarification
  pass.**
- **ASM-4**: "Release-readiness" (REQ-020) is assumed to be a single combined gate covering both
  the orchestration engine and the URL shortener application, rather than two independent gates,
  pending clarification (see AMB-9). **[RESOLVED AND PROMOTED — CLR-16, Section 13.1, 2026-09-12]**
  "Release-readiness" is now a **confirmed decision**: one single combined gate covers both the
  orchestration engine and the URL shortener application. See CLR-16 for the resolution record.

### 9.4 Constraints

- This specification MUST NOT contradict any of the eleven principles in
  `docs/governance/constitution.md` v1.0.0 (**Ratified** — corrected by the Task 10 corrective
  batch, `codex-recommend.md` Finding #12; see `docs/governance/human-gates-log.md`); where a
  candidate requirement would have created tension, it was written to avoid that tension rather
  than silently overriding the
  constitution.
- This specification MUST NOT select implementation technology (Section 8) — that decision belongs
  to the ADR/plan stage.
- Non-waivable constitution principles (I — Specification Before Implementation, III — Human
  Governance, IV — Test-Driven Engineering, XI — Evidence-Based Completion) apply in full to every
  downstream artifact built from this specification; no exception process may suspend them.
- No numeric non-functional target in Section 7 may be treated as confirmed until explicitly
  approved at Human Gate 2 or a later gate — this specification's own drafting must not, and does
  not, silently promote an assumption to a requirement.
- This document itself remains a DRAFT (Section 12) until Human Gate 2 is recorded; no downstream
  artifact may cite it as approved before that.

### 9.5 Ambiguities (flagged, not silently resolved — routed to `/speckit.clarify`)

**Clarification status: all nine ambiguities below were reviewed at Human Gate 3 (2026-09-12).
Eight were resolved immediately (each carries its original question text, unedited, plus its
resolution); one (AMB-7) was explicitly deferred rather than force-resolved at that time. AMB-7 was
subsequently dispositioned on 2026-09-13 via an explicitly human-approved temporary assumption
(CLR-17) rather than left open indefinitely — see Section 13 for the full clarification record,
rationale, and owner for every item, including the distinction between "resolved at Human Gate 3"
and "later approved as an assumption."**

- **AMB-1**: What precisely constitutes a "duplicate" creation request for idempotency purposes
  (REQ-007) — identical destination URL, an explicit idempotency key/header, or an identical full
  request payload?
  **[RESOLVED — CLR-2]** None of the three, in the sense of triggering dedup: identical-destination-
  URL dedup is explicitly rejected, and idempotency-key/payload-based dedup is not implemented in
  this iteration (backlog). See REQ-007 and Section 13.1.
- **AMB-2**: Whether an expired short code (REQ-005) must return a response distinguishable from an
  unknown short code (REQ-004), or whether the two must be intentionally indistinguishable to avoid
  leaking the prior existence of a link.
  **[RESOLVED — CLR-7]** Distinguishable: expired → `410 Gone`-equivalent; unknown → `404`-equivalent.
  See REQ-005 and Section 13.1.
- **AMB-3**: Whether caller-chosen ("custom alias") short codes are in scope, or whether all short
  codes are always system-generated.
  **[RESOLVED — CLR-4]** All short codes are always system-generated; custom aliases are out of
  scope for this iteration (backlog). See Section 2.1 and Section 13.1.
- **AMB-4**: Whether any authentication/authorization is in scope for API consumers, software
  engineers, or reviewers interacting with the system (see ASM-1).
  **[RESOLVED — CLR-10]** No authentication/authorization is enforced in this iteration; ASM-1 is
  promoted from assumption to confirmed decision. See Section 13.1.
- **AMB-5**: Maximum accepted long-URL length and short-code length/character set.
  **[RESOLVED — CLR-3, CLR-5]** Short code: 7 characters, base62. Long URL: 2048-character maximum.
  See REQ-002, Section 2.1, and Section 13.1.
- **AMB-6**: Whether a Link's destination or expiration is mutable after creation, or whether Links
  are immutable once created.
  **[RESOLVED — CLR-15]** Links are immutable once created; no update/PATCH capability in this
  iteration. See Section 2.1 and Section 13.1.
- **AMB-7**: Retention period for analytics data and for audit evidence (constitution Principle VI
  names "audit retention" as a governed policy area but sets no duration).
  **[RESOLVED — CLR-17, approved temporary assumption, 2026-09-13]** No real compliance-derived
  retention period exists for this assessment prototype; the human candidate has explicitly
  approved a disclosed 90-day placeholder to unblock planning, revisited before any
  production-scale deployment. See Section 13.1 (CLR-17) for the full record.
- **AMB-8**: Whether rate-limiting/quota behavior (implied by the mass-creation DoS threat in
  REQ-021) is per-caller-identity, per-IP, or global, and what the applicable threshold is.
  **[RESOLVED — CLR-11]** No rate-limiting is enforced in this iteration. If implemented later, the
  proposed (not yet approved) scope/threshold is per-IP, 100 requests/minute — recorded as a
  residual backlog assumption, not a confirmed number. See REQ-021 and Section 13.1/13.2.
- **AMB-9**: Whether "release-readiness" (REQ-020) is one combined gate for the whole system or two
  independent gates — one for the orchestration engine, one for the URL shortener application (see
  ASM-4).
  **[RESOLVED — CLR-16]** One combined gate. ASM-4 is promoted from assumption to confirmed
  decision. See REQ-020 and Section 13.1.

These are carried forward, unresolved, to the clarification stage rather than answered here by
agent judgment alone, per constitution Principle I.

**Clarification-stage update (2026-09-12)**: the clarification stage referenced in the sentence
above has now run. Per constitution Principle I, none of the nine ambiguities was silently
resolved: each resolution recorded next to it above is either an explicit controller decision
(Section 13.1, owner: human candidate via controlling session) or, for AMB-7, an explicitly
recorded open deferral rather than an invented answer.

### 9.6 Exclusions (explicitly out of scope for this specification)

- Choice of programming language, framework, database engine, cloud provider, deployment platform,
  and agent-orchestration library (Section 8) — scope-limited to the ADR/plan stage, not omitted by
  oversight.
- A graphical end-user interface or consumer-facing dashboard beyond what is needed to demonstrate
  the five user journeys in Section 5 — the demonstration domain is the API and orchestration
  behavior, not a shipped consumer product.
- Production-scale infrastructure concerns: multi-region deployment, autoscaling policy, CDN
  integration, billing or monetization — out of scope for an assessment prototype (constitution
  Section 1: the domain's simplicity is intentional).
- Third-party identity-provider integration (SSO/OAuth) unless a future clarification brings
  authentication into scope (see AMB-4).
- Any named external legal/regulatory compliance regime (e.g., a specific data-protection statute)
  — only the generic policy-guardrail categories named in constitution Principle VI apply unless a
  future clarification names a specific regime.

### 9.7 Proposed Validation Targets (assumption — requires approval) — consolidated

| REQ | Non-functional area | Proposed target | Status |
|---|---|---|---|
| REQ-021 | Security | 4 named abuse classes in test suite; clean dependency/secret scans | Assumption — requires approval |
| REQ-022 | Reliability | 100% of fault-injection scenarios resolve to an enumerated terminal outcome | Assumption — requires approval |
| REQ-023 | Scalability | Correctness at a demonstration-scale concurrency count (exact figure deferred to plan stage) | Assumption — requires approval |
| REQ-024 | Maintainability | Structural/dependency-direction check defined at plan/ADR stage; no number set | Assumption — requires approval |
| REQ-025 | Observability | 100% of audit-relevant events carry the run/correlation ID | Assumption — requires approval |
| REQ-026 | Auditability | 100% schema-complete audit records across all event types | Assumption — requires approval |
| REQ-027 | Performance | p95 redirect-resolution latency ≤ 200 ms, demonstration environment only | Assumption — requires approval |
| REQ-028 | Recoverability | Recovery-time objective to be proposed at plan stage; no number set here | Assumption — requires approval |
| REQ-029 | Testability | N/A — qualitative (all requirements carry acceptance criteria) | Not numeric |
| REQ-030 | Change safety | 100% of material post-approval changes have a recorded impact-analysis artifact | Assumption — requires approval |
| REQ-031 | Controlled autonomy | 100% of agent actions attributable to a declared stage owner and exit-criteria-checked | Assumption — requires approval |

## 10. Deferred Technology Decisions — Pointer

See Section 8. No entry in that list is decided here; each is a named placeholder for the
`/speckit.plan` and ADR stages (Tasks 6–7 of the governing plan), which this specification's
requirements (Sections 6–7) constrain but do not preempt.

## 11. Traceability Matrix (preliminary)

Full bidirectional traceability (requirement → scenario → decision → design → task → implementation
→ test → validation evidence → documentation, per constitution Principle X) is assembled
incrementally as later stages produce their artifacts. This preliminary matrix seeds that chain
from the specification side: every requirement ID maps to the scenario(s) that exercise it, the
user type(s) whose journey depends on it, and the constitution principle(s) it upholds, so a later
task/ADR can be linked back without renumbering.

| REQ | Scenario(s) | User type(s) | Constitution principle(s) |
|---|---|---|---|
| REQ-001–REQ-004 | SCN-A, SCN-B | API Consumer | V, VII |
| REQ-005 | SCN-A, SCN-B | API Consumer | V |
| REQ-006 | SCN-A | API Consumer, Assessment Reviewer | V, IX |
| REQ-007–REQ-009 | SCN-A, SCN-B | API Consumer | VIII |
| REQ-010 | SCN-A, SCN-B | Release Owner | VIII |
| REQ-011, REQ-012 | SCN-A, SCN-B, SCN-C | Software Engineer, Assessment Reviewer | II |
| REQ-013 | SCN-A, SCN-B, SCN-C | Human Reviewer/Approver | III |
| REQ-014 | SCN-A, SCN-B | Software Engineer | VIII |
| REQ-015 | SCN-B | Release Owner | VIII |
| REQ-016 | SCN-A, SCN-B, SCN-C | Human Reviewer/Approver | III, VIII |
| REQ-017 | SCN-A, SCN-C | Software Engineer | II, VIII |
| REQ-018 | SCN-A, SCN-C | Human Reviewer/Approver | II |
| REQ-019 | SCN-A, SCN-B, SCN-C | Assessment Reviewer | IX, X |
| REQ-020 | SCN-A, SCN-B | Release Owner | VI |
| REQ-021 | SCN-A, SCN-B | Assessment Reviewer | V |
| REQ-022, REQ-023 | SCN-A, SCN-B | Assessment Reviewer | VIII |
| REQ-024 | SCN-B | Software Engineer | VII |
| REQ-025, REQ-026 | SCN-A, SCN-B, SCN-C | Assessment Reviewer | IX |
| REQ-027 | SCN-A | API Consumer | VIII, IX |
| REQ-028 | SCN-C | Software Engineer | VIII |
| REQ-029 | all | Assessment Reviewer | IV, X |
| REQ-030 | SCN-B | Release Owner | VI |
| REQ-031 | SCN-A, SCN-B, SCN-C | Human Reviewer/Approver | II, III |

## 12. Human Gate 2 (Pending — Not Yet Completed)

This specification takes effect for downstream SpecKit stages (`/speckit.clarify`, `/speckit.plan`,
ADRs) only once a human reviewer completes Human Gate 2:

- [ ] Confirms no technology was chosen prematurely (Section 8 lists only deferred pointers).
- [ ] Confirms no ambiguity was silently resolved (Section 9.5 lists every open ambiguity found).
- [ ] Confirms every requirement carries both a positive and a negative acceptance criterion
      (Sections 6–7).
- [ ] Confirms all three required scenarios are present and non-linear (Section 4).
- [ ] Confirms human approval gates exist and are not silently skippable or timeout-approved
      (REQ-013, Section 3.1).
- [ ] Confirms recovery and safe-stop are present (REQ-015, REQ-016, REQ-017, REQ-018).
- [ ] Confirms every requirement is testable (Section 6–7 acceptance criteria; REQ-029).
- [ ] Confirms traceability is possible from any requirement to scenario, user type, and
      constitution principle (Section 11).
- [ ] Records approval, or requested amendments, before `/speckit.clarify` proceeds against this
      document.

**Delegated technical-review decision (2026-09-13): REQUEST AMENDMENTS.** Codex reviewed this
checklist as a delegated technical reviewer, not as a human approver. Human Gate 2 remains open and
all boxes intentionally remain unchecked. Before approval, reconcile the conflicting safe-stop
semantics between REQ-016, CLR-13, SCN-C, and the plan's separate `PENDING_APPROVAL` state; restate
REQ-007's current (post-CLR-2) behavior with explicit positive and negative criteria or move it out
of the active numbered requirement set; and state explicitly which proposed non-functional targets
Human Gate 2 approves versus leaves deferred.

Until this checklist is completed and recorded, this document remains a DRAFT and should be
treated as the best-available current guidance, not as ratified scope.

**Note (2026-09-12)**: Human Gate 3 (`/speckit.clarify`, Section 13) has been completed
independently of this gate. Gate 3's completion resolves or explicitly defers every ambiguity in
Section 9.5; it does not itself satisfy any Human Gate 2 checklist item above, which remains a
separate, still-pending approval of the specification as a whole.

---

## 13. Clarification Resolutions (`/speckit.clarify` — Human Gate 3)

**Completed**: 2026-09-12. **Owner of every decision recorded below**: human candidate (via
controlling session) — per constitution Principle I and Principle III, no item below was resolved
by agent judgment alone presented as if it were a human decision; every resolution in Section 13.1
is an explicit, rationale-bearing decision attributed to that owner, and every item in Section 13.2
is an explicit deferral, not a silent assumption.

This section is the authoritative clarification record. Sections 2, 6, 7, 8, and 9 above were
updated in place to cite these records at the point each affected requirement/entity/assumption
lives, per this project's convention of never deleting the history of what was asked (Section 0).

### 13.1 Resolved Clarifications

Format per item: **Identifier** — Question — Impact — Resolution — Owner — Date. Items marked
"(newly surfaced)" were not one of the nine numbered ambiguities in the original Section 9.5 but
were identified as material during the mandated 25-topic-area review (source: task-5 source
material, Section 8 topic list) and are resolved to the same standard.

---

**CLR-1 — URL validation: scheme allow-list and SSRF guard** *(newly surfaced; sharpens REQ-002,
REQ-021)*
- **Question**: REQ-002 requires rejecting "a scheme outside an explicit allow-list" but the
  original specification never named the allow-list's actual contents, nor whether
  private-network-resolving hosts must be rejected (SSRF).
- **Impact**: Without a concrete allow-list and SSRF rule, REQ-002/REQ-021 are not testable as
  written (REQ-029 requires every requirement be verifiable without further interpretation), and
  the shortener-as-phishing-relay / open-redirect-chaining abuse classes REQ-021 names cannot be
  test-designed.
- **Resolution**: Allow only `http` and `https` schemes; reject `javascript:`, `file:`, `data:`,
  and every other scheme. Additionally reject, at creation time, any long URL whose host resolves
  to a loopback, link-local, or private RFC1918 address (SSRF guard).
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-2 — Duplicate URL behavior (dedup)** *(resolves AMB-1)*
- **Question**: What precisely constitutes a "duplicate" creation request for idempotency purposes
  (REQ-007) — identical destination URL, an explicit idempotency key/header, or an identical full
  request payload?
- **Impact**: Determines whether REQ-007 is implemented at all in this iteration, and whether
  creation is a pure function of destination URL (dedup) or of the request occurrence itself
  (no dedup). Affects REQ-001, REQ-003, REQ-007, and the creation API's response contract.
- **Resolution**: Creating a short link for an already-shortened long URL returns a **new**,
  independent short code — no implicit URL-based deduplication. This is the simplest, most
  predictable behavior; full request-level dedup/idempotency is out of scope for this iteration and
  is recorded as backlog. REQ-007's original MUST-level obligation is superseded accordingly (see
  REQ-007 for the full resolution note and updated acceptance behavior).
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-3 — Short-code format** *(resolves part of AMB-5)*
- **Question**: What is the short-code length and character set (AMB-5)?
- **Impact**: Affects REQ-003 testability, storage/display assumptions, and collision-probability
  reasoning for REQ-003/REQ-008.
- **Resolution**: 7-character, base62 (`[a-zA-Z0-9]`) short codes. The concrete generation
  *algorithm* (random vs. counter-based vs. hash-based) is **not** decided here — that is a Section
  8 deferred technology/implementation decision, forward-pointed to ADR-003 at the plan stage. Only
  the testable properties (length, character set) are confirmed as requirements in this document.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-4 — Custom alias out of scope** *(resolves AMB-3)*
- **Question**: Are caller-chosen ("custom alias") short codes in scope, or are all short codes
  always system-generated?
- **Impact**: Determines whether the creation API accepts a caller-supplied short-code field, and
  whether short-code-collision handling must account for adversarial caller input.
- **Resolution**: All short codes are always system-generated (per CLR-3); custom aliases are out of
  scope for this iteration and recorded as backlog. Any caller-supplied short-code field, if
  submitted, is ignored or rejected — not silently honored.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.
- *Note*: this specific resolution was not one of the ten items given verbatim in the task-5 brief;
  it was derived here as the direct, low-risk implication of CLR-3 ("generated," 7-char base62,
  with no caller-choice mechanism described) and the demonstrated simplicity-first pattern used
  across the other nine brief resolutions. Recorded to the same concrete/falsifiable/rationale-
  bearing standard; flagged in the task-5 report for visibility.

---

**CLR-5 — Maximum long-URL length** *(resolves remainder of AMB-5)*
- **Question**: What is the maximum accepted long-URL length (AMB-5)?
- **Impact**: Affects REQ-002 testability (a length-based rejection boundary must be a concrete
  number to be testable per REQ-029) and storage/validation design.
- **Resolution**: 2048 characters. A submitted long URL exceeding this length is rejected the same
  way as a malformed URL (REQ-002).
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.
- *Note*: not one of the ten brief-verbatim items; 2048 is a widely used practical URL-length
  ceiling (common browser/server interoperability bound), chosen here as a concrete, testable,
  low-controversy default consistent with the brief's own resolutions' style. Flagged in the task-5
  report for visibility/possible re-ratification.

---

**CLR-6 — Expiration mechanism and default** *(newly surfaced; sharpens REQ-005)*
- **Question**: How is a Link's expiration set, and what is the default if the caller does not set
  one?
- **Impact**: Affects the creation API contract (REQ-001) and REQ-005's testability (a "default"
  must be stated to write a boundary test).
- **Resolution**: Expiration is optional and caller-set at creation via an `expires_at` condition;
  default when omitted is **no expiration** (the Link never self-expires).
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-7 — Expired vs. unknown distinguishability** *(resolves AMB-2)*
- **Question**: Must an expired short code's response be distinguishable from an unknown short
  code's response, or must the two be intentionally indistinguishable to avoid leaking prior
  existence?
- **Impact**: Affects REQ-004 and REQ-005's negative acceptance criteria and the redirect API's
  response contract; also an information-disclosure/security design choice (REQ-021).
- **Resolution**: Distinguishable. Expired → `410 Gone`-equivalent outcome. Unknown/never-issued →
  `404`-equivalent outcome.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-8 — Analytics consistency model** *(newly surfaced; sharpens REQ-006)*
- **Question**: What analytics data is captured per redirect, and under what consistency model?
- **Impact**: Affects REQ-006's testability and the concurrency model for analytics writes
  (REQ-008/REQ-009).
- **Resolution**: Redirect count (per-Link counter) and last-accessed timestamp, captured under an
  **eventually consistent** model — an asynchronous, non-blocking increment is acceptable. Recorded
  as a proposed validation target for the demonstration, not a hard production consistency
  guarantee.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-9 — Concurrency / transaction handling** *(newly surfaced; sharpens REQ-003, REQ-008,
REQ-009)*
- **Question**: What transactional guarantees apply to concurrent writes, and what consistency
  model applies specifically to analytics increments under contention?
- **Impact**: Affects whether REQ-003 (uniqueness) and REQ-008 (concurrent correctness) are
  testable as stated, and bounds how aggressively analytics writes may be batched/deferred.
- **Resolution**: The persistence layer MUST provide per-write transactional isolation sufficient to
  guarantee short-code uniqueness (REQ-003) under concurrent creation. For analytics increments
  specifically, last-writer-wins (eventually consistent) semantics are acceptable at prototype
  scale. **Scope note**: the controller's rationale for this decision referenced a specific
  mechanism ("SQLite transaction per write") as illustrative context; per this document's own
  Section 8 boundary (persistence engine is a deferred technology decision) and this task's
  explicit instruction not to select a technology stack, only the *behavioral* commitment above is
  ratified in this specification — the specific persistence engine remains open for the ADR/plan
  stage.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-10 — Authentication assumption** *(resolves AMB-4 / promotes ASM-1)*
- **Question**: Is any authentication/authorization in scope for API consumers, software engineers,
  or reviewers interacting with the system?
- **Impact**: Affects REQ-001/REQ-004 acceptance criteria (whether an authorization dimension is
  required) and the overall threat model (REQ-021).
- **Resolution**: No authentication or authorization is enforced in this iteration. This promotes
  ASM-1 from assumption to confirmed decision. A forward pointer (ADR-010) is recorded in Section 8
  in case a future iteration revisits this.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-11 — Rate limiting** *(resolves AMB-8)*
- **Question**: Is rate-limiting/quota behavior in scope, and if so, is it per-caller-identity,
  per-IP, or global, and what threshold applies?
- **Impact**: Affects REQ-021's mass-creation-DoS abuse-class test design and REQ-001's creation API
  contract.
- **Resolution**: No rate-limiting is enforced in this iteration. If implemented in a future
  iteration, the proposed (not yet approved) scope/threshold is per-IP, 100 requests/minute —
  recorded as a residual backlog assumption requiring its own approval before implementation, not a
  confirmed requirement now.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.
- **Residual item carried to Section 13.2**: the 100 req/min/IP figure itself remains an
  unapproved backlog assumption (see Section 13.2).

---

**CLR-12 — Retry eligibility rule** *(newly surfaced; sharpens REQ-014, REQ-022)*
- **Question**: Which failure classes are retryable, and which are permanently non-retryable?
- **Impact**: Affects REQ-014's testability (a retry/backoff test needs a concrete transient/
  permanent boundary) and REQ-022 (reliability terminal-outcome classification).
- **Resolution**: Only transient failures are retryable — e.g., a database lock/write-contention
  timeout, or a simulated transient stage failure injected for testing. Validation failures and
  policy failures are always classified permanent and are never retried.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-13 — Safe-stop trigger conditions** *(newly surfaced; sharpens REQ-016)* **[UPDATED 2026-09-13 —
terminology correction, no change to the two-category enumeration]**
- **Question**: Precisely which conditions trigger safe-stop?
- **Impact**: Affects REQ-016's testability (a safe-stop trigger test needs a concrete, closed
  enumeration of trigger conditions) and downstream orchestration-engine design.
- **Resolution**: Safe-stop is entered when (a) a mandatory policy check resolves `FAIL` without an
  approved exception, or (b) bounded retries are exhausted **and** no fallback path is registered
  for that stage (or the registered fallback itself also fails) **and** no applicable compensation
  exists. Category (b) nests exactly two recovery attempts, tried in order — fallback (constitution
  Principle VIII; a distinct, lower-risk alternate path, never a retry of the same operation), then
  compensation (Principle VIII; a corrective action for an already-externally-observed effect) —
  before falling through to safe-stop. This is a **terminology correction**, not a new trigger
  category: the original 2026-09-12 wording said "fallback path" but no fallback mechanism existed
  yet at that time, and the concrete implementation built afterward actually checked compensation —
  a real drift between this record and the built system, caught during pre-implementation review
  and corrected here once a real fallback mechanism was designed (see `docs/engineering-runbook.md`
  Phase 12, Task 50). The enumeration remains exactly two top-level categories, unchanged from the
  original resolution's intent.
- **`PENDING_APPROVAL` vs. `SAFE_STOPPED` (added 2026-09-13, per external review):** both are
  "halt forward progress" states under REQ-016's general requirement, but they are not the same
  thing and must not be conflated. `PENDING_APPROVAL` is entered when an `entryApproval`/
  `exitApproval` gate is awaiting a human decision (SCN-C's Stage 4, Stage 6's design review,
  Stage 11's release sign-off) — it is a **normal, expected, recoverable** pause, not a failure; it
  resolves the moment a human calls `approve()`. `SAFE_STOPPED` (this CLR's actual subject) is
  entered only via the two conditions above — it is a **failure** state, and resuming from it
  requires either a granted policy exception (`retrySafeStopped`) or a fixed root cause, not simply
  a human's routine sign-off. A test asserting REQ-016 must distinguish which of the two states a
  given halt produced, not treat "the workflow stopped" as one undifferentiated outcome.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12 (original); terminology corrected and `PENDING_APPROVAL`/`SAFE_STOPPED`
  distinction added 2026-09-13.

---

**CLR-14 — Rollback-impossible case / compensation** *(newly surfaced; sharpens REQ-015)*
- **Question**: Which effects are impossible to roll back, requiring compensation instead?
- **Impact**: Affects REQ-015's testability and the orchestration engine's recovery-path design.
- **Resolution**: Once a redirect has been served to a caller, that delivery is a completed,
  externally observed effect and can never be rolled back. Recovery in that case MUST use
  compensation (e.g., an incident/audit note, a corrective follow-up action), never a rollback
  attempt.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

---

**CLR-15 — Link mutability** *(resolves AMB-6)*
- **Question**: Is a Link's destination or expiration mutable after creation, or are Links immutable
  once created?
- **Impact**: Determines whether an update/PATCH API surface, its authorization model, and its
  audit/change-safety obligations (REQ-030) are in scope.
- **Resolution**: Links are immutable once created; no update/PATCH capability in this iteration.
  Mutation support, if ever needed, is backlog.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.
- *Note*: not one of the ten brief-verbatim items; resolved here consistent with the simplicity-
  first, backlog-the-complexity pattern the controller applied to CLR-2 (dedup) and CLR-4 (custom
  alias). Flagged in the task-5 report for visibility/possible re-ratification, since — unlike
  CLR-4/CLR-5 — this one has a real (if modest) architectural footprint (it forecloses an update
  endpoint).
- **Human confirmation (2026-09-13)**: the human candidate reviewed this flag directly and
  confirmed the immutable-Links resolution as correct — not merely accepted by default. This
  closes the re-ratification concern raised above; CLR-15 now stands on the same footing as any
  other resolution in this section.

---

**CLR-17 — Analytics/audit retention period (approved temporary assumption)** *(resolves AMB-7)*
- **Question**: What is the retention period for analytics data and for audit evidence (constitution
  Principle VI names "audit retention" as a governed policy area but sets no duration)?
- **Impact**: Affects storage design, compliance posture, and whether audit-trail completeness
  (REQ-019, REQ-026) has an implicit expiry that could break "independently reconstructable"
  evidence claims.
- **Resolution**: **Approved temporary assumption, not a confirmed production requirement**: for
  this demonstration prototype, analytics and audit-event data are retained for **90 days** from
  creation, after which they may be purged without violating any requirement in this
  specification. This is explicitly a placeholder to unblock planning, not a compliance-derived
  figure — no real retention/legal requirement exists for an interview-assessment prototype. It
  must be revisited with real compliance input before any production-scale deployment, and any
  later change to this figure is a material requirement change subject to the constitution's
  change-control principle (VI), not a routine edit.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-13.
- *Note*: AMB-7 was originally left genuinely open at Human Gate 3 (2026-09-12) rather than
  force-resolved, per constitution Principle I. This CLR-17 entry is a distinct, later event: the
  human candidate explicitly approving a disclosed placeholder assumption so that Task 6 (technical
  plan) is not blocked by an unresolved NFR, per constitution Principle VI's requirement that a
  policy exception/assumption be explicitly approved rather than silently asserted. AMB-7 is not
  "resolved" in the sense of a real answer being known — it is "dispositioned" in the sense the
  guide's own Human Gate 3 rules require (resolved, or represented by an explicitly approved
  assumption).

---

**CLR-16 — Release-readiness: one combined gate** *(resolves AMB-9 / promotes ASM-4)*
- **Question**: Is release-readiness (REQ-020) one combined gate for the whole system, or two
  independent gates (orchestration engine vs. URL shortener application)?
- **Resolution**: One combined gate. This promotes ASM-4 from assumption to confirmed decision, and
  matches the existing Section 3.1 Stage 11 design (a single synchronization node joining Stages
  8/9/10 for one Workflow Run, not two parallel release-readiness nodes).
- **Impact**: Affects REQ-020's outcome model and Section 3.1's Stage 11 join semantics.
- **Owner**: human candidate (via controlling session).
- **Date**: 2026-09-12.

### 13.2 Deferred Questions / Residual Assumptions

Items below remain genuinely open. Each is recorded with an owner and a required decision point,
per constitution Principle I — none is force-resolved to appear closed.

| ID | Question | Impact | Current status | Owner | Required decision point |
|---|---|---|---|---|---|
| AMB-7 | What is the retention period for analytics data and for audit evidence? | Affects storage design, compliance posture, and whether audit-trail completeness (REQ-019, REQ-026) has an implicit expiry that could break "independently reconstructable" evidence claims. | **RESOLVED via CLR-17 (2026-09-13) — approved temporary assumption, not a confirmed production requirement.** 90-day placeholder retention approved by the human candidate to unblock planning. See Section 13.1. | human candidate (via controlling session) | Before any production-scale deployment, this placeholder must be replaced with a real compliance-derived figure. |
| CLR-11 residual | What is the approved rate-limiting threshold, if/when rate-limiting is implemented? | Affects REQ-021's mass-creation-DoS control design once actually built. | **Backlog assumption, not approved.** Proposed figure on record: 100 req/min/IP (see CLR-11). | human candidate (via controlling session) | Before rate-limiting is implemented (not required for this iteration's release-readiness outcome, since CLR-11 confirms it is out of scope now). |
| ASM-2 (11 NFR targets) | Are the eleven numeric "Proposed validation target" figures in Section 7 (REQ-021–REQ-031) approved as confirmed requirements? | Determines whether Section 7's numbers are binding for this iteration's release-readiness outcome (REQ-020). | **CLOSED 2026-09-13.** All eleven figures in Section 7 are approved as **binding demonstration acceptance targets** for this iteration's three required scenarios (SCN-A/B/C) — each is checked by its named evidence test, and a failed target is a real, reportable finding, not a number to shrug off. None is a **production SLA**: every figure is scaled to this prototype's local H2/laptop execution environment and labeled as demonstration data wherever it appears (constitution Principle IX), per the pattern already used for CLR-8 (analytics) and CLR-11 (rate-limiting). The two figures this document originally left with no number at all — REQ-023's concurrency count and REQ-028's recovery-time objective — are now set to 20 concurrent requests and 5 seconds respectively, closing the "deferred to the plan stage" gap those two lines originally left open past the point the plan stage actually ran. | human candidate (via controlling session) | Closed — no longer a required decision point. Revisit only if this system is ever scoped toward a real deployment, at which point every figure here needs re-derivation from real capacity/SLA requirements, not reuse of these demonstration numbers. |

### 13.3 Mapping to Original Ambiguities/Assumptions

| Resolution | Original spec reference | Relationship |
|---|---|---|
| CLR-1 (URL validation / SSRF) | none (REQ-002/REQ-021 underspecification) | Newly surfaced |
| CLR-2 (duplicate URL / dedup) | AMB-1 | 1:1 |
| CLR-3 (short-code format) | AMB-5 (short-code portion) | 1:1 (partial) |
| CLR-4 (custom alias out of scope) | AMB-3 | 1:1 (derived, not brief-verbatim) |
| CLR-5 (max long-URL length) | AMB-5 (URL-length portion) | 1:1 (partial, not brief-verbatim) |
| CLR-6 (expiration mechanism/default) | none (REQ-005 underspecification) | Newly surfaced |
| CLR-7 (expired vs. unknown) | AMB-2 | 1:1 |
| CLR-8 (analytics consistency) | none (REQ-006 underspecification) | Newly surfaced |
| CLR-9 (concurrency/transactions) | none (REQ-003/008/009 underspecification) | Newly surfaced |
| CLR-10 (auth) | AMB-4 / ASM-1 | 1:1 |
| CLR-11 (rate limiting) | AMB-8 | 1:1 |
| CLR-12 (retry eligibility) | none (REQ-014 underspecification) | Newly surfaced |
| CLR-13 (safe-stop triggers) | none (REQ-016 underspecification) | Newly surfaced |
| CLR-14 (rollback-impossible/compensation) | none (REQ-015 underspecification) | Newly surfaced |
| CLR-15 (Link mutability) | AMB-6 | 1:1 (derived, not brief-verbatim) |
| CLR-16 (release-readiness gate) | AMB-9 / ASM-4 | 1:1 |
| CLR-17 (retention — approved temporary assumption) | AMB-7 | 1:1 (resolved 2026-09-13, after initial Human Gate 3 deferral) |
