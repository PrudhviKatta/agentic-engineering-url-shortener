# Technical Plan: Agentic Software Engineering System — URL Shortener

**Feature:** `001-agentic-url-shortener` &nbsp;|&nbsp; **Stage:** `/speckit.plan` (Task 6 of 48)

**Status: DRAFT — pending whatever human review this plan itself calls for.** This document is
authored by an agent acting under the project's SpecKit lifecycle. It is not approved merely by
existing, and this plan document itself may not be cited as ratified until a human reviewer
records approval, in the same spirit as `spec.md`'s own Human Gate 2 framing — this plan-approval
gate remains open as of this writing and is not affected by the correction below. **Historical
note, corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): at the time
this plan was authored (Task 6), every technology/architecture choice beyond ADR-001/002 was
phrased as "proposed for ADR at Task 7," since Task 7 had not yet run. Task 7 has since run: the
human candidate reviewed and accepted ADR-003 through ADR-010, without amendment, on 2026-09-13
(`docs/governance/human-gates-log.md`, "Human Gate 4" rows) — see Section 12's technology table
below for their current status. Accepting those eight ADRs is a separate, already-closed gate from
this plan document's own still-open plan-approval gate referenced above.

**Delegated technical-review decision (2026-09-13): REQUEST AMENDMENTS.** Codex reviewed the status
block and all fourteen numbered sections as a delegated technical reviewer, not as a human
approver. This plan remains unapproved and implementation remains blocked. Required corrections
include: align the planned executable graph with the specification's twelve-stage graph; preserve
SCN-B's seven parallel impact-analysis branches and join rather than reducing them to one stage;
use the constitution's definition of fallback instead of labeling retry, compensation, or
safe-stop as fallback; version workflow-step and approval instances across replans; close the
workflow creation/inspection/audit contract gaps; and remove known, intentionally untested metric
defects from the delivery plan.

**Authority.** This plan sits directly beneath `docs/governance/constitution.md` (v1.0.0, Ratified)
and `specs/001-agentic-url-shortener/spec.md` (approved with clarifications CLR-1..17) and directly
above the ADRs Task 7 has since formalized (ADR-003 through ADR-010, all Accepted 2026-09-13 — see
Section 12) and the task breakdown `tasks.md` (Task 9) has produced. It must not contradict either
upstream artifact; every requirement/clarification it touches is cited by ID so a reviewer can
check that claim directly rather than take it on faith.

**Provenance note (disclosed, not concealed).** A separate, far more granular document — the
controller's own execution runbook, `docs/engineering-runbook.md`
— already exists and has already worked out concrete Java class shapes, JPA entity definitions, and
a Task-by-Task TDD sequence for building this system. That document is explicitly subordinate to
this plan (see its own "Authority" note, added 2026-09-13). This plan does not re-derive its
architecture from nothing; it promotes that runbook's already-decided technical facts — the
`StageDefinition`/`DependencyGraph`/`WorkflowEngine` design, the JPA entity shapes, the policy
PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE model, the MTTR formula — to the architecture-level
altitude a SpecKit `plan.md` operates at, with full rationale, not as a copy-paste of Java code
blocks. `research.md` carries the detailed per-decision rationale; `data-model.md` carries the full
schema; `contracts/openapi.yaml` carries the API contract. This document is the narrative and
node-level design that ties them together.

---

## 1. System Context

### 1.1 System boundary

One Spring Boot process (a single deployable JAR, no external services) exposes two independently
inspectable HTTP surfaces (constitution Principle VII) over one embedded H2 database:

- **URL-shortener domain** — `POST /links`, `GET /links/{code}`, `GET /r/{code}`,
  `GET /links/{code}/analytics` (spec.md Section 6.1, REQ-001–REQ-010).
- **Orchestration engine** — `POST /workflows`, `GET /workflows/{id}`,
  `POST /workflows/{id}/approvals/{step}`, `GET /workflows/{id}/audit` (spec.md Section 6.2,
  REQ-011–REQ-020).
- **Operational plane** — `GET /healthz`, `GET /readyz`, `GET /metrics` (REQ-010, REQ-022,
  REQ-025, REQ-026).

A separate CLI entry point (`ScenarioRunner`, run via `java -jar url-shortener.jar --scenario=...`
or `--metrics-report`) drives the same `WorkflowEngine` used by the HTTP surface to produce the
three mandatory scenario demonstrations (Section 11) without requiring a human to script HTTP
calls by hand for the demo itself — the HTTP surface remains the system's real, reviewable
interface; the CLI is a convenience wrapper over the same engine, not a second implementation.

### 1.2 Actors

| Actor | Role | Interacts via |
|---|---|---|
| API Consumer | Creates/resolves short links (spec.md Section 5.1) | `/links`, `/r/{code}` |
| Software Engineer | Inspects in-flight decomposition/design/implementation (5.2) | `/workflows/{id}`, `/workflows/{id}/audit` |
| Human Reviewer/Approver | Resolves Approval Gates (5.3) | `/workflows/{id}/approvals/{step}` |
| Release Owner | Consults release-readiness outcome (5.4) | `/workflows/{id}`, `/metrics` |
| Assessment Reviewer | Independently audits process and product (5.5) | `/workflows/{id}/audit`, `/metrics`, repo/docs directly |
| Agent (orchestrator/stage owner) | Executes stage handlers within its declared bounds (REQ-031) | Internal — `WorkflowEngine`, `StageHandler` implementations |

### 1.3 External dependencies

None beyond the JDK and the embedded H2 file (`data/app.mv.db`). No network call to any
third-party service exists in this design — deliberately, to keep the prototype runnable
offline within the 2–3 day timebox (research.md D2, D5).

### 1.4 Trust boundaries

- **External caller → HTTP surface:** the only externally-facing trust boundary. No
  authentication is enforced (CLR-10; research.md D10) — every endpoint, including the
  approval-decision endpoint, is documented as trusted-caller-only. This is this plan's single
  largest disclosed security caveat (Section 8).
- **HTTP/CLI layer → domain/orchestration layers:** an internal boundary enforced by package
  structure, not a network boundary — `api`/`observability` controllers depend on `domain`/
  `orchestration`/`policy`/`persistence` packages, never the reverse (REQ-024, constitution
  Principle VII).
- **Application → H2 file:** a local trust boundary only; H2 file-mode has no network listener
  exposed by this design.

### 1.5 Major runtime components

`LinkController` / `HealthController` (API delivery) → `UrlValidator`, `ShortCodeGenerator`,
`ShortLink` (domain logic) → `ShortLinkJpaRepository`, `RedirectEventJpaRepository`
(persistence) — the URL-shortener vertical. `WorkflowController`, `MetricsController`
(API delivery) → `WorkflowEngine`, `DependencyGraph`, `PolicyEngine`, `CompensationRegistry`,
`RetryExecutor`, `TimeoutExecutor` (orchestration/policy/reliability) → the six orchestration JPA
repositories (persistence) — the orchestration vertical. `AuditRecorder`/`MetricsService`
(observability) reads from, and only from, persisted state in both verticals — never from an
in-memory/narrative source (REQ-012, REQ-019 negative criteria).

### 1.6 Data ownership

- `short_links`, `redirect_events` — owned by the URL-shortener domain; the orchestration engine
  never writes to them directly (a stage handler that needs to create a short link, e.g. in a
  scenario, calls the same domain code an HTTP request would, not a shortcut around it).
- `workflow_instances`, `workflow_steps`, `workflow_events`, `approvals`, `policy_evaluations`,
  `policy_exceptions` — owned exclusively by the orchestration engine.
- No table is owned jointly; see `data-model.md`'s closing "Cross-Table Invariants" section for the
  explicit no-join rule between the two schemas.

### 1.7 Application-plane vs. control-plane responsibilities

**Application plane** (does the actual URL-shortener work): validates and normalizes input,
generates codes, resolves redirects, records analytics, reports health. **Control plane**
(governs how *any* requirement — including, but not limited to, URL-shortener capabilities —
becomes a reviewable engineering outcome): the `WorkflowEngine`, `PolicyEngine`, and reliability
primitives. The control plane is not URL-shortener-specific — it is a general-purpose SDLC
workflow runner that happens to be demonstrated exclusively against the URL-shortener domain in
this iteration (constitution Section 1's own framing: the domain is deliberately simple; the
process is the actual subject of assessment).

---

## 2. URL-Shortener Architecture

### 2.1 API and schema deliverables

- **API contract:** `specs/001-agentic-url-shortener/contracts/openapi.yaml` — OpenAPI 3.0.3,
  versioned `0.1.0`, covering all 11 required endpoints (4 domain, 4 orchestration, 3
  operational). Validated as parseable YAML with all 11 paths present (`python3 -c "import
  yaml; ..."`, executed during this plan's authoring — see Section 10 for the standing
  contract-test obligation this creates going forward).
- **Request/response/error schemas:** `CreateLinkRequest`, `LinkResponse`, `AnalyticsResponse`,
  `ErrorResponse`, `CreateWorkflowRequest`/`WorkflowCreatedResponse`, `WorkflowStateResponse`,
  `ApprovalRequest`, `AuditEventResponse`, `ReliabilitySummary` — all defined in
  `contracts/openapi.yaml`'s `components.schemas`, transcribed from the runbook's actual DTO
  record definitions (`CreateLinkRequest(longUrl, expiresAt)`, `LinkResponse(code, longUrl,
  shortUrl, expiresAt)`, `AnalyticsResponse(code, redirectCount, lastAccessedAt)`,
  `WorkflowStateResponse(id, status, steps[...])`, `AuditEventResponse(actorType, action,
  timestamp, result, reason)`, `ApprovalRequest(decision, actor, reason)`,
  `ReliabilitySummary(successRate, retryFrequency, rollbackOrCompensationFrequency,
  endToEndLatencySeconds, isDemonstrationData)`) — not invented independently of that ground
  truth.
- **Workflow-state, approval, audit-event, policy-evaluation, persistence schemas:**
  `data-model.md` — one section per table (`short_links`, `redirect_events`,
  `workflow_instances`, `workflow_steps`, `workflow_events`, `approvals`,
  `policy_evaluations`, `policy_exceptions`), each with a full column/type/nullable/constraint
  table and a purpose paragraph tying it to the REQ IDs it satisfies.
- **Two endpoints not yet present in the runbook's reference implementation, added here as
  plan-level design decisions:** `GET /links/{code}` (metadata lookup, distinct from the
  redirect-performing `GET /r/{code}`) and `POST /workflows` (a generic HTTP workflow-creation
  path — the runbook currently only exercises creation via the `ScenarioRunner` CLI calling
  `WorkflowEngine.submit()` directly, which does not by itself satisfy REQ-011's "the system MUST
  allow a new Workflow Run to be created" as an externally reachable capability). Both are
  specified in `contracts/openapi.yaml` with an explicit note on the implementation obligation
  they create for Task 9's task breakdown. This is a real design decision this plan is making,
  not an oversight being carried forward silently.
- **Compatibility and migration rules:** this is a pre-release contract (version `0.1.0`); no
  external consumer yet exists. Even so, any breaking change before the first tagged release
  requires an entry in `research.md` recording owner, version impact, backward-compatibility
  impact, affected consumers (currently: none, but the three scenario demonstrations and their
  tests are internal "consumers" of this contract and must be checked), tests touched,
  documentation touched, rollout/migration notes, and the approval that authorized the change
  (REQ-030, constitution Principle VI) — this rule is stated directly in
  `contracts/openapi.yaml`'s `info.description`, not only here.
- **Representative examples:** every request/response schema in `contracts/openapi.yaml` carries
  at least one worked `example` (e.g. `POST /links` with and without `expiresAt`; a `410`
  expired-link error body; a `WorkflowCreatedResponse` for the greenfield scenario's feature
  name).
- **Executable contract validation:** Section 10 (Testing) defines `LinkControllerContractTest`,
  `WorkflowControllerContractTest`, and `MetricsControllerContractTest` as the executable
  counterpart to this static contract — a schema that is never exercised by a real
  `TestRestTemplate` call is not considered validated by this plan.

### 2.2 Component plan

| Concern | Component(s) | Notes |
|---|---|---|
| API delivery | `LinkController`, `WorkflowController`, `HealthController`, `MetricsController`, `ApiExceptionHandler` | One controller per bounded concern; `ApiExceptionHandler` centralizes `InvalidUrlException → 422` and `ResponseStatusException` mapping so error-shape consistency (REQ-002, REQ-004, REQ-005) isn't duplicated per controller. |
| Domain logic | `ShortLink` (expiry rule), `UrlValidator`, `InvalidUrlException` | Framework-agnostic where practical (constitution Principle VII) — `UrlValidator.validate()` is a static method with no Spring dependency, directly unit-testable. |
| Short-code generation | `ShortCodeGenerator` | Base62 encoding of the assigned `id` (research.md D3, proposed ADR-003). |
| URL validation | `UrlValidator` | Scheme allow-list (`http`/`https` only, CLR-1), length bound (2048, CLR-5), SSRF guard against loopback/link-local/private-RFC1918-resolving hosts (CLR-1) — see Section 8. |
| Redirect resolution | `LinkController.redirect` (`GET /r/{code}`) | The sole place `redirect_events` rows and `short_links.redirect_count`/`last_accessed_at` are written (REQ-006). |
| Persistence | `ShortLinkJpaRepository`, `RedirectEventJpaRepository` | Spring Data JPA over H2 (ADR-002); no raw SQL. |
| Analytics | `LinkController.analytics` (`GET /links/{code}/analytics`) | Reads `short_links.redirect_count`/`last_accessed_at` directly — eventually consistent (CLR-8/CLR-9), never blocks a redirect response on its own write completing. |
| Expiration | `ShortLink.isExpired()` | Pure function of `expiresAt` vs. current instant; evaluated at resolution time, not at a background sweep — no scheduled job deletes or marks expired rows in this iteration (a link that never gets resolved again simply never re-evaluates its own expiry, which is sufficient since expiry is only ever observed at resolution time, per CLR-6/CLR-7). |
| Health and readiness | `HealthController` (`/healthz`, `/readyz`) | `/readyz` performs a real `DataSource` reachability check via Spring's own connection-pool exception propagation — never a hardcoded `"ready"` (REQ-010 negative criterion). |
| Telemetry | `AuditRecorder`, `MetricsService` | Cross-cutting; see Section 7. |
| Configuration | `application.yml` | `ddl-auto: update` (disclosed prototype-only convenience, ADR-002 Consequences), H2 file-mode connection string, no secrets present (Section 8). |

---

## 3. Agentic Orchestration Architecture

### 3.1 Model summary

The orchestration engine (research.md D4, proposed ADR-004) is a custom, in-process
`WorkflowEngine` executing an explicit `DependencyGraph` of `StageDefinition` nodes. A
`StageDefinition` is `(name, dependsOn, handler, entryApproval, exitApproval, maxRetries,
timeoutSeconds, parallelGroup)`. `DependencyGraph.topologicalBatches()` groups the graph into
ordered batches of concurrently runnable stages; stages sharing a `parallelGroup` tag (or simply
having no unmet dependency at the same batch) execute concurrently via a fixed thread pool
(research.md D8) and are joined before the next batch begins — this is the literal mechanism for
spec.md Section 3's "parallel paths" and "synchronization" requirements.

**Gate semantics (the mechanism behind every Approval Gate in this plan):** `entryApproval=true`
blocks the stage's handler from running at all until approved — used for a destructive/
irreversible action. `exitApproval=true` lets the handler run and produce real output first, then
blocks only that stage's *dependents* until a human reviews the produced output — used whenever
there is something concrete to review (a design, a detected ambiguity, a release-readiness
report). Approving an `exitApproval` gate does not re-run the stage; it releases already-produced
output to its dependents.

**State transitions.** Every `WorkflowStepEntity.status` moves through the `StepStatus` vocabulary:
`PENDING → RUNNING → (SUCCEEDED | FAILED → RETRYING → ... | SAFE_STOPPED | COMPENSATED |
ROLLED_BACK)`, with `WAITING_APPROVAL` interposed at an entry gate and `INVALIDATED`/`SKIPPED`
reachable only via a replan (Section 3.3) or an unmet-precondition branch, respectively. The
workflow-level `WorkflowStatus` (`CREATED → RUNNING → (PENDING_APPROVAL | SAFE_STOPPED |
REPLANNING) → (COMPLETED | FAILED)`) is derived from, not independent of, the aggregate step
states — there is exactly one place (`WorkflowEngine.runFrom`) that decides workflow-level status
from step-level status, so the two never drift out of sync.

### 3.2 Baseline stage graph — full node table

This is the same 12-stage graph `spec.md` Section 3.1 defines, restated here with the concrete
orchestration-engine mechanics (gate type, timeout, retry, failure classification, fallback,
audit events) that realize each spec-level stage. "Realized by" names the concrete stage
class(es) from the runbook's Phase 9 scenario wiring that implement the corresponding spec stage
for this demonstration; a future non-demonstration requirement would supply different handler
logic behind the same node shape, not a different graph.

| # | Node (spec.md Stage) | Realized by | Purpose | Inputs | Outputs | Preconditions | Postconditions | Actor | Allowed transitions | Prohibited transitions | Timeout | Retry policy | Failure classification | Fallback | Audit events |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Requirement ingestion | `RequirementStage` (partial — capture is the entry to `submit()`) | Capture the requirement verbatim with provenance | Raw requirement text/feature label | Persisted `workflow_instances` row + `context_json` seed | A requirement input exists | Requirement stored, correlation ID assigned | Agent (orchestrator) | → Requirement normalization | → any stage past normalization without a captured requirement | 30s (`StageDefinition.of` default) | 0 retries (input capture is not itself retryable — a missing input is permanent) | Permanent if input unreadable/absent | Safe-stop (REQ-016) | `stage_succeeded:requirement_ingestion` or safe-stop event |
| 2 | Requirement normalization | `RequirementStage` (quality checks) | Restate the requirement in normalized form | Stage 1 output | Normalized requirement text recorded via `ctx.recordDecision` | Stage 1 complete | Requirement restated, decision recorded | Agent | → Ambiguity detection | → Decomposition directly (must pass through Stage 3) | 30s | Up to 2 (transient parse failure only, CLR-12) | Transient: parse hiccup; Permanent: unparseable input | Retry, then safe-stop (REQ-014, REQ-016) | `stage_succeeded:requirement` incl. normalization decision |
| 3 | Ambiguity detection | `RequirementStage` (returns `ambiguityDetected`) | Score requirement complete/consistent/testable/in-policy, or flag ambiguous | Normalized requirement | `ambiguityDetected: bool`, `reason` | Stage 2 complete | Either "clear" (decision recorded, Stage 5 unblocked) or "ambiguous" (exit gate engaged) | Agent | → Task decomposition (clear) or → Human clarification (ambiguous) | → Task decomposition while ambiguous and ungated | 30s | 0 (a scoring outcome is not itself retryable) | Permanent if scoring inconclusive | Safe-stop on inconclusive scoring (REQ-016) | `ambiguity_detected:requirement` (only if ambiguous) + `stage_succeeded:requirement` |
| 4 | Human clarification | Engine-level `exitApproval` gate on Stage 3/`requirement`, `POST /workflows/{id}/approvals/{step}` | Record a human decision resolving the flagged ambiguity | Ambiguity flag + rationale | `approvals` row with `decision`, `reason`; workflow `PENDING_APPROVAL` while open | Stage 3 flagged ambiguity | Decision recorded; dependent branch released (approved) or blocked (rejected) | Human | → Task decomposition (approved) ; unaffected concurrent documentation work for other requirements continues | → any auto-approval on timeout/silence | N/A (gate has no timeout — REQ-013 negative criterion: never default-to-yes) | N/A — a human decision is not retried, it is awaited | N/A | Safe-stop while pending (REQ-016); no timeout-implied approval (Principle III) | `approval_approved:requirement` / `approval_rejected:requirement` |
| 5 | Task decomposition | `DecompositionStage` | Decompose into reviewable units with acceptance criteria | Stage 3 (clear) or Stage 4 (approved) output | Decomposition artifact | Stage 3 clear or Stage 4 approved | Decomposition recorded | Agent, human-reviewable | → Architecture and design | → Design while decomposition incoherent | 30s | 0 (an incoherent decomposition is a permanent/validation failure, CLR-12) | Permanent | Safe-stop on incoherent decomposition (REQ-016) | `stage_succeeded:decomposition` |
| 6 | Architecture and design | `DesignStage` (`exitApproval=true`) | Produce design with API/schema impact recorded | Decomposition | Design artifact + `ctx.recordDecision("design_produced", ...)` | Stage 5 complete | Design produced; dependents (Implementation, Documentation) blocked until reviewed | Agent, human-reviewable | → Implementation, → Documentation (both, once approved) | → Implementation before design review, when the design was material (REQ-013) | 30s | 0 | Permanent if design cannot satisfy decomposition | Safe-stop (REQ-016) | `stage_succeeded:design` then `approval_approved:design` / `approval_rejected:design` |
| 7 | Implementation | `ImplementationStage` | Produce code/artifacts satisfying acceptance criteria, tests first (Principle IV) | Approved design | Implementation artifact; security-control-reporting flags (`urlSchemeAllowlistEnforced`, `ssrfGuardEnforced`) for Stage 10's policy checks | Stage 6 complete and approved | Code exists; artifacts available to Testing/Security/Documentation | Agent | → Testing, → Security validation (parallel fan-out) | → Release-readiness directly, bypassing Testing/Security | 30s (raised per-stage as needed for real build/test tool invocation) | Up to `maxRetries` on transient tool failure only (CLR-12) | Transient: tool/timeout hiccup; Permanent: compile/validation error | Bounded retry, then safe-stop (REQ-014, REQ-016) | `stage_succeeded:implementation` or retry/safe-stop events |
| 8 | Testing | `TestingStage` (`parallelGroup="verify"`) | Execute all applicable test levels and observe passing | Implementation output | Test outcomes | Stage 7 complete | All applicable tests executed and recorded | Agent | → Release-readiness (join) | → Release-readiness on an unresolved failing test | 30s | 0 (a failing test is a permanent/validation-class outcome, CLR-12) | Permanent | Safe-stop on unresolved failing test (REQ-016) | `stage_succeeded:testing` or safe-stop |
| 9 | Documentation | `DocumentationStage` (`parallelGroup="verify"`, depends on Design not Implementation-completion) | Reflect decided design/API/schema in docs | Stage 6 output (not Stage 7's completion) | Documentation artifact | Stage 6 complete | Docs reflect decided design | Agent | → Release-readiness (join) | — | 30s | Same retry/safe-stop pattern as other agent stages | Transient/permanent per CLR-12 | Retry then safe-stop | `stage_succeeded:documentation` |
| 10 | Security and risk validation | `SecurityStage` (`parallelGroup="verify"`) + `PolicyEngine.evaluate` | Resolve every applicable policy check to PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE | Stages 6, 7 output (join) | `policy_evaluations` rows | Stages 6 and 7 complete | Every mandatory policy resolved | Agent; human-reviewable for exceptions | → Release-readiness (join) | → Release-readiness with an unresolved mandatory `FAIL` | 30s | 0 for policy checks themselves (a policy evaluation is deterministic, not flaky) | `FAIL` is always classified permanent (CLR-12) | `FAIL` blocks the dependent branch (REQ-016); `EXCEPTION_REQUESTED` requires Approval Gate (REQ-013) | `policy_exception_granted:<id>` and/or safe-stop |
| 11 | Release-readiness determination | `ReleaseReadinessStage` (`exitApproval=true`, synchronization point) | Produce a deterministic ready/not-ready outcome from real policy/test/exception state | Stages 8, 9, 10 (join) | Release-readiness report | Stages 8, 9, 10 complete | Deterministic outcome recorded, gated for human sign-off | Agent; human sign-off | → Final engineering summary (approved) | → "ready" while a mandatory `FAIL` or unapproved `EXCEPTION_REQUESTED` exists (REQ-020 negative criterion) | 30s | 0 | Permanent if any input is FAIL/unresolved | Safe-stop / not-ready outcome (REQ-016) | `stage_succeeded:release_readiness` then `approval_approved:release_readiness` |
| 12 | Final engineering summary | External to `WorkflowEngine` (Task 47 deliverable, reads persisted evidence) | Compile a summary from persisted evidence, not narrative recollection | Stage 11 output + full audit trail | `docs/assessment/final_engineering_summary.md` | Stage 11 complete | Summary compiled and cross-referenced to repo paths/commands (Principle IX, XI) | Agent, human-reviewed | → (terminal) | → compiling from an incomplete evidence trail | N/A | N/A | Permanent if evidence trail incomplete | Safe-stop if evidence trail incomplete (Principle IX, XI) | N/A (this stage documents the audit trail; it does not itself emit a `workflow_events` row in this design) |

### 3.3 Conditional branches, fan-out, and the join

- **Conditional branch (Stage 3 → 4):** Stage 5's entry precondition is "Stage 3 clear **or**
  Stage 4 approved" — a hard fan-out block (SCN-C), not a delay: no downstream node proceeds on
  an unclear input by construction of the precondition, not by an extra runtime check bolted on
  afterward.
- **Fan-out (Stage 6 → {7, 9}; Stage 7 → {8, 10}):** Documentation does not wait on
  Implementation's completion (only Design's); Testing and Security both depend on Implementation
  and run concurrently with each other. This is exactly SCN-A's non-linear elaboration
  (spec.md Section 4).
- **Synchronization (Stage 11):** a true join of three independent branches (8, 9, 10) — modeled
  as `ReleaseReadinessStage.dependsOn = ["testing", "security", "documentation"]`, not as three
  sequential checks.
- **Change Impact Analysis group (SCN-B insertion):** a fifth, brownfield-only node
  (`ImpactAnalysisStage`) is inserted with `dependsOn=[]` (or `[requirement]`) and
  `implementation.dependsOn` extended to include it, gated by an approval before Implementation
  may begin — see Section 11.2.

### 3.4 Rejection, retry, fallback, rollback/compensation, safe-stop, resumption, and dynamic replanning states

These are cross-cutting states any node above may enter, not separate nodes:

- **Rejection:** an `approvals` row with `decision=rejected` blocks the dependent branch
  permanently for that plan version — it does not retry, and it does not silently continue; the
  only path forward is a human-authorized replan (Section 3.5) or accepting the workflow's
  terminal `FAILED`/`SAFE_STOPPED` state.
- **Retry:** `RetryExecutor.withBoundedRetry` — up to `stage.maxRetries()+1` attempts, immediate
  rethrow on `PermanentStageException`, bounded retry on `TransientStageException` only
  (CLR-12). Backoff is a configurable fixed delay per attempt (`backoffMillis`); each attempt
  increments `workflow_steps.retry_count`.
  fails to bound.
- **Timeout:** `TimeoutExecutor.withTimeout` wraps each attempt independently — a slow attempt is
  a distinct failure event from a thrown exception, both funneled into the same retry/safe-stop
  path.
- **Fallback:** for this iteration, "fallback" is realized as *retry-then-safe-stop* — there is no
  third, alternate-implementation fallback path beyond retry and safe-stop in this prototype's
  scope (a disclosed scope decision, not an oversight: a real "fallback to a degraded mode"
  behavior was not named as a concrete requirement beyond REQ-014's own "fallback support"
  language, which this design satisfies via the compensation path, Section 6, rather than a third
  distinct mechanism).
- **Rollback / compensation:** `CompensationRegistry` — an in-transaction, not-yet-committed
  effect rolls back for free (transaction abort); an externally-observed effect (the canonical
  example, a served redirect, CLR-14) runs its registered compensating action instead. See
  research.md D9.
- **Safe-stop:** the single closed enumeration from CLR-13 — (a) a mandatory policy `FAIL`
  without an approved exception, or (b) bounded retries exhausted with no applicable
  compensation. Both funnel into the same `SafeStopException` → `WorkflowStatus.SAFE_STOPPED`
  path (Task 34's convergence check in the runbook exists specifically to prove there is no third,
  divergent safe-stop path).
- **Resumption:** `WorkflowEngine.runFrom` reloads `WorkflowContext` from
  `workflow_instances.context_json` and re-derives which batch to run next from persisted
  `workflow_steps.status` — it never re-executes a `SUCCEEDED` step and never skips a `PENDING`
  one (REQ-017 negative criteria).
- **Dynamic replanning:** `WorkflowEngine.replan(workflowId, fromStage, newStages, actor,
  reason)` — marks every step reachable from `fromStage` (by graph reachability, not sequence
  order) `INVALIDATED`, inserts `newStages` at `PENDING`, bumps `plan_version`, records a
  `replanned` audit event, and preserves `ctx.decisions()`/`ctx.artifacts()` accumulated before
  `fromStage` untouched. **Fixed ordering rule:** `replan()` is always called before `approve()`
  when a gate's resolution is "replace what comes next," because `approve()` synchronously
  resumes execution — the replacement stages must already be registered before that happens
  (this ordering is load-bearing, not stylistic; Task 35/41 in the runbook test it explicitly).

---

## 4. State and Decision Lineage

Every element spec.md Section 2.1/constitution Principle IX/X requires preserved is preserved in
exactly one place — no element below is reconstructed from an agent's narrative or transcript:

| Element | Preserved in | Mechanism |
|---|---|---|
| Workflow instance state | `workflow_instances.status`, `workflow_steps.status` | Updated transactionally after every batch (Section 3.1) |
| Normalized requirements | `workflow_instances.context_json.decisions[]` (Stage 2's recorded decision) | `WorkflowContext.recordDecision` |
| Task decomposition | `workflow_steps.result` for the `decomposition` step | `StageHandler` return value, JSON-serialized |
| Dependencies | `workflow_steps.depends_on` | Persisted at `submit()`/`replan()` time from `StageDefinition.dependsOn` |
| Decisions | `context_json.decisions[]` | `{actor, decision, rationale}` per entry, append-only |
| Assumptions | `context_json.artifacts` (e.g. an assumption recorded as an artifact key) + `docs/scenarios/*.md` narrative for scenario-specific assumptions | `WorkflowContext.addArtifact` |
| Approvals / rejections | `approvals` table | One row per gate instance; `decision`/`decided_by`/`reason`/`decided_at` |
| Artifact versions | `workflow_instances.plan_version` + `workflow_steps.status=INVALIDATED` for superseded steps | Bumped only by `replan()` |
| Test outcomes | `workflow_steps.result` for the `testing` step | Same mechanism as decomposition |
| Risk outcomes | `policy_evaluations` rows + `workflow_steps.result` for `security` | `PolicyEngine.evaluate` output |
| Replanning history | `workflow_events` rows tagged `replanned` + `INVALIDATED` step rows (never deleted) | `replan()` |
| Correlation identifiers | `workflow_instances.correlation_id`, attached to every `workflow_events` row's logical run | Assigned at `submit()`, immutable thereafter |
| Terminal status | `workflow_instances.status ∈ {COMPLETED, FAILED, SAFE_STOPPED}` | Set exactly once execution reaches a genuinely terminal condition |

`data-model.md` Section 3 (`workflow_instances`) and Section 4 (`workflow_steps`) are the concrete
schemas backing every row of this table; nothing above is a design aspiration without a column to
hold it.

---

## 5. Human-in-the-Loop Controls

Every mandatory gate category the source material requires, mapped to its concrete mechanism:

| Gate category | Mechanism | Requirement(s) |
|---|---|---|
| Unresolved ambiguity | Stage 3 → 4 conditional branch, `exitApproval` on `requirement` | REQ-013, REQ-016, SCN-C |
| Architecture approval | `exitApproval=true` on `design` (Stage 6) | REQ-013, Section 3.1 Stage 6 |
| Security-sensitive action | Mandatory `SEC-001`/`SEC-002` policy checks (Section 9); `EXCEPTION_REQUESTED` outcome always routes to an Approval Gate | REQ-013, REQ-021 |
| Destructive/irreversible action | `entryApproval=true` reserved for any stage whose handler itself performs an irreversible effect (none in this domain's current stage set beyond the redirect-serving action itself, which is why CLR-14's compensation-only rule exists for it) | REQ-013, CLR-14 |
| Constitutional exception | `PolicyExceptionEntity` — requires `reason`, `scope`, `approvingAuthority`, `compensatingControl`, `expiry` all populated before `grantPolicyException` will persist a row | REQ-013, constitution Section 4.2 |
| Material risk acceptance | Same `PolicyExceptionEntity` path when the risk is expressed as a mandatory-policy exception; a brownfield change's regression-risk acknowledgment (Section 11.2) is a `design`/impact-analysis-stage `exitApproval` | REQ-013, REQ-030 |
| Release readiness | `exitApproval=true` on `release_readiness` (Stage 11) | REQ-013, REQ-020 |
| Final submission | Task 47's Final Engineering Summary is explicitly human-reviewed before it is treated as the submission artifact (Stage 12, "Agent, human-reviewed") | REQ-013, Principle XI |

**Non-negotiable properties of every gate above (REQ-013 negative criteria, enforced by design,
not by convention):** no gate has a timeout that resolves to implicit approval; no agent code path
calls `approve()` on its own authority; every decided gate carries a `reason`.

---

## 6. Reliability

| Concept | Definition in this design |
|---|---|
| Transient failure | A `TransientStageException` — e.g. a database lock/write-contention timeout, or a simulated transient failure injected for testing (CLR-12). Eligible for retry. |
| Permanent failure | A `PermanentStageException`, or any validation/policy failure (CLR-12: validation and policy failures are *always* permanent, regardless of remaining retry budget). Never retried. |
| Retry eligibility | Transient only (CLR-12). |
| Retry bounds | `stage.maxRetries()` — a declared, per-stage integer; `RetryExecutor` rethrows immediately once exhausted. |
| Backoff | A fixed delay (`backoffMillis`) between attempts, configured per call site; not exponential in this iteration (a disclosed simplification — sufficient at demonstration scale/timebox, not a claimed production backoff strategy). |
| Timeout | `TimeoutExecutor.withTimeout`, per-attempt, milliseconds-granularity; `StageDefinition.timeoutSeconds` converted at the call site. |
| Idempotency | Stage-level: `WorkflowEngine.runFrom` never re-executes a `SUCCEEDED` step (REQ-017). Domain-level: creation is explicitly **not** idempotent across distinct requests (CLR-2/REQ-007 superseded) — this is a confirmed, deliberate design choice, not a gap. |
| Duplicate execution protection | Step-status-gated re-entry (`runFrom` only schedules `PENDING`/`RETRYING` steps); no distributed-lock mechanism needed at this concurrency scale. |
| Fallback | Retry-then-safe-stop (Section 3.4) — no third alternate-path fallback in this iteration's scope. |
| Rollback | JPA transaction abort for effects that never left the transaction boundary. |
| Compensation | `CompensationRegistry`-registered corrective action for externally-observed effects (CLR-14). |
| Safe-stop | The CLR-13 closed enumeration; single convergent terminal path (`SafeStopException` → `WorkflowStatus.SAFE_STOPPED`). |
| Resumption | `runFrom` reloading persisted `context_json` + step statuses; exercised by both a genuine process restart and a `retrySafeStopped()` call after an exception is granted. |
| Partial failure | A batch where some stages `SUCCEEDED` and one `SAFE_STOPPED` — the succeeded stages' results remain persisted and are never discarded (REQ-016 negative criterion); only the affected stage(s)/their dependents halt. |
| Dependency unavailability | Modeled as a transient failure of the specific persistence operation attempting to reach H2 (REQ-009); `/readyz` surfaces sustained unavailability at the operational level (REQ-010). |

---

## 7. Observability

**Signals captured:** structured log lines correlated by `correlation_id`/`workflow_id`
(REQ-025); `workflow_events` rows as the audit-event stream (REQ-026); `workflow_steps` status
history as the state-transition history; `context_json.decisions[]` as the decision history;
`approvals` rows as the approval history; `workflow_steps.retry_count` plus `RETRYING`-status
transitions as retry evidence; `COMPENSATED`/`ROLLED_BACK` step statuses plus
`compensation_ran:*` events as compensation evidence; `replanned` events plus `INVALIDATED` steps
as replanning evidence. There is no separate distributed-tracing backend in this iteration
(disclosed scope decision — a single-process prototype has no cross-service trace to assemble;
the `correlation_id` threaded through every `workflow_events` row is this design's trace-equivalent
at prototype scale).

### 7.1 Computed/demonstrable measurements

`GET /metrics` and `--metrics-report` expose a `ReliabilitySummary` computed live from persisted
`workflow_instances`/`workflow_steps` state — **always tagged `isDemonstrationData: true`**, never
presented as a production SLA (constitution Principle IX; this plan's own instruction from the
source material: "Do not present synthetic demonstration measurements as production statistics"):

- **Workflow success rate** — `COMPLETED` instances ÷ total terminal instances.
- **Failure rate** — `FAILED` + unrecovered `SAFE_STOPPED` instances ÷ total terminal instances.
- **Retry frequency** — steps with `retry_count > 0` ÷ total steps executed.
- **Rollback/compensation frequency** — steps reaching `ROLLED_BACK`/`COMPENSATED` ÷ total steps
  that failed at least once.
- **End-to-end latency** — `workflow_instances.updated_at − created_at` for terminal instances,
  reported per-run, not averaged into a single production-style percentile claim.

### 7.2 MTTR — exact mechanics

Formula (verbatim, not to be deviated from):

```
MTTR = Total recovery duration across recovered failure events
       ÷ Number of recovered failure events
```

**Definitions and captured fields**, realized by `record RecoveryEvent(failureDetectedAt,
recoveryStartAt, recoveryCompleteAt, recovered: boolean, mechanism: String)`:

| Field | Meaning |
|---|---|
| `failureDetectedAt` | Instant a stage attempt's failure was classified (transient or permanent) |
| `recoveryStartAt` | Instant a recovery action (retry attempt, compensation run) began |
| `recoveryCompleteAt` | Instant that recovery action concluded (success or exhaustion) |
| Individual recovery duration | `recoveryCompleteAt − recoveryStartAt` for one event |
| Recovery mechanism used | `"retry"` \| `"compensation"` \| `"rollback"` |
| Recovered vs. unrecovered | `recovered=true` only if the stage ultimately reached a non-safe-stopped terminal state after the recovery action; `false` if it safe-stopped anyway |
| Number of recovered failure events | Count of `RecoveryEvent`s with `recovered=true` |
| Total recovery duration | Sum of individual recovery durations across only the recovered events |
| MTTR calculation | `Total recovery duration ÷ Number of recovered failure events`, computed by `MetricsService.computeMttr` |
| Measurement population | All `RecoveryEvent`s recorded within the demonstration run(s) being reported on |
| Exclusions | **Unrecovered failures are excluded from the MTTR denominator by construction** — `computeMttr` filters to `recovered=true` before dividing; `MttrReport.unrecoveredCount` reports them separately, never folded into the average |
| Demonstration-data limitations | `MttrReport`/`ReliabilitySummary` are computed over whatever demonstration runs (Section 11's three scenarios plus any ad hoc test runs) exist in the local H2 file at report time — a sample size of single-digit recovery events, explicitly not statistically representative of production failure rates. `MttrReport.populationNote` states this in the response itself, not only in this document. |

`MttrReport.mttrSeconds` is `null` (not zero, not omitted) when zero events have recovered — a
null-vs-zero distinction that matters because "MTTR is 0 seconds" and "MTTR cannot be computed,
nothing has recovered yet" are different, non-interchangeable claims.

---

## 8. Security

- **Input validation:** every externally supplied field (`longUrl`, `expiresAt`, approval
  `decision`/`actor`/`reason`) is validated before use; `UrlValidator` runs before any
  `short_links` row is written (REQ-002, REQ-021).
- **Allowed URL schemes:** exactly `http` and `https` (CLR-1). No auto-correction of a disallowed
  scheme into an allowed one (REQ-002 negative criterion).
- **Malicious redirect / SSRF considerations:** any long URL resolving to a loopback, link-local,
  or private RFC1918 address is rejected at creation time (CLR-1). The brownfield scenario
  (Section 11.2) hardens this further against IPv6 Unique Local Addresses (RFC 4193, `fc00::/7`)
  — a real gap in `Inet6Address.isSiteLocalAddress()`'s deprecated site-local coverage, not a
  hypothetical example.
- **Internal/local address considerations:** covered by the same SSRF guard above; no separate
  allow-list exists for "internal" vs. "external" beyond the loopback/link-local/RFC1918 checks.
- **Abuse cases (REQ-021's named classes):** open-redirect chaining and shortener-as-phishing-relay
  (mitigated by the scheme allow-list + SSRF guard); short-code enumeration (**not prevented** —
  research.md D3's sequential-encoding trade-off is a disclosed residual risk, not a silent gap);
  mass-creation denial-of-service (**not enforced** this iteration, CLR-11 — the security test
  suite instead demonstrates that input-size/format bounds hold under high-volume submission,
  per REQ-021's CLR-11-scoped framing, and that the absence of rate-limiting is a documented,
  approved residual risk).
- **Rate limiting:** none enforced (CLR-11). Proposed, unapproved figure on record for a future
  iteration: 100 req/min/IP (Section 13.2 of `spec.md`).
- **Authentication assumptions:** none enforced anywhere (CLR-10; research.md D10). This is this
  plan's single largest security caveat: the approval-decision endpoint
  (`POST /workflows/{id}/approvals/{step}`) — the one endpoint whose call *releases a governance
  gate* — is exactly as unauthenticated as `GET /healthz`. Documented here directly, not left for
  a reviewer to discover.
- **Precise characterization of the approval endpoint's posture (added by the Task 10 corrective
  batch — `pre_implementation_analysis.md` Section 10 / `codex-recommend.md` Finding #7).** This
  system enforces **approval-state**, not **human identity**. Concretely: the workflow logic
  itself cannot be bypassed — `entryApproval`/`exitApproval` are designed as real, to-be-tested
  gates that no agent code path is designed to satisfy on its own authority (ADR-006) — but the
  endpoint does not verify that the network caller invoking it is the intended human reviewer. Any
  caller able to reach the process can supply a decision and a self-asserted `actor` string
  (`approvals.decided_by`; `data-model.md` Section 6). The correct phrase for this system's
  behavior, used consistently from this point forward wherever this endpoint's behavior is
  characterized in this document, is **"approval-state enforcement with self-asserted actor
  identity,"** not "authorized human approval enforcement" — the latter would overclaim a property
  (verified human identity) this design does not provide and ADR-010 does not claim to provide.
- **Network-level containment measure (added by the Task 10 corrective batch, same source as
  above).** The server will be bound to loopback only — `server.address: 127.0.0.1`, already
  present in the tracked `src/main/resources/application.yml` (a configuration line, not a task
  deliverable — it takes effect the moment the application boots) — so that, regardless of the
  above, no caller outside the local machine can reach any endpoint, including the approval
  endpoint. This is a containment measure, not a reversal of ADR-010's no-authentication decision:
  it narrows the no-auth surface from "any network caller that can reach the process" to "any
  process on the local machine," which is the deployment boundary this entire prototype already
  assumes (Section 1.4's trust-boundary framing). ADR-010 itself remains Accepted, unchanged, and
  is not being reopened by this addition.
- **Secrets management:** no secrets exist in this design (no external API keys, no credentials —
  H2 file-mode requires none). `application.yml` contains no secret material; if any were ever
  added, constitution Principle V's "no secrets in logs" rule and `AuditRecorder`'s payload
  serialization would need an explicit redaction pass, which does not currently exist because
  nothing currently needs it.
- **Dependency risk:** standard Spring Boot BOM-managed dependencies only (`spring-boot-starter-*`,
  H2, Jackson, JUnit/Mockito/AssertJ) — no unusual or unmaintained third-party libraries. A
  dependency-vulnerability scan is named as a release-readiness input (REQ-021's proposed
  validation target) but its concrete tool/command is a Task 9+ implementation decision, not
  fixed here.
- **Audit integrity:** `workflow_events` rows are never updated or deleted by any code path in
  this design (REQ-026) — enforced by the absence of an update/delete method on
  `WorkflowEventJpaRepository`'s usage anywhere in the codebase, a code-review-level guarantee at
  this scale, not a database trigger (disclosed limitation, `data-model.md` Section 5).
- **Least privilege:** the orchestration engine and the URL-shortener domain use separate
  repository sets with no cross-schema joins (Section 1.6); a compromised orchestration-layer bug
  cannot directly corrupt `short_links` data through a shared query path, because none exists.
- **Secure defaults:** scheme allow-list, length bound, and SSRF guard are all enforced
  unconditionally (not opt-in flags); `ddl-auto: update` and the absence of authentication are the
  two explicitly named secure-defaults exceptions, both disclosed rather than silent.
- **Threat modeling (summary):** the primary threat surface is the unauthenticated HTTP API
  itself — an external caller can create unlimited links (no rate limit), enumerate short codes
  (sequential encoding), and — if this system were ever exposed beyond a trusted-caller
  environment — call the approval-decision endpoint with no verification of who is deciding. Every
  one of these is either mitigated (scheme/SSRF checks) or explicitly disclosed as an accepted,
  documented residual risk for this prototype's scope (CLR-10, CLR-11, D3, D10) — none is an
  undiscovered gap.

---

## 9. Compliance and Change Control

- **Policy domains applicable to this prototype:** `security` (SEC-001 scheme allow-list,
  SEC-002 SSRF guard), `compliance` (COMP-001 audit-trail presence), `licensing` (LIC-001
  dependency license review, advisory) — the four policies defined in
  `src/main/resources/policies/v1.yaml` per research.md D7.
- **Seven-domain disclosure (added by the Task 10 corrective batch — closes MEDIUM-1 in
  `pre_implementation_analysis.md` Section 10 / `codex-recommend.md` Finding #11; required by
  `compliance.md` COMP-CHK-012 [MANDATORY]).** Constitution Principle VI names, "at minimum,"
  seven policy-guardrail domains. Every one of the seven is mapped below to either a concrete
  `PolicyEngine` check or the specific alternative mechanism that covers it — none is a silent
  omission:
  - **Security** → `SEC-001`/`SEC-002`, concrete `PolicyEngine` checks (above).
  - **Compliance** → `COMP-001` (audit-trail presence), a concrete `PolicyEngine` check, scoped
    narrowly to audit-trail presence rather than a broader compliance-program check (a disclosed
    scope decision, not an expansion claim).
  - **Software licensing** → `LIC-001`, a concrete (advisory) `PolicyEngine` check.
  - **Audit retention** → not a `PolicyEngine` check in this iteration; covered instead by CLR-17's
    disclosed 90-day retention placeholder (`spec.md` Section 13.1/13.2), explicitly non-binding
    and approved by the human candidate as a temporary assumption pending a real compliance-derived
    figure (`docs/governance/human-gates-log.md`).
  - **Approved dependencies** → not a `PolicyEngine` check in this iteration; covered instead by
    the manual dependency-scan approach named in Section 8 above and formalized as a documented
    task-level recommendation at T045 (`SECURITY.md`'s `mvn org.owasp:dependency-check-maven:check`
    guidance, no CI configured — a disclosed limitation, not a hidden gap).
  - **Privacy** → not a `PolicyEngine` check in this iteration; this prototype's domain (a URL
    shortener with no user accounts, no PII fields, no authentication) has no personal data to
    govern beyond the long/short URL pair itself, so no dedicated privacy policy check applies —
    disclosed here as the specific reason for the absence, not a silent gap. Any future iteration
    that adds account data would need a real `PRIV-*` policy at that point.
  - **Change control** → not a `PolicyEngine`-evaluated category in this iteration; covered instead
    by REQ-030's own mechanism, `ImpactAnalysisStage` (Section 11.2's SCN-B insertion) plus
    `replan()`'s versioned, audited change record (Section 3.4) — a separate mechanism from
    `PolicyEngine`, by design, since change control here is a workflow-structural property
    (an inserted approval-gated stage), not a per-stage evaluated check.

  This mapping is additive documentation only — it changes no `PolicyEngine` behavior, adds no new
  policy ID, and reverses no ADR; it satisfies `compliance.md` COMP-CHK-012's specific requirement
  that the mapping be stated in one place, by inspection.
- **Versioned policy model:** a single `version` string (e.g. `"1.0.0"`) per policy file; every
  `policy_evaluations` row records the version active at evaluation time (`data-model.md` Section
  7) so a later policy-file edit cannot silently reinterpret history.
- **Policy evaluation inputs/outputs:** input is `(stageName, Map<String,Object> context)` — the
  `WorkflowContext` artifacts a stage handler reported; output is `List<PolicyCheckResult>`, one
  per applicable policy, each exactly `PASS`/`FAIL`/`EXCEPTION_REQUESTED`/`NOT_APPLICABLE`
  (constitution Principle VI's four-value model, no fifth state).
- **Mandatory vs. advisory:** `PolicyDefinition.mandatory` — `SEC-001`, `SEC-002`, `COMP-001` are
  mandatory (a `FAIL` blocks progression per REQ-016/CLR-13); `LIC-001` is advisory (a `FAIL`
  is recorded but does not itself trigger safe-stop).
- **Compliance-check workflow stage:** Stage 10 (Security and risk validation, Section 3.2).
- **Change-request workflow:** any post-approval change to a requirement, architecture decision,
  schema, orchestration state definition, security control, or release criterion (REQ-030) must
  carry a recorded impact analysis and approval before taking effect — realized concretely by
  SCN-B's `ImpactAnalysisStage` pattern (Section 11.2) for code-level brownfield changes, and by
  this plan's general "proposed for ADR" framing for any future architecture-level change not yet
  accepted (the specific ADR-003 through ADR-010 changes this plan originally flagged this way have
  since been reviewed and Accepted at Task 7, 2026-09-13 — see Section 12; the framing itself
  remains this plan's standing mechanism for any *subsequent* architecture change).
- **Policy exception workflow:** `WorkflowEngine.grantPolicyException(workflowId, policyId,
  reason, scope, approvingAuthority, compensatingControl, expiry)` persists a `policy_exceptions`
  row; `retrySafeStopped(workflowId)` re-evaluates the previously safe-stopped step, which now
  passes because `hasApprovedUnexpiredException` finds the just-granted, unexpired row.
- **Human approval requirements:** every field of `PolicyExceptionEntity` — `reason`, `scope`,
  `approvingAuthority`, `compensatingControl`, `expiry` — is required at grant time; there is no
  code path that persists a partially-specified exception.
- **Exception expiry and review conditions:** `expiry` is mandatory (never open-ended, constitution
  Section 4.2); `hasApprovedUnexpiredException` re-checks `expiry.isAfter(now)` on every
  evaluation, so an expired exception silently stops covering its policy again without needing an
  explicit revocation action.
- **Compensating controls:** recorded verbatim in `policy_exceptions.compensating_control` — never
  inferred or defaulted.
- **Policy audit-event structure:** every exception grant emits a
  `policy_exception_granted:<policyId>` `workflow_events` row with `actorType="human"` and the
  exception's `reason` as the event's `reason` field.
- **Release-blocking conditions:** Stage 11 (`ReleaseReadinessStage`) can only report "ready" when
  every mandatory policy check is `PASS` or `NOT_APPLICABLE` (never `FAIL`, never an unapproved
  `EXCEPTION_REQUESTED`) **and** all applicable tests passed (Stage 8) — this plan explicitly
  demonstrates that a failed mandatory policy check prevents release readiness: it is not merely
  asserted, it is the literal precondition `ReleaseReadinessStage`'s handler checks before
  producing anything other than a not-ready outcome, and Section 10's test plan includes a
  dedicated test asserting exactly this (a run with a `FAIL`-state policy never reports "ready").
- **Upstream-change impact propagation:** a change to `spec.md` (a new/amended REQ) or to this
  `plan.md` triggers, per constitution Principle VI/X, a required re-check of every downstream
  artifact citing the changed ID — this plan's own traceability table (Section 13) is the concrete
  mechanism a reviewer uses to find every artifact that must be re-validated when a REQ changes.
- **Versioning/revalidation of changed artifacts:** requirements carry stable REQ IDs (never
  renumbered, spec.md Section 0); architecture decisions are ADR-numbered and superseded, not
  edited in place; contracts are versioned (`contracts/openapi.yaml`'s `info.version`); schemas
  are versioned implicitly via Hibernate's `ddl-auto` for this prototype (a disclosed limitation —
  a real migration tool would version schema changes explicitly, backlog per research.md); tests
  and documentation are revalidated by re-running the affected test classes and re-reading the
  affected doc sections, tracked via the same traceability table.

---

## 10. Testing

Red-green-refactor (constitution Principle IV) for every behavior below, matching the runbook's
Task-level TDD structure exactly — this plan does not introduce a different testing philosophy,
only names the categories the source material requires and maps them to concrete, already-scoped
test classes:

| Category | Test class(es) | REQ(s) |
|---|---|---|
| Domain unit tests | `ShortCodeGeneratorTest`, `UrlValidatorTest`, `UrlValidatorSsrfTest` | REQ-001–REQ-003, REQ-021 |
| API contract tests | `LinkControllerContractTest`, `WorkflowControllerContractTest`, `MetricsControllerContractTest` | REQ-001, REQ-004–REQ-006, REQ-011–REQ-013, REQ-019 |
| Persistence tests | `ShortLinkRepositoryTest` (`@DataJpaTest`) | REQ-003, REQ-008, REQ-009 |
| Integration tests | `UrlShortenerApplicationTests` (full context load incl. H2 `DataSource`) | REQ-010 |
| Orchestration state-transition tests | `DependencyGraphTest`, `WorkflowContextTest`, `WorkflowEngineExecutionTest` | REQ-011, REQ-012 |
| Approval/rejection tests | `WorkflowEngineApprovalTest` | REQ-013 |
| Retry tests | `RetryTest`, `WorkflowEngineReliabilityIntegrationTest` | REQ-014, REQ-022 |
| Timeout tests | `TimeoutTest` | REQ-014 |
| Fallback tests | Covered within `WorkflowEngineReliabilityIntegrationTest` (retry-then-safe-stop path) | REQ-014, REQ-022 |
| Rollback/compensation tests | `CompensationRegistryTest`, one rollback-eligible + one compensation-eligible case in `WorkflowEngineReliabilityIntegrationTest` | REQ-015 |
| Safe-stop tests | `WorkflowEngineSafeStopTest` | REQ-016 |
| Resumption tests | Covered by `WorkflowEngineContextLineageTest` (context reload across a resume boundary) | REQ-017, REQ-028 |
| Replanning tests | `WorkflowEngineReplanningTest`, `AmbiguousScenarioTest` (event-order assertions) | REQ-018 |
| Concurrency tests | REQ-008's N-way concurrency test (persistence layer); Task 22's parallel-batch execution test | REQ-008, REQ-023 |
| Security tests | `UrlValidatorSsrfTest`, the four REQ-021 abuse-class tests | REQ-002, REQ-021 |
| End-to-end tests | `GreenfieldScenarioTest`, `BrownfieldScenarioTest`, `AmbiguousScenarioTest` | all three scenarios (Section 11) |
| Release-readiness checks | A dedicated assertion (within `WorkflowEnginePolicyEnforcementTest` or a release-readiness-specific test) that a mandatory-`FAIL` run never reports "ready" | REQ-020, Section 9 |

**Contract-test obligation created by Section 2's two plan-level API additions:** since
`GET /links/{code}` and `POST /workflows` are specified here but not yet implemented in the
runbook's reference code, `tasks.md` (Task 9) must add their contract tests as first-class tasks,
not assume they are already covered by the existing `LinkControllerContractTest`/
`WorkflowControllerContractTest` names above.

---

## 11. Scenario Designs

### 11.1 SCN-A — Greenfield

- **Initial input:** a well-defined feature requirement (runbook example:
  `"add-expiration-endpoint"`).
- **Requirement interpretation:** `RequirementStage` performs completeness/consistency/
  testability/policy-boundary checks and records **why clarification was skipped** — not merely
  that it was skipped (spec.md Section 4, SCN-A).
- **Decomposition:** `DecompositionStage` → reviewable units with acceptance criteria.
- **Orchestration path:** full 12-node baseline graph (Section 3.2), Stage 4 not triggered on the
  first pass; Design (`exitApproval`) → fan-out to Implementation and Documentation; Implementation
  → fan-out to Testing and Security; join at Release-readiness (`exitApproval`).
- **Approvals:** `design`, `release_readiness`.
- **Failure paths:** if ambiguity is discovered mid-Implementation/Testing, only that branch
  safe-stops pending clarification; already-in-flight, unaffected Documentation work is not
  discarded (spec.md Section 4's explicit non-linear elaboration) — realized via `replan()`
  targeting only the affected branch, not the whole graph.
- **Validation:** `GreenfieldScenarioTest` — asserts terminal `COMPLETED` and that the first (or
  an early) recorded decision documents the no-clarification-required rationale.
- **Generated evidence:** `docs/scenarios/greenfield.md` — requirement, quality checks performed,
  skip rationale, decomposition, the actual `GET /workflows/{id}/audit` JSON from a real run,
  acceptance criteria, terminal outcome.
- **Expected terminal outcome:** `COMPLETED`, release-readiness `ready`.

### 11.2 SCN-B — Brownfield

- **Initial input:** a real, disclosed defect in already-shipped code — `UrlValidator`'s SSRF
  guard fails to reject IPv6 Unique Local Addresses (RFC 4193, `fc00::/7`) because
  `Inet6Address.isSiteLocalAddress()` only implements the deprecated site-local range
  (`FEC0::/10`). Chosen deliberately over the originally-planned link-mutation demo, which CLR-15
  (Links are immutable) forecloses.
- **Requirement interpretation:** a targeted hardening of an existing security control, not a new
  capability.
- **Decomposition:** the fix is scoped to exactly one production file (`UrlValidator.java`) and
  its test suite.
- **Orchestration path:** baseline graph plus an inserted `ImpactAnalysisStage` (`dependsOn=[]`),
  with `implementation.dependsOn` extended to `["impact_analysis", "design"]` — the seven impact
  dimensions (spec.md Section 4, SCN-B) are represented in the stage's structured result
  (`impactedModules`, `impactedTests`, `regressionRisk`; the remaining four — interfaces, data
  flows, documentation, rollout/rollback — are covered in `docs/scenarios/brownfield.md`'s
  narrative for this specific, contract-shape-unchanged fix, since a private-method-level security
  hardening has no interface/data-flow/rollout impact to report beyond "none," which is itself a
  recorded finding, not an omission).
- **Approvals:** the joined Change Impact Report gates `implementation` (REQ-013) — brownfield's
  defining difference from greenfield, since existing behavior and existing consumers are at risk.
- **Failure paths:** if the regression suite (`UrlValidatorTest` + `UrlValidatorSsrfTest`) fails
  after the fix, the branch safe-stops rather than proceeding to Documentation or
  Release-readiness (REQ-016).
- **Validation:** `BrownfieldScenarioTest` (impact analysis precedes implementation, non-empty
  findings) plus a real TDD cycle against `UrlValidatorSsrfTest` (failing test for
  `http://[fd00::1]/internal` → fix → passing test → full regression suite green).
- **Generated evidence:** `docs/scenarios/brownfield.md` — Change Impact Summary, Dependency Map,
  Test-First Change Plan, Regression Risk Matrix, Documentation Impact, Replanning Decision, Human
  Approval Requirement; two coherent commits (the fix itself, then the scenario/orchestration
  wiring).
- **Expected terminal outcome:** `COMPLETED`, with a recorded rollback/compensation plan
  (REQ-015) as a Release-readiness input per REQ-020's brownfield-specific requirement.

### 11.3 SCN-C — Ambiguous Requirement

- **Initial input:** a deliberately ambiguous requirement — `"add rate limiting"` with no numeric
  target (the exact residual gap CLR-11 already names in `spec.md` Section 13.2).
- **Requirement interpretation:** `requirement`'s handler *succeeds* (this is not an error) and
  reports `ambiguityDetected=true` with a reason; `exitApproval=true` halts before `decomposition`
  runs — a hard fan-out block, not a delayed one.
- **Decomposition:** blocked entirely until clarification resolves — nothing downstream silently
  proceeds on the unclear input (constitution Principle I).
- **Orchestration path:** `requirement → [suspended] → replan(decomposition→decomposition_v2) →
  approve(requirement) → decomposition_v2 → ...`. The fixed **replan-before-approve** ordering
  (Section 3.4) applies here mechanically: `approve()` synchronously resumes execution, so
  `decomposition_v2` must already be registered before `approve()` is called.
- **Approvals:** the `requirement` exit gate, resolved with the human's clarification answer
  (e.g., "rate limit set to 100 req/min/IP").
- **Failure paths:** N/A as a distinct path here — the "failure" this scenario demonstrates *is*
  the ambiguity itself, correctly detected and blocked rather than silently guessed past.
- **Validation:** `AmbiguousScenarioTest` — asserts `PENDING_APPROVAL` before replan/approve,
  `COMPLETED` after, and the audit-event ordering `ambiguity_detected < replanned <
  approval_approved`.
- **Generated evidence:** `docs/scenarios/ambiguous.md` — the 17-point evidence list (original
  ambiguous input, detected ambiguity, classification/severity, affected requirements, workflow
  state before/after, clarification request text, recorded decision, updated assumption,
  downstream impact analysis, replanning event, invalidated/regenerated artifacts, resumed state,
  final validation, audit trail excerpt, terminal outcome).
- **Expected terminal outcome:** `COMPLETED`, with the audit trail preserving Stages 1–3's
  original execution unchanged (REQ-018 negative criterion) alongside the replan.

---

## 12. Technology Decisions

Full per-decision rationale lives in `research.md`. This table is the summary the source material's
Section 12 asks for, restated from the runbook's own Architecture Decisions table, with this
plan's own Status column added. At authoring time (Task 6) that column distinguished the two
already-Accepted ADRs from the eight still pending Task 7; see the correction immediately below
for its current, post-Task-7 state.

**Status column corrected by the Task 10 corrective batch** (`pre_implementation_analysis.md` /
`codex-recommend.md` Finding #12): Task 7 (the ADR Generation Gate) has since run and the human
candidate has accepted all eight of ADR-003 through ADR-010, without amendment, on 2026-09-13 (see
`docs/governance/human-gates-log.md`, "Human Gate 4" rows). The table below previously listed them
as "Proposed for ADR at Task 7," which was accurate when this plan was authored at Task 6 but is
now stale; it is corrected here to their current Accepted status. This correction is about
ADR-003..010's own acceptance status, a gate that has already closed — it does not change or
close this plan document's own separate, still-pending plan-approval status (see this document's
own top-of-file Status line, left as-is).

| # | Decision question | Selected/proposed option | Status | Rationale (short) |
|---|---|---|---|---|
| ADR-001 | Language/framework | Java 21 (LTS) + Spring Boot 3.3 | **Accepted** (2026-09-13) | Static typing + Spring test slices fit a state-machine-heavy orchestration engine; explicit human directive |
| ADR-002 | Persistence | H2 embedded, file-mode, via Spring Data JPA | **Accepted** (2026-09-13) | Idiomatic Spring Boot embedded DB; zero-infra; `@DataJpaTest`-native |
| ADR-003 | Short-code generation | Base62 encoding of `short_links.id` | **Accepted** (2026-09-13) | Deterministic, collision-free by construction; no retry loop needed |
| ADR-004 | Orchestration model | Custom in-process `WorkflowEngine` over an explicit `DependencyGraph` | **Accepted** (2026-09-13) | Satisfies every required orchestration characteristic without disproportionate infra |
| ADR-005 | External orchestration product (Temporal/Camunda/Activiti) | Rejected | **Accepted** (2026-09-13) | Infra/ops burden disproportionate to a local 2–3 day prototype |
| ADR-006 | Human approval mechanism | Synchronous `POST /workflows/{id}/approvals/{step}` | **Accepted** (2026-09-13) | Explicit, auditable, non-inferred act; no UI in scope |
| ADR-007 | Policy engine format | Versioned YAML, Jackson-parsed | **Accepted** (2026-09-13) | Human-readable, diffable, versioned without a DB migration |
| ADR-008 | Concurrency for parallel stages | `ExecutorService` + `Future.get()` per batch | **Accepted** (2026-09-13) | Proves real parallel fan-out without reactive-wide rewrite |
| ADR-009 | Rollback vs. compensation | Transaction-abort rollback / registered-action compensation | **Accepted** (2026-09-13) | Matches Principle VIII's required distinction; matches CLR-14 |
| ADR-010 | Authentication | None enforced; documented trusted-caller-only | **Accepted** (2026-09-13) | Matches CLR-10's already-confirmed specification decision |

Task 7 (the ADR Generation Gate) has run: each of the eight rows above was formally drafted as its
own ADR document and routed to the human candidate for explicit accept/reject — per the project's
own guardrail, no agent may self-approve an ADR (see ADR-001/002's own "Governance" sections for
the precedent this plan follows) — and all eight were accepted without amendment on 2026-09-13.

---

## 13. Traceability

Full chain per the source material: **Requirement → Scenario → Design → ADR → Task → Code → Test
→ Validation → Documentation → Evidence.** This plan seeds the middle links (Design/ADR/Task) that
`spec.md` Section 11 could not yet populate; `tasks.md` (Task 9) will complete the Task→Code→Test
columns with concrete task IDs once it exists.

| Requirement(s) | Scenario(s) | Design (this plan) | ADR | Documentation | Evidence (planned) |
|---|---|---|---|---|---|
| REQ-001–REQ-003 | SCN-A, SCN-B | Section 2 (component plan), `contracts/openapi.yaml` `POST /links` | ADR-003 (short-code) | `data-model.md` §1 | `LinkControllerContractTest`, `ShortCodeGeneratorTest` |
| REQ-004, REQ-005 | SCN-A, SCN-B | Section 2, `contracts/openapi.yaml` `GET /r/{code}` | — | `data-model.md` §1–2 | Resolution/expiration boundary tests |
| REQ-006 | SCN-A | Section 2, `contracts/openapi.yaml` `GET /links/{code}/analytics` | — | `data-model.md` §1–2 | Analytics-capture + failure-injection tests |
| REQ-007 (superseded in part, CLR-2) | SCN-A | Section 6 ("Idempotency" row) — creation is explicitly non-idempotent across distinct requests, a confirmed design choice, not an unmet requirement | — | `research.md` D-decisions Backlog | Retained for historical traceability only; no MUST-level test obligation for this iteration per CLR-2 |
| REQ-008, REQ-009 | SCN-A, SCN-B | Section 6 (reliability) | ADR-002 (persistence) | `data-model.md` §1 | Concurrency + outage failure-injection tests |
| REQ-010 | SCN-A, SCN-B | Section 2, `contracts/openapi.yaml` `/healthz`/`/readyz` | ADR-002 | Section 1.5 | Health-check nominal/degraded tests |
| REQ-011, REQ-012 | SCN-A, SCN-B, SCN-C | Section 3 (node table) | ADR-004 | `data-model.md` §3–4 | `DependencyGraphTest`, `WorkflowEngineExecutionTest` |
| REQ-013 | SCN-A, SCN-B, SCN-C | Section 5 (HITL gates) | ADR-006 | `data-model.md` §6 | `WorkflowEngineApprovalTest` |
| REQ-014 | SCN-A, SCN-B | Section 6 (reliability) | — | — | `RetryTest`, `TimeoutTest` |
| REQ-015 | SCN-B | Section 6, research.md D9 | ADR-009 | — | `CompensationRegistryTest` |
| REQ-016 | SCN-A, SCN-B, SCN-C | Section 3.4, 6 | — | — | `WorkflowEngineSafeStopTest` |
| REQ-017, REQ-028 | SCN-A, SCN-C | Section 3.4, 6 | — | — | `WorkflowEngineContextLineageTest` |
| REQ-018 | SCN-A, SCN-C | Section 3.4 (replan) | ADR-004 | `docs/scenarios/ambiguous.md` | `WorkflowEngineReplanningTest`, `AmbiguousScenarioTest` |
| REQ-019, REQ-026 | SCN-A, SCN-B, SCN-C | Section 4, 7 | — | `data-model.md` §5 | Audit-trail completeness test |
| REQ-020 | SCN-A, SCN-B | Section 9 (release-blocking) | — | — | Release-readiness FAIL/pass tests |
| REQ-021 | SCN-A, SCN-B | Section 8 (security) | ADR-010 | — | `UrlValidatorSsrfTest`, abuse-class tests |
| REQ-022, REQ-023 | SCN-A, SCN-B | Section 6, 7 | ADR-008 | — | Fault-injection + concurrency test suites |
| REQ-024 | SCN-B | Section 1.4, 2 | ADR-004 | — | Architecture/dependency-direction review |
| REQ-025 | SCN-A, SCN-B, SCN-C | Section 7 | — | — | Correlation-ID completeness check |
| REQ-027 | SCN-A | — (deferred numeric target, spec.md ASM-2) | — | — | Latency measurement, demonstration-scale |
| REQ-029 | all | Sections 6–7 of `spec.md` themselves | — | — | This plan's own acceptance-criteria citations |
| REQ-030 | SCN-B | Section 9 (change control) | — | `docs/scenarios/brownfield.md` | Change-log/impact-analysis cross-reference |
| REQ-031 | SCN-A, SCN-B, SCN-C | Section 3 (actor column per node) | — | — | Audit-trail review; stage-boundary violation test |

---

## 14. Delivery Sequence & Scope Control

### 14.1 Nine vertical slices (matches this plan's phase structure)

Each slice is runnable/testable end-to-end before the next begins — no slice is "implementation
only, tests later" (constitution Principle IV):

| # | Slice | Corresponds to |
|---|---|---|
| 1 | Engineering baseline | Phase 2 — Spring MVC skeleton, health endpoints (already scaffolded, Task 2/11) |
| 2 | Walking skeleton | Phase 2 — full-context boot against real H2 file-mode DB |
| 3 | Core URL behavior | Phase 3 — creation, validation, short-code generation, redirect, expiration, analytics |
| 4 | Orchestration state model | Phase 4 — `DependencyGraph`, `WorkflowEngine`, persistence, approval gates, context lineage |
| 5 | Approval governance | Phase 5 — `PolicyEngine`, mandatory-FAIL blocking, exception workflow |
| 6 | Reliability controls | Phase 6 — retry, timeout, compensation, safe-stop convergence |
| 7 | Dynamic replanning | Phase 7 — `replan()`, invalidate-downstream, resume |
| 8 | Observability | Phase 8 — audit consolidation, MTTR/reliability metrics, `/metrics` |
| 9 | Three scenarios + release readiness | Phase 9–11 — greenfield/brownfield/ambiguous demonstrations, cross-scenario evidence review, traceability, final summary |

### 14.2 Must-have scope (this plan's mandatory deliverable set)

Phases 2–10 as scoped in the runbook and restated in Section 3–11 above: the full URL-shortener
domain, the full 12-node orchestration graph with all cross-cutting reliability/governance
mechanisms, the versioned policy engine, and all three mandatory scenarios with generated evidence
documents. Nothing in this set may be displaced by optional enhancement work (source material's
explicit constraint).

### 14.3 Deferred backlog (explicitly out of scope for this iteration)

Restated from `research.md`'s closing Backlog section — not omitted by oversight:

- Authentication/authorization (ADR-010 residual).
- Per-caller/per-IP rate limiting (CLR-11; proposed unapproved figure: 100 req/min/IP).
- Flyway/Liquibase-managed schema migrations (currently Hibernate `ddl-auto: update`).
- Request-level idempotency-key deduplication (REQ-007, superseded by CLR-2).
- Custom short-code aliases (CLR-4).
- Link mutation/update capability (CLR-15 — Links are immutable this iteration).
- Multi-region/distributed short-code ID generation (D3's single-writer-sequence caveat).
- A real, compliance-derived audit/analytics retention policy (CLR-17's 90-day figure is a
  disclosed placeholder).

### 14.4 Day-level milestones — an honest accounting, not a reset clock

The assignment's nominal timebox is 2–3 days. **This plan is being authored after Tasks 1–5 (SpecKit
bootstrap, project skeleton, constitution ratification, spec approval, and clarification) plus an
unplanned governance-remediation detour (a stack pivot from Python to Java/Spring Boot after Task 2,
formalized retroactively as ADR-001/002) have already consumed real elapsed effort across
2026-09-12–2026-09-13.** The milestones below are planned from **now** — the start of Phase 2 —
not from a fictitious Day-1-hour-0 that has already passed:

- **Day 1 (from now):** Phases 2–4 — engineering baseline through orchestration state model
  (slices 1–4). This is the highest-risk slice: the orchestration engine (`DependencyGraph`,
  `WorkflowEngine`, approval gates, context persistence) is the project's actual differentiator
  (constitution Principle II) and the load-bearing dependency for every later phase.
- **Day 2 (from now):** Phases 5–9 — policy engine, reliability controls, dynamic replanning,
  observability, and all three scenarios (slices 5–9). This is the day every governance mechanism
  this plan documents (Sections 5, 6, 9) actually gets built and exercised, not merely designed.
- **Day 3 (from now):** Phases 10–11 — cross-scenario evidence review, architecture
  overview/traceability matrix, reviewer navigation guide, README/SECURITY/CONTRIBUTING,
  full quality-suite execution, and the Final Engineering Summary/Independent Assessment,
  plus general hardening/buffer time given governance work already consumed part of the nominal
  Day 1.

### 14.5 Critical-path dependencies

Orchestration engine (Phase 4) blocks policy enforcement (Phase 5), reliability wiring (Phase 6),
and replanning (Phase 7) — none of those can be meaningfully tested against a fake/stub engine and
still count as validating this plan's actual design. All three scenarios (Phase 9) depend on
Phases 3 (domain, for the greenfield/brownfield demo content), 4 (engine), 5 (policy), 6
(reliability), and 7 (replanning, for SCN-C specifically) all being real and passing — Phase 9
cannot be pulled forward or run against partial infrastructure.

### 14.6 Stop conditions

**If the orchestration engine core (Phase 4 — `DependencyGraph`, `WorkflowEngine` sequential +
parallel execution, approval gates, persisted context) is not passing its own test suite by the
end of Day 1, cut Scenario C's replanning depth before cutting any governance or audit
requirement.** Concretely, an acceptable Day-1-slip fallback is: implement SCN-C's ambiguity
detection and suspension (Stage 3→4 hard block, Section 3.3) fully, but reduce the demonstrated
replan to a single-stage substitution (as already scoped in Section 11.3) rather than a
multi-stage cascade — never trade away audit completeness (REQ-019/REQ-026), the mandatory-FAIL
release-block (Section 9), or any Approval Gate's non-silent-approval guarantee (REQ-013) to save
schedule. Those are the constitution's non-waivable principles (I, III, IV, XI) and this plan does
not create a scope-control exception for them.

### 14.7 Minimum defensible release-readiness outcome

If Day 3 arrives with the deferred-backlog items (Section 14.3) still deferred but Phases 2–9
complete and passing, this plan considers that a legitimate "ready" release-readiness outcome
(Section 9) — the backlog items were never part of this iteration's must-have scope. If any of
Phases 2–9 is incomplete, the honest outcome is **not-ready-with-reasons** (REQ-020's own
enumerated outcome set), naming exactly which phase/requirement is unmet — never a narrative claim
of completeness unsupported by the evidence trail this plan's own Section 4/7 require.
