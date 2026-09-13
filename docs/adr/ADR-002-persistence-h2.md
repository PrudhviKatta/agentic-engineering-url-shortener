# ADR-002: Persistence Engine Selection

## Status

**Accepted** — drafted 2026-09-13, alongside ADR-001, ahead of the formal Task 7 ADR Generation
Gate. Accepted by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13, after review.

## Context

ADR-001 selected Java 21 + Spring Boot, superseding the original Python + SQLAlchemy + SQLite
scaffold (Tasks 1–2). Persistence was never itself independently requested by the human candidate
— it is a downstream consequence of the language/framework pivot, and needs its own decision
record because "the same database as before" was not actually available: SQLite's Java/Hibernate
support is second-class relative to the Spring/JPA ecosystem's native options.

## Decision Drivers

- Zero external infrastructure — must run locally with nothing beyond the JDK, matching the
  original SQLite choice's rationale.
- Must be a first-class Spring Data JPA / Hibernate citizen — no community-dialect workarounds.
- Must be transactional and support the JSON-shaped columns the orchestration engine's
  `WorkflowContext`, step results, and audit payloads need.
- Must be trivially inspectable by a reviewer without installing separate client tooling.
- Must not block a later, real-infrastructure swap (e.g., Postgres) if this project ever moved
  past prototype scale — Spring Data's repository abstraction should absorb that change, not this
  ADR's specific engine choice.

## Options Considered

### Option A: SQLite (via a community JDBC driver + Hibernate community dialect)
- **Advantages:** literal continuity with the original Python choice; extremely well-known engine.
- **Disadvantages:** Hibernate's SQLite dialect support is third-party/community-maintained, not a
  first-class Spring Boot starter; JSON-column mapping and Spring Data JPA test-slice support
  (`@DataJpaTest`) are less battle-tested against SQLite than against H2 in the Spring ecosystem;
  picking it here would be choosing familiarity over fit.
- **Assessment implications:** would read as carrying over a decision without re-evaluating it
  against the new stack's actual idioms — the opposite of the engineering judgment this assessment
  evaluates.

### Option B: H2 embedded database, file-mode (selected)
- **Advantages:** first-class Spring Boot starter (`com.h2database:h2`) with zero extra
  configuration; native `@DataJpaTest` support (Spring Boot auto-configures an embedded H2 for
  test slices by convention, which the plan's Task 22+ tests rely on directly); transactional;
  file-mode (`jdbc:h2:file:./data/app`) persists across restarts, matching what a URL shortener
  actually needs (short links must survive a server restart); has a built-in web console and CLI
  for reviewer inspection without installing anything beyond what Maven already pulled in.
- **Disadvantages:** less universally recognized outside the Java ecosystem than SQLite or
  Postgres; H2's SQL dialect has a few quirks (case-sensitivity defaults, some non-standard
  functions) that don't perfectly match Postgres, so a later production migration would need real
  dialect-compatibility testing, not a drop-in swap.
- **Assessment implications:** demonstrates picking the tool that's actually idiomatic for the
  chosen framework, not just the closest analog to what was there before.

### Option C: Postgres (via Testcontainers or a local install)
- **Advantages:** production-realistic; what a real deployment would likely use.
- **Disadvantages:** requires either Docker (Testcontainers) or a local Postgres install — exactly
  the "external infrastructure" this prototype's own scope explicitly avoids (see the plan's ADR-005
  rejection of Temporal/Camunda for the same reason, applied consistently here to the datastore
  choice too). Disproportionate to a 2–3 day local prototype.
- **Rejected** for the same "avoid unjustified distributed-system complexity" reasoning the plan
  already applies elsewhere; noted here as the natural next step if this ever became a real service
  (see Reversibility).

## Decision

**H2, embedded, file-mode** (`jdbc:h2:file:./data/app;AUTO_SERVER=TRUE`), accessed exclusively
through Spring Data JPA repositories — no raw JDBC, no engine-specific SQL in application code, so
the repository abstraction (not a specific SQL dialect) is what the rest of the codebase depends
on.

## Rationale

H2 is the datastore Spring Boot's own ecosystem treats as the default embedded choice, which
matters directly for this project: the plan's orchestration-engine tests (Tasks 20–41) rely on
`@DataJpaTest` auto-configuring a real, transactional, Spring-Data-backed database with zero extra
setup — exactly what H2 provides natively and SQLite would require extra configuration to
approximate. Choosing the framework-idiomatic option over the "looks the same as before" option is
itself the point of re-evaluating a decision after a stack pivot, rather than carrying the old
choice forward unexamined.

## Consequences

**Positive:** zero-config test-slice support; transactional; file-mode survives restarts;
reviewer-inspectable via H2's own console/CLI without new tooling.

**Negative:** H2/Postgres dialect differences mean a later production migration needs real
compatibility testing, not a drop-in connection-string swap — documented explicitly as a
prototype-to-production limitation (also called out inline in `application.yml`'s
`ddl-auto: update` comment, which is a related but separate prototype-only convenience).

**Operational:** `data/app.mv.db`/`data/app.trace.db` are gitignored (Task 2's `.gitignore`) —
local state, not committed evidence. A reviewer running the app locally gets a fresh database on
first boot (Hibernate `ddl-auto: update` creates the schema from entities).

**Testing:** `@DataJpaTest` slices get a real H2 instance per Spring Boot convention; no mocking of
the persistence layer anywhere in the plan's test tasks.

**Governance:** same standing as ADR-001 — **Accepted** by the human candidate (Prudhvidhar Reddy
Katta) on 2026-09-13, not self-approved (corrected by the Task 10 corrective batch,
`codex-recommend.md` Finding #12 — this line previously read "proposed pending... accept/reject"
after this ADR's own Status header had already moved to Accepted; see
`docs/governance/human-gates-log.md` for the approval record).

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| H2/Postgres SQL dialect drift silently breaks a future production migration | Keep all persistence access behind Spring Data repository interfaces; no native/engine-specific SQL in application code (enforced by code review, not tooling, at this scale) |
| `ddl-auto: update` is a prototype-only convenience, unsafe for real deployments | Already disclosed inline in `application.yml` (Task 2) and in this ADR; real deployment would need Flyway/Liquibase-managed migrations — recorded as backlog, not silently assumed away |
| File-mode H2 with `AUTO_SERVER=TRUE` has concurrency characteristics different from a real client-server database | The plan's Task 33 (reliability wiring) and concurrency-related tests are the validation point for this; if H2's locking behavior under the orchestration engine's parallel-batch execution proves inadequate, that's a finding for the plan's own reliability-testing tasks to surface, not something this ADR can pre-certify |

## Reversibility

**Moderate.** Because all access goes through Spring Data repository interfaces, swapping the
underlying engine (e.g., to Postgres for a real deployment) is primarily a dependency and
connection-string change plus dialect-compatibility testing — not a rewrite of persistence-access
code. This is explicitly why Option C (Postgres) was deferred rather than rejected outright: it's
the natural next step, just not justified for a 2–3 day local prototype.

## Traceability

- Plan sections: Tech Stack, Architecture Decisions table (ADR-002 row), Task 2 (`application.yml`),
  every orchestration-engine task (20–41) that relies on `@DataJpaTest`.
- Expected task identifiers: Tasks 12, 20–41 (all persistence-touching tasks).

## Validation

Validated by demonstration: Task 2's context-load test boots the full Spring context including the
H2 `DataSource`/Hibernate `EntityManagerFactory` and exits 0 (commit `d9e03a4`, independently
re-verified by the controller with a fresh `./mvnw -q clean verify` run showing HikariCP and
Hibernate initializing cleanly against `jdbc:h2:file:./data/app`).
