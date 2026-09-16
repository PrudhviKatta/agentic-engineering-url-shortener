# Architecture Quality Checklist

**Category 2 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `specs/001-agentic-url-shortener/plan.md` Sections 1–2, ADR-001/002/003/008, and the
eventual module/package structure of the Spring Boot codebase.
**Constitution principles:** VII (Architecture and Maintainability), II (Explicit Agentic
Orchestration, for the engine's own architecture), X (Traceability).
**Status:** Final review: **PASS WITH LIMITATION** (no optional machine-enforced dependency rule).
See `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed if failed.
- Every item states a pass/fail condition and a citation.

## Items

- **[MANDATORY] ARCH-CHK-001** — Verify domain logic, API delivery, persistence, orchestration,
  policy enforcement, telemetry, and infrastructure concerns live in separate packages/modules
  (`plan.md` Section 1.5's "major runtime components"), such that a change to persistence (e.g.
  swapping H2 for another engine per ADR-002's "not blocking a later swap" driver) does not require
  editing validation logic (`UrlValidator`). **Fail if**: a persistence-layer class and a
  validation-layer class are found in the same compilation unit, or a repository interface leaks
  into the domain-validation package. **Cites**: Principle VII, REQ-024, ADR-002.

- **[MANDATORY] ARCH-CHK-002** — Verify external dependencies (database, clock, entropy/randomness
  source for short-code generation) are accessed through explicit interfaces the domain layer
  defines (not concrete framework classes imported directly into domain code), so they are
  substitutable/fakeable in tests without contorting domain code. **Fail if**: a domain class (e.g.
  short-code generation logic) directly instantiates a JDBC/JPA class or calls
  `System.currentTimeMillis()`/`new Random()` inline rather than through an injected abstraction.
  **Cites**: Principle VII (second bullet), ADR-003.

- **[MANDATORY] ARCH-CHK-003** — Verify every material architectural decision recorded as an ADR
  (ADR-001..010) documents at least one rejected alternative with stated advantages/disadvantages —
  not just the chosen option. **Fail if**: any Accepted ADR's "Options Considered" section lists
  only the selected option. **Cites**: Principle VII (third bullet); cross-reference `adr.md`
  CHK-ADR-003 for the ADR-specific version of this check.

- **[MANDATORY] ARCH-CHK-004** — Verify complexity introduced in the implementation is traceable to
  a specific requirement or a named, disclosed engineering-timebox need — not "anticipated future
  flexibility" nothing in `spec.md` asks for. **Fail if**: a generalization (e.g. a plugin
  architecture for short-code algorithms, a multi-database abstraction layer) exists with no REQ or
  ADR citation justifying it. **Cites**: Principle VII (fourth bullet).

- **[MANDATORY] ARCH-CHK-005** — Verify the orchestration engine (`WorkflowEngine`,
  `DependencyGraph`, ADR-004) and the URL-shortener domain (`ShortLink`, `UrlValidator`,
  `ShortCodeGenerator`) use separate repository sets with no cross-schema joins, per `plan.md`
  Section 1.6's least-privilege/data-ownership design. **Fail if**: a repository or query in one
  domain directly joins against the other domain's tables. **Cites**: Principle VII, Principle V
  (least privilege), plan.md Section 1.6/8.

- **[MANDATORY] ARCH-CHK-006** — Verify `WorkflowEngine` is a plain, constructor-injected class (not
  a Spring-managed singleton depending on the full application context) so it is unit-testable
  without booting Spring, per ADR-004's stated design driver. **Fail if**: `WorkflowEngine`'s test
  suite requires `@SpringBootTest`/full context load to exercise core scheduling logic. **Cites**:
  ADR-004, Principle VII (fifth bullet: independently testable).

- **[MANDATORY] ARCH-CHK-007** — Verify a code component is independently replaceable — e.g., the
  persistence engine (ADR-002) could be swapped without requiring a rewrite of `WorkflowEngine` or
  domain validation, per ADR-002's explicit "must not block a later real-infrastructure swap"
  driver. **Fail if**: swapping H2 for another JDBC-compatible engine would require changes outside
  the repository/configuration layer. **Cites**: ADR-002, Principle VII.

- **[MANDATORY] ARCH-CHK-008 [ANTI-PATTERN: undocumented architecture drift]** — Verify the
  as-built package/module structure matches what `plan.md` Section 2.2 ("Component plan") describes,
  or that any divergence is itself recorded as a plan.md update or a new/amended ADR before being
  treated as the current design. **Fail if**: a reviewer finds a component in the codebase with no
  corresponding entry in `plan.md`/an ADR, or `plan.md` describes a component that does not exist
  and no note explains why. **Cites**: Principle X (documentation-currency rule), Principle VI
  (change control); cross-reference `documentation.md` CHK-DOC-005.

- **[MANDATORY] ARCH-CHK-009** — Verify the language/framework/persistence stack actually built
  matches ADR-001 (Java 21 + Spring Boot 3.3.x, Spring Web MVC) and ADR-002 (H2 embedded, file-mode)
  exactly, with any deviation recorded as an ADR amendment, not a silent substitution. **Fail if**:
  `pom.xml`/`application.yml` specify a different language version, framework, or database than the
  Accepted ADR text, with no superseding ADR. **Cites**: ADR-001, ADR-002, Principle VI.

- **[OPTIONAL] ARCH-CHK-010** — Verify a structural/dependency-direction check (e.g. a build-time
  architecture test such as ArchUnit, or an equivalent manual review checklist) is defined at
  implementation time to make REQ-024 (maintainability) machine-verifiable rather than only
  narratively true. **Fail if**: no such check exists and REQ-024 relies solely on manual code
  review with no repeatable verification artifact. **Cites**: REQ-024's proposed validation target,
  Principle VII.

- **[MANDATORY] ARCH-CHK-011** — Verify the system boundary and trust boundaries (`plan.md` Sections
  1.1/1.4) match what is actually deployed/exposed — e.g., that no component crosses a documented
  trust boundary (application-plane vs. control-plane, Section 1.7) without an explicit, reviewed
  reason. **Fail if**: the orchestration control-plane API and the URL-shortener application-plane
  API share an unguarded code path that the plan's trust-boundary section does not describe. **Cites**:
  plan.md Section 1.4/1.7, Principle V.

- **[OPTIONAL] ARCH-CHK-012** — Verify a component-level diagram or equivalent textual model (major
  runtime components, Section 1.5) is kept current enough that a new reviewer can locate any given
  requirement's owning component without reading all source files. **Fail if**: Section 1.5's
  component list omits a component that exists in the codebase. **Cites**: Principle X, REQ-024.
