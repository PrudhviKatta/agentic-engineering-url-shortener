# Research & Technology Decisions — Agentic URL Shortener

**Status: DRAFT**, produced at the `/speckit.plan` stage (Task 6) — this document's own status is
unchanged by the correction below; it remains a Task 6 deliverable pending whatever plan-approval
gate `plan.md` itself is pending. This document is the detailed decision record `plan.md` Section
12 summarizes in table form. It does not itself grant ADR status to anything.

**Corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): when this
document was authored at Task 6, only ADR-001 (language/framework) and ADR-002 (persistence) were
**Accepted**, and every other decision below (D3–D10) was phrased as a **proposal for Task 7's
formal ADR Generation Gate**, not an already-settled fact. Task 7 has since run: the human
candidate reviewed and accepted ADR-003 through ADR-010 individually, without amendment, on
2026-09-13 (`docs/governance/human-gates-log.md`, "Human Gate 4" rows). All ten ADRs (ADR-001
through ADR-010) are therefore now **Accepted**; the per-decision headings below (D3–D10) are
updated accordingly. This is a factual correction to this document's own now-stale forward-looking
language, not a re-opening of any decision — no option, rationale, or selected choice below has
changed.

**Provenance.** These are not this plan's own first-principles derivation. They are transcribed
and re-justified, at plan altitude, from the pre-decision table already worked out in the
controller's execution runbook (`docs/engineering-runbook.md`,
"Architecture Decisions" section) — a Java implementation-detail document that is explicitly
subordinate to this plan and to the ADRs Task 7 produced from it. Where the runbook's own
language already frames a decision as provisional ("finalized here; formalized as ADRs in Task
8" — note that document's Task-8 label maps to this project's Task 7), this document inherited
that same provisional framing at authoring time; Task 7's subsequent Accepted outcome for all
eight is recorded above rather than by silently rewriting this historical framing.

For each decision: question, options considered, assessment criteria, selected/proposed option,
rationale, consequences, risks, reversibility, and validation approach — per the source material's
Section 12 requirement.

---

## D1. Language and framework — **ADR-001, Accepted**

- **Decision question:** What language/runtime and web framework implement the API and
  orchestration layers?
- **Options:** (A) Python 3.11 + FastAPI + SQLAlchemy (the project's original, since-abandoned
  choice); (B) Java 21 (LTS) + Spring Boot 3.3 (Spring MVC, blocking); (C) Node.js/TypeScript +
  Express or NestJS.
- **Assessment criteria:** local-only runnability within a 2–3 day timebox; fit for a
  state-machine-heavy orchestration engine (compile-time contract checking, DI-testable layers);
  test-tooling maturity for unit/contract/persistence-slice tests; reviewer-familiarity for a
  financial-services-style assessment; cost of the decision given how much code already existed
  when it was made.
- **Selected:** (B) Java 21 (LTS) + Spring Boot 3.3.x, Spring Web MVC (not WebFlux), Maven.
- **Rationale:** explicit human directive, which under constitution Principle III is dispositive
  for a technology-selection decision; independently, Spring's test slices
  (`@DataJpaTest`/`@SpringBootTest`/`@WebMvcTest`) give the orchestration/policy/reliability layers
  precisely the layer-isolated testability constitution Principle IV's TDD requirement needs, and
  Java's static typing turns a class of `WorkflowContext` shape-mistakes into compile errors rather
  than runtime surprises. See `docs/adr/ADR-001-language-and-framework.md` for the full record.
- **Consequences:** more boilerplate per unit of behavior than the Python original; Spring
  autoconfiguration "magic" is a standing auditability risk, mitigated by preferring explicit
  constructor injection (already the pattern used throughout the runbook's `WorkflowEngine`
  design — never a Spring-managed singleton, ADR-004).
- **Risks:** a second stack pivot after real Java code exists would be as expensive as the
  original pivot; mitigated by treating ADR-001 as the last word absent a fresh, equally explicit
  human request.
- **Reversibility:** was low-cost at decision time (zero domain/orchestration code existed);
  high-cost now that this plan authorizes Tasks 11+ to build real Java code against it.
- **Validation:** `./mvnw -q clean verify` exits 0 with a passing Spring context-load test
  (already demonstrated, commit `d9e03a4`).

## D2. Persistence engine — **ADR-002, Accepted**

- **Decision question:** What datastore backs both the URL-shortener domain tables and the
  orchestration engine's tables?
- **Options:** (A) SQLite via a community JDBC/Hibernate dialect; (B) H2 embedded, file-mode; (C)
  Postgres via Testcontainers or a local install.
- **Assessment criteria:** zero external infrastructure; first-class Spring Data JPA/Hibernate
  support (specifically `@DataJpaTest` auto-configuration); transactional; supports JSON-shaped
  columns for `WorkflowContext`/audit payloads; reviewer-inspectable without extra tooling;
  doesn't foreclose a later Postgres migration.
- **Selected:** (B) H2, embedded, file-mode (`jdbc:h2:file:./data/app;AUTO_SERVER=TRUE`), accessed
  only through Spring Data JPA repositories.
- **Rationale:** H2 is Spring Boot's own ecosystem default for embedded/test use; SQLite's
  Hibernate dialect is community-maintained, not first-class; Postgres requires external
  infrastructure this 2–3 day local prototype's own scope explicitly avoids (consistent with
  ADR-005's identical reasoning for the orchestration engine itself). See
  `docs/adr/ADR-002-persistence-h2.md`.
- **Consequences:** zero-config `@DataJpaTest` support; file-mode survives restarts (REQ-028);
  H2/Postgres SQL-dialect drift is a documented, real limitation for any future production
  migration (recorded as backlog — Section 4 of `plan.md`).
- **Risks:** H2's file-mode locking behavior under the orchestration engine's parallel-batch
  execution (ADR-008) has different concurrency characteristics than a real client-server
  database; this plan's Phase 6 reliability tests are the validation point for that risk, not a
  pre-certification.
- **Reversibility:** moderate — all access goes through Spring Data repository interfaces, so a
  future Postgres swap is a dependency/connection-string change plus dialect-compatibility
  testing, not a persistence-access rewrite.
- **Validation:** full Spring context boot (including HikariCP + Hibernate `EntityManagerFactory`)
  exits 0 against `jdbc:h2:file:./data/app` (commit `d9e03a4`).

## D3. Short-code generation — **ADR-003, Accepted (2026-09-13)**

- **Decision question:** What algorithm produces the 7-character base62 short codes CLR-3/CLR-4
  confirm as the code's testable properties?
- **Options:** (A) Base62 encoding of an auto-increment integer primary key; (B) random
  generation with collision-retry; (C) hash-based (e.g., truncated SHA-256 of the long URL +
  salt).
- **Assessment criteria:** collision behavior under REQ-003 (must never surface a collision as a
  successful creation); implementation simplicity within the timebox; predictability/auditability
  for a reviewer; whether it needs a retry loop under concurrent load (REQ-008).
- **Selected:** (A) Base62 encoding of `short_links.id` (an `IDENTITY`-strategy auto-increment PK).
- **Rationale:** deterministic and collision-free *by construction* — uniqueness follows directly
  from the primary key's own uniqueness guarantee, with no retry loop, no collision-probability
  argument, and no extra index beyond the `code` column's own `UNIQUE` constraint needed to prove
  REQ-003. Simplest option that is still fully testable and auditable.
- **Consequences:** short codes are sequential/guessable in relative order (an attacker who knows
  one recently issued code can infer roughly how many links exist) — a genuine, disclosed
  information-leak trade-off, addressed in `plan.md` Section 8 (enumeration is not prevented by
  code unpredictability in this design; it would need to be prevented by a future
  rate-limiting/monitoring control instead, itself backlog per CLR-11).
- **Risks:** single-writer sequence is a scaling constraint at real production volume (not a
  concern at demonstration scale); documented as backlog, not silently assumed away.
- **Reversibility:** moderate — changing the algorithm later does not change the `code` column's
  shape (still 7-char base62) or any consumer contract, only the internal generation function.
- **Validation:** `ShortCodeGeneratorTest` (Task 16) plus `REQ-003`'s N-way concurrency test
  (Task 12/17 persistence + API contract tests).

## D4. Orchestration model — **ADR-004, Accepted (2026-09-13)**

- **Decision question:** What executes the Section 3.1 stage dependency graph — a custom
  in-process engine, or an existing workflow-orchestration framework/product?
- **Options:** (A) Custom in-process `WorkflowEngine` over an explicit `DependencyGraph` of
  `StageDefinition`s, state persisted via Spring Data JPA; (B) an embedded workflow library; (C)
  an external orchestration product (Temporal, Camunda, Activiti — see D5).
- **Assessment criteria:** must express explicit dependencies, sequential/parallel paths,
  synchronization, conditional branching, entry/exit gates, retry/rollback/compensation/safe-stop,
  resumption, and dynamic replanning (spec.md Section 3); must not add infrastructure
  disproportionate to a 2–3 day local prototype; must be fully unit-testable in isolation from the
  web layer; must be auditable by a reviewer reading Java source, not a vendor's opaque runtime.
- **Selected:** (A) Custom `WorkflowEngine` — a plain, constructor-injected Java class (not a
  Spring-managed singleton, so it is testable without booting the full Spring context) over a
  `DependencyGraph` of `StageDefinition` records. Each `StageDefinition` carries `name`,
  `dependsOn`, a `StageHandler` (`WorkflowContext -> Map<String,Object>`), independent
  `entryApproval`/`exitApproval` flags, `maxRetries`, `timeoutSeconds`, and an optional
  `parallelGroup` tag. `DependencyGraph.topologicalBatches()` resolves the graph into ordered
  lists of same-batch (parallel-eligible) stages; `validate()` rejects cycles
  (`CycleException`) and dangling dependencies (`UnknownDependencyException`) before any
  persistence write happens. State (`WorkflowInstanceEntity`/`WorkflowStepEntity`/
  `WorkflowEventEntity`) is persisted via Spring Data JPA after every batch, not only at
  terminal states, so `GET /workflows/{id}` reflects real, currently-durable state.
- **Rationale:** satisfies every explicit orchestration characteristic spec.md Section 3 names
  without adopting distributed-systems infrastructure the timebox and local-runnability
  constraints cannot support; every transition is Java source a reviewer can read end-to-end,
  which is itself evidence toward constitution Principle IX (audit-grade evidence) and Principle
  X (traceability) — there is no vendor black box between "what the code says happens" and "what
  actually happened."
- **`entryApproval` vs. `exitApproval` (the key design distinction, not a naming detail):**
  `entryApproval=true` blocks the stage's *handler* from running at all until a human approves —
  correct for a destructive/irreversible action that must not even be attempted without sign-off.
  `exitApproval=true` lets the handler run and produce output first, then blocks only that stage's
  *dependents* until the produced output (a design, a detected ambiguity, a release-readiness
  report) is reviewed — correct whenever there is something concrete to review, not merely a
  proposal to approve. A stage may set neither, either, or (rarely) both. This distinction is the
  concrete mechanism DER-5 (spec.md Section 9.2) flags as needing to exist without spec.md itself
  choosing it.
- **Consequences:** the engine's own correctness (cycle detection, topological batching, gate
  semantics, replan reachability) becomes this project's own thing to get right and test — there
  is no vendor test suite to lean on. Fully accepted, since Phase 4's test suite (`plan.md`
  Section 10) exists specifically to carry that burden.
- **Risks:** hand-rolled engines are a common source of subtle state-machine bugs (e.g., a replan
  racing an in-flight approval). Mitigated by the fixed ordering rule "replan before approve" (see
  D4's replan note below) and by dedicated tests for exactly that race (Task 35/41).
- **Reversibility:** low — this is the architectural core of the differentiator constitution
  Principle II names; replacing it later would be a substantial rewrite, not a swap. This is why
  it was flagged for explicit ADR review at Task 7 (since run — see ADR-004, Accepted 2026-09-13)
  rather than left as an unexamined default.
- **Validation:** `DependencyGraphTest`, `WorkflowEngineExecutionTest`,
  `WorkflowEngineApprovalTest`, `WorkflowEngineContextLineageTest`,
  `WorkflowEngineReliabilityIntegrationTest`, `WorkflowEngineSafeStopTest`,
  `WorkflowEngineReplanningTest` (Phase 4/6/7 test suite — see `plan.md` Section 10).

## D5. Why not an external orchestration product (Temporal/Camunda/Activiti) — **ADR-005, Accepted (2026-09-13)**

- **Decision question:** Should the orchestration engine be built on an existing
  workflow-orchestration product instead of a custom engine (D4)?
- **Options:** Temporal, Camunda, Activiti, or equivalent.
- **Assessment criteria:** operational footprint vs. a local 2–3 day prototype; whether the
  product's own runtime would itself need to be independently audited to satisfy constitution
  Principle IX, on top of this project's own code.
- **Selected:** Rejected — infra/ops burden (a separate server process, its own persistence,
  its own operational learning curve) is disproportionate to what a local, reviewer-run prototype
  needs, and a custom engine is demonstrably auditable end-to-end in H2 and in Java source, which
  a third-party runtime's internal state model would not be to the same degree.
- **Consequences / risk:** forgoes production-grade durability guarantees (e.g., Temporal's
  crash-consistent replay) that a real production deployment of this system would likely want;
  explicitly named as a production-scale gap, not a prototype defect (`plan.md`'s
  "production-grade discipline vs. production-scale infrastructure" distinction, Section 1).
- **Reversibility:** low if ever revisited post-prototype — would be a full orchestration-layer
  rewrite, not a configuration change. Documented, not hidden.
- **Validation:** N/A (a rejection, not a build) — validated by the same Phase 4 test suite proving
  D4's custom engine satisfies every required characteristic without the extra infrastructure.

## D6. Human approval mechanism — **ADR-006, Accepted (2026-09-13)**

- **Decision question:** How does a human actually record an approval/rejection decision against a
  pending gate?
- **Options:** (A) a synchronous REST endpoint (`POST /workflows/{id}/approvals/{step}`) a human
  or reviewer calls directly; (B) a message-queue-based async approval channel; (C) a built
  reviewer UI/dashboard.
- **Assessment criteria:** no UI is in scope for this assessment (spec.md Section 9.6 exclusion);
  the decision must be an explicit, auditable, non-inferred act (REQ-013).
- **Selected:** (A) — the workflow blocks in `PENDING_APPROVAL`/`WAITING_APPROVAL` state until this
  endpoint is called with an explicit `approved`/`rejected` decision, actor, and reason.
- **Rationale:** simplest mechanism that satisfies REQ-013's explicit/non-silent requirement
  without building UI the assignment explicitly excludes from scope.
- **Consequences:** a real deployment would need real authentication on this endpoint specifically
  (it is the single most security-sensitive endpoint in the system, since it is the only path that
  releases a blocked branch) — explicitly named in `plan.md` Section 8 as the top
  authentication-backlog priority if ADR-010 (auth) is ever revisited.
- **Risks:** without authentication, `actor`/`decidedBy` is self-asserted, not verified — disclosed
  in `data-model.md`'s `approvals`/`policy_exceptions` table notes.
- **Reversibility:** high — swapping the transport (REST → queue → UI) does not change
  `WorkflowEngine.approve()`'s contract.
- **Validation:** `WorkflowEngineApprovalTest`, `WorkflowControllerContractTest`.

## D7. Policy engine format — **ADR-007, Accepted (2026-09-13)**

- **Decision question:** How are versioned policy guardrails (constitution Principle VI) expressed
  and evaluated?
- **Options:** (A) versioned YAML files parsed at evaluation time; (B) policy rows in the
  database, editable at runtime; (C) a rules-engine library (e.g., Drools).
- **Assessment criteria:** human-readable and diffable (so a policy change is itself a reviewable
  commit, not a hidden runtime mutation); versioned without a database migration per change;
  avoids a heavyweight rules-engine dependency disproportionate to ~4 policy checks at prototype
  scale.
- **Selected:** (A) — `src/main/resources/policies/v{N}.yaml`, parsed via Jackson's `YAMLMapper`.
  Each policy declares `id`, `domain`, `stage`, `mandatory`, `description`, and `check_key` (the
  `WorkflowContext` artifact key whose truthiness the check reads). The evaluated file's
  `version` string is recorded on every `policy_evaluations` row (`data-model.md` Section 7), so a
  later policy-file edit never silently reinterprets a historical evaluation (REQ-030).
- **Rationale:** one JSON/YAML tooling family (Jackson, already a dependency via
  `spring-boot-starter-web`) rather than introducing a second YAML library; git-diffable policy
  history is itself part of this project's change-control story (`plan.md` Section 9).
- **Consequences:** policy changes require a code deployment (a new YAML file on the classpath),
  not a live database edit — an intentional constraint, not a limitation, since it keeps every
  policy change in the same review/commit process as code.
- **Risks:** a hand-rolled `check_key`-truthiness model is less expressive than a real rules
  engine (no compound conditions across multiple artifact keys in this iteration) — acceptable at
  the ~4-policy prototype scale; flagged as a scaling limitation if the policy set ever grows
  materially.
- **Reversibility:** moderate — `PolicyEngine.evaluate()`'s external contract
  (`List<PolicyCheckResult>` from a stage name + context map) would not need to change if the
  backing format were swapped later.
- **Validation:** `PolicyEngineTest`, `PolicyExceptionTest`, `WorkflowEnginePolicyEnforcementTest`.

## D8. Concurrency model for parallel stage fan-out — **ADR-008, Accepted (2026-09-13)**

- **Decision question:** How are parallel-eligible stages (e.g., Testing/Documentation/Security
  fanning out from Implementation, Section 3.1) actually executed concurrently?
- **Options:** (A) `java.util.concurrent.ExecutorService` (fixed thread pool) + `Future.get()` per
  stage in a batch; (B) reactive/WebFlux end-to-end; (C) virtual threads (Java 21 preview feature
  at the time of writing).
- **Assessment criteria:** stage handlers in this prototype are short-lived and I/O-light (no real
  external calls); must prove genuine parallel fan-out and synchronization without forcing a
  reactive rewrite of the whole codebase (which ADR-001 already rejected for the web layer).
- **Selected:** (A) — `Executors.newFixedThreadPool(...)`, one `Future` per stage within a
  `DependencyGraph.topologicalBatches()` batch, `Future.get()` to join the batch before moving to
  the next.
- **Rationale:** minimal additional complexity; proves real fan-out/synchronization (spec.md
  Section 3's explicit requirement) without adopting a reactive programming model project-wide for
  a demonstration-scale prototype.
- **Consequences:** thread-pool sizing is a fixed constant, not adaptive — acceptable at
  demonstration concurrency levels (REQ-023's own scope framing).
- **Risks:** H2 file-mode locking (D2) under concurrent writes from parallel stages is the
  specific interaction Phase 6's reliability tests target; a finding there is a reliability-test
  finding, not a re-litigation of D2 or D8 individually.
- **Reversibility:** high — an internal implementation detail behind `WorkflowEngine.runFrom`; not
  part of any external contract.
- **Validation:** the runbook's Task 22 parallel-execution test plus Phase 6's concurrency-focused
  reliability tests.

## D9. Rollback vs. compensation mechanism — **ADR-009, Accepted (2026-09-13)**

- **Decision question:** How does the engine implement the rollback/compensation distinction
  constitution Principle VIII and REQ-015/CLR-14 require?
- **Options:** (A) treat all stage effects as rollback-eligible (incorrect — conflates the two per
  REQ-015's negative criterion); (B) distinguish purely in-memory/DB effects (rollback-eligible,
  via JPA transaction abort) from externally-visible effects (compensation-eligible, via a
  registered compensating action); (C) always compensate, never roll back (loses the cheaper
  rollback path for genuinely reversible effects).
- **Selected:** (B) — a `CompensationRegistry` maps a stage name to a registered
  `Consumer<Map<String,Object>>` compensating action. A stage whose effect never left the
  transaction boundary rolls back for free (the JPA transaction simply isn't committed); a stage
  whose effect was already externally observed (the canonical example: a redirect already served
  to a caller, CLR-14) runs its registered compensation instead — logging an incident/audit note
  or an equivalent corrective action, never attempting to "undo" the already-observed effect.
- **Rationale:** directly implements CLR-14's confirmed rule and REQ-015's rollback/compensation
  distinction without conflating them into one mechanism.
- **Consequences:** requires each stage handler that produces an externally-visible effect to
  explicitly register its compensating action — an opt-in design, so a stage that forgets to
  register one and later needs compensation instead falls through to safe-stop (Task 33/34) rather
  than silently doing nothing. This fail-safe (safe-stop, not silent no-op) is itself the
  intended behavior, not a gap.
- **Risks:** relies on each stage author correctly classifying their own effect as
  rollback-eligible vs. compensation-eligible; a misclassification would only surface at test time
  (Phase 6), which is why Task 32/33's tests explicitly cover one of each case.
- **Reversibility:** high — `CompensationRegistry`'s contract (`register`/`run` keyed by stage
  name) would not need to change under a different registration mechanism later.
- **Validation:** `CompensationRegistryTest`, `WorkflowEngineReliabilityIntegrationTest`,
  `WorkflowEngineSafeStopTest`.

## D10. Authentication — **ADR-010, Accepted (2026-09-13)**

- **Decision question:** Is any authentication/authorization mechanism implemented in this
  iteration?
- **Options:** (A) none, documented as a trusted-caller-only assumption; (B) a minimal API-key
  header check; (C) full OAuth2/OIDC integration.
- **Selected:** (A) — none enforced, per CLR-10's already-confirmed specification-level decision
  (this is the one item in this table where the *specification* itself, not just this plan, has
  already resolved the underlying question; the ADR formalizes the technology-level consequence of
  that resolution, primarily that every endpoint — including the approval-decision endpoint,
  D6 — is unauthenticated).
- **Rationale:** matches the confirmed specification decision; building real auth was explicitly
  out of scope for this assessment's demonstrated capabilities (spec.md Section 9.6).
- **Consequences:** every endpoint in `contracts/openapi.yaml` is trusted-caller-only; `actor`/
  `decidedBy`/`approvingAuthority` fields throughout `data-model.md` are self-asserted strings, not
  verified identities — the single most material security caveat in this plan (`plan.md` Section
  8 discusses it directly, not as a footnote).
- **Risks:** in a real deployment, the unauthenticated approval endpoint (D6) would be the highest
  -severity finding in this design; explicitly flagged, not discovered later by a reviewer.
- **Reversibility:** moderate — adding auth later is additive (a filter/interceptor layer) rather
  than a rewrite of business logic, since no code path currently depends on caller identity for
  correctness (only for the self-asserted audit fields).
- **Validation:** N/A for this iteration (nothing to test); the backlog item is "when
  authentication is added, add an auth-bypass-attempt test," not testable before it exists.

---

## Backlog (explicitly deferred, not silently omitted)

Per constitution Principle VII/Section 4 scope-control and CLR-2/CLR-4/CLR-11/CLR-15/CLR-17:

- Real authentication/authorization (ADR-010 residual — see D10).
- Per-caller/per-IP rate limiting (CLR-11: proposed, unapproved figure on record — 100 req/min/IP).
- Request-level idempotency-key-based deduplication (REQ-007, superseded by CLR-2 for this
  iteration).
- Custom short-code aliases (CLR-4).
- Link mutation (update/PATCH) capability (CLR-15 — Links are immutable this iteration).
- Flyway/Liquibase-managed schema migrations (currently `ddl-auto: update`, a disclosed
  prototype-only convenience — ADR-002 Consequences).
- Multi-region/distributed short-code generation (D3's single-writer-sequence scaling caveat).
- A real, compliance-derived audit/analytics retention policy (CLR-17's 90-day figure is an
  approved *placeholder*, not a production figure).
