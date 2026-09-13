# Data Model — Agentic URL Shortener

**Status: DRAFT.** This document is produced by `/speckit.plan` (Task 6) and is pending whatever
human review Task 6 itself requires. It is not independently approved.

**Authority and provenance.** These table definitions are not invented here. They are transcribed
from the authoritative JPA `@Entity` class definitions already fixed in the controller's execution
runbook (`docs/engineering-runbook.md`, Tasks 12, 21, 23, 28,
29 — a Java implementation-detail document that is subordinate to this plan per its own "Authority"
note, but whose already-worked-out column/type/constraint decisions are the correct ground truth to
promote into the architecture-level artifact rather than re-derive from scratch). This document
restates those decisions at the SpecKit `data-model.md` altitude: one purpose paragraph plus a
column table per table, with the requirement(s) each table exists to satisfy.

Engine: **H2, embedded, file-mode** (ADR-002, Accepted), accessed exclusively through Spring Data
JPA repositories — no raw SQL in application code (ADR-002 Decision). Schema is created via
Hibernate `ddl-auto: update` from these entities; there is no separate DDL migration file in this
iteration (Flyway/Liquibase-managed migrations are backlog — see Section 4/scope-control in
`plan.md`). All `Instant` columns are stored as UTC timestamps. All JSON-shaped columns
(`depends_on`, `result`, `payload`, `context_json`) are `CLOB` columns holding Jackson-serialized
JSON, converted via `AttributeConverter` implementations (`JsonMapAttributeConverter`,
`JsonListAttributeConverter`) — chosen over a native H2 JSON type so the same converter pattern
works unchanged if the engine is ever swapped (ADR-002 Reversibility).

Two independent schemas share one physical database: the **URL-shortener domain** schema
(`short_links`, `redirect_events`) and the **orchestration engine** schema (`workflow_instances`,
`workflow_steps`, `workflow_events`, `approvals`, `policy_evaluations`, `policy_exceptions`).
They are not foreign-keyed to each other — the orchestration engine is a general-purpose SDLC
workflow runner that happens to be demonstrated against the URL-shortener domain, per constitution
Principle VII (separation of concerns) — a workflow's `feature` field is a free-text label, not a
foreign key into `short_links`.

---

## 1. `short_links`

**Purpose.** One row per issued short URL mapping (spec.md Section 2.1, "Link"). Satisfies
REQ-001 (creation), REQ-003 (uniqueness, via the `code` unique constraint), REQ-004/REQ-005
(resolution and expiration, via `expires_at`), and REQ-006 (analytics capture, via
`redirect_count`/`last_accessed_at`). Rows are never updated except for `redirect_count` and
`last_accessed_at` on a successful redirect (CLR-15: Links are otherwise immutable — no
update/PATCH capability exists over `code`, `long_url`, or `expires_at` after creation).

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY` (auto-increment). Also the input to Base62 short-code derivation (ADR-003). |
| `code` | `VARCHAR(16)` | No | `UNIQUE`. Populated in a second write after `id` is assigned (ADR-003: Base62 encoding of `id`). Confirmed shape (CLR-3): exactly 7 base62 characters (`[a-zA-Z0-9]`) for all codes issued under this design. |
| `long_url` | `VARCHAR(2048)` | No | Max length 2048 enforced at both the API validation layer (REQ-002/CLR-5) and the column definition (defense in depth). |
| `created_at` | `TIMESTAMP` | No | Defaults to creation instant. Immutable after insert. |
| `expires_at` | `TIMESTAMP` | Yes | `NULL` = no expiration (CLR-6 confirmed default). Caller-set at creation only; never updated thereafter (CLR-15). |
| `redirect_count` | `INT` | No | Default `0`. Incremented on each successful (non-expired) redirect. Eventually consistent, last-writer-wins under concurrency (CLR-8, CLR-9) — the one column in this schema explicitly exempted from strict write-serialization. |
| `last_accessed_at` | `TIMESTAMP` | Yes | `NULL` until first successful redirect. Same eventual-consistency note as `redirect_count`. |

**Not modeled as a column, by design:** a "deleted"/"revoked" flag — link immutability (CLR-15)
means the only lifecycle this table needs is create → (optionally) expire; there is no soft-delete
requirement in this iteration.

---

## 2. `redirect_events`

**Purpose.** One row per redirect-resolution *attempt* against a short code (spec.md Section 2.1,
"Redirect Event"), regardless of outcome. Satisfies REQ-006 (analytics capture) and provides the
raw evidence a later analytics rollup or audit could reconstruct from, independent of the
already-aggregated `short_links.redirect_count`. Also the evidence base for REQ-021's
abuse-pattern-visibility requirement (e.g., a burst of `not_found` outcomes against sequential
codes would be visible here even though no rate-limiting is enforced this iteration, CLR-11).

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. |
| `short_link_id` | `BIGINT` | No | Logical reference to `short_links.id`. Populated only for a *known* code (an `outcome = 'not_found'` row is not written, since there is no `short_link_id` to attach it to — see Note below). |
| `occurred_at` | `TIMESTAMP` | No | Defaults to the instant the resolution attempt was processed. |
| `outcome` | `VARCHAR(16)` | No | One of `redirected` \| `expired` \| `not_found` (application-level enumeration, not a DB `CHECK` constraint in this iteration — enforced by the single write path in `LinkController`/`RedirectService`). |

**Note (traceability caveat, disclosed not silently assumed):** the runbook's reference
implementation for this table currently records `expired` and `redirected` outcomes (both of which
have a resolvable `short_link_id`) but does not persist a `not_found` row for an unknown code,
since there is no link row to key it to. This is a known gap against REQ-006's literal "analytics
event for each successful redirect resolution" framing (which only requires the success case) but
is called out here because REQ-021's abuse-visibility framing would benefit from an unknown-code
counter too. Recorded as a backlog refinement (a schema-free option: a separate lightweight counter
keyed by requested code, not a `short_link_id`-nullable column, to avoid recording arbitrary
attacker-supplied strings as if they were meaningful keys) — not required for this iteration's
release-readiness outcome (REQ-020), since REQ-006's MUST-level obligation is about successful
redirects only.

---

## 3. `workflow_instances`

**Purpose.** One row per Workflow Run (spec.md Section 2.1, "Workflow Run") — the root of the
orchestration engine's persisted state. Satisfies REQ-011 (workflow creation), REQ-012 (workflow
inspection), REQ-017 (resumption), REQ-018 (dynamic replanning, via `plan_version`), and anchors
the correlation-ID requirement (REQ-025) that every event and log line for this run must carry.

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `VARCHAR(64)` | No | Primary key. Application-assigned workflow/run identifier (UUID-shaped string), not a DB sequence — assigned at `submit()` time so it can be returned to the caller before any stage executes. |
| `feature` | `VARCHAR(255)` | Yes | Free-text label for the ingested requirement/feature this run executes against (e.g. `"add-expiration-endpoint"`). Not a foreign key into any domain table (see schema-separation note above). |
| `status` | `VARCHAR(32)` | No | Enum `WorkflowStatus`: `CREATED, RUNNING, PENDING_APPROVAL, SAFE_STOPPED, REPLANNING, COMPLETED, FAILED`. Stored via `EnumType.STRING` (reviewer-legible in the H2 console, not an opaque ordinal). |
| `correlation_id` | `VARCHAR(64)` | Yes | The run/correlation identifier every audit event, log line, and produced artifact for this run must carry (REQ-025). Distinct from `id` to allow a future case where multiple workflow instances share one external correlation context (not exercised in this iteration, but kept separate rather than conflated). |
| `plan_version` | `INT` | No | Default `1`. Incremented by `replan()` (REQ-018). A stage's `WorkflowStepEntity` rows record which invocation of the plan produced them, so replan history is reconstructable rather than only the current plan being visible. |
| `context_json` | `CLOB` (JSON) | No | Default `{}`. Serialized `WorkflowContext` — the accumulated **decision lineage** (`decisions: [{actor, decision, rationale}]`) and **artifacts** (`Map<String,Object>`) carried across stage boundaries and across process restarts. This is the literal persistence mechanism for spec.md Section 4's "preserved decision lineage" requirement — see `plan.md` Section 4. |
| `created_at` | `TIMESTAMP` | No | Set at `submit()`. |
| `updated_at` | `TIMESTAMP` | No | Updated on every status transition. |

---

## 4. `workflow_steps`

**Purpose.** One row per stage instance within a Workflow Run (spec.md Section 2.1, "Stage"). This
is the per-node state the Section 3.1 orchestration graph is actually persisted as — a
`WorkflowStepEntity` is created for every `StageDefinition` at `submit()`/`replan()` time, and its
`status` column is the literal state machine REQ-011/REQ-016/REQ-017 require to be inspectable
without reconstructing anything from an agent transcript.

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. |
| `workflow_id` | `VARCHAR(64)` | No | Logical reference to `workflow_instances.id`. |
| `name` | `VARCHAR(128)` | No | Stage name (e.g. `design`, `implementation`, `impact_analysis`). **Corrected post-Task-10 (see closing note below):** unique per `(workflow_id, plan_version, name)`, not `(workflow_id, name)` alone — a replan may retire a name at one `plan_version` and legitimately reintroduce the same name at a later `plan_version` (in addition to, or instead of, an explicitly versioned variant like `decomposition_v2`); the `plan_version` column above is what makes those rows distinguishable. Enforced by application logic via `findByWorkflowIdAndPlanVersionAndName`, not a DB unique constraint. |
| `status` | `VARCHAR(32)` | No | Enum `StepStatus`: `PENDING, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, RETRYING, ROLLED_BACK, COMPENSATED, SAFE_STOPPED, INVALIDATED, SKIPPED`. This is the full node-transition vocabulary Section 3 of `plan.md` maps onto. |
| `sequence` | `INT` | No | Topological-batch ordering hint for display; the authoritative execution order is the `DependencyGraph`'s computed batches, not this column alone. |
| `depends_on` | `CLOB` (JSON list) | Yes | Serialized `List<String>` of predecessor stage names — the persisted edge list for this node. |
| `started_at` | `TIMESTAMP` | Yes | Null until the stage's handler is actually invoked (i.e., not set while `WAITING_APPROVAL` on an entry gate). |
| `ended_at` | `TIMESTAMP` | Yes | Null until the stage reaches a terminal per-step outcome. |
| `retry_count` | `INT` | No | Default `0`. Incremented once per failed attempt (REQ-014); the reliability-metrics layer (`plan.md` Section 7) reads this directly rather than re-deriving it from event logs. |
| `result` | `CLOB` (JSON map) | Yes | Serialized `Map<String,Object>` — the stage handler's output, merged into the run's context artifacts. This is the durable record of "outputs" for the node table in `plan.md` Section 3. |
| `plan_version` | `INT` | No | **Added post-Task-10 (see closing note below).** The `workflow_instances.plan_version` value active when this step row was created or replaced (by `submit()` or by `replan()`). Without this column, a step and its replan-produced replacement cannot be reliably distinguished, and `(workflow_id, name)` alone is not a safe uniqueness assumption once a replan can reintroduce a retired stage name. Uniqueness for a live step is over `(workflow_id, plan_version, name)`, not `(workflow_id, name)` alone. |

---

## 5. `workflow_events`

**Purpose.** The append-only audit trail (spec.md Section 2.1, "Audit Record"; REQ-019, REQ-026).
Every state transition, decision, approval/rejection, retry, failure, and replan produces exactly
one row here. Rows are never updated or deleted (REQ-026's negative acceptance criterion: "never
allows an existing audit record to be edited or deleted after the fact") — enforced at this
iteration's scale by there being no `UPDATE`/`DELETE` code path against this table anywhere in the
codebase, not by a database-level immutability trigger (a documented, accepted prototype-scope
limitation — see `plan.md` Section 8, least-privilege/secure-defaults discussion).

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. Insertion order is the authoritative event order for a given `workflow_id` (queried via `findByWorkflowIdOrderById`). |
| `workflow_id` | `VARCHAR(64)` | No | Logical reference to `workflow_instances.id`. |
| `actor_type` | `VARCHAR(32)` | No | `agent` \| `human` \| `orchestrator` — the "responsible actor" REQ-026 requires on every event. |
| `action` | `VARCHAR(255)` | No | Machine-parseable event name, e.g. `stage_succeeded:design`, `ambiguity_detected:requirement`, `replanned`, `approval_approved:release_readiness`, `policy_exception_granted:SEC-001`, `compensation_ran:notify_customer`. |
| `timestamp` | `TIMESTAMP` | No | Event instant. |
| `payload` | `CLOB` (JSON map) | Yes | Event-specific structured detail (e.g. a policy evaluation's reason, a replan's old/new stage list). |
| `result` | `VARCHAR(64)` | Yes | Outcome tag where applicable (e.g. `OK`, `DETECTED`, `PASS`, `FAIL`). |
| `reason` | `VARCHAR(1024)` | Yes | Human-readable rationale — required by REQ-026 to be present on every event that has one (approvals, rejections, exceptions, replans); may be null only for purely mechanical transitions where no rationale is meaningful (e.g. a routine `stage_succeeded` with no decision content). |

---

## 6. `approvals`

**Purpose.** One row per human-owned Approval Gate instance (spec.md Section 2.1, "Approval Gate";
REQ-013). Distinguishes a *requested* gate (row exists, `decision` null) from a *decided* one, so
"pending" is a real, queryable state rather than inferred from absence.

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. |
| `workflow_id` | `VARCHAR(64)` | No | Logical reference to `workflow_instances.id`. |
| `plan_version` | `INT` | No | **Added post-Task-10 (see closing note below).** The `workflow_instances.plan_version` this gate was requested/decided under. Required so a decision made against one plan version cannot be (mis)read as governing a different plan version's reintroduced gate of the same step name — directly implements ADR-006's own stated rule that a rejection blocks a branch "for that plan version." |
| `step_instance_id` | `BIGINT` | No | **Added post-Task-10 (see closing note below).** Logical reference to the specific `workflow_steps.id` row this gate instance governs — not merely a `step_name` string. Replaces the prior bare-`step_name` reference so an approval is unambiguously scoped to one concrete step instance, not any row that happens to share its name across plan versions. |
| `step_name` | `VARCHAR(128)` | No | Which `workflow_steps.name` this gate blocks (an entry gate blocks the stage itself; an exit gate blocks its dependents — see `plan.md` Section 3). Retained alongside `step_instance_id` for human-legible display; `step_instance_id` is the authoritative reference. |
| `requested_at` | `TIMESTAMP` | No | Set when the gate is first reached. |
| `decided_at` | `TIMESTAMP` | Yes | Null while pending. REQ-013's negative criterion ("never treats an unanswered gate as approved") is enforced by the engine never proceeding on a row with `decided_at IS NULL` — there is no timeout-based auto-transition anywhere in the codebase. |
| `decision` | `VARCHAR(16)` | Yes | `approved` \| `rejected`. Null while pending. |
| `decided_by` | `VARCHAR(128)` | Yes | Actor identity string (no authentication is enforced this iteration — CLR-10 — so this is a self-asserted label, not a verified identity; disclosed as a security-model limitation in `plan.md` Section 8). |
| `reason` | `VARCHAR(1024)` | Yes | Rationale for the decision — required by REQ-013's "recorded with rationale" language for any decided row; may be null only while `decision` is also null. |

---

## 7. `policy_evaluations`

**Purpose.** One row per policy check performed at a given stage of a given run (spec.md Section
2.1, "Policy Check Result"; constitution Principle VI). Satisfies REQ-020's requirement that
release-readiness be derived from "actual policy... state," not narrative claims — this table is
that actual state, queried directly rather than recomputed from memory.

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. |
| `workflow_id` | `VARCHAR(64)` | No | Logical reference to `workflow_instances.id`. |
| `stage_name` | `VARCHAR(128)` | No | Which stage this evaluation ran during. |
| `policy_id` | `VARCHAR(32)` | No | e.g. `SEC-001`, `SEC-002`, `COMP-001`, `LIC-001` (ADR-007's versioned policy file — see `plan.md` Section 9). |
| `policy_version` | `VARCHAR(16)` | No | The policy file's declared `version` string at evaluation time (e.g. `"1.0.0"`) — recorded per-row so a later policy-file change never silently reinterprets a historical evaluation (REQ-030, change safety). |
| `outcome` | `VARCHAR(24)` | No | Enum `PolicyOutcome`: `PASS, FAIL, EXCEPTION_REQUESTED, NOT_APPLICABLE` — the exact four-value model constitution Principle VI mandates. |
| `reason` | `VARCHAR(1024)` | Yes | Why the outcome was reached (e.g. which `check_key` was absent from the stage's reported artifacts). |

---

## 8. `policy_exceptions`

**Purpose.** One row per human-approved, time-bounded deviation from a mandatory policy (spec.md
Section 2.1, "Exception"; constitution Section 4.2). Existence of an unexpired row for
`(workflow_id, policy_id)` is the sole mechanism by which a mandatory `FAIL` may be bypassed
(`WorkflowEngine.hasApprovedUnexpiredException`) — there is no other override path in the codebase.

| Column | Type | Nullable | Constraints |
|---|---|---|---|
| `id` | `BIGINT` | No | Primary key, `IDENTITY`. |
| `workflow_id` | `VARCHAR(64)` | No | Logical reference to `workflow_instances.id`. |
| `policy_id` | `VARCHAR(32)` | No | The policy this exception applies to. |
| `reason` | `VARCHAR(1024)` | No | Why the exception is being requested/granted — never optional (constitution Principle VI: exceptions require recorded rationale). |
| `scope` | `VARCHAR(255)` | No | The bounded scope of the exception (e.g. `"implementation stage"`) — an exception is never workflow-wide by default. |
| `approving_authority` | `VARCHAR(128)` | No | Who granted it. Same self-asserted-identity caveat as `approvals.decided_by` (no auth enforced this iteration). |
| `compensating_control` | `VARCHAR(1024)` | **No** | **Corrected post-Task-10 (see closing note below) — changed from nullable to NOT NULL.** The offsetting control accepted in lieu of the failed mandatory policy. Constitution Principle VI requires every approved policy exception to record a compensating control; a nullable column allowed that requirement to be silently skipped at the schema level rather than enforced by it. |
| `approved_at` | `TIMESTAMP` | No | When the exception was granted. |
| `expiry` | `TIMESTAMP` | No | Mandatory expiry — an exception is never open-ended (constitution Section 4.2, "time-limited"). `hasApprovedUnexpiredException` checks `expiry.isAfter(now)` on every re-evaluation, so an expired exception silently stops covering the policy again rather than needing an explicit revocation step. |

---

## Cross-Table Invariants (informative, enforced in application code, not DB constraints)

- A `workflow_steps` row's `depends_on` set must reference only stage names that exist (or existed,
  if `INVALIDATED` by a replan) within the same `workflow_id` — enforced by `DependencyGraph.validate()`
  before `submit()`/`replan()` persists anything (`UnknownDependencyException`/`CycleException`).
- `approvals` and `policy_evaluations`/`policy_exceptions` rows always reference a `step_name`/
  `stage_name` that exists in `workflow_steps` for the same `workflow_id` — no orphaned gate or
  evaluation record. **Post-Task-10 addition:** `approvals.step_instance_id` must further reference
  the exact `workflow_steps.id` row current as of `approvals.plan_version` — an approval never binds
  to a step name in the abstract, only to the specific plan-versioned instance it was requested
  against.
- `workflow_events` is the union of every state-changing write to `workflow_instances`,
  `workflow_steps`, `approvals`, `policy_evaluations`, and `policy_exceptions` — every write to one
  of those five tables has a corresponding `workflow_events` row in the same logical operation
  (REQ-026 completeness). This is a code-review-enforced invariant in this iteration (see `plan.md`
  Section 10's audit-trail completeness test), not a database trigger.
- `short_links`/`redirect_events` and the six orchestration tables are never joined in a query —
  the domain and the orchestration engine are independently inspectable per constitution Principle
  VII.

---

## Post-Task-10 Corrective Note

The `workflow_steps.plan_version` column, the `approvals.plan_version`/`approvals.step_instance_id`
columns, and the change of `policy_exceptions.compensating_control` from nullable to `NOT NULL`
were **not** part of the original Task 6 draft of this document. They were added by the Task 10
corrective batch to close a real, independently-confirmed gap: both Task 10's own
`/speckit.analyze` + Independent Reviewer Gate (`docs/assessment/pre_implementation_analysis.md`)
and an external Codex review (`codex-recommend.md`, Findings #6 and #11) found that, without a
`plan_version` column, a `workflow_steps` row created before a replan and its replacement row
created after cannot be reliably distinguished, `(workflow_id, step_name)` alone is not a safe
uniqueness assumption once a replan can reintroduce a retired stage name, and an approval could not
be unambiguously scoped to the plan version and specific step instance it governed — directly
undercutting ADR-006's own stated rule that a rejection blocks a branch "for that plan version."
Similarly, a nullable `compensating_control` allowed constitution Principle VI's "every approved
exception records a compensating control" requirement to be satisfied only by convention, not by
the schema itself. None of these changes reverses an Accepted ADR or a `spec.md` requirement — they
make already-agreed decisions (ADR-006's plan-version-scoped rejection rule; Principle VI's
compensating-control requirement) representable in the schema that is supposed to implement them.
