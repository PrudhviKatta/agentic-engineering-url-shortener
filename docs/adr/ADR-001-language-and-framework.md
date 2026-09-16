# ADR-001: Language and Framework Selection

## Status

**Accepted** — drafted 2026-09-13, ahead of the formal Task 7 ADR Generation Gate, at the human
candidate's explicit direction (see Context). Accepted by the human candidate on 2026-09-13, after review, per the project's own guardrail that only the human candidate
may mark an ADR Accepted (guide Section 29, "Do Not Allow Claude to Approve Its Own Decisions").

## Context

The project began (Tasks 1–2) on Python 3.11 + FastAPI + SQLAlchemy + SQLite, a stack the
controlling agent selected unilaterally when first authoring the implementation plan, before any
ADR had been proposed or accepted — itself a process gap disclosed here rather than concealed
(see "Process note" below). Task 3 (constitution) was drafted and in fix-review when the human
candidate explicitly requested a switch to Java + Spring Boot. The controller re-translated the
full 48-task implementation plan to Java/Spring Boot and re-executed Task 2 as a Maven/Spring Boot
skeleton, removing the Python scaffold. This ADR formalizes that decision after the fact, as an
external review of the project's governance correctly pointed out was missing: a stack change of
this magnitude needs a recorded decision, reason, and impact analysis, not just an updated plan
file.

**Process note (disclosed, not defended):** the *original* Python selection was never itself
formalized as an ADR before Task 2 built on it — the controller pre-decided it "for scheduling
purposes" in the plan document, a pattern the plan's own Architecture Decisions table explicitly
flagged as provisional pending Task 7. This ADR-001 slot is now filled by the corrected (Java)
decision directly; there is no separate superseded "ADR-001: Python" document, because no such
document was ever created — only the plan's informal pre-decision table, which this ADR now
formally replaces for the language/framework choice specifically.

## Decision Drivers

- Must run locally, without external infrastructure, within a 2–3 day assessment timebox.
- Must support the orchestration engine's requirements: explicit state machine, persistence,
  concurrent (parallel-batch) execution, dependency injection for testability of engine/policy/
  reliability layers in isolation from the web layer.
- Must have mature, low-ceremony testing tooling (unit, contract/API, persistence-slice tests).
- Reviewer-familiarity and enterprise-realism matter for this specific assessment context (a
  Java/Spring Boot submission is a defensible, arguably more "enterprise-realistic" choice for a
  financial-services-style engineering assessment than a Python microframework).
- Explicit human preference, stated directly, after the Python version was already partially built
  — this is not a technical requirement, but it is dispositive: the guide is explicit that
  technology selection is a human-owned decision, and the human candidate exercised that ownership.

## Options Considered

### Option A: Python 3.11 + FastAPI + SQLAlchemy (original choice)
- **Advantages:** minimal boilerplate, fast to iterate, native async, huge test-tooling ecosystem
  (pytest/httpx), well-suited to a 2–3 day timebox.
- **Disadvantages:** less common in enterprise/financial-services Java shops; weaker static typing
  than Java generics + compiler-checked exhaustiveness for a state-machine-heavy orchestration
  engine; SQLite's Java-world equivalents are second-class, but that's not a Python concern —
  within Python, SQLite is idiomatic and well-supported.
- **Assessment implications:** demonstrates breadth (already-built and reviewed for Tasks 1–2), but
  was superseded before any domain/orchestration code existed, so the cost of abandoning it was
  limited to scaffolding only.

### Option B: Java 21 (LTS) + Spring Boot 3.3 (selected)
- **Advantages:** strong static typing and compiler-enforced contracts fit a state-machine-heavy
  orchestration engine well; Spring's test slices (`@DataJpaTest`, `@SpringBootTest`,
  `@WebMvcTest`) give precisely the kind of layer-isolated testability the orchestration engine,
  policy engine, and reliability primitives need; Spring Data JPA + an embedded database is a
  well-trodden, reviewer-legible pattern; LTS release gives a stable, well-documented target.
- **Disadvantages:** more boilerplate/ceremony per unit of behavior than FastAPI; Spring Boot's
  autoconfiguration "magic" can obscure what's actually wired, which cuts against this project's
  own transparency/auditability principle if over-relied on (mitigated by preferring explicit
  constructor injection over field injection, and explicit `@Bean`/`@Configuration` over
  autoconfigured defaults where the choice matters for auditability — see Consequences).
- **Assessment implications:** matches the human candidate's stated preference; enterprise-familiar
  stack for a financial-services-style assessment.

### Option C: Node.js/TypeScript + Express or NestJS
- **Advantages:** also enterprise-common, strong typing available via TypeScript, fast iteration.
- **Disadvantages:** not requested by the human candidate; NestJS's decorator-heavy DI model has
  similar "magic" concerns to Spring without Java's compile-time guarantees; would require yet
  another full stack re-translation with no driving requirement to justify it.
- **Rejected without deep evaluation** — no one asked for it, and Options A/B already bracket the
  real decision space (Python already built, human requested Java).

## Decision

**Java 21 (LTS) + Spring Boot 3.3.x**, using Spring Web MVC (blocking/servlet style, not
WebFlux/reactive) for parity with the original synchronous FastAPI design, and Maven (with Maven
Wrapper) as the build tool.

## Rationale

The human candidate — who under this project's own constitution (Principle III) owns technology
selection — explicitly requested this stack. Independent of that instruction, Java's static typing
and Spring's test-slice architecture are a genuinely good fit for an orchestration engine whose
correctness hinges on state-machine transitions and persisted, resumable workflow state: the kind
of defect a Python `dict`-shaped `WorkflowContext` might let slip past review (a typo'd key, an
`Optional` no one checked) is a compile error in the Java translation instead. The switch happened
before any domain or orchestration code existed — the blast radius was one build-tooling commit,
not a rewrite of tested business logic.

## Consequences

**Positive:**
- Compile-time contract checking across the orchestration engine's stage graph, context, and
  persistence entities.
- Spring's test slices give precise, fast, isolated tests for the policy/reliability/orchestration
  layers without booting the full web stack for every test.
- Enterprise-familiar stack for this assessment's context.

**Negative:**
- More verbose than the Python equivalent for the same behavior (visible directly in the plan's
  translated Task 11–41 code relative to its Python originals).
- Spring Boot autoconfiguration can obscure wiring; mitigated by preferring explicit configuration
  where auditability matters (see Operational consequences).

**Operational:**
- Requires JDK 21 at runtime/build time (already installed and verified on this machine at
  `/opt/homebrew/opt/openjdk@21`); every Maven invocation in this project must set `JAVA_HOME`
  explicitly, since the machine's default `mvn` resolves a newer non-LTS JDK — documented in the
  plan's Task 2 and binding on every later task.

**Testing:**
- JUnit 5 + Mockito + AssertJ + Spring Boot Test, per the plan's Tech Stack section. No new testing
  philosophy introduced by this ADR beyond what the plan already specifies.

**Governance:**
- This ADR is filed ahead of the formal Task 7 ADR Generation Gate, at the human candidate's
  direct request for the pivot itself. It still requires the same explicit accept/reject the other
  nine ADRs will receive at Task 7 — being "first" does not exempt it from governance.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Spring's implicit wiring obscures what's actually connected, hurting auditability | Prefer constructor injection over field injection; keep `WorkflowEngine` a plain, framework-agnostic class constructed explicitly in tests (per the plan's Task 22–24 design), not a component discovered only via Spring's classpath scanning |
| Team/reviewer unfamiliarity with Spring Boot conventions slows review | Plan document gives complete, real code for every task — no "standard Spring idiom, left as an exercise" gaps |
| A second stack pivot mid-flight would be far more expensive now that Java code exists | This ADR, once accepted, is the last word on language/framework absent a formal amendment through `/speckit.clarify` → plan → ADR again — not a unilateral re-pivot |

## Reversibility

**Low-cost now, high-cost later.** As of this ADR (2026-09-13), zero domain or orchestration code
exists — only a project skeleton and a context-load test. Reversing this decision today costs one
build-tooling commit, exactly as reversing the original Python choice did. Once Tasks 11+ land
real Java code, reversing this decision would cost the same order of effort as the original
pivot (a full re-translation), and should not be undertaken without an equally explicit human
request and a fresh impact analysis.

## Traceability

- Requirements: none directly (technology selection is explicitly out of scope for `spec.md` per
  Section 8's deferred-decisions framing).
- Plan sections: Tech Stack (plan header), Architecture Decisions table (ADR-001 row), Task 2, File
  Structure.
- Expected task identifiers: Tasks 11–48 (all Java implementation tasks depend on this decision).

## Validation

Validated by demonstration, not by a separate test: Task 2's `./mvnw -q clean verify` exits 0 with
a passing Spring context-load test (commit `d9e03a4`), confirming the toolchain and dependency set
actually work end-to-end on this machine before any task built on top of it.
undefined
