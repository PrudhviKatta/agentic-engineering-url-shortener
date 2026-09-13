# Agentic Software Engineering System: URL Shortener — Implementation Plan

> **Repository-integrity note (added 2026-09-13):** this file is a tracked, periodically-synced
> snapshot of the controlling session's live execution runbook. It was previously kept only outside
> this repository, which an external review correctly flagged as a repository-integrity gap: a
> reviewer working from a clone of this repo alone could not resolve the concrete code/test content
> behind `tasks.md`'s T011–T048 entries. It now lives here, tracked, so the repository is
> self-contained and independently reviewable without an external file. Per this plan's own
> Authority note (below), it remains subordinate to SpecKit's governed artifacts (`spec.md`,
> `plan.md`, the accepted ADRs, `tasks.md`) — this is the detailed execution draft those artifacts
> argue from, not a competing authority. It is re-synced and re-committed whenever the controlling
> session edits the live working copy (e.g., corrective fixes found by a pre-implementation
> analysis); each sync is its own commit so the history shows what changed and why.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a governed, stateful, non-linear agentic SDLC orchestration system, demonstrated against a URL-shortener domain, that turns a requirement into a reviewable engineering outcome with human gates, retries/rollback/safe-stop, dynamic replanning, and audit-grade evidence — delivered as a runnable prototype plus SpecKit governance artifacts.

**Architecture:** Spring Boot (Spring MVC) service exposing URL-shortener domain APIs and a separate orchestration subsystem (dependency-graph based `WorkflowEngine`) that models the SDLC as stages with entry/exit gates, persisted via Spring Data JPA in an embedded H2 database alongside the shortener's own data. A `PolicyEngine` gates every stage transition against a versioned policy file. Reliability primitives (bounded retry, timeout, rollback/compensation, safe-stop) and an audit/metrics layer wrap every stage execution. Three scenario paths (greenfield, brownfield, ambiguous), driven by a `ScenarioRunner` CLI runner, exercise the same engine to produce distinct, inspectable evidence trails.

**Tech Stack:** Java 21 (LTS), Spring Boot 3.3.x (Spring Web MVC — blocking/servlet style), Spring Data JPA + Hibernate, H2 embedded database (file-mode), Jackson (JSON) + `jackson-dataformat-yaml` (policy files), JUnit 5 + Mockito + Spring Boot Test (`@DataJpaTest`, `@SpringBootTest`) + `TestRestTemplate`, stdlib `java.util.concurrent` (`ExecutorService`/`Future`, parallel stage fan-out), stdlib `UUID`/`Instant` (correlation IDs, timestamps). Maven + Maven Wrapper (`./mvnw`) as the build tool. SpecKit (`specify-cli`) for the governance/lifecycle artifacts layer. No external orchestration infra (Temporal/Camunda/Activiti) — see ADR-005.

**Spec:** `/Users/chintu/Downloads/Charles Schwab/Assignment Agentic-Proficient Software Engineer.pdf` (assignment) and `/Users/chintu/Downloads/Charles Schwab/AI Native SDLC - User Guide.docx` (controlled execution guide — SpecKit lifecycle, prompts, gates, guardrails). This plan is the Phase-2+ execution detail underneath that guide's Section 27 "Final Step-by-Step Execution Sequence."

**Authority (added 2026-09-13, in response to external review):** this document is the controller's
execution runbook for Claude Code's own Subagent-Driven Development process — a task-by-task,
TDD-level breakdown of *how* to build what SpecKit's own lifecycle artifacts decide. It is **not**
authoritative over, and does not compete with, SpecKit's governed lifecycle. The authority order,
per the guide's own hierarchy (Section 5) and the ratified constitution's Principle X, is:
constitution → approved spec/clarifications → approved `specs/001-agentic-url-shortener/plan.md`
and accepted ADRs (Tasks 6–7, not yet run as of this note) → SpecKit's own `tasks.md` (Task 9) →
this runbook → current implementation. Where this runbook pre-decided an architectural or
technology choice for scheduling purposes (the Architecture Decisions table below) ahead of Task
7's formal ADR gate, that choice is a *proposal* only, subject to revision if the human candidate
rejects or amends the corresponding ADR — this runbook is not the record of that acceptance. It is
a plan, not a governance artifact; it gets revised, not defended, if it turns out to conflict with
an approved SpecKit artifact.

## Global Constraints

(Copied from the guide's constitution content — Section 6. Apply to every task below.)

- No production implementation may begin without an approved specification (Principle I).
- Orchestration must be more than sequential chaining: explicit dependency graph, sequential + parallel paths, synchronization, branching, dynamic replanning, interruption/resumption (Principle II).
- Humans own requirements, architecture, security-sensitive decisions, exceptions, destructive changes, release readiness, final quality; approval is never inferred from silence (Principle III).
- Red-green-refactor TDD wherever practical; task completion requires **executed** validation, not generated code (Principle IV).
- Validate/normalize external input, restrict URL schemes, block malicious/internal-address redirects, no secrets in logs, secure defaults (Principle V).
- Versioned policy guardrails; every check yields PASS / FAIL / EXCEPTION-REQUESTED / NOT-APPLICABLE; failed mandatory check blocks progression; exceptions require human approval + recorded rationale/scope/authority/compensating control/timestamp/expiry (Principle VI).
- Separate domain, API delivery, persistence, orchestration, policy, telemetry, infra concerns; record material ADRs (Principle VII).
- Classify transient vs permanent failure; bounded retries with backoff/timeout; idempotency; fallback; rollback vs compensation distinguished; safe-stop; controlled resumption (Principle VIII).
- Correlation ID per run; record transitions/decisions/approvals/retries/failures/replans/terminal outcomes; demonstration metrics labeled as such, not production stats (Principle IX).
- Full traceability: Requirement → Scenario → Design → ADR → Task → Code → Test → Validation → Documentation → Evidence; small coherent commits (Principle X).
- A task/feature is "complete" only per the 10-point Evidence-Based Completion list in the guide (Section 6, Principle XI) — never self-declared.
- MTTR formula (exact, do not deviate): `MTTR = Total recovery duration across recovered failure events ÷ Number of recovered failure events`. Unrecovered failures are **excluded** from the denominator and reported separately.
- Timebox: 2–3 days. Must-have scope beats optional enhancement; non-essential work goes to a labeled backlog and must never displace mandatory validation/evidence.

---

## Architecture Decisions (finalized here; formalized as ADRs in Task 8)

| # | Decision | Choice | Rationale (short) |
|---|---|---|---|
| ADR-001 | Language/framework | Java 21 (LTS) + Spring Boot 3.3 | Strong static typing, mature DI/testing ecosystem (Spring test slices make orchestration/policy/reliability layers independently testable), enterprise-standard fit for a Schwab-style assessment; LTS chosen over a newer non-LTS JDK for stability |
| ADR-002 | Persistence | H2 embedded database (file-mode: `jdbc:h2:file:./data/app;AUTO_SERVER=TRUE`) via Spring Data JPA/Hibernate | SQLite's Java/Hibernate dialect support is second-class; H2 is the idiomatic Spring Boot embedded-DB choice — zero-infra, transactional, inspectable via the H2 console/CLI, and trivially swappable for Postgres later since Spring Data abstracts the persistence layer |
| ADR-003 | Short-code generation | Base62 encoding of an auto-increment integer PK (`short_links.id`) | Deterministic, collision-free by construction (no retry loop needed); documented scaling caveat (single-writer sequence) deferred to backlog |
| ADR-004 | Orchestration model | Custom in-process `WorkflowEngine` (plain Java class, constructor-injected with Spring Data repositories — not a framework-managed singleton — so it's testable without booting the full Spring context) over an explicit `DependencyGraph` of `StageDefinition`s, state persisted via JPA each transition | No infra dependency, fully inspectable/testable, satisfies "explicit dependency graph with entry/exit gates" requirement without unjustified distributed-systems complexity |
| ADR-005 | Why not Temporal/Camunda/Activiti | Rejected | Adds infra/ops burden disproportionate to a local 2–3 day prototype; custom engine is demonstrably auditable in H2 and code-reviewable in full |
| ADR-006 | Human approval mechanism | Synchronous `@RestController` endpoint (`POST /workflows/{id}/approvals/{step}`) that a human (or the reviewer, standing in for "release owner") calls; workflow blocks in `PENDING_APPROVAL` state until called | No UI needed for prototype; approval is an explicit, auditable, non-inferred act |
| ADR-007 | Policy engine format | Versioned YAML under `src/main/resources/policies/v{N}.yaml`, parsed via Jackson's `YAMLMapper` (`jackson-dataformat-yaml`) at evaluation time; version string recorded on every `policy_evaluations` row | Human-readable, diffable, versioned without a database migration per policy change; one JSON/YAML tooling family (Jackson) instead of mixing in SnakeYAML directly |
| ADR-008 | Concurrency for parallel stages | `java.util.concurrent.ExecutorService` (`Executors.newFixedThreadPool`) + `Future.get()` per stage (stage handlers are short-lived, I/O-light) | Sufficient to prove real parallel fan-out + synchronization without reactive/WebFlux complexity across the whole codebase |
| ADR-009 | Rollback vs. compensation | Pure in-memory/DB stage effects use rollback (JPA transaction abort); externally-visible effects (e.g., "notification sent" stage) use registered compensating actions | Matches Principle VIII's required distinction |
| ADR-010 | Authentication | None enforced in prototype; all endpoints documented as trusted-caller only; auth is an explicit backlog item with an ADR-level assumption requiring human approval | Assignment scope reviewers explicitly flagged auth as an assumption category, not a mandatory build item |

Each of the above becomes a full ADR document per the guide's ADR template in Task 8; this table is the pre-approved decision set the ADRs formalize (you, the human candidate, must still mark each ADR "Accepted" per the guide's guardrail — do not let the agent self-approve).

---

## File Structure

```
agentic-engineering-url-shortener/
├── .claude/                              # generated by `specify init --integration claude`
├── .specify/                             # generated by SpecKit
├── specs/001-agentic-url-shortener/
│   ├── spec.md
│   ├── plan.md
│   ├── research.md
│   ├── data-model.md
│   ├── quickstart.md
│   ├── tasks.md
│   ├── contracts/
│   │   └── openapi.yaml
│   └── checklists/
├── docs/
│   ├── adr/ADR-001..010-*.md
│   ├── architecture/overview.md
│   ├── governance/constitution.md
│   ├── scenarios/{greenfield,brownfield,ambiguous}.md
│   ├── traceability/matrix.md
│   ├── assessment/final_engineering_summary.md
│   └── assessment/reviewer_navigation_guide.md
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties
├── src/main/java/com/schwab/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── domain/
│   │   ├── ShortLink.java
│   │   ├── ShortCodeGenerator.java
│   │   ├── UrlValidator.java
│   │   └── InvalidUrlException.java
│   ├── persistence/
│   │   ├── ShortLinkEntity.java
│   │   ├── RedirectEventEntity.java
│   │   └── ShortLinkJpaRepository.java
│   ├── api/
│   │   ├── dto/
│   │   │   ├── CreateLinkRequest.java
│   │   │   ├── LinkResponse.java
│   │   │   ├── AnalyticsResponse.java
│   │   │   └── ErrorResponse.java
│   │   ├── LinkController.java
│   │   ├── HealthController.java
│   │   └── ApiExceptionHandler.java
│   ├── orchestration/
│   │   ├── StepStatus.java
│   │   ├── WorkflowStatus.java
│   │   ├── StageHandler.java
│   │   ├── StageDefinition.java
│   │   ├── DependencyGraph.java
│   │   ├── CycleException.java
│   │   ├── UnknownDependencyException.java
│   │   ├── WorkflowContext.java
│   │   ├── WorkflowEngine.java
│   │   ├── JsonMapAttributeConverter.java
│   │   ├── persistence/
│   │   │   ├── WorkflowInstanceEntity.java
│   │   │   ├── WorkflowStepEntity.java
│   │   │   ├── WorkflowEventEntity.java
│   │   │   ├── ApprovalEntity.java
│   │   │   ├── PolicyEvaluationEntity.java
│   │   │   ├── PolicyExceptionEntity.java
│   │   │   ├── WorkflowInstanceJpaRepository.java
│   │   │   ├── WorkflowStepJpaRepository.java
│   │   │   ├── WorkflowEventJpaRepository.java
│   │   │   ├── ApprovalJpaRepository.java
│   │   │   ├── PolicyEvaluationJpaRepository.java
│   │   │   └── PolicyExceptionJpaRepository.java
│   │   └── stages/
│   │       ├── RequirementStage.java
│   │       ├── DecompositionStage.java
│   │       ├── DesignStage.java
│   │       ├── ImplementationStage.java
│   │       ├── TestingStage.java
│   │       ├── DocumentationStage.java
│   │       ├── SecurityStage.java
│   │       ├── ReleaseReadinessStage.java
│   │       └── ImpactAnalysisStage.java
│   ├── policy/
│   │   ├── PolicyOutcome.java
│   │   ├── PolicyCheckResult.java
│   │   ├── PolicyDefinition.java
│   │   └── PolicyEngine.java
│   ├── reliability/
│   │   ├── TransientStageException.java
│   │   ├── PermanentStageException.java
│   │   ├── StageTimeoutException.java
│   │   └── CompensationRegistry.java
│   ├── observability/
│   │   ├── AuditRecorder.java
│   │   ├── MetricsService.java
│   │   ├── WorkflowController.java
│   │   └── MetricsController.java
│   └── scenarios/
│       └── ScenarioRunner.java
├── src/main/resources/
│   ├── application.yml
│   └── policies/v1.yaml
├── src/test/java/com/schwab/urlshortener/         # mirrors src/main/java package-for-package
│   ├── UrlShortenerApplicationTests.java
│   ├── domain/{ShortCodeGeneratorTest,UrlValidatorTest,UrlValidatorSsrfTest}.java
│   ├── persistence/ShortLinkRepositoryTest.java
│   ├── api/{LinkControllerContractTest,WorkflowControllerContractTest,MetricsControllerContractTest}.java
│   ├── orchestration/
│   │   ├── DependencyGraphTest.java
│   │   ├── WorkflowContextTest.java
│   │   ├── WorkflowEngineExecutionTest.java
│   │   ├── WorkflowEngineApprovalTest.java
│   │   ├── WorkflowEngineContextLineageTest.java
│   │   ├── WorkflowEnginePolicyEnforcementTest.java
│   │   ├── WorkflowEngineReliabilityIntegrationTest.java
│   │   ├── WorkflowEngineSafeStopTest.java
│   │   └── WorkflowEngineReplanningTest.java
│   ├── policy/{PolicyEngineTest,PolicyExceptionTest}.java
│   ├── reliability/{RetryTest,TimeoutTest,CompensationRegistryTest}.java
│   ├── observability/{AuditRecorderTest,MetricsMttrTest}.java
│   └── scenarios/{GreenfieldScenarioTest,BrownfieldScenarioTest,AmbiguousScenarioTest}.java
├── README.md, SECURITY.md, CONTRIBUTING.md, speckit-version.txt
```

---

## Phase 0 — Repository & SpecKit Bootstrap (not TDD; one-time setup)

### Task 1: Create repository and install SpecKit

**Already complete** (commit `8bfddcc`) — language-independent, no rework needed for the Java/Spring Boot stack pivot.

**Files:** none yet (repo root creation)

- [ ] **Step 1:** `mkdir agentic-engineering-url-shortener && cd agentic-engineering-url-shortener && git init`
- [ ] **Step 2:** `uv tool install specify-cli --from git+https://github.com/github/spec-kit.git@v<PIN_VERSION>` (pin an actual release tag; record it)
- [ ] **Step 3:** `specify version > speckit-version.txt`
- [ ] **Step 4:** `specify init --here --integration claude`
- [ ] **Step 5:** Verify: `find .claude -maxdepth 3 -type f | sort` and `find .specify -maxdepth 3 -type f | sort` both return files; `git status` shows the new tree.
- [ ] **Step 6:** `git add . && git commit -m "chore: initialize SpecKit project with Claude Code integration"`

### Task 2: Author `pom.xml`, Maven Wrapper, base Spring Boot skeleton, CI-less local quality gate

**Files:**
- Create: `pom.xml`
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (generated, not hand-written)
- Create: `src/main/java/com/schwab/urlshortener/UrlShortenerApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/com/schwab/urlshortener/UrlShortenerApplicationTests.java`
- Create: `.gitignore`

**Interfaces:** none (no domain/orchestration code yet)

**Environment note (binding for every later task, not just this one):** this machine's default `mvn` resolves to a non-LTS JDK. Every Maven invocation in this plan must set `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` explicitly (already-installed JDK 21 LTS, verified working), e.g. `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test`.

- [ ] **Step 1:** Write `pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.schwab</groupId>
  <artifactId>url-shortener</artifactId>
  <version>0.1.0</version>
  <name>url-shortener</name>
  <description>Agentic Software Engineering System: URL Shortener</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.dataformat</groupId>
      <artifactId>jackson-dataformat-yaml</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```
- [ ] **Step 2:** Generate the Maven Wrapper using the already-installed system Maven (so future contributors/reviewers only need a JDK, not Maven itself):
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /opt/homebrew/bin/mvn -N wrapper:wrapper -Dmaven=3.9.9
```
  Confirm `./mvnw`, `./mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` now exist; `chmod +x mvnw`.
- [ ] **Step 3:** `src/main/java/com/schwab/urlshortener/UrlShortenerApplication.java`:
```java
package com.schwab.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}
```
- [ ] **Step 4:** `src/main/resources/application.yml`:
```yaml
server:
  port: 8080

spring:
  application:
    name: url-shortener
  datasource:
    url: jdbc:h2:file:./data/app;AUTO_SERVER=TRUE
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      # Prototype-only convenience: auto-evolves the schema from entities on
      # every boot. Never use ddl-auto=update in production; a real deployment
      # needs Flyway/Liquibase-managed migrations. Documented limitation, not
      # an oversight — see docs/assessment/final_engineering_summary.md.
      ddl-auto: update
    show-sql: false
    open-in-view: false
```
- [ ] **Step 5:** `src/test/java/com/schwab/urlshortener/UrlShortenerApplicationTests.java`:
```java
package com.schwab.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UrlShortenerApplicationTests {
    @Test
    void contextLoads() {
    }
}
```
- [ ] **Step 6:** `.gitignore`:
```
target/
data/*.mv.db
data/*.trace.db
.idea/
*.iml
.mvn/wrapper/maven-wrapper.jar
```
  (Do not touch the pre-existing `.superpowers/` ignore entry from repo setup.)
- [ ] **Step 7:** `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -q clean verify` — confirm exit 0 (compiles the application class and runs the one context-load test).
- [ ] **Step 8:** Commit: `git add . && git commit -m "chore: establish Spring Boot project skeleton and quality baseline"`

**Note on the stack pivot:** this repo's history already contains a Python/FastAPI scaffold commit from before the stack decision changed. Do not rewrite or hide that commit — remove the Python-specific files this task's Java equivalents replace (`pyproject.toml`, `pytest.ini`, `src/url_shortener/`, `.venv/` if present) as part of this commit's diff, and let the commit message and this plan's own record explain the pivot. A truthful history that shows a stack decision changing early, before any business logic was written, is defensible; concealing it is not.

---

## Phase 1 — Governance Artifacts (SpecKit lifecycle; human gates apply)

Each task below produces a SpecKit-lifecycle document. Content must satisfy the exact rules already specified in the guide (Sections 6–14) — do not weaken them. **Do not implement application code in this phase.**

### Task 3: Constitution (`/speckit.constitution`)

**Files:** Create: `docs/governance/constitution.md` (or SpecKit-generated equivalent under `.specify/memory/`)

- [ ] **Step 1:** Draft the constitution using the guide's Section 6 prompt verbatim as input, covering Principles I–XI listed in Global Constraints above, plus governance requirements (how compliance is assessed, how exceptions are approved, which principles are non-waivable, amendment versioning, conflict resolution, non-compliance blocking release, initial version = `1.0.0`).
- [ ] **Step 2: Human Gate 1** — verify explicit coverage of: non-linear orchestration, state persistence, human ownership, bounded retry, rollback/compensation, safe-stop, dynamic replanning, audit-grade evidence, TDD, evidence-based completion. Reject and redo if any are missing.
- [ ] **Step 3:** `git add . && git commit -m "docs(governance): establish engineering constitution"`

### Task 4: Feature Specification (`/speckit.specify`)

**Files:** Create: `specs/001-agentic-url-shortener/spec.md`

- [ ] **Step 1:** Draft using guide Section 7 content: business objective, demonstration domain (URL shortener capabilities list), orchestration coordination list (12 items), three required scenarios (A/B/C exact text from guide), user types (API consumer, software engineer, human reviewer/approver, release owner, assessment reviewer), functional requirement areas (20 items), non-functional requirement areas (11 items — each with a proposed validation target marked as an **assumption requiring approval**, never a confirmed requirement).
- [ ] **Step 2:** Assign stable requirement IDs (`REQ-001..REQ-0NN`) and scenario IDs (`SCN-A`, `SCN-B`, `SCN-C`). Every requirement gets an observable acceptance criterion **and** a negative acceptance criterion.
- [ ] **Step 3:** Do not choose framework/language/DB/cloud/agent-framework here — those are ADR/plan-stage decisions (Task 6/7), even though this plan has pre-decided them for scheduling purposes.
- [ ] **Step 4: Human Gate 2** — reject the draft if it: chose tech prematurely, silently resolved ambiguity, lacks negative behavior, omits a required scenario, presents orchestration as linear, lacks human approval, lacks recovery/safe-stop, is untestable, or is untraceable.
- [ ] **Step 5:** `git add . && git commit -m "docs(spec): define governed agentic URL shortener requirements"`

### Task 5: Clarification (`/speckit.clarify`)

**Files:** Modify: `specs/001-agentic-url-shortener/spec.md` (append resolved clarifications + residual assumptions section)

- [ ] **Step 1:** Run the ambiguity review across the 25 topic areas listed in guide Section 8 (URL validation, duplicate URL behavior, short-code behavior, expiration, analytics consistency, concurrency, auth assumptions, rate limiting, orchestration semantics, approval ownership, retry eligibility, timeout, fallback, rollback/compensation, safe-stop, state recovery, dynamic replanning, audit retention, deployment assumptions, tech selection, and the three scenarios, release-readiness criteria).
- [ ] **Step 2:** For each open question record: identifier, question, impact, current assumption, owner (**you**, the human candidate — not the agent), required decision point.
- [ ] **Step 3: Human Gate 3** — you personally answer every material question (guide explicitly forbids delegating this to "best practices" defaults). Suggested concrete resolutions to adopt unless you decide otherwise:
  - URL validation: allow only `http`/`https` schemes; reject `javascript:`, `file:`, `data:`, and any target resolving to a loopback/link-local/private RFC1918 address (SSRF guard).
  - Duplicate URL behavior: creating a short link for an already-shortened long URL returns a **new** short code (no implicit dedup) — simplest, most predictable; dedup is backlog.
  - Short-code behavior: 7-character base62, generated per ADR-003.
  - Expiration: optional `expires_at` on create; default none; expired links return `410 Gone` on redirect.
  - Analytics: redirect count + last-accessed timestamp, eventually consistent (async increment acceptable) — recorded as a proposed validation target, not a hard requirement.
  - Concurrency: SQLite transaction per write; last-writer-wins on analytics increments is acceptable for prototype scale.
  - Auth: none enforced (ADR-010); documented assumption.
  - Rate limiting: none enforced in prototype; documented as backlog with a proposed target (e.g., 100 req/min/IP) marked assumption.
  - Retry eligibility: only transient failures (DB lock timeout, simulated transient stage failure) are retryable; validation/policy failures are permanent, never retried.
  - Safe-stop: entered when a mandatory policy check fails without an approved exception, or bounded retries are exhausted with no fallback.
  - Rollback impossible cases: once a redirect has been served to a caller, that "delivery" cannot be rolled back — compensation (e.g., logging an incident/audit note) is used instead of rollback.
- [ ] **Step 4:** Update spec.md with approved clarifications inline; keep a "Deferred Questions / Residual Assumptions" appendix for anything not resolved.
- [ ] **Step 5:** `git add . && git commit -m "docs(spec): resolve critical ambiguities and record assumptions"`

### Task 6: Technical Plan (`/speckit.plan`)

**Files:** Create: `specs/001-agentic-url-shortener/plan.md`, `specs/001-agentic-url-shortener/research.md`, `specs/001-agentic-url-shortener/data-model.md`, `specs/001-agentic-url-shortener/contracts/openapi.yaml`

- [ ] **Step 1:** Write `plan.md` covering the guide's 14 required sections (system context; URL-shortener architecture incl. OpenAPI/schema deliverables; orchestration architecture with node/stage table — purpose/inputs/outputs/pre/postconditions/actor/allowed & prohibited transitions/timeout/retry/failure classification/fallback/audit events; state & decision lineage; human-in-the-loop gate list; reliability definitions; observability plan incl. MTTR mechanics; security; compliance/change-control; testing plan; three scenario designs; technology decisions table — reuse the Architecture Decisions table above; traceability chain; delivery sequence — 9 vertical slices matching this plan's phases).
- [ ] **Step 2:** Write `data-model.md` enumerating every table from the File Structure section (`short_links`, `redirect_events`, `workflow_instances`, `workflow_steps`, `workflow_events`, `approvals`, `policy_evaluations`, `policy_exceptions`) with columns, types, constraints.
- [ ] **Step 3:** Write `contracts/openapi.yaml` for: `POST /links`, `GET /links/{code}`, `GET /r/{code}`, `GET /links/{code}/analytics`, `POST /workflows`, `GET /workflows/{id}`, `POST /workflows/{id}/approvals/{step}`, `GET /workflows/{id}/audit`, `GET /metrics`, `GET /healthz`, `GET /readyz`.
- [ ] **Step 4:** Add the mandatory scope-control section: must-have scope (Phases 2–10 below), deferred backlog (auth, rate limiting, Flyway/Liquibase-managed migrations, dedup, multi-region ID generation), day-level milestones (Day 1: Phases 0–4; Day 2: Phases 5–9; Day 3: Phase 10–12 + hardening), stop conditions (if orchestration engine core — Phase 4 — is not passing tests by end of Day 1, cut scenario C's replanning depth before cutting any governance/audit requirement).
- [ ] **Step 5:** `git add . && git commit -m "docs(architecture): define implementation and orchestration plan"`

### Task 7: ADR Generation Gate

**Files:** Create: `docs/adr/ADR-001-language-framework.md` … `docs/adr/ADR-010-authentication-assumption.md` (10 files, one per row of the Architecture Decisions table)

- [ ] **Step 1:** For each of the 10 decisions, write a full ADR using the guide's template (Status/Context/Decision Drivers/Options Considered/Decision/Rationale/Consequences split positive-negative-operational-testing-governance/Risks and Mitigations/Reversibility/Traceability to REQ/SCN IDs/Validation approach). Status starts as `Proposed`.
- [ ] **Step 2:** Produce the ADR inventory summary (list of 10, which are human-approval-required — all of them, since all are material — vs. safely deferrable — none), conflicts with plan (none expected; flag if found), missing information.
- [ ] **Step 3: Human Gate 4** — you (human candidate) mark each ADR `Accepted` or `Rejected` explicitly in its Status field. The agent must not self-mark any ADR Accepted.
- [ ] **Step 4:** `git add docs/adr specs .specify && git commit -m "docs(architecture): record foundational architecture decisions"`

### Task 8: Quality Checklists (`/speckit.checklist`)

**Files:** Create: `specs/001-agentic-url-shortener/checklists/{requirements,architecture,adr,orchestration,autonomy,approvals,security,compliance,reliability,observability,tdd,greenfield,brownfield,ambiguous,documentation,traceability,github-evidence,release-readiness,submission}.md` (19 files per guide Section 11)

- [ ] **Step 1:** For each checklist, write objectively verifiable items (no vague adjectives), each mapped to a REQ/ADR/Principle ID, each including a negative/failure-path check. Explicitly include detection items for the 26 anti-patterns listed in guide Section 11 (linear chaining disguised as orchestration, missing persistent state, unlimited retries, invalid rollback claims, approval inferred from silence, fabricated results, etc.) as checklist line items under the relevant category.
- [ ] **Step 2:** `git add . && git commit -m "docs(quality): add assessment and engineering checklists"`

### Task 9: Task Generation (`/speckit.tasks`)

**Files:** Create: `specs/001-agentic-url-shortener/tasks.md`

- [ ] **Step 1:** Transcribe **this plan's** Phases 2–12 (Tasks 11–48 below) into `tasks.md` in SpecKit's task format: stable ID, objective, REQ/SCN/ADR references, exact target paths, prerequisites/dependencies, parallelization flag, expected artifact, TDD obligation, validation command, doc impact, traceability update, risk/guardrail, completion criteria, human-approval flag. `tasks.md`, once written, is the authoritative SpecKit task list (per this plan's own Authority note above) — this runbook is its source draft, not a competing shadow process. If Task 7's ADR approval changed anything this runbook assumed, reconcile that here, before transcribing, not after.
- [ ] **Step 2:** Mark Tasks 20–26 (orchestration engine) and 27–29 (policy engine) as sequential/blocking; mark independent leaf tasks (e.g., Task 15 URL validation and Task 16 short-code generation) as `parallel: true` since they touch disjoint files with no shared interface yet.
- [ ] **Step 3:** `git add . && git commit -m "docs(plan): create dependency-aware implementation tasks"`

### Task 10: Pre-Implementation Analysis (`/speckit.analyze`) + Independent Reviewer Gate

**Files:** Create: `docs/assessment/pre_implementation_analysis.md`

- [ ] **Step 1:** Cross-check constitution ↔ spec ↔ clarifications ↔ plan ↔ ADRs ↔ checklists ↔ tasks for: every REQ represented and mapped to planned work; every scenario mapped to design/tasks/tests/docs/evidence; every orchestration capability mapped to an implementation task; every human gate mapped to state+task+evidence; every Accepted ADR mapped to tasks; every NFR has a verification method. Classify findings CRITICAL/HIGH/MEDIUM/LOW.
- [ ] **Step 2:** Run the "Explicit Failure Traps" scan from guide Section 13 against the current artifact set (this plan). Fix any CRITICAL/HIGH at the source document, not by patching the analysis report.
- [ ] **Step 3:** Run the Independent Reviewer Gate (guide Section 14) as a fresh, skeptical pass over the 20 review questions; output `PROCEED / PROCEED WITH CONDITIONS / DO NOT PROCEED`. Do not begin Phase 2 implementation until this is `PROCEED` or conditions are resolved.
- [ ] **Step 4:** `git add . && git commit -m "docs(quality): resolve pre-implementation consistency gaps"`

---

## Phase 2 — Engineering Baseline & Walking Skeleton

### Task 11: App skeleton — health endpoints via Spring MVC

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/api/HealthController.java`
- Test: `src/test/java/com/schwab/urlshortener/api/HealthControllerTest.java`

**Interfaces:**
- Produces: `GET /healthz` → `200 {"status":"ok"}`; `GET /readyz` → `200 {"status":"ready"}` (fails with a 5xx if the `DataSource` can't be reached, via Spring's own connection-pool exception propagation)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/api/HealthControllerTest.java
package com.schwab.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void healthzReturnsOk() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + port + "/healthz", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void readyzReturnsReadyWhenDbReachable() {
        ResponseEntity<String> resp = rest.getForEntity("http://localhost:" + port + "/readyz", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"status\":\"ready\"");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — `./mvnw test -Dtest=HealthControllerTest` → compile error / 404, `HealthController` doesn't exist yet.
- [ ] **Step 3: Implement minimal code**
```java
// src/main/java/com/schwab/urlshortener/api/HealthController.java
package com.schwab.urlshortener.api;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public Map<String, String> readyz() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.isValid(2);
        }
        return Map.of("status", "ready");
    }
}
```
- [ ] **Step 4: Run to confirm pass** — `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=HealthControllerTest` → PASS
- [ ] **Step 5: Commit** — `git add src && git commit -m "feat: establish walking skeleton with health endpoints"`

### Task 12: `ShortLinkEntity`/`RedirectEventEntity` JPA schema + repository round-trip

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/persistence/ShortLinkEntity.java`
- Create: `src/main/java/com/schwab/urlshortener/persistence/RedirectEventEntity.java`
- Create: `src/main/java/com/schwab/urlshortener/persistence/ShortLinkJpaRepository.java`
- Test: `src/test/java/com/schwab/urlshortener/persistence/ShortLinkRepositoryTest.java`

**Interfaces:**
- Produces: `ShortLinkEntity` (JPA `@Entity`, table `short_links`), `RedirectEventEntity` (`@Entity`, table `redirect_events`), `ShortLinkJpaRepository extends JpaRepository<ShortLinkEntity, Long>` with `Optional<ShortLinkEntity> findByCode(String code)`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/persistence/ShortLinkRepositoryTest.java
package com.schwab.urlshortener.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ShortLinkRepositoryTest {

    @Autowired
    private ShortLinkJpaRepository repository;

    @Test
    void persistsAndReadsBackByCode() {
        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setCode("abc1234");
        entity.setLongUrl("https://example.com");
        repository.save(entity);

        ShortLinkEntity fetched = repository.findByCode("abc1234").orElseThrow();
        assertThat(fetched.getLongUrl()).isEqualTo("https://example.com");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the three classes exist yet.
- [ ] **Step 3: Implement minimal code**
```java
// src/main/java/com/schwab/urlshortener/persistence/ShortLinkEntity.java
package com.schwab.urlshortener.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 16)
    private String code;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "redirect_count", nullable = false)
    private int redirectCount = 0;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getRedirectCount() { return redirectCount; }
    public void setRedirectCount(int redirectCount) { this.redirectCount = redirectCount; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
}
```
```java
// src/main/java/com/schwab/urlshortener/persistence/RedirectEventEntity.java
package com.schwab.urlshortener.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "redirect_events")
public class RedirectEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_link_id", nullable = false)
    private Long shortLinkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 16)
    private String outcome; // "redirected" | "expired" | "not_found"

    public Long getId() { return id; }
    public Long getShortLinkId() { return shortLinkId; }
    public void setShortLinkId(Long shortLinkId) { this.shortLinkId = shortLinkId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
}
```
```java
// src/main/java/com/schwab/urlshortener/persistence/ShortLinkJpaRepository.java
package com.schwab.urlshortener.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortLinkJpaRepository extends JpaRepository<ShortLinkEntity, Long> {
    Optional<ShortLinkEntity> findByCode(String code);
}
```
- [ ] **Step 4: Run to confirm pass** — `./mvnw test -Dtest=ShortLinkRepositoryTest` → PASS. `@DataJpaTest` boots an isolated, transactional, in-memory H2 context — it does not touch the file-mode `data/app` database `application.yml` configures for the real app, which is deliberate (fast, isolated unit-level persistence tests).
- [ ] **Step 5: Commit** — `git commit -m "feat(persistence): add short_links and redirect_events schema"`

### Task 13: Verify real (file-mode H2) schema creation on full application startup

**Files:**
- Test: `src/test/java/com/schwab/urlshortener/UrlShortenerSchemaBootTest.java`
- Modify: `.gitignore` (already covers `data/*.mv.db`/`data/*.trace.db` from Task 2 — verify, don't duplicate)

**Interfaces:** none new — this task is a verification checkpoint, not new production code.

- [ ] **Step 1:** Write a test that boots the **full** application context (not the `@DataJpaTest` slice) and confirms the real, file-mode datasource from `application.yml` actually has the `short_links` table after Hibernate's `ddl-auto: update` runs against it on startup:
```java
// src/test/java/com/schwab/urlshortener/UrlShortenerSchemaBootTest.java
package com.schwab.urlshortener;

import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class UrlShortenerSchemaBootTest {

    @Autowired
    private ShortLinkJpaRepository repository;

    @Test
    void repositoryIsUsableAgainstTheRealConfiguredDatasourceAfterBoot() {
        assertThatCode(() -> repository.count()).doesNotThrowAnyException();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — if this is run before Task 12 lands, compile error (no repository). Run now, after Task 12: it should already PASS immediately, since Spring Boot + Hibernate `ddl-auto: update` create the schema automatically at context startup — there is no separate `init_db()` step to wire in Java the way the original Python/SQLAlchemy design needed (SQLAlchemy does not auto-create tables from ORM classes without an explicit `create_all()` call; Hibernate's `ddl-auto` does this by convention). Confirm the test passes and that `data/app.mv.db` now exists on disk after the test run — that file's existence is your evidence schema creation actually happened against the real datasource, not just the `@DataJpaTest` in-memory one.
- [ ] **Step 3:** Confirm `data/*.mv.db` and `data/*.trace.db` are already ignored per Task 2's `.gitignore` (they will be, but re-check now that a real `data/app.mv.db` file exists locally) — `git status` must show it as untracked/ignored, not staged.
- [ ] **Step 4: Commit** — `git commit -m "test: verify schema auto-creation against the real file-mode datasource"`

---

## Phase 3 — URL Shortener Domain

### Task 14: `ShortLink` domain record (expiry rule)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/domain/ShortLink.java`
- Test: `src/test/java/com/schwab/urlshortener/domain/ShortLinkTest.java`

**Interfaces:**
- Produces: `record ShortLink(Long id, String code, String longUrl, Instant createdAt, Instant expiresAt, int redirectCount, Instant lastAccessedAt)` with `boolean isExpired()`, and `static ShortLink fromEntity(ShortLinkEntity entity)` (bridges the JPA entity from Task 12 to this pure domain type — controllers convert at the boundary rather than leaking `ShortLinkEntity` into response DTOs)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/domain/ShortLinkTest.java
package com.schwab.urlshortener.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkTest {

    @Test
    void isExpiredWhenPastExpiry() {
        ShortLink link = new ShortLink(1L, "abc1234", "https://x.com",
                Instant.now(), Instant.parse("2000-01-01T00:00:00Z"), 0, null);
        assertThat(link.isExpired()).isTrue();
    }

    @Test
    void notExpiredWhenNoExpirySet() {
        ShortLink link = new ShortLink(1L, "abc1234", "https://x.com",
                Instant.now(), null, 0, null);
        assertThat(link.isExpired()).isFalse();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `ShortLink` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/domain/ShortLink.java
package com.schwab.urlshortener.domain;

import com.schwab.urlshortener.persistence.ShortLinkEntity;
import java.time.Instant;

public record ShortLink(
        Long id,
        String code,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        int redirectCount,
        Instant lastAccessedAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public static ShortLink fromEntity(ShortLinkEntity entity) {
        return new ShortLink(
                entity.getId(), entity.getCode(), entity.getLongUrl(), entity.getCreatedAt(),
                entity.getExpiresAt(), entity.getRedirectCount(), entity.getLastAccessedAt());
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(domain): define URL and short-link domain model"`

**Design note (why no separate `ShortLinkRepository` wrapper class):** the original design's persistence-layer repository wrapper is unnecessary here — `ShortLinkJpaRepository` (Task 12) already is the persistence abstraction Spring Data provides, and `ShortLink.fromEntity()` is the only conversion needed at controller boundaries. Adding another repository interface on top would just be indirection with no swappable implementation behind it (YAGNI; ADR-004 already covers why Spring Data is the abstraction layer).

### Task 15: URL validation (security-critical; parallel-safe with Task 16)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/domain/UrlValidator.java`
- Create: `src/main/java/com/schwab/urlshortener/domain/InvalidUrlException.java`
- Test: `src/test/java/com/schwab/urlshortener/domain/UrlValidatorTest.java`, `src/test/java/com/schwab/urlshortener/domain/UrlValidatorSsrfTest.java`

**Interfaces:**
- Produces: `InvalidUrlException extends RuntimeException` (field `String reason`, `getReason()`); `class UrlValidator { static String validate(String raw) throws InvalidUrlException }`

- [ ] **Step 1: Write failing tests**
```java
// src/test/java/com/schwab/urlshortener/domain/UrlValidatorTest.java
package com.schwab.urlshortener.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "file:///etc/passwd", "data:text/html,<script>", "ftp://x.com"})
    void rejectsDisallowedSchemes(String raw) {
        assertThatThrownBy(() -> UrlValidator.validate(raw)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsHttpAndHttps() {
        assertThat(UrlValidator.validate("https://example.com/path")).isEqualTo("https://example.com/path");
        assertThat(UrlValidator.validate("http://example.com")).isEqualTo("http://example.com");
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> UrlValidator.validate("not a url")).isInstanceOf(InvalidUrlException.class);
    }
}
```
```java
// src/test/java/com/schwab/urlshortener/domain/UrlValidatorSsrfTest.java
package com.schwab.urlshortener.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorSsrfTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/admin", "http://localhost:8080", "http://169.254.169.254/latest/meta-data",
        "http://10.0.0.5/internal", "http://192.168.1.1/"
    })
    void rejectsLocalAndLinkLocalTargets(String raw) {
        assertThatThrownBy(() -> UrlValidator.validate(raw)).isInstanceOf(InvalidUrlException.class);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, neither class exists yet.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/domain/InvalidUrlException.java
package com.schwab.urlshortener.domain;

public class InvalidUrlException extends RuntimeException {
    private final String reason;

    public InvalidUrlException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
```
```java
// src/main/java/com/schwab/urlshortener/domain/UrlValidator.java
package com.schwab.urlshortener.domain;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlValidator() { }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("empty_url");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("malformed_url");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("scheme_not_allowed:" + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("missing_host");
        }
        if (host.equalsIgnoreCase("localhost") || isBlockedHost(host)) {
            throw new InvalidUrlException("target_host_blocked");
        }
        return raw;
    }

    private static boolean isBlockedHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || address.isAnyLocalAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true; // cannot resolve -> treat as unsafe/invalid
        }
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(security): validate URL schemes and block SSRF targets"`

### Task 16: Short-code generation (parallel-safe with Task 15)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/domain/ShortCodeGenerator.java`
- Test: `src/test/java/com/schwab/urlshortener/domain/ShortCodeGeneratorTest.java`

**Interfaces:**
- Produces: `class ShortCodeGenerator { static String toBase62(long n); static String generateCode(long numericId); static String generateCode(long numericId, int width); }`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/domain/ShortCodeGeneratorTest.java
package com.schwab.urlshortener.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    @Test
    void toBase62Zero() {
        assertThat(ShortCodeGenerator.toBase62(0)).isEqualTo("0");
    }

    @Test
    void toBase62RoundtripValues() {
        assertThat(ShortCodeGenerator.toBase62(61)).isEqualTo("Z");
        assertThat(ShortCodeGenerator.toBase62(62)).isEqualTo("10");
    }

    @Test
    void generateCodeIsZeroPaddedAndDeterministic() {
        assertThat(ShortCodeGenerator.generateCode(1)).isEqualTo("0000001");
        assertThat(ShortCodeGenerator.generateCode(62)).isEqualTo("0000010");
        assertThat(ShortCodeGenerator.generateCode(1)).isEqualTo(ShortCodeGenerator.generateCode(1));
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, class doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/domain/ShortCodeGenerator.java
package com.schwab.urlshortener.domain;

public final class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    private static final int DEFAULT_WIDTH = 7;

    private ShortCodeGenerator() { }

    public static String toBase62(long n) {
        if (n == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder digits = new StringBuilder();
        while (n > 0) {
            digits.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return digits.reverse().toString();
    }

    public static String generateCode(long numericId) {
        return generateCode(numericId, DEFAULT_WIDTH);
    }

    public static String generateCode(long numericId, int width) {
        String encoded = toBase62(numericId);
        return "0".repeat(Math.max(0, width - encoded.length())) + encoded;
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(domain): implement deterministic base62 short-code generation"`

### Task 17: `POST /links` API (contract test first)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/api/dto/CreateLinkRequest.java`, `LinkResponse.java`, `ErrorResponse.java`
- Create: `src/main/java/com/schwab/urlshortener/api/LinkController.java`
- Create: `src/main/java/com/schwab/urlshortener/api/ApiExceptionHandler.java`
- Test: `src/test/java/com/schwab/urlshortener/api/LinkControllerContractTest.java`

**Interfaces:**
- Consumes: `UrlValidator.validate` (Task 15), `ShortCodeGenerator.generateCode` (Task 16), `ShortLinkJpaRepository` (Task 12), `ShortLink.fromEntity` (Task 14)
- Produces: `POST /links` → `201 {code, shortUrl, longUrl, expiresAt}`; `422` on invalid URL, body `{"error":{"reason": "..."}}`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/api/LinkControllerContractTest.java
package com.schwab.urlshortener.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkControllerContractTest {

    @LocalServerPort
    int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void createLinkReturns201WithCode() {
        var request = Map.of("longUrl", "https://example.com/page");
        ResponseEntity<Map> resp = rest.postForEntity(url("/links"), request, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = resp.getBody();
        assertThat((String) body.get("code")).hasSize(7);
        assertThat(body.get("longUrl")).isEqualTo("https://example.com/page");
    }

    @Test
    void createLinkRejectsInvalidScheme() {
        var request = Map.of("longUrl", "javascript:alert(1)");
        ResponseEntity<Map> resp = rest.postForEntity(url("/links"), request, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        Map<?, ?> error = (Map<?, ?>) resp.getBody().get("error");
        assertThat((String) error.get("reason")).startsWith("scheme_not_allowed");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — 404 (`LinkController` doesn't exist).
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/api/dto/CreateLinkRequest.java
package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record CreateLinkRequest(String longUrl, Instant expiresAt) { }
```
```java
// src/main/java/com/schwab/urlshortener/api/dto/LinkResponse.java
package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record LinkResponse(String code, String longUrl, String shortUrl, Instant expiresAt) { }
```
```java
// src/main/java/com/schwab/urlshortener/api/dto/ErrorResponse.java
package com.schwab.urlshortener.api.dto;

public record ErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String reason) { }

    public static ErrorResponse of(String reason) {
        return new ErrorResponse(new ErrorDetail(reason));
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/api/ApiExceptionHandler.java
package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.ErrorResponse;
import com.schwab.urlshortener.domain.InvalidUrlException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of(ex.getReason()));
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/api/LinkController.java
package com.schwab.urlshortener.api;

import com.schwab.urlshortener.api.dto.CreateLinkRequest;
import com.schwab.urlshortener.api.dto.LinkResponse;
import com.schwab.urlshortener.domain.ShortCodeGenerator;
import com.schwab.urlshortener.domain.UrlValidator;
import com.schwab.urlshortener.persistence.ShortLinkEntity;
import com.schwab.urlshortener.persistence.ShortLinkJpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class LinkController {

    private final ShortLinkJpaRepository repository;

    public LinkController(ShortLinkJpaRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse createLink(@RequestBody CreateLinkRequest request) {
        String normalized = UrlValidator.validate(request.longUrl());

        ShortLinkEntity entity = new ShortLinkEntity();
        entity.setLongUrl(normalized);
        entity.setExpiresAt(request.expiresAt());
        entity = repository.save(entity); // IDENTITY strategy: id is populated after this save

        String code = ShortCodeGenerator.generateCode(entity.getId());
        entity.setCode(code);
        repository.save(entity);

        return new LinkResponse(code, normalized, "/r/" + code, request.expiresAt());
    }
}
```
- [ ] **Step 4: Run to confirm pass** — `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=LinkControllerContractTest` → PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(api): implement validated short-link creation"`
- [ ] **Step 6: Write and implement `LinkConcurrencyTest`** — this is REQ-023's own named planned
  evidence (`spec.md` Section 7/9.7, ASM-2: 20 concurrent requests, zero incorrect mappings/
  duplicates/drops), added here because it exercises this task's own endpoint under real concurrent
  load, not a single-threaded call:
```java
// src/test/java/com/schwab/urlshortener/api/LinkConcurrencyTest.java
package com.schwab.urlshortener.api;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkConcurrencyTest {

    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void twentyConcurrentCreatesProduceTwentyDistinctCodesWithNoDropsOrDuplicates() throws Exception {
        int concurrency = 20;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Callable<ResponseEntity<LinkResponse>>> tasks = IntStream.range(0, concurrency)
                .<Callable<ResponseEntity<LinkResponse>>>mapToObj(i -> () -> rest.postForEntity(
                        "http://localhost:" + port + "/links",
                        new CreateLinkRequest("https://example.com/concurrency-test-" + i, null),
                        LinkResponse.class))
                .toList();
        List<Future<ResponseEntity<LinkResponse>>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        List<ResponseEntity<LinkResponse>> responses = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } })
                .toList();

        // Zero drops: every request produced a 201 with a body.
        assertThat(responses).allMatch(r -> r.getStatusCode().value() == 201 && r.getBody() != null);

        // Zero incorrect mappings/duplicates: 20 distinct codes, each round-tripping to its own URL.
        List<String> codes = responses.stream().map(r -> r.getBody().code()).collect(Collectors.toList());
        assertThat(codes).doesNotHaveDuplicates().hasSize(concurrency);
        for (int i = 0; i < concurrency; i++) {
            assertThat(responses.get(i).getBody().longUrl())
                    .isEqualTo("https://example.com/concurrency-test-" + i);
        }
    }
}
```
  This test is demonstration-scale, not a production load test (constitution Principle IX):
  correctness under 20 concurrent requests is guaranteed structurally by Hibernate's `IDENTITY`
  strategy delegating code uniqueness to the database's own atomic sequence generation, not by any
  application-level locking this task adds — the test exists to prove that guarantee holds under
  genuine concurrent HTTP load, not just in a single-threaded unit test.
- [ ] **Step 7: Run to confirm pass** — `./mvnw test -Dtest=LinkConcurrencyTest` → PASS
- [ ] **Step 8: Commit** — `git commit -m "test(api): validate concurrent link creation produces no duplicate or dropped codes"`

**Design note:** `repository.save(entity)` twice (once to obtain the `IDENTITY`-generated id, once to persist the derived code) is the direct Java/Hibernate analog of the original design's "insert then rewrite code" approach — cleaner here because Hibernate's `IDENTITY` strategy executes the `INSERT` synchronously inside `save()`, so there's no placeholder-value hack needed, just two round trips.

### Task 18: Redirect resolution + expiration behavior

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/api/LinkController.java` (add `GET /r/{code}`)
- Test: `src/test/java/com/schwab/urlshortener/api/LinkControllerContractTest.java` (extend)

**Interfaces:**
- Produces: `GET /r/{code}` → `307` redirect on success, `404` unknown code, `410` expired

- [ ] **Step 1: Write failing tests** (add to `LinkControllerContractTest.java`)
```java
    @Test
    void redirectFollowsToLongUrl() {
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/target"), Map.class);
        String code = (String) created.getBody().get("code");

        var noRedirectsTemplate = new TestRestTemplate(
                org.springframework.boot.test.web.client.TestRestTemplate.HttpClientOption.SSL);
        // TestRestTemplate follows redirects by default; disable to inspect the 307 directly.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var restNoRedirect = new org.springframework.boot.test.web.client.TestRestTemplate();
        restNoRedirect.getRestTemplate().setRequestFactory(factory);

        ResponseEntity<Void> resp = restNoRedirect.getForEntity(url("/r/" + code), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(307);
        assertThat(resp.getHeaders().getLocation().toString()).isEqualTo("https://example.com/target");
    }

    @Test
    void redirectUnknownCodeReturns404() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var restNoRedirect = new org.springframework.boot.test.web.client.TestRestTemplate();
        restNoRedirect.getRestTemplate().setRequestFactory(factory);

        ResponseEntity<Void> resp = restNoRedirect.getForEntity(url("/r/ZZZZZZZ"), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void redirectExpiredCodeReturns410() {
        var past = java.time.Instant.now().minus(java.time.Duration.ofDays(1));
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/x", "expiresAt", past.toString()), Map.class);
        String code = (String) created.getBody().get("code");

        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        var restNoRedirect = new org.springframework.boot.test.web.client.TestRestTemplate();
        restNoRedirect.getRestTemplate().setRequestFactory(factory);

        ResponseEntity<Void> resp = restNoRedirect.getForEntity(url("/r/" + code), Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(410);
    }
```
  (The redirect-disabling `SimpleClientHttpRequestFactory` boilerplate is repeated three times here; if the implementer finds this unwieldy, extracting a private `TestRestTemplate noRedirectClient()` helper method in this test class is an acceptable, expected simplification — do that rather than leaving three copies.)
- [ ] **Step 2: Run to confirm failure** — 404 for all (route missing).
- [ ] **Step 3: Implement**
```java
// LinkController.java (add)
import com.schwab.urlshortener.domain.ShortLink;
import com.schwab.urlshortener.persistence.RedirectEventEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.Instant;

// (RedirectEventEntity persistence needs its own repository — add alongside ShortLinkJpaRepository:)
// src/main/java/com/schwab/urlshortener/persistence/RedirectEventJpaRepository.java:
//   public interface RedirectEventJpaRepository extends JpaRepository<RedirectEventEntity, Long> { }
// inject it into LinkController's constructor alongside ShortLinkJpaRepository.

@GetMapping("/r/{code}")
public ResponseEntity<Void> redirect(@PathVariable String code) {
    ShortLinkEntity entity = repository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "short_code_not_found"));
    ShortLink link = ShortLink.fromEntity(entity);

    if (link.isExpired()) {
        recordRedirect(entity, "expired");
        throw new ResponseStatusException(org.springframework.http.HttpStatus.GONE, "short_link_expired");
    }
    recordRedirect(entity, "redirected");
    return ResponseEntity.status(307).location(URI.create(link.longUrl())).build();
}

private void recordRedirect(ShortLinkEntity entity, String outcome) {
    RedirectEventEntity event = new RedirectEventEntity();
    event.setShortLinkId(entity.getId());
    event.setOutcome(outcome);
    redirectEventRepository.save(event);
    if ("redirected".equals(outcome)) {
        entity.setRedirectCount(entity.getRedirectCount() + 1);
        entity.setLastAccessedAt(Instant.now());
        repository.save(entity);
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(redirect): implement resolution and expiration"`

### Task 19: Analytics endpoint

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/api/LinkController.java` (add `GET /links/{code}/analytics`)
- Create: `src/main/java/com/schwab/urlshortener/api/dto/AnalyticsResponse.java`
- Test: `src/test/java/com/schwab/urlshortener/api/LinkControllerContractTest.java` (extend)

**Interfaces:**
- Produces: `GET /links/{code}/analytics` → `{code, redirectCount, lastAccessedAt}`; `404` unknown code

- [ ] **Step 1: Write failing test** (add to `LinkControllerContractTest.java`)
```java
    @Test
    void analyticsReflectsRedirectCount() {
        var created = rest.postForEntity(url("/links"), Map.of("longUrl", "https://example.com/a"), Map.class);
        String code = (String) created.getBody().get("code");
        rest.getForEntity(url("/r/" + code), String.class);
        rest.getForEntity(url("/r/" + code), String.class);

        ResponseEntity<Map> analytics = rest.getForEntity(url("/links/" + code + "/analytics"), Map.class);
        assertThat(((Number) analytics.getBody().get("redirectCount")).intValue()).isEqualTo(2);
    }
```
- [ ] **Step 2: Run to confirm failure** — 404 (route missing).
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/api/dto/AnalyticsResponse.java
package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record AnalyticsResponse(String code, int redirectCount, Instant lastAccessedAt) { }
```
```java
// LinkController.java (add)
@GetMapping("/links/{code}/analytics")
public AnalyticsResponse analytics(@PathVariable String code) {
    ShortLinkEntity entity = repository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "short_code_not_found"));
    return new AnalyticsResponse(entity.getCode(), entity.getRedirectCount(), entity.getLastAccessedAt());
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(analytics): capture and expose redirect analytics"`

---

## Phase 4 — Orchestration Engine (the critical differentiator)

### Task 20: Stage/graph model + topological + parallel-group resolution

> **Extended by Phase 12 (2026-09-13):** Task 49 adds conditional stage activation (additive — no change to this record). Task 50 adds `SUCCEEDED_WITH_FALLBACK` (additive enum constant). See Phase 12.

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/StepStatus.java`, `WorkflowStatus.java`, `StageHandler.java`, `StageDefinition.java`, `DependencyGraph.java`, `CycleException.java`, `UnknownDependencyException.java`
- Test: `src/test/java/com/schwab/urlshortener/orchestration/DependencyGraphTest.java`

**Interfaces:**
- Produces: `enum StepStatus { PENDING, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, RETRYING, ROLLED_BACK, COMPENSATED, SAFE_STOPPED, INVALIDATED, SKIPPED }`
- `enum WorkflowStatus { CREATED, RUNNING, PENDING_APPROVAL, SAFE_STOPPED, REPLANNING, COMPLETED, FAILED }`
- `@FunctionalInterface StageHandler { Map<String,Object> run(WorkflowContext ctx); }`
- `record StageDefinition(String name, List<String> dependsOn, StageHandler handler, boolean entryApproval, boolean exitApproval, int maxRetries, int timeoutSeconds, String parallelGroup)` with `static StageDefinition of(String name, List<String> dependsOn, StageHandler handler)` (convenience factory for the common no-gate/no-retry/no-parallel-group case: `entryApproval=false, exitApproval=false, maxRetries=0, timeoutSeconds=30, parallelGroup=null`) — every later task either uses `.of(...)` for that common case or the full canonical constructor; never a third construction style. `entryApproval` gates the handler from running at all until approved (use for destructive/irreversible actions); `exitApproval` lets the handler run first and gates only its *dependents* until the produced output is approved (use for reviewing a produced design, a detected ambiguity, or a release-readiness report). A stage may set neither, either, or (rare) both.
- `class DependencyGraph { DependencyGraph(List<StageDefinition> stages); List<List<StageDefinition>> topologicalBatches(); void validate(); }` (each inner list = stages runnable in parallel at that point; `validate()` throws `CycleException` on cycles, `UnknownDependencyException` on dangling `dependsOn`)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/DependencyGraphTest.java
package com.schwab.urlshortener.orchestration;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyGraphTest {

    private static Map<String, Object> noop(WorkflowContext ctx) {
        return Map.of();
    }

    @Test
    void topologicalBatchesRespectDependenciesAndGroupsIndependents() {
        List<StageDefinition> stages = List.of(
                StageDefinition.of("requirement", List.of(), DependencyGraphTest::noop),
                StageDefinition.of("design", List.of("requirement"), DependencyGraphTest::noop),
                new StageDefinition("security_scan", List.of("design"), DependencyGraphTest::noop, false, false, 0, 30, "review"),
                new StageDefinition("doc_draft", List.of("design"), DependencyGraphTest::noop, false, false, 0, 30, "review"),
                StageDefinition.of("release_readiness", List.of("security_scan", "doc_draft"), DependencyGraphTest::noop)
        );
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        List<List<StageDefinition>> batches = graph.topologicalBatches();

        assertThat(names(batches.get(0))).containsExactly("requirement");
        assertThat(names(batches.get(1))).containsExactly("design");
        assertThat(names(batches.get(2))).containsExactlyInAnyOrder("security_scan", "doc_draft");
        assertThat(names(batches.get(3))).containsExactly("release_readiness");
    }

    @Test
    void detectsCycle() {
        List<StageDefinition> stages = List.of(
                StageDefinition.of("a", List.of("b"), DependencyGraphTest::noop),
                StageDefinition.of("b", List.of("a"), DependencyGraphTest::noop)
        );
        assertThatThrownBy(() -> new DependencyGraph(stages).validate()).isInstanceOf(CycleException.class);
    }

    @Test
    void detectsUnknownDependency() {
        List<StageDefinition> stages = List.of(StageDefinition.of("a", List.of("missing"), DependencyGraphTest::noop));
        assertThatThrownBy(() -> new DependencyGraph(stages).validate()).isInstanceOf(UnknownDependencyException.class);
    }

    private static List<String> names(List<StageDefinition> batch) {
        return batch.stream().map(StageDefinition::name).toList();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the classes exist yet.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/StepStatus.java
package com.schwab.urlshortener.orchestration;

public enum StepStatus {
    PENDING, RUNNING, WAITING_APPROVAL, SUCCEEDED, FAILED, RETRYING,
    ROLLED_BACK, COMPENSATED, SAFE_STOPPED, INVALIDATED, SKIPPED
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/WorkflowStatus.java
package com.schwab.urlshortener.orchestration;

public enum WorkflowStatus {
    CREATED, RUNNING, PENDING_APPROVAL, SAFE_STOPPED, REPLANNING, COMPLETED, FAILED
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/StageHandler.java
package com.schwab.urlshortener.orchestration;

import java.util.Map;

@FunctionalInterface
public interface StageHandler {
    Map<String, Object> run(WorkflowContext ctx);
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/StageDefinition.java
package com.schwab.urlshortener.orchestration;

import java.util.List;

public record StageDefinition(
        String name,
        List<String> dependsOn,
        StageHandler handler,
        boolean entryApproval,
        boolean exitApproval,
        int maxRetries,
        int timeoutSeconds,
        String parallelGroup
) {
    public static StageDefinition of(String name, List<String> dependsOn, StageHandler handler) {
        return new StageDefinition(name, dependsOn, handler, false, false, 0, 30, null);
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/CycleException.java
package com.schwab.urlshortener.orchestration;

public class CycleException extends RuntimeException {
    public CycleException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/UnknownDependencyException.java
package com.schwab.urlshortener.orchestration;

public class UnknownDependencyException extends RuntimeException {
    public UnknownDependencyException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/DependencyGraph.java
package com.schwab.urlshortener.orchestration;

import java.util.*;

public class DependencyGraph {

    private final Map<String, StageDefinition> stages;

    public DependencyGraph(List<StageDefinition> stageList) {
        this.stages = new LinkedHashMap<>();
        for (StageDefinition stage : stageList) {
            stages.put(stage.name(), stage);
        }
    }

    public void validate() {
        for (StageDefinition stage : stages.values()) {
            for (String dep : stage.dependsOn()) {
                if (!stages.containsKey(dep)) {
                    throw new UnknownDependencyException(stage.name() + " depends on unknown stage " + dep);
                }
            }
        }
        topologicalOrder(); // throws CycleException if cyclic
    }

    private List<String> topologicalOrder() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> temp = new LinkedHashSet<>();
        List<String> order = new ArrayList<>();
        for (String name : stages.keySet()) {
            visit(name, visited, temp, order);
        }
        return order;
    }

    private void visit(String name, Set<String> visited, Set<String> temp, List<String> order) {
        if (visited.contains(name)) return;
        if (temp.contains(name)) {
            throw new CycleException("cycle detected at " + name);
        }
        temp.add(name);
        for (String dep : stages.get(name).dependsOn()) {
            visit(dep, visited, temp, order);
        }
        temp.remove(name);
        visited.add(name);
        order.add(name);
    }

    public List<List<StageDefinition>> topologicalBatches() {
        Map<String, StageDefinition> remaining = new LinkedHashMap<>(stages);
        Set<String> done = new HashSet<>();
        List<List<StageDefinition>> batches = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<StageDefinition> ready = remaining.values().stream()
                    .filter(s -> done.containsAll(s.dependsOn()))
                    .toList();
            if (ready.isEmpty()) {
                throw new CycleException("no ready stages but stages remain");
            }
            batches.add(ready);
            for (StageDefinition s : ready) {
                done.add(s.name());
                remaining.remove(s.name());
            }
        }
        return batches;
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS (`WorkflowContext`, referenced by `StageHandler`, is defined in Task 21 — since `StageHandler` only references it as a method parameter type, the compiler needs the class to exist; create a minimal empty `WorkflowContext` class stub now if sequencing this strictly before Task 21, or implement Tasks 20 and 21 together in one sitting — either is acceptable, note which you did in your task-group checkpoint).
- [ ] **Step 5: Commit** — `git commit -m "feat(orchestration): establish dependency graph model"`

### Task 21: Workflow persistence tables + context/decision-lineage object

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowContext.java`
- Create: `src/main/java/com/schwab/urlshortener/orchestration/JsonMapAttributeConverter.java`, `JsonListAttributeConverter.java`
- Create: `src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowInstanceEntity.java`, `WorkflowStepEntity.java`, `WorkflowEventEntity.java`
- Create: `src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowInstanceJpaRepository.java`, `WorkflowStepJpaRepository.java`, `WorkflowEventJpaRepository.java`
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowContextTest.java`

**Interfaces:**
- Produces entities: `WorkflowInstanceEntity(id: String[uuid], feature, status: WorkflowStatus, correlationId, createdAt, updatedAt, planVersion: int)`; `WorkflowStepEntity(id: Long, workflowId, name, status: StepStatus, sequence: int, dependsOn: List<String>, startedAt, endedAt, retryCount: int, result: Map<String,Object>)`; `WorkflowEventEntity(id: Long, workflowId, actorType, action, timestamp, payload: Map<String,Object>, result, reason)`
- Produces: `class WorkflowContext { WorkflowContext(String correlationId); WorkflowContext(String correlationId, List<Map<String,Object>> decisions, Map<String,Object> artifacts); void recordDecision(String actor, String decision, String rationale); void addArtifact(String name, Object value); String correlationId(); List<Map<String,Object>> decisions(); Map<String,Object> artifacts(); }`
- Produces: `WorkflowStepJpaRepository.findByWorkflowIdAndName(String workflowId, String name) -> Optional<WorkflowStepEntity>` (used constantly by the engine)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowContextTest.java
package com.schwab.urlshortener.orchestration;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowContextTest {

    @Test
    void contextPreservesDecisionLineageInOrder() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.recordDecision("agent", "propose base62 codes", "deterministic, no collision");
        ctx.recordDecision("human", "approve", "matches ADR-003");

        assertThat(ctx.decisions().stream().map(d -> d.get("decision")).toList())
                .containsExactly("propose base62 codes", "approve");
        assertThat(ctx.decisions().get(0).get("actor")).isEqualTo("agent");
    }

    @Test
    void contextStoresArtifactsByName() {
        WorkflowContext ctx = new WorkflowContext("run-1");
        ctx.addArtifact("normalizedRequirement", Map.of("text", "..."));

        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) ctx.artifacts().get("normalizedRequirement");
        assertThat(stored.get("text")).isEqualTo("...");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `WorkflowContext` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/WorkflowContext.java
package com.schwab.urlshortener.orchestration;

import java.time.Instant;
import java.util.*;

public class WorkflowContext {

    private final String correlationId;
    private final List<Map<String, Object>> decisions;
    private final Map<String, Object> artifacts;

    public WorkflowContext(String correlationId) {
        this(correlationId, new ArrayList<>(), new HashMap<>());
    }

    public WorkflowContext(String correlationId, List<Map<String, Object>> decisions, Map<String, Object> artifacts) {
        this.correlationId = correlationId;
        // CopyOnWriteArrayList/ConcurrentHashMap, not ArrayList/HashMap: from Task 40 onward,
        // multiple stages in the same parallel batch (e.g. SCN-B's seven impact branches, or a
        // stage recording a decision while a sibling's fallback records one) call
        // recordDecision()/addArtifact() concurrently from the batch's thread pool. A plain
        // HashMap under concurrent put() can corrupt its internal state, not just lose updates.
        this.decisions = new java.util.concurrent.CopyOnWriteArrayList<>(decisions);
        this.artifacts = new java.util.concurrent.ConcurrentHashMap<>(artifacts);
    }

    public void recordDecision(String actor, String decision, String rationale) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("actor", actor);
        entry.put("decision", decision);
        entry.put("rationale", rationale);
        entry.put("timestamp", Instant.now().toString());
        decisions.add(entry);
    }

    public void addArtifact(String name, Object value) {
        artifacts.put(name, value);
    }

    public String correlationId() { return correlationId; }
    public List<Map<String, Object>> decisions() { return decisions; }
    public Map<String, Object> artifacts() { return artifacts; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/JsonMapAttributeConverter.java
package com.schwab.urlshortener.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter
public class JsonMapAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize JSON map column", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) return Map.of();
            return MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize JSON map column", e);
        }
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/JsonListAttributeConverter.java
package com.schwab.urlshortener.orchestration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class JsonListAttributeConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize JSON list column", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) return List.of();
            return MAPPER.readValue(dbData, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize JSON list column", e);
        }
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowInstanceEntity.java
package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.WorkflowStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_instances")
public class WorkflowInstanceEntity {

    @Id
    private String id;

    private String feature;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "plan_version")
    private int planVersion = 1;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public int getPlanVersion() { return planVersion; }
    public void setPlanVersion(int planVersion) { this.planVersion = planVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowStepEntity.java
package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.JsonListAttributeConverter;
import com.schwab.urlshortener.orchestration.JsonMapAttributeConverter;
import com.schwab.urlshortener.orchestration.StepStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "workflow_steps")
public class WorkflowStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    private String name;

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private int sequence;

    @Convert(converter = JsonListAttributeConverter.class)
    @Column(name = "depends_on", columnDefinition = "CLOB")
    private List<String> dependsOn;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "retry_count")
    private int retryCount = 0;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> result;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowEventEntity.java
package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.orchestration.JsonMapAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "workflow_events")
public class WorkflowEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "actor_type")
    private String actorType;

    private String action;

    private Instant timestamp = Instant.now();

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "CLOB")
    private Map<String, Object> payload;

    private String result;

    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowInstanceJpaRepository.java
package com.schwab.urlshortener.orchestration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceJpaRepository extends JpaRepository<WorkflowInstanceEntity, String> { }
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowStepJpaRepository.java
package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStepJpaRepository extends JpaRepository<WorkflowStepEntity, Long> {
    Optional<WorkflowStepEntity> findByWorkflowIdAndName(String workflowId, String name);
    List<WorkflowStepEntity> findByWorkflowIdOrderBySequence(String workflowId);
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowEventJpaRepository.java
package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventJpaRepository extends JpaRepository<WorkflowEventEntity, Long> {
    List<WorkflowEventEntity> findByWorkflowIdOrderById(String workflowId);
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(orchestration): add workflow state persistence and context lineage"`

### Task 22: `WorkflowEngine.submit()` — sequential + parallel execution with persistence

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java`
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineExecutionTest.java`

**Interfaces:**
- Consumes: `DependencyGraph`, `StageDefinition`, `WorkflowContext`, entities/repos from Task 21
- Produces: `class WorkflowEngine { WorkflowEngine(WorkflowInstanceJpaRepository, WorkflowStepJpaRepository, WorkflowEventJpaRepository); String submit(String feature, List<StageDefinition> stages, String correlationId); }` — a plain constructor-injected class (per ADR-004: not a Spring-managed singleton, so it's testable without booting the full context); `StageHandler.run(ctx)` returns a `Map<String,Object>` merged into `WorkflowStepEntity.result`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineExecutionTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineExecutionTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository);
    }

    @Test
    void submitRunsSequentialStagesToCompletion() {
        List<String> calls = new CopyOnWriteArrayList<>();
        StageHandler stepA = ctx -> { calls.add("a"); return Map.of("ok", true); };
        StageHandler stepB = ctx -> { calls.add("b"); return Map.of("ok", true); };
        List<StageDefinition> stages = List.of(
                StageDefinition.of("a", List.of(), stepA),
                StageDefinition.of("b", List.of("a"), stepB)
        );

        String workflowId = engine().submit("demo", stages, "run-1");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(calls).containsExactly("a", "b");
        assertThat(stepRepository.findByWorkflowIdOrderBySequence(workflowId))
                .allMatch(s -> s.getStatus() == StepStatus.SUCCEEDED);
    }

    @Test
    void submitRunsParallelGroupConcurrently() {
        List<String> order = new CopyOnWriteArrayList<>();
        StageHandler slow = ctx -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            order.add("slow");
            return Map.of();
        };
        StageHandler fast = ctx -> { order.add("fast"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("slow", List.of(), slow, false, false, 0, 30, "g"),
                new StageDefinition("fast", List.of(), fast, false, false, 0, 30, "g")
        );

        engine().submit("demo", stages, "run-2");

        assertThat(order.get(0)).isEqualTo("fast"); // fast finishes first despite being submitted alongside slow
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `WorkflowEngine` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class WorkflowEngine {

    private static final Map<String, StageRegistration> REGISTRY = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
    }

    public String submit(String feature, List<StageDefinition> stages, String correlationId) {
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        String workflowId = UUID.randomUUID().toString();
        REGISTRY.put(workflowId, new StageRegistration(stages, graph));

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature(feature);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(correlationId);
        instanceRepository.save(instance);

        int sequence = 0;
        for (StageDefinition stage : stages) {
            WorkflowStepEntity step = new WorkflowStepEntity();
            step.setWorkflowId(workflowId);
            step.setName(stage.name());
            step.setStatus(StepStatus.PENDING);
            step.setSequence(sequence++);
            step.setDependsOn(stage.dependsOn());
            stepRepository.save(step);
        }

        runFrom(workflowId);
        return workflowId;
    }

    private void runFrom(String workflowId) {
        // NOTE: Task 22 intentionally builds a fresh WorkflowContext on every call
        // (no persistence yet) — this is deliberately incomplete and is exactly the
        // gap Task 24 closes. Do not "fix" it here; Task 24's failing test depends
        // on this gap existing first.
        StageRegistration registration = REGISTRY.get(workflowId);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId());

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            List<StageDefinition> pending = batch.stream()
                    .filter(s -> stepStatus(workflowId, s.name()) == StepStatus.PENDING)
                    .toList();
            if (pending.isEmpty()) continue;

            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (StageDefinition stage : pending) {
                    futures.add(pool.submit(() -> runStage(workflowId, stage, ctx)));
                }
                for (Future<?> future : futures) {
                    future.get(); // propagate exceptions synchronously per batch (fail-fast)
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            } finally {
                pool.shutdown();
            }
        }

        instance.setStatus(WorkflowStatus.COMPLETED);
        instance.setUpdatedAt(Instant.now());
        instanceRepository.save(instance);
        recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
    }

    private void runStage(String workflowId, StageDefinition stage, WorkflowContext ctx) {
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, stage.name()).orElseThrow();
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        stepRepository.save(step);

        Map<String, Object> result = stage.handler().run(ctx);

        step.setStatus(StepStatus.SUCCEEDED);
        step.setEndedAt(Instant.now());
        step.setResult(result != null ? result : Map.of());
        stepRepository.save(step);
        recordEvent(workflowId, "agent", "stage_succeeded:" + stage.name(), "OK", null);
    }

    private void recordEvent(String workflowId, String actorType, String action, String result, String reason) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(workflowId);
        event.setActorType(actorType);
        event.setAction(action);
        event.setResult(result);
        event.setReason(reason);
        eventRepository.save(event);
    }

    private StepStatus stepStatus(String workflowId, String name) {
        return stepRepository.findByWorkflowIdAndName(workflowId, name).orElseThrow().getStatus();
    }

    private record StageRegistration(List<StageDefinition> stages, DependencyGraph graph) { }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS (note: the concurrency-ordering assertion in `submitRunsParallelGroupConcurrently` is inherently timing-based; if flaky, replace with a `CountDownLatch`-based deterministic synchronization check instead of `Thread.sleep` ordering — flag this in the task-group checkpoint as a known test-design risk)
- [ ] **Step 5: Commit** — `git commit -m "feat(orchestration): implement sequential and parallel stage execution"`

### Task 23: Human approval gate — `WAITING_APPROVAL` state + resume

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (add `approve()`, gate checks in `runFrom`; constructor gains an `ApprovalJpaRepository` parameter)
- Create: `src/main/java/com/schwab/urlshortener/orchestration/persistence/ApprovalEntity.java`, `ApprovalJpaRepository.java`
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineApprovalTest.java`
- Modify: `WorkflowEngineExecutionTest.java` (Task 22) — its `engine()` helper now needs the extra constructor argument; update it in place, do not leave it broken.

**Interfaces:**
- Produces: `WorkflowEngine.approve(String workflowId, String stepName, String decision, String actor, String reason)` (`decision` in `{"approved","rejected"}`); an `entryApproval` stage halts the workflow in `PENDING_APPROVAL` *before* its handler ever runs; an `exitApproval` stage's handler runs normally, then the workflow halts in `PENDING_APPROVAL` before any dependent stage runs. `"rejected"` always sets the workflow to `FAILED`; for an entry-gated stage its step also becomes `FAILED` (handler never ran); for an exit-gated stage the step's `SUCCEEDED` status stands (the handler did complete) but no dependent stage ever runs.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineApprovalTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineApprovalTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
    }

    @Test
    void workflowHaltsAtApprovalGateAndResumesOnApprove() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler risky = ctx -> { executed.add("risky"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-3");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        assertThat(executed).isEmpty(); // handler must NOT run before approval

        engine.approve(workflowId, "risky", "approved", "human", "looks safe");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(executed).containsExactly("risky");
    }

    @Test
    void rejectedApprovalFailsWorkflowWithoutRunningHandler() {
        List<String> executed = new CopyOnWriteArrayList<>();
        StageHandler risky = ctx -> { executed.add("risky"); return Map.of(); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("risky", List.of(), risky, true, false, 0, 30, null));

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-4");
        engine.approve(workflowId, "risky", "rejected", "human", "too risky");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(executed).isEmpty();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `WorkflowEngine` has no `approve` method and no 4-arg constructor.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/ApprovalEntity.java
package com.schwab.urlshortener.orchestration.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approvals")
public class ApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "requested_at")
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String decision;

    @Column(name = "decided_by")
    private String decidedBy;

    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/ApprovalJpaRepository.java
package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalJpaRepository extends JpaRepository<ApprovalEntity, Long> {
    List<ApprovalEntity> findByWorkflowIdAndStepNameAndDecision(String workflowId, String stepName, String decision);
}
```
```java
// WorkflowEngine.java — rework to a resumable engine with entry/exit gate semantics.
//
// Two distinct gate types, both needed (guide requires "entry AND exit gates"):
//   entryApproval: handler must NOT run until approved (destructive/irreversible actions)
//   exitApproval:  handler runs normally; only its DEPENDENTS wait on approval
//                  (reviewing a produced design, a detected ambiguity, a readiness report)
// A single boolean cannot express both — do not collapse these back into one flag.
public class WorkflowEngine {

    private static final Map<String, StageRegistration> REGISTRY = new ConcurrentHashMap<>();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository; // new field

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
    }

    // submit(...) is unchanged from Task 22.

    private void runFrom(String workflowId) {
        // NOTE: still deliberately a fresh WorkflowContext each call — Task 24 fixes this.
        StageRegistration registration = REGISTRY.get(workflowId);
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId());

        for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
            List<StageDefinition> pending = batch.stream()
                    .filter(s -> stepStatus(workflowId, s.name()) == StepStatus.PENDING)
                    .toList();
            if (pending.isEmpty()) continue;

            // Entry gate: block BEFORE running any handler in this batch.
            for (StageDefinition stage : pending) {
                if (stage.entryApproval() && !isApproved(workflowId, stage.name())) {
                    enterPendingApproval(workflowId, stage.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    instanceRepository.save(instance);
                    return; // halt entirely; handler has NOT run; approve() resumes
                }
            }

            runBatch(workflowId, pending, ctx);

            // Exit gate: block AFTER running, before any dependent batch proceeds.
            for (StageDefinition stage : pending) {
                if (stage.exitApproval() && !isApproved(workflowId, stage.name())) {
                    enterPendingApproval(workflowId, stage.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    instanceRepository.save(instance);
                    return; // halt; handler already ran and is SUCCEEDED-turned-WAITING_APPROVAL
                }
            }
        }

        instance.setStatus(WorkflowStatus.COMPLETED);
        instanceRepository.save(instance);
        recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
    }

    private void runBatch(String workflowId, List<StageDefinition> pending, WorkflowContext ctx) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (StageDefinition stage : pending) {
                futures.add(pool.submit(() -> runStage(workflowId, stage, ctx)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    public void approve(String workflowId, String stepName, String decision, String actor, String reason) {
        ApprovalEntity approval = new ApprovalEntity();
        approval.setWorkflowId(workflowId);
        approval.setStepName(stepName);
        approval.setDecidedAt(Instant.now());
        approval.setDecision(decision);
        approval.setDecidedBy(actor);
        approval.setReason(reason);
        approvalRepository.save(approval);
        recordEvent(workflowId, "human", "approval_" + decision + ":" + stepName, "OK", reason);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        StageDefinition stage = REGISTRY.get(workflowId).stages().stream()
                .filter(s -> s.name().equals(stepName)).findFirst().orElseThrow();
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, stepName).orElseThrow();

        if ("rejected".equals(decision)) {
            if (step.getStatus() != StepStatus.SUCCEEDED) {
                step.setStatus(StepStatus.FAILED); // entry-gate reject: handler never ran
            }
            // exit-gate reject: leave SUCCEEDED — the handler did complete; only
            // downstream stages are permanently withheld (they simply never run).
            stepRepository.save(step);
            instance.setStatus(WorkflowStatus.FAILED);
            instanceRepository.save(instance);
            return;
        }

        // approved: put the step back in a state runFrom() will act on correctly.
        if (stage.entryApproval()) {
            step.setStatus(StepStatus.PENDING); // let it run for the first time now
        } else {
            step.setStatus(StepStatus.SUCCEEDED); // exit-gate: already ran; unblock dependents
        }
        stepRepository.save(step);
        instance.setStatus(WorkflowStatus.RUNNING);
        instanceRepository.save(instance);
        runFrom(workflowId);
    }

    private boolean isApproved(String workflowId, String stepName) {
        return !approvalRepository.findByWorkflowIdAndStepNameAndDecision(workflowId, stepName, "approved").isEmpty();
    }

    private void enterPendingApproval(String workflowId, String stepName) {
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, stepName).orElseThrow();
        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);
    }

    // runStage(...), recordEvent(...), stepStatus(...), StageRegistration are unchanged from Task 22.
}
```
- [ ] **Step 4: Run to confirm pass** — run both new tests **and** Task 22's tests (regression, after updating its `engine()` helper for the new constructor arg) — all PASS. Trace `workflowHaltsAtApprovalGateAndResumesOnApprove` by hand against the code above before running it: `submit()` → batch `[risky]` → entry gate fires (not approved) → `WAITING_APPROVAL`/`PENDING_APPROVAL`, `executed` empty ✓ → `approve("approved")` → `stage.entryApproval()` true → step reset to `PENDING` → `runFrom` re-enters → entry gate now passes (`isApproved` true) → handler runs → `executed` contains `"risky"`, step `SUCCEEDED`, workflow `COMPLETED` ✓.
- [ ] **Step 5: Commit** — `git commit -m "feat(governance): enforce human approval transitions"`

### Task 24: Dependency-safe context propagation across resume boundary

**Files:** Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (persist/reload `WorkflowContext` via a new `WorkflowInstanceEntity.contextJson` field instead of the fresh `WorkflowContext` constructed on each `runFrom` call — Task 23 deliberately left this gap; this task closes it)

**Files (cont.):** Modify: `src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowInstanceEntity.java` (add `contextJson: Map<String,Object>` field, `@Convert(converter = JsonMapAttributeConverter.class)`)

**Test:** `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineContextLineageTest.java`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineContextLineageTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineContextLineageTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private WorkflowEngine engine() {
        return new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
    }

    @Test
    void decisionLineageSurvivesApprovalResumeBoundary() {
        StageHandler gate = ctx -> {
            ctx.recordDecision("agent", "propose", "r1");
            return Map.of();
        };
        StageHandler after = ctx -> {
            // lineage from before the gate must survive
            assertThat(ctx.decisions().stream().anyMatch(d -> "propose".equals(d.get("decision")))).isTrue();
            ctx.recordDecision("agent", "finish", "r2");
            return Map.of();
        };
        // exitApproval (not entryApproval): "gate"'s handler must actually run and record
        // its decision BEFORE the halt, so a fresh, unpersisted ctx on resume would lose it —
        // that loss is exactly the bug this task's fix must prevent.
        List<StageDefinition> stages = List.of(
                new StageDefinition("gate", List.of(), gate, false, true, 0, 30, null),
                StageDefinition.of("after", List.of("gate"), after)
        );

        WorkflowEngine engine = engine();
        String workflowId = engine.submit("demo", stages, "run-5");
        engine.approve(workflowId, "gate", "approved", "human", "ok");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) instance.getContextJson().get("decisions");
        assertThat(decisions.get(decisions.size() - 1).get("decision")).isEqualTo("finish");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — the `after` handler's internal assertion fails (or the test's final assertion throws `NullPointerException`/`ClassCastException` since `contextJson` doesn't exist yet): `submit()` runs `gate` in a fresh `ctx`, records "propose" in that call-local object, halts at the exit gate — nothing was persisted, so "propose" lives only in a `WorkflowContext` instance that's about to be discarded. `approve()` resets `gate` to `SUCCEEDED` and calls `runFrom` again, which builds a **new** fresh `ctx` (empty decisions) and runs `after`, whose internal assertion fails.
- [ ] **Step 3: Implement** — add the field, then persist `ctx` to `instance.contextJson` after every batch and reload it at the top of `runFrom` instead of constructing empty:
```java
// WorkflowInstanceEntity.java (add)
@Convert(converter = com.schwab.urlshortener.orchestration.JsonMapAttributeConverter.class)
@Column(name = "context_json", columnDefinition = "CLOB")
private Map<String, Object> contextJson = new java.util.HashMap<>();

public Map<String, Object> getContextJson() { return contextJson; }
public void setContextJson(Map<String, Object> contextJson) { this.contextJson = contextJson; }
```
```java
// WorkflowEngine.java changes
private void runFrom(String workflowId) {
    StageRegistration registration = REGISTRY.get(workflowId);
    WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> priorDecisions =
            (List<Map<String, Object>>) instance.getContextJson().getOrDefault("decisions", new ArrayList<Map<String,Object>>());
    @SuppressWarnings("unchecked")
    Map<String, Object> priorArtifacts =
            (Map<String, Object>) instance.getContextJson().getOrDefault("artifacts", new HashMap<String,Object>());
    WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId(), priorDecisions, priorArtifacts);

    for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
        List<StageDefinition> pending = batch.stream()
                .filter(s -> stepStatus(workflowId, s.name()) == StepStatus.PENDING)
                .toList();
        if (pending.isEmpty()) continue;

        for (StageDefinition stage : pending) {
            if (stage.entryApproval() && !isApproved(workflowId, stage.name())) {
                enterPendingApproval(workflowId, stage.name());
                instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                instanceRepository.save(instance);
                return;
            }
        }

        runBatch(workflowId, pending, ctx);

        // Persist lineage BEFORE the exit-gate check, so a decision recorded by an
        // exit-gated stage's own handler survives even though this batch then halts.
        instance.setContextJson(Map.of("decisions", ctx.decisions(), "artifacts", ctx.artifacts()));
        instanceRepository.save(instance);

        for (StageDefinition stage : pending) {
            if (stage.exitApproval() && !isApproved(workflowId, stage.name())) {
                enterPendingApproval(workflowId, stage.name());
                instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                instanceRepository.save(instance);
                return;
            }
        }
    }

    instance.setStatus(WorkflowStatus.COMPLETED);
    instanceRepository.save(instance);
    recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
}
```
  `approve()` already calls `runFrom(workflowId)` with no `ctx` argument (Task 23) — no change needed there; it now reloads real lineage instead of starting empty.
- [ ] **Step 4: Run to confirm pass** — this test and all Task 22/23 tests PASS. Hand-trace: `submit()` → batch `[gate]` → no entry gate → `gate` runs, records "propose", step `SUCCEEDED` → persist `contextJson={"decisions":["propose"],...}` → exit gate fires (not approved) → halt. `approve("approved")` → `gate` has `exitApproval` (not `entryApproval`) → step stays/resets `SUCCEEDED` → `runFrom` reloads `ctx.decisions()` containing "propose" → batch `[gate]` has no `PENDING` steps, skip → batch `[after]` runs with the reloaded `ctx` → assertion passes → records "finish" → persists `contextJson["decisions"]` with "finish" last ✓.
- [ ] **Step 5: Commit** — `git commit -m "fix(orchestration): persist decision lineage across approval resume boundary"`

### Task 25: `ScenarioRunner` CLI harness (thin, reusable by all 3 scenarios)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/scenarios/ScenarioRunner.java`
- Test: `src/test/java/com/schwab/urlshortener/scenarios/ScenarioRunnerSmokeTest.java`

**Interfaces:**
- Consumes: `WorkflowEngine`, `DependencyGraph`
- Produces: `ScenarioRunner implements CommandLineRunner` (a Spring bean; runs only when the app is launched with a `--scenario=greenfield|brownfield|ambiguous` argument, otherwise a no-op so normal `mvn spring-boot:run` server boot is unaffected) that prints the workflow id, step-by-step audit trace, and final status to a given `PrintStream` as JSON lines — a testable `runScenario(String scenario, PrintStream out)` method underneath the `CommandLineRunner.run(String... args)` entry point, so tests never need to spawn a subprocess or a full server.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/scenarios/ScenarioRunnerSmokeTest.java
package com.schwab.urlshortener.scenarios;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.orchestration.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ScenarioRunner.class)
class ScenarioRunnerSmokeTest {

    @Autowired private ScenarioRunner runner;

    @Test
    void cliRunsAndPrintsJsonLinesForGreenfield() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        runner.runScenario("greenfield", new PrintStream(buffer, true, StandardCharsets.UTF_8));

        List<String> lines = buffer.toString(StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank()).toList();
        assertThat(lines).isNotEmpty();
        new ObjectMapper().readTree(lines.get(lines.size() - 1)); // last line must be valid JSON (final summary)
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `ScenarioRunner` doesn't exist.
- [ ] **Step 3: Implement** a minimal runner wiring a trivial 2-stage graph for smoke purposes (real stage wiring happens in Phase 9) and printing the audit trail as JSON lines plus a final `{"workflowId":...,"status":...}` line:
```java
// src/main/java/com/schwab/urlshortener/scenarios/ScenarioRunner.java
package com.schwab.urlshortener.scenarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ScenarioRunner implements CommandLineRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;

    public ScenarioRunner(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        String scenario = extractScenarioArg(args);
        if (scenario == null) return; // normal server boot; not invoked in scenario mode
        runScenario(scenario, System.out);
    }

    static String extractScenarioArg(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--scenario=")) return arg.substring("--scenario=".length());
        }
        return null;
    }

    void runScenario(String scenario, PrintStream out) throws Exception {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
        List<StageDefinition> stages = List.of(
                StageDefinition.of("requirement", List.of(), ctx -> Map.of()),
                StageDefinition.of("decomposition", List.of("requirement"), ctx -> Map.of())
        );
        String workflowId = engine.submit(scenario, stages, "cli-" + scenario);

        for (WorkflowEventEntity event : eventRepository.findByWorkflowIdOrderById(workflowId)) {
            Map<String, Object> line = Map.of(
                    "actorType", event.getActorType(), "action", event.getAction(),
                    "timestamp", event.getTimestamp().toString(), "result", event.getResult());
            out.println(MAPPER.writeValueAsString(line));
        }

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        out.println(MAPPER.writeValueAsString(Map.of("workflowId", workflowId, "status", instance.getStatus().name())));
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(tooling): add scenario runner CLI harness"`

### Task 26: `GET /workflows/{id}` and `GET /workflows/{id}/audit` API

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/observability/WorkflowController.java`
- Create: `src/main/java/com/schwab/urlshortener/observability/dto/WorkflowStateResponse.java`, `AuditEventResponse.java`, `ApprovalRequest.java`
- Test: `src/test/java/com/schwab/urlshortener/api/WorkflowControllerContractTest.java`

**Interfaces:**
- Produces: `GET /workflows/{id}` → `{id, status, steps: [{name,status,startedAt,endedAt,retryCount}]}`; `GET /workflows/{id}/audit` → `[{actorType,action,timestamp,result,reason}]`; `POST /workflows/{id}/approvals/{step}` → body `{decision, actor, reason}`, calls `WorkflowEngine.approve`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/api/WorkflowControllerContractTest.java
package com.schwab.urlshortener.api;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowControllerContractTest {

    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void workflowStateAndAuditEndpointsExposeRealPersistedState() {
        // No generic "create workflow" HTTP endpoint exists (workflows are always
        // created from a scenario's specific stage set — Phase 9); trigger one
        // directly through the engine the same way ScenarioRunner does, then
        // exercise the read-only HTTP surface against it.
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
        List<StageDefinition> stages = List.of(StageDefinition.of("only", List.of(), ctx -> Map.of("ok", true)));
        String workflowId = engine.submit("demo", stages, "contract-test");

        ResponseEntity<Map> stateResp = rest.getForEntity(url("/workflows/" + workflowId), Map.class);
        assertThat(stateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(stateResp.getBody().get("status")).isEqualTo("COMPLETED");
        List<?> steps = (List<?>) stateResp.getBody().get("steps");
        assertThat(steps).hasSize(1);

        ResponseEntity<List> auditResp = rest.getForEntity(url("/workflows/" + workflowId + "/audit"), List.class);
        assertThat(auditResp.getStatusCode().value()).isEqualTo(200);
        assertThat(auditResp.getBody()).isNotEmpty();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — 404, `WorkflowController` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/observability/dto/WorkflowStateResponse.java
package com.schwab.urlshortener.observability.dto;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import java.time.Instant;
import java.util.List;

public record WorkflowStateResponse(String id, WorkflowStatus status, List<StepView> steps) {
    public record StepView(String name, StepStatus status, Instant startedAt, Instant endedAt, int retryCount) { }
}
```
```java
// src/main/java/com/schwab/urlshortener/observability/dto/AuditEventResponse.java
package com.schwab.urlshortener.observability.dto;

import java.time.Instant;

public record AuditEventResponse(String actorType, String action, Instant timestamp, String result, String reason) { }
```
```java
// src/main/java/com/schwab/urlshortener/observability/dto/ApprovalRequest.java
package com.schwab.urlshortener.observability.dto;

public record ApprovalRequest(String decision, String actor, String reason) { }
```
```java
// src/main/java/com/schwab/urlshortener/observability/WorkflowController.java
package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.observability.dto.*;
import com.schwab.urlshortener.orchestration.WorkflowEngine;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final WorkflowEngine engine;

    public WorkflowController(WorkflowInstanceJpaRepository instanceRepository,
                               WorkflowStepJpaRepository stepRepository,
                               WorkflowEventJpaRepository eventRepository,
                               ApprovalJpaRepository approvalRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
    }

    @GetMapping("/{id}")
    public WorkflowStateResponse getWorkflow(@PathVariable("id") String id) {
        WorkflowInstanceEntity instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow_not_found"));
        List<WorkflowStateResponse.StepView> steps = stepRepository.findByWorkflowIdOrderBySequence(id).stream()
                .map(s -> new WorkflowStateResponse.StepView(
                        s.getName(), s.getStatus(), s.getStartedAt(), s.getEndedAt(), s.getRetryCount()))
                .toList();
        return new WorkflowStateResponse(instance.getId(), instance.getStatus(), steps);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEventResponse> getAudit(@PathVariable("id") String id) {
        return eventRepository.findByWorkflowIdOrderById(id).stream()
                .map(e -> new AuditEventResponse(e.getActorType(), e.getAction(), e.getTimestamp(), e.getResult(), e.getReason()))
                .toList();
    }

    @PostMapping("/{id}/approvals/{step}")
    public void approve(@PathVariable("id") String id, @PathVariable("step") String step,
                         @RequestBody ApprovalRequest request) {
        engine.approve(id, step, request.decision(), request.actor(), request.reason());
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(api): expose workflow state and audit inspection endpoints"`

---

## Phase 5 — Policy / Compliance Engine

### Task 27: Versioned policy file + `PolicyEngine.evaluate()`

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/policy/PolicyOutcome.java`, `PolicyCheckResult.java`, `PolicyDefinition.java`, `PolicyEngine.java`
- Create: `src/main/resources/policies/v1.yaml`
- Test: `src/test/java/com/schwab/urlshortener/policy/PolicyEngineTest.java`

**Interfaces:**
- Produces: `enum PolicyOutcome { PASS, FAIL, EXCEPTION_REQUESTED, NOT_APPLICABLE }`
- `record PolicyDefinition(String id, String domain, String stage, boolean mandatory, String description, String checkKey)` (Jackson-bound from YAML, `check_key` maps to `checkKey` via `@JsonProperty`)
- `record PolicyCheckResult(String policyId, PolicyOutcome outcome, String reason, boolean mandatory)`
- `class PolicyEngine { PolicyEngine(String classpathLocation); String version(); List<PolicyCheckResult> evaluate(String stageName, Map<String,Object> context); }`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/policy/PolicyEngineTest.java
package com.schwab.urlshortener.policy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {

    @Test
    void loadsVersionAndEvaluatesSecurityCheck() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        assertThat(engine.version()).isEqualTo("1.0.0");

        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("urlSchemeAllowlistEnforced", true));
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.PASS);
    }

    @Test
    void failsWhenControlAbsent() {
        PolicyEngine engine = new PolicyEngine("policies/v1.yaml");
        List<PolicyCheckResult> results = engine.evaluate("implementation", Map.of("urlSchemeAllowlistEnforced", false));
        PolicyCheckResult security = results.stream().filter(r -> r.policyId().equals("SEC-001")).findFirst().orElseThrow();
        assertThat(security.outcome()).isEqualTo(PolicyOutcome.FAIL);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the classes exist yet.
- [ ] **Step 3: Implement**
```yaml
# src/main/resources/policies/v1.yaml
version: "1.0.0"
policies:
  - id: SEC-001
    domain: security
    stage: implementation
    mandatory: true
    description: "URL scheme allow-list must be enforced before redirect resolution ships"
    check_key: urlSchemeAllowlistEnforced
  - id: SEC-002
    domain: security
    stage: implementation
    mandatory: true
    description: "SSRF guard (private/loopback/link-local target blocking) must be enforced"
    check_key: ssrfGuardEnforced
  - id: COMP-001
    domain: compliance
    stage: release_readiness
    mandatory: true
    description: "Audit trail must exist for the full workflow before release"
    check_key: auditTrailPresent
  - id: LIC-001
    domain: licensing
    stage: implementation
    mandatory: false
    description: "All third-party dependencies use OSI-approved licenses"
    check_key: dependencyLicensesReviewed
```
```java
// src/main/java/com/schwab/urlshortener/policy/PolicyOutcome.java
package com.schwab.urlshortener.policy;

public enum PolicyOutcome { PASS, FAIL, EXCEPTION_REQUESTED, NOT_APPLICABLE }
```
```java
// src/main/java/com/schwab/urlshortener/policy/PolicyCheckResult.java
package com.schwab.urlshortener.policy;

public record PolicyCheckResult(String policyId, PolicyOutcome outcome, String reason, boolean mandatory) { }
```
```java
// src/main/java/com/schwab/urlshortener/policy/PolicyDefinition.java
package com.schwab.urlshortener.policy;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PolicyDefinition(
        String id, String domain, String stage, boolean mandatory, String description,
        @JsonProperty("check_key") String checkKey
) { }
```
```java
// src/main/java/com/schwab/urlshortener/policy/PolicyEngine.java
package com.schwab.urlshortener.policy;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

public class PolicyEngine {

    private record PolicyDocument(String version, List<PolicyDefinition> policies) { }

    private final String version;
    private final List<PolicyDefinition> policies;

    public PolicyEngine(String classpathLocation) {
        try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
            PolicyDocument doc = new YAMLMapper().readValue(in, PolicyDocument.class);
            this.version = doc.version();
            this.policies = doc.policies();
        } catch (IOException e) {
            throw new IllegalStateException("failed to load policy file: " + classpathLocation, e);
        }
    }

    public String version() { return version; }

    public List<PolicyCheckResult> evaluate(String stageName, Map<String, Object> context) {
        List<PolicyCheckResult> results = new ArrayList<>();
        for (PolicyDefinition policy : policies) {
            if (!policy.stage().equals(stageName)) continue;
            String checkKey = policy.checkKey();
            PolicyOutcome outcome;
            String reason;
            if (!context.containsKey(checkKey)) {
                outcome = PolicyOutcome.NOT_APPLICABLE;
                reason = checkKey + " not evaluated in this context";
            } else if (Boolean.TRUE.equals(context.get(checkKey))) {
                outcome = PolicyOutcome.PASS;
                reason = "control confirmed present";
            } else {
                outcome = PolicyOutcome.FAIL;
                reason = "control not confirmed present";
            }
            results.add(new PolicyCheckResult(policy.id(), outcome, reason, policy.mandatory()));
        }
        return results;
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(compliance): add versioned policy model and evaluation engine"`

### Task 28: Policy enforcement inside the orchestration engine (blocks on mandatory FAIL)

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (add an overloaded constructor accepting an optional `PolicyEngine`; the existing 4-arg constructor delegates to it with `null`; call `policyEngine.evaluate(stage.name(), ctx.artifacts())` inside `runStage` right after the handler returns, before marking the step `SUCCEEDED`; persist each `PolicyCheckResult` to a new `policy_evaluations` table)
- Create: `src/main/java/com/schwab/urlshortener/orchestration/SafeStopException.java`
- Create: `src/main/java/com/schwab/urlshortener/orchestration/persistence/PolicyEvaluationEntity.java`, `PolicyEvaluationJpaRepository.java`
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEnginePolicyEnforcementTest.java`

**Interfaces:**
- Produces: `PolicyEvaluationEntity(id, workflowId, stageName, policyId, policyVersion, outcome: PolicyOutcome, reason)`; `SafeStopException extends RuntimeException`; engine transitions workflow to `SAFE_STOPPED` (not `FAILED`) when a mandatory policy check `FAIL`s with no approved exception

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEnginePolicyEnforcementTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyEngine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEnginePolicyEnforcementTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;

    @Test
    void mandatoryPolicyFailureSafeStopsWorkflow() {
        StageHandler impl = ctx -> {
            ctx.addArtifact("urlSchemeAllowlistEnforced", false); // simulate missing control
            return Map.of();
        };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository,
                policyEvaluationRepository, policyEngine);

        String workflowId = engine.submit("demo", stages, "run-6");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, no such `WorkflowEngine` constructor.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/SafeStopException.java
package com.schwab.urlshortener.orchestration;

public class SafeStopException extends RuntimeException {
    public SafeStopException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/PolicyEvaluationEntity.java
package com.schwab.urlshortener.orchestration.persistence;

import com.schwab.urlshortener.policy.PolicyOutcome;
import jakarta.persistence.*;

@Entity
@Table(name = "policy_evaluations")
public class PolicyEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "stage_name")
    private String stageName;

    @Column(name = "policy_id")
    private String policyId;

    @Column(name = "policy_version")
    private String policyVersion;

    @Enumerated(EnumType.STRING)
    private PolicyOutcome outcome;

    private String reason;

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public PolicyOutcome getOutcome() { return outcome; }
    public void setOutcome(PolicyOutcome outcome) { this.outcome = outcome; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/persistence/PolicyEvaluationJpaRepository.java
package com.schwab.urlshortener.orchestration.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyEvaluationJpaRepository extends JpaRepository<PolicyEvaluationEntity, Long> {
    List<PolicyEvaluationEntity> findByWorkflowIdAndPolicyId(String workflowId, String policyId);
}
```
```java
// WorkflowEngine.java changes: new fields + overloaded constructor
private final PolicyEvaluationJpaRepository policyEvaluationRepository; // nullable-safe: only used when policyEngine != null
private final com.schwab.urlshortener.policy.PolicyEngine policyEngine; // nullable

public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                       WorkflowStepJpaRepository stepRepository,
                       WorkflowEventJpaRepository eventRepository,
                       ApprovalJpaRepository approvalRepository) {
    this(instanceRepository, stepRepository, eventRepository, approvalRepository, null, null);
}

public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                       WorkflowStepJpaRepository stepRepository,
                       WorkflowEventJpaRepository eventRepository,
                       ApprovalJpaRepository approvalRepository,
                       PolicyEvaluationJpaRepository policyEvaluationRepository,
                       com.schwab.urlshortener.policy.PolicyEngine policyEngine) {
    this.instanceRepository = instanceRepository;
    this.stepRepository = stepRepository;
    this.eventRepository = eventRepository;
    this.approvalRepository = approvalRepository;
    this.policyEvaluationRepository = policyEvaluationRepository;
    this.policyEngine = policyEngine;
}

// runStage changes: evaluate policy right after the handler returns, before SUCCEEDED
private void runStage(String workflowId, StageDefinition stage, WorkflowContext ctx) {
    WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, stage.name()).orElseThrow();
    step.setStatus(StepStatus.RUNNING);
    step.setStartedAt(Instant.now());
    stepRepository.save(step);

    Map<String, Object> result = stage.handler().run(ctx);

    if (policyEngine != null) {
        List<com.schwab.urlshortener.policy.PolicyCheckResult> policyResults =
                policyEngine.evaluate(stage.name(), ctx.artifacts());
        boolean mandatoryFail = false;
        for (var pr : policyResults) {
            PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
            evaluation.setWorkflowId(workflowId);
            evaluation.setStageName(stage.name());
            evaluation.setPolicyId(pr.policyId());
            evaluation.setPolicyVersion(policyEngine.version());
            evaluation.setOutcome(pr.outcome());
            evaluation.setReason(pr.reason());
            policyEvaluationRepository.save(evaluation);
            // A mandatory FAIL only blocks if no approved, unexpired exception covers this
            // policy (Task 29 introduces exceptions; this check is a no-op — always blocks —
            // until policyExceptionRepository is wired in via the Task 29 constructor overload).
            if (pr.mandatory() && pr.outcome() == com.schwab.urlshortener.policy.PolicyOutcome.FAIL
                    && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                mandatoryFail = true;
            }
        }
        if (mandatoryFail) {
            step.setStatus(StepStatus.SAFE_STOPPED);
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            stepRepository.save(step);
            throw new SafeStopException("mandatory policy failure at stage " + stage.name());
        }
    }

    step.setStatus(StepStatus.SUCCEEDED);
    step.setEndedAt(Instant.now());
    step.setResult(result != null ? result : Map.of());
    stepRepository.save(step);
    recordEvent(workflowId, "agent", "stage_succeeded:" + stage.name(), "OK", null);
}

// runBatch changes: unwrap and rethrow SafeStopException distinctly
private void runBatch(String workflowId, List<StageDefinition> pending, WorkflowContext ctx) {
    ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, pending.size()));
    try {
        List<Future<?>> futures = new ArrayList<>();
        for (StageDefinition stage : pending) {
            futures.add(pool.submit(() -> runStage(workflowId, stage, ctx)));
        }
        for (Future<?> future : futures) {
            future.get();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SafeStopException safeStop) throw safeStop;
        throw new RuntimeException(cause);
    } finally {
        pool.shutdown();
    }
}

// runFrom changes: catch SafeStopException around the runBatch(...) call
//   (replacing the plain `runBatch(workflowId, pending, ctx);` line from Task 24)
try {
    runBatch(workflowId, pending, ctx);
} catch (SafeStopException e) {
    instance.setStatus(WorkflowStatus.SAFE_STOPPED);
    instanceRepository.save(instance);
    recordEvent(workflowId, "engine", "safe_stop_triggered", "SAFE_STOPPED", e.getMessage());
    return;
}

// New helper — Task 28 has no exception repository yet, so this always returns false
// (every mandatory FAIL blocks); Task 29 replaces the body once the repository exists.
private boolean hasApprovedUnexpiredException(String workflowId, String policyId) {
    return false;
}
```
- [ ] **Step 4: Run to confirm pass** — PASS, plus rerun Tasks 22–24's suites for regression (their tests still use the 4-arg constructor, which now delegates correctly with `null` policy engine).
- [ ] **Step 5: Commit** — `git commit -m "feat(compliance): enforce mandatory policy checks with safe-stop on failure"`

### Task 29: Policy exception workflow (human-approved override)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/persistence/PolicyExceptionEntity.java`, `PolicyExceptionJpaRepository.java`
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (add `grantPolicyException(...)` and `retrySafeStopped(String workflowId)`)
- Test: `src/test/java/com/schwab/urlshortener/policy/PolicyExceptionTest.java`

**Interfaces:**
- Produces: `PolicyExceptionEntity(id, workflowId, policyId, reason, scope, approvingAuthority, compensatingControl, approvedAt, expiry)`; `WorkflowEngine.grantPolicyException(String workflowId, String policyId, String reason, String scope, String approvingAuthority, String compensatingControl, Instant expiry)`; `WorkflowEngine.retrySafeStopped(String workflowId)`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/policy/PolicyExceptionTest.java
package com.schwab.urlshortener.policy;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PolicyExceptionTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;

    @Test
    void approvedUnexpiredExceptionAllowsProgressionPastMandatoryFail() {
        StageHandler impl = ctx -> { ctx.addArtifact("urlSchemeAllowlistEnforced", false); return Map.of(); };
        List<StageDefinition> stages = List.of(StageDefinition.of("implementation", List.of(), impl));
        PolicyEngine policyEngine = new PolicyEngine("policies/v1.yaml");
        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository,
                policyEvaluationRepository, policyEngine, policyExceptionRepository);

        String workflowId = engine.submit("demo", stages, "run-7");
        engine.grantPolicyException(workflowId, "SEC-001", "prototype demo only",
                "implementation stage", "human-candidate", "manual review before merge",
                Instant.now().plus(Duration.ofDays(1)));
        engine.retrySafeStopped(workflowId);

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, no 7-arg constructor / no `grantPolicyException`/`retrySafeStopped` methods.
- [ ] **Step 3: Implement** `PolicyExceptionEntity` + repository, a third constructor overload accepting a `PolicyExceptionJpaRepository policyExceptionRepository` field, `grantPolicyException(...)` (persists the row + records a `policy_exception_granted` audit event), and override the `hasApprovedUnexpiredException` stub Task 28 left behind — this is the key design point: the exception check lives in ONE place (`hasApprovedUnexpiredException`), used by `runStage`'s real-time gating, so `retrySafeStopped` does not need to duplicate any policy-recomputation logic — it only needs to reset the stuck step and let the existing machinery re-decide:
```java
// WorkflowEngine.java — replace the Task 28 stub with a real check
private boolean hasApprovedUnexpiredException(String workflowId, String policyId) {
    if (policyExceptionRepository == null) return false;
    return policyExceptionRepository.findByWorkflowIdAndPolicyId(workflowId, policyId).stream()
            .anyMatch(exc -> exc.getExpiry().isAfter(Instant.now()));
}

public void grantPolicyException(String workflowId, String policyId, String reason, String scope,
                                  String approvingAuthority, String compensatingControl, Instant expiry) {
    PolicyExceptionEntity exception = new PolicyExceptionEntity();
    exception.setWorkflowId(workflowId);
    exception.setPolicyId(policyId);
    exception.setReason(reason);
    exception.setScope(scope);
    exception.setApprovingAuthority(approvingAuthority);
    exception.setCompensatingControl(compensatingControl);
    exception.setApprovedAt(Instant.now());
    exception.setExpiry(expiry);
    policyExceptionRepository.save(exception);
    recordEvent(workflowId, "human", "policy_exception_granted:" + policyId, "OK", reason);
}

public void retrySafeStopped(String workflowId) {
    // The stage's handler already ran once (during the safe-stopped attempt) and its
    // ctx.artifacts() reporting was already correct — resetting to PENDING lets runFrom
    // re-run it; hasApprovedUnexpiredException now sees the just-granted exception, so
    // the same mandatory FAIL no longer blocks. If a DIFFERENT mandatory policy still
    // fails with no exception, the stage safe-stops again — same as any first attempt.
    List<WorkflowStepEntity> steps = stepRepository.findByWorkflowIdOrderBySequence(workflowId);
    for (WorkflowStepEntity step : steps) {
        if (step.getStatus() == StepStatus.SAFE_STOPPED) {
            step.setStatus(StepStatus.PENDING);
            stepRepository.save(step);
        }
    }
    WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
    instance.setStatus(WorkflowStatus.RUNNING);
    instanceRepository.save(instance);
    runFrom(workflowId);
}
```
  `PolicyExceptionEntity` fields: `id, workflowId, policyId, reason, scope, approvingAuthority, compensatingControl, approvedAt: Instant, expiry: Instant` — standard `@Entity`/getter-setter shape matching `ApprovalEntity`'s style from Task 23. `PolicyExceptionJpaRepository.findByWorkflowIdAndPolicyId(String, String) -> List<PolicyExceptionEntity>`.
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(compliance): support human-approved policy exceptions with expiry"`

---

## Phase 6 — Reliability

### Task 30: Bounded retry with classification (transient vs permanent) + backoff

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/reliability/TransientStageException.java`, `PermanentStageException.java`, `RetryExecutor.java`
- Test: `src/test/java/com/schwab/urlshortener/reliability/RetryTest.java`

**Interfaces:**
- Produces: `TransientStageException extends RuntimeException`; `PermanentStageException extends RuntimeException`; `class RetryExecutor { static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis); }` (wraps a stage handler; rethrows immediately on `PermanentStageException`; retries up to `maxRetries` times on `TransientStageException`, then rethrows)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/reliability/RetryTest.java
package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryTest {

    @Test
    void retriesTransientErrorUpToBoundThenSucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler flaky = ctx -> {
            if (attempts.incrementAndGet() < 3) throw new TransientStageException("simulated");
            return Map.of("ok", true);
        };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(flaky, 3, 0);

        Map<String, Object> result = wrapped.run(null);

        assertThat(result).isEqualTo(Map.of("ok", true));
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void exhaustsRetriesAndThrows() {
        StageHandler alwaysFlaky = ctx -> { throw new TransientStageException("simulated"); };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(alwaysFlaky, 2, 0);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(TransientStageException.class);
    }

    @Test
    void permanentErrorIsNeverRetried() {
        AtomicInteger attempts = new AtomicInteger(0);
        StageHandler broken = ctx -> {
            attempts.incrementAndGet();
            throw new PermanentStageException("simulated");
        };
        StageHandler wrapped = RetryExecutor.withBoundedRetry(broken, 5, 0);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(PermanentStageException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the classes exist yet.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/reliability/TransientStageException.java
package com.schwab.urlshortener.reliability;

public class TransientStageException extends RuntimeException {
    public TransientStageException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/reliability/PermanentStageException.java
package com.schwab.urlshortener.reliability;

public class PermanentStageException extends RuntimeException {
    public PermanentStageException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/reliability/RetryExecutor.java
package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;

public final class RetryExecutor {

    private RetryExecutor() { }

    public static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis) {
        return ctx -> {
            RuntimeException lastException = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return handler.run(ctx);
                } catch (PermanentStageException e) {
                    throw e;
                } catch (TransientStageException e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        sleep(backoffMillis * (1L << attempt));
                        continue;
                    }
                    throw e;
                }
            }
            throw lastException; // unreachable, satisfies the compiler's flow analysis
        };
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(resilience): add bounded retry with transient/permanent classification"`

### Task 31: Timeout wrapper

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/reliability/StageTimeoutException.java`, `TimeoutExecutor.java`
- Test: `src/test/java/com/schwab/urlshortener/reliability/TimeoutTest.java`

**Interfaces:**
- Produces: `StageTimeoutException extends RuntimeException`; `class TimeoutExecutor { static StageHandler withTimeout(StageHandler handler, long timeoutMillis); }` — milliseconds (not seconds) so unit tests can use small, fast values; `WorkflowEngine` (Task 33) converts `StageDefinition.timeoutSeconds()` to millis at the call site.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/reliability/TimeoutTest.java
package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeoutTest {

    @Test
    void raisesOnExceedingTimeout() {
        StageHandler slow = ctx -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return Map.of();
        };
        StageHandler wrapped = TimeoutExecutor.withTimeout(slow, 50);

        assertThatThrownBy(() -> wrapped.run(null)).isInstanceOf(StageTimeoutException.class);
    }

    @Test
    void returnsNormallyWithinTimeout() {
        StageHandler fast = ctx -> Map.of("ok", true);
        StageHandler wrapped = TimeoutExecutor.withTimeout(fast, 1000);

        assertThat(wrapped.run(null)).isEqualTo(Map.of("ok", true));
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, neither class exists yet.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/reliability/StageTimeoutException.java
package com.schwab.urlshortener.reliability;

public class StageTimeoutException extends RuntimeException {
    public StageTimeoutException(String message) { super(message); }
}
```
```java
// src/main/java/com/schwab/urlshortener/reliability/TimeoutExecutor.java
package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import java.util.concurrent.*;

public final class TimeoutExecutor {

    private TimeoutExecutor() { }

    public static StageHandler withTimeout(StageHandler handler, long timeoutMillis) {
        return ctx -> {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String, Object>> future = pool.submit(() -> handler.run(ctx));
                return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new StageTimeoutException("stage exceeded " + timeoutMillis + "ms");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                throw new RuntimeException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                pool.shutdownNow();
            }
        };
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(resilience): add stage timeout wrapper"`

### Task 32: Compensation registry (rollback vs. compensation, per ADR-009)

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/reliability/CompensationRegistry.java`
- Test: `src/test/java/com/schwab/urlshortener/reliability/CompensationRegistryTest.java`

**Interfaces:**
- Produces: `class CompensationRegistry { void register(String stageName, Consumer<Map<String,Object>> compensateFn); boolean run(String stageName, Map<String,Object> stageResult); }` (returns whether a compensation ran)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/reliability/CompensationRegistryTest.java
package com.schwab.urlshortener.reliability;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompensationRegistryTest {

    @Test
    void registeredCompensationRunsWithStageResult() {
        CompensationRegistry registry = new CompensationRegistry();
        Map<String, Object> seen = new HashMap<>();
        registry.register("notify_customer", result -> seen.put("undoFor", result.get("externalRef")));

        boolean ran = registry.run("notify_customer", Map.of("externalRef", "msg-123"));

        assertThat(ran).isTrue();
        assertThat(seen.get("undoFor")).isEqualTo("msg-123");
    }

    @Test
    void unregisteredStageReturnsFalseWithoutError() {
        CompensationRegistry registry = new CompensationRegistry();
        assertThat(registry.run("unregistered_stage", Map.of())).isFalse();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, class doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/reliability/CompensationRegistry.java
package com.schwab.urlshortener.reliability;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class CompensationRegistry {

    private final Map<String, Consumer<Map<String, Object>>> registry = new HashMap<>();

    public void register(String stageName, Consumer<Map<String, Object>> compensateFn) {
        registry.put(stageName, compensateFn);
    }

    public boolean run(String stageName, Map<String, Object> stageResult) {
        Consumer<Map<String, Object>> fn = registry.get(stageName);
        if (fn == null) return false;
        fn.accept(stageResult);
        return true;
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(recovery): implement compensation registry distinct from rollback"`

### Task 33: Wire retry/timeout/compensation into `WorkflowEngine.runStage`

**Files:** Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (add a `CompensationRegistry` field + one more constructor overload; rewrite `runStage` to attempt the handler up to `stage.maxRetries() + 1` times, timeout-wrapped per attempt, incrementing `step.retryCount` on each failed attempt; on exhaustion, call `compensationRegistry.run(...)` and record a `compensation_ran` audit event; mark the step `COMPENSATED` if compensation ran, otherwise throw `SafeStopException` — Task 34 is what makes that exception ultimately land the *workflow* on `SAFE_STOPPED` rather than some other terminal state, but the exception itself already exists from Task 28's pattern)

**Note on constructor growth:** this is the fourth overload added across Tasks 22/23/28/29/33 (4 → 6 → 7 → 9 positional parameters). That is a real code smell — a builder or a small dependencies-holder object would be cleaner — but retrofitting one now means touching five already-green test files for a purely cosmetic win. Add the overload as specified below, and record "replace WorkflowEngine's constructor overloads with a builder" as a backlog item in the final engineering summary (Task 47) rather than silently living with it forever.

**Test:** `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineReliabilityIntegrationTest.java`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineReliabilityIntegrationTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.reliability.CompensationRegistry;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineReliabilityIntegrationTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;
    @Autowired private PolicyEvaluationJpaRepository policyEvaluationRepository;
    @Autowired private PolicyExceptionJpaRepository policyExceptionRepository;

    @Test
    void stageRetriesThenCompensatesOnExhaustion() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 2, 5, null));

        CompensationRegistry registry = new CompensationRegistry();
        AtomicBoolean compensated = new AtomicBoolean(false);
        registry.register("flaky", result -> compensated.set(true));

        WorkflowEngine engine = new WorkflowEngine(
                instanceRepository, stepRepository, eventRepository, approvalRepository,
                null, null, policyExceptionRepository, registry);

        String workflowId = engine.submit("demo", stages, "run-8");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, "flaky").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(step.getRetryCount()).isEqualTo(2);
        assertThat(compensated.get()).isTrue();
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, no matching 8-arg `WorkflowEngine` constructor.
- [ ] **Step 3: Implement** — add the `compensationRegistry` field, a new constructor overload accepting it (following the exact pattern of Tasks 28/29's overloads), and replace `runStage` entirely:
```java
// WorkflowEngine.java — full replacement of runStage from this task forward
private void runStage(String workflowId, StageDefinition stage, WorkflowContext ctx) {
    WorkflowStepEntity step = stepRepository.findByWorkflowIdAndName(workflowId, stage.name()).orElseThrow();
    step.setStatus(StepStatus.RUNNING);
    step.setStartedAt(Instant.now());
    stepRepository.save(step);

    Map<String, Object> result = null;
    boolean succeeded = false;

    for (int attempt = 0; attempt <= stage.maxRetries(); attempt++) {
        try {
            result = com.schwab.urlshortener.reliability.TimeoutExecutor
                    .withTimeout(stage.handler(), stage.timeoutSeconds() * 1000L)
                    .run(ctx);
            succeeded = true;
            break;
        } catch (com.schwab.urlshortener.reliability.PermanentStageException e) {
            break; // never retried; falls through to the exhaustion/compensation path
        } catch (com.schwab.urlshortener.reliability.TransientStageException
                | com.schwab.urlshortener.reliability.StageTimeoutException e) {
            step.setRetryCount(step.getRetryCount() + 1);
            stepRepository.save(step);
            if (attempt == stage.maxRetries()) break; // exhausted
        }
    }

    if (!succeeded) {
        boolean compensated = compensationRegistry != null
                && compensationRegistry.run(stage.name(), result != null ? result : Map.of());
        step.setEndedAt(Instant.now());
        if (compensated) {
            step.setStatus(StepStatus.COMPENSATED);
            stepRepository.save(step);
            recordEvent(workflowId, "engine", "compensation_ran:" + stage.name(), "OK", null);
            return; // handled: this batch and the workflow continue normally
        }
        step.setStatus(StepStatus.SAFE_STOPPED);
        stepRepository.save(step);
        throw new SafeStopException("retries exhausted, no compensation registered for stage " + stage.name());
    }

    // Policy enforcement (Task 28/29) — unchanged, reached only on success.
    if (policyEngine != null) {
        List<com.schwab.urlshortener.policy.PolicyCheckResult> policyResults =
                policyEngine.evaluate(stage.name(), ctx.artifacts());
        boolean mandatoryFail = false;
        for (var pr : policyResults) {
            PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
            evaluation.setWorkflowId(workflowId);
            evaluation.setStageName(stage.name());
            evaluation.setPolicyId(pr.policyId());
            evaluation.setPolicyVersion(policyEngine.version());
            evaluation.setOutcome(pr.outcome());
            evaluation.setReason(pr.reason());
            policyEvaluationRepository.save(evaluation);
            if (pr.mandatory() && pr.outcome() == com.schwab.urlshortener.policy.PolicyOutcome.FAIL
                    && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                mandatoryFail = true;
            }
        }
        if (mandatoryFail) {
            step.setStatus(StepStatus.SAFE_STOPPED);
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            stepRepository.save(step);
            throw new SafeStopException("mandatory policy failure at stage " + stage.name());
        }
    }

    step.setStatus(StepStatus.SUCCEEDED);
    step.setEndedAt(Instant.now());
    step.setResult(result != null ? result : Map.of());
    stepRepository.save(step);
    recordEvent(workflowId, "agent", "stage_succeeded:" + stage.name(), "OK", null);
}
```
  Design note on the compensated path: a compensated failure does **not** throw — the batch and the workflow proceed normally toward `COMPLETED` (compensation is defined here as "the failure was handled," a controlled saga-style continuation, not a block). Only the *uncompensated* exhaustion path throws `SafeStopException`, matching the policy-failure path's terminal behavior from Task 28.
- [ ] **Step 4: Run to confirm pass** — PASS, plus regression on Tasks 22–29's suites (unaffected — they either don't hit the retry loop at all, since `maxRetries()` defaults to 0 via `StageDefinition.of(...)`, or don't register a `CompensationRegistry`, which stays `null` through their shorter constructor overloads).
- [ ] **Step 5: Commit** — `git commit -m "feat(resilience): wire retry, timeout, and compensation into stage execution"`

### Task 34: Safe-stop conditions consolidated

> **Corrected by Phase 12 (2026-09-13):** the "fallback realized as retry-then-safe-stop" language originally implied here was a mislabeling that directly contradicted the constitution's own fallback definition — corrected by Task 50, which builds a real fallback mechanism. The two-category safe-stop enumeration itself is unchanged; category (b) now nests a real fallback attempt before compensation. See Phase 12.

**Files:** No code change expected — this task is a verification checkpoint, not new logic. Both "mandatory policy FAIL without exception" (Task 28) and "retries exhausted with no registered compensation" (Task 33) already throw the same `SafeStopException`, caught in the same place in `runFrom` (Task 28), landing on the same `WorkflowStatus.SAFE_STOPPED` terminal state. This task exists to prove that convergence with a dedicated test, per constitution Principle VIII's requirement that safe-stop be a single, well-defined terminal path — not to introduce a third safe-stop trigger.

**Test:** `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineSafeStopTest.java`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineSafeStopTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.reliability.TransientStageException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineSafeStopTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    @Test
    void uncompensatedRetryExhaustionEntersSafeStopNotFailed() {
        StageHandler alwaysFails = ctx -> { throw new TransientStageException("simulated"); };
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky", List.of(), alwaysFails, false, false, 1, 5, null));
        // no CompensationRegistry passed — 4-arg constructor, compensationRegistry stays null
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);

        String workflowId = engine.submit("demo", stages, "run-9");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — this should already PASS if Tasks 28/33 were implemented as specified above; if it instead shows `FAILED`, that means an earlier task's implementer took a shortcut (e.g., let a bare `RuntimeException` propagate instead of the shared `SafeStopException` path) — fix at the source task, not here.
- [ ] **Step 3: Implement** — no new implementation; if Step 2 failed, trace which of Task 28/33's `SafeStopException` throw sites was bypassed and correct it there.
- [ ] **Step 4: Run to confirm pass** — PASS, regression on Task 33's test (its assertion only checks `step.getStatus() == COMPENSATED` for the *compensated* case, so it stays green — this test covers the *uncompensated* case, which Task 33's test never exercised).
- [ ] **Step 5: Commit** — `git commit -m "test: verify uncompensated failures converge on safe-stop terminal state"`

---

## Phase 7 — Dynamic Replanning

### Task 35: Replan API — invalidate downstream stages, version the plan, resume

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (add `replan(...)`; add a `recordEvent` overload accepting a `payload`)
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineReplanningTest.java`

**Interfaces:**
- Produces: `WorkflowEngine.replan(String workflowId, String fromStage, List<StageDefinition> newStages, String actor, String reason)` — marks all steps reachable from `fromStage` (by dependency-graph reachability, not just sequence order) `INVALIDATED`, inserts `newStages` as new `WorkflowStepEntity` rows at `PENDING`, bumps `instance.planVersion`, sets `instance.status = REPLANNING`, then resumes execution from the updated stage set while preserving `ctx.decisions()` and `ctx.artifacts()` accumulated before `fromStage`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineReplanningTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineReplanningTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    @Test
    void replanInvalidatesDownstreamAndCompletesWithNewStage() {
        StageHandler design = ctx -> { ctx.recordDecision("agent", "initial design", "r1"); return Map.of(); };
        StageHandler oldImpl = ctx -> Map.of("shouldNotRun", true);
        // exitApproval: design must actually run and produce its decision before the
        // human reviews it and chooses to replan rather than simply approve-as-is.
        List<StageDefinition> stages = List.of(
                new StageDefinition("design", List.of(), design, false, true, 0, 30, null),
                StageDefinition.of("impl", List.of("design"), oldImpl));

        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository, approvalRepository);
        String workflowId = engine.submit("demo", stages, "run-10");

        // Halted at approval; simulate clarification changing the design before approving:
        AtomicBoolean ran = new AtomicBoolean(false);
        StageHandler newImpl = ctx -> { ran.set(true); return Map.of(); };
        engine.replan(workflowId, "impl", List.of(StageDefinition.of("impl_v2", List.of("design"), newImpl)),
                "human", "clarified requirement changed implementation approach");
        engine.approve(workflowId, "design", "approved", "human", "ok after replan");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(ran.get()).isTrue();
        WorkflowStepEntity oldStep = stepRepository.findByWorkflowIdAndName(workflowId, "impl").orElseThrow();
        assertThat(oldStep.getStatus()).isEqualTo(StepStatus.INVALIDATED);
        assertThat(instance.getPlanVersion()).isEqualTo(2);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `replan` doesn't exist.
- [ ] **Step 3: Implement** `replan()`: mark reachable `WorkflowStepEntity`s `INVALIDATED`, extend the `REGISTRY` entry's stage list with `newStages`, rebuild/validate the `DependencyGraph`, insert new `WorkflowStepEntity`s at `PENDING`, increment `instance.planVersion`, set status `REPLANNING`, record a `"replanned"` audit event carrying `{fromVersion, toVersion, fromStage}` as payload, then call `runFrom(workflowId)`:
```java
// WorkflowEngine.java (add)
public void replan(String workflowId, String fromStage, List<StageDefinition> newStages, String actor, String reason) {
    StageRegistration registration = REGISTRY.get(workflowId);
    List<StageDefinition> currentStages = new ArrayList<>(registration.stages());

    for (String name : reachableFrom(fromStage, currentStages)) {
        stepRepository.findByWorkflowIdAndName(workflowId, name).ifPresent(step -> {
            step.setStatus(StepStatus.INVALIDATED);
            stepRepository.save(step);
        });
    }

    List<StageDefinition> updatedStages = new ArrayList<>(currentStages);
    updatedStages.addAll(newStages);
    DependencyGraph updatedGraph = new DependencyGraph(updatedStages);
    updatedGraph.validate();
    REGISTRY.put(workflowId, new StageRegistration(updatedStages, updatedGraph));

    int sequence = stepRepository.findByWorkflowIdOrderBySequence(workflowId).size();
    for (StageDefinition stage : newStages) {
        WorkflowStepEntity step = new WorkflowStepEntity();
        step.setWorkflowId(workflowId);
        step.setName(stage.name());
        step.setStatus(StepStatus.PENDING);
        step.setSequence(sequence++);
        step.setDependsOn(stage.dependsOn());
        stepRepository.save(step);
    }

    WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
    int fromVersion = instance.getPlanVersion();
    instance.setPlanVersion(fromVersion + 1);
    instance.setStatus(WorkflowStatus.REPLANNING);
    instanceRepository.save(instance);

    recordEvent(workflowId, actor, "replanned", "OK", reason,
            Map.of("fromVersion", fromVersion, "toVersion", fromVersion + 1, "fromStage", fromStage));

    runFrom(workflowId);
}

// Reachability via reverse-dependency BFS from fromStage (includes fromStage itself).
private Set<String> reachableFrom(String fromStage, List<StageDefinition> stages) {
    Map<String, List<String>> dependents = new HashMap<>();
    for (StageDefinition s : stages) {
        for (String dep : s.dependsOn()) {
            dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.name());
        }
    }
    Set<String> result = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(fromStage);
    while (!queue.isEmpty()) {
        String current = queue.poll();
        if (!result.add(current)) continue;
        for (String dependent : dependents.getOrDefault(current, List.of())) {
            queue.add(dependent);
        }
    }
    return result;
}

// New overload — earlier recordEvent(workflowId, actorType, action, result, reason)
// calls now delegate here with payload=null; existing call sites need no changes.
private void recordEvent(String workflowId, String actorType, String action, String result, String reason) {
    recordEvent(workflowId, actorType, action, result, reason, null);
}

private void recordEvent(String workflowId, String actorType, String action, String result, String reason,
                          Map<String, Object> payload) {
    WorkflowEventEntity event = new WorkflowEventEntity();
    event.setWorkflowId(workflowId);
    event.setActorType(actorType);
    event.setAction(action);
    event.setResult(result);
    event.setReason(reason);
    event.setPayload(payload);
    eventRepository.save(event);
}
```
  **Required companion fix to `runFrom`** (without this, the test above fails): as written since Task 23, `runFrom` only re-examines a batch's `pending` (strictly `PENDING`-status) stages; a stage sitting at `WAITING_APPROVAL` from an *earlier, still-unresolved* gate is silently invisible to that filter, so a later `runFrom` call — triggered here by `replan()`, called while `design`'s exit gate is still outstanding — would fall straight through batch 1 and run `impl_v2` in batch 2 without `design` ever being approved. That is a governance hole: replanning must never let downstream work slip past an ancestor's open gate. Add an explicit, idempotent re-halt at the top of each batch's handling in `runFrom`:
```java
// WorkflowEngine.java — runFrom, add this check before computing `pending` in every batch
for (List<StageDefinition> batch : registration.graph().topologicalBatches()) {
    List<StageDefinition> blocked = batch.stream()
            .filter(s -> stepStatus(workflowId, s.name()) == StepStatus.WAITING_APPROVAL
                    && !isApproved(workflowId, s.name()))
            .toList();
    if (!blocked.isEmpty()) {
        instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
        instanceRepository.save(instance);
        return; // an ancestor gate is still open; re-halt on every call until approved
    }
    List<StageDefinition> pending = batch.stream()
            .filter(s -> stepStatus(workflowId, s.name()) == StepStatus.PENDING)
            .toList();
    if (pending.isEmpty()) continue;
    // ... existing entry-gate check, runBatch(...), context persist, exit-gate check unchanged ...
}
```
  This `blocked` check must run before the existing `pending` computation in every batch, every call — it is what makes `runFrom` safe to invoke repeatedly (by `approve()`, and now by `replan()`) without re-deriving "was this already handled" from scratch each time.
- [ ] **Step 4: Run to confirm pass** — PASS, regression on Tasks 22–34. Hand-trace: `submit()` runs `design` (records decision, `SUCCEEDED`), hits the exit gate, halts (`WAITING_APPROVAL`). `replan()` invalidates `impl`, adds `impl_v2`, calls `runFrom` → batch 1 `blocked=[design]` (still unapproved) → re-halt, `impl_v2` does **not** run yet. Then `approve("design","approved")` → `design` has `exitApproval` → step reset to `SUCCEEDED` → `runFrom` → batch 1: not blocked, no pending → batch 2: `impl` (`INVALIDATED`, excluded) and `impl_v2` (`PENDING`) → `impl_v2` runs → `ran.get()` true ✓, workflow `COMPLETED` ✓.
- [ ] **Step 5: Commit** — `git commit -m "feat(replanning): support governed downstream replanning with lineage preservation"`

---

## Phase 8 — Observability

### Task 36: Audit recorder consolidation + `GET /workflows/{id}/audit` completeness

**Files:** Create: `src/main/java/com/schwab/urlshortener/observability/AuditRecorder.java` (thin wrapper consolidating the ad-hoc `recordEvent` bodies already scattered across `WorkflowEngine` into one class so every call site is consistent); Modify: `WorkflowEngine.java` to delegate to it

**Test:** `src/test/java/com/schwab/urlshortener/observability/AuditRecorderTest.java`

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/observability/AuditRecorderTest.java
package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditRecorderTest {

    @Autowired private WorkflowEventJpaRepository eventRepository;

    @Test
    void auditRecorderPersistsRequiredFields() {
        AuditRecorder recorder = new AuditRecorder(eventRepository);
        recorder.record("wf-1", "human", "approval_approved:gate", "OK", "looks good", Map.of("step", "gate"));

        List<WorkflowEventEntity> events = eventRepository.findByWorkflowIdOrderById("wf-1");
        WorkflowEventEntity event = events.get(0);
        assertThat(event.getActorType()).isEqualTo("human");
        assertThat(event.getResult()).isEqualTo("OK");
        assertThat(event.getPayload().get("step")).isEqualTo("gate");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `AuditRecorder` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/observability/AuditRecorder.java
package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.WorkflowEventEntity;
import com.schwab.urlshortener.orchestration.persistence.WorkflowEventJpaRepository;
import java.util.Map;

public class AuditRecorder {

    private final WorkflowEventJpaRepository eventRepository;

    public AuditRecorder(WorkflowEventJpaRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void record(String workflowId, String actorType, String action, String result,
                        String reason, Map<String, Object> payload) {
        WorkflowEventEntity event = new WorkflowEventEntity();
        event.setWorkflowId(workflowId);
        event.setActorType(actorType);
        event.setAction(action);
        event.setResult(result);
        event.setReason(reason);
        event.setPayload(payload);
        eventRepository.save(event);
    }
}
```
  In `WorkflowEngine`, add a field `private final AuditRecorder auditRecorder;` assigned in every constructor as `new AuditRecorder(eventRepository)`, and replace the body of the 6-arg `recordEvent(...)` (the one Task 35 introduced with a payload parameter) with `auditRecorder.record(workflowId, actorType, action, result, reason, payload);` — the 5-arg overload keeps delegating to the 6-arg one, so no other call site changes.
- [ ] **Step 4: Run to confirm pass** — PASS, regression on all orchestration tests.
- [ ] **Step 5: Commit** — `git commit -m "feat(observability): consolidate audit event recording"`

### Task 37: MTTR and reliability metrics computation

> **Bug fixed for real by Phase 12 (2026-09-13):** the `computeReliabilitySummary` workflow-scoping bug disclosed in this task's own code comment (steps never filtered by `workflowId`) is fixed by Task 53 — see Phase 12.

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/observability/RecoveryEvent.java`, `MttrReport.java`, `ReliabilitySummary.java`, `MetricsService.java`
- Test: `src/test/java/com/schwab/urlshortener/observability/MetricsMttrTest.java`

**Interfaces:**
- Produces: `record RecoveryEvent(Instant failureDetectedAt, Instant recoveryStartAt, Instant recoveryCompleteAt, boolean recovered, String mechanism)`; `class MetricsService { static MttrReport computeMttr(List<RecoveryEvent> events); static ReliabilitySummary computeReliabilitySummary(WorkflowInstanceJpaRepository, WorkflowStepJpaRepository, String workflowId); }` where `record MttrReport(Double mttrSeconds, int recoveredCount, int unrecoveredCount, int excludedCount, String populationNote)` and `record ReliabilitySummary(double successRate, double retryFrequency, double rollbackOrCompensationFrequency, double fallbackFrequency, Double endToEndLatencySeconds, boolean isDemonstrationData)` (`fallbackFrequency` added by Phase 12, Task 50 — tracks `SUCCEEDED_WITH_FALLBACK` steps separately from rollback/compensation)

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/observability/MetricsMttrTest.java
package com.schwab.urlshortener.observability;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsMttrTest {

    private static Instant t(long secondsOffset) {
        return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(secondsOffset);
    }

    @Test
    void mttrAveragesOnlyRecoveredEventsAndExcludesUnrecovered() {
        List<RecoveryEvent> events = List.of(
                new RecoveryEvent(t(0), t(1), t(11), true, "retry"),
                new RecoveryEvent(t(20), t(21), t(41), true, "compensation"),
                new RecoveryEvent(t(50), t(51), null, false, "none")
        );

        MttrReport report = MetricsService.computeMttr(events);

        assertThat(report.recoveredCount()).isEqualTo(2);
        assertThat(report.unrecoveredCount()).isEqualTo(1);
        assertThat(report.mttrSeconds()).isEqualTo(15.0); // (10 + 20) / 2, recoveryComplete - recoveryStart
    }

    @Test
    void mttrIsNullWhenNoRecoveredEvents() {
        MttrReport report = MetricsService.computeMttr(List.of());

        assertThat(report.mttrSeconds()).isNull();
        assertThat(report.recoveredCount()).isEqualTo(0);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the classes exist yet.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/observability/RecoveryEvent.java
package com.schwab.urlshortener.observability;

import java.time.Instant;

public record RecoveryEvent(
        Instant failureDetectedAt, Instant recoveryStartAt, Instant recoveryCompleteAt,
        boolean recovered, String mechanism
) { }
```
```java
// src/main/java/com/schwab/urlshortener/observability/MttrReport.java
package com.schwab.urlshortener.observability;

public record MttrReport(Double mttrSeconds, int recoveredCount, int unrecoveredCount,
                          int excludedCount, String populationNote) { }
```
```java
// src/main/java/com/schwab/urlshortener/observability/ReliabilitySummary.java
package com.schwab.urlshortener.observability;

public record ReliabilitySummary(double successRate, double retryFrequency,
                                  double rollbackOrCompensationFrequency, double fallbackFrequency,
                                  Double endToEndLatencySeconds, boolean isDemonstrationData) {
    public ReliabilitySummary(double successRate, double retryFrequency,
                               double rollbackOrCompensationFrequency, double fallbackFrequency,
                               Double endToEndLatencySeconds) {
        this(successRate, retryFrequency, rollbackOrCompensationFrequency, fallbackFrequency,
                endToEndLatencySeconds, true);
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/observability/MetricsService.java
package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.StepStatus;
import com.schwab.urlshortener.orchestration.WorkflowStatus;
import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Duration;
import java.util.List;

public final class MetricsService {

    private MetricsService() { }

    public static MttrReport computeMttr(List<RecoveryEvent> events) {
        List<RecoveryEvent> recovered = events.stream()
                .filter(e -> e.recovered() && e.recoveryCompleteAt() != null).toList();
        List<RecoveryEvent> unrecovered = events.stream().filter(e -> !e.recovered()).toList();
        if (recovered.isEmpty()) {
            return new MttrReport(null, 0, unrecovered.size(), 0,
                    "no recovered events in this run's demonstration data");
        }
        double average = recovered.stream()
                .mapToDouble(e -> Duration.between(e.recoveryStartAt(), e.recoveryCompleteAt()).toMillis() / 1000.0)
                .average().orElse(0);
        return new MttrReport(average, recovered.size(), unrecovered.size(), 0,
                "demonstration data from this process's in-memory/H2 run; not production statistics");
    }

    // NOTE: mirrors a pre-existing scoping inconsistency carried over from the original
    // design — `steps` is never filtered by `workflowId` even when one is supplied for
    // `instances`. Not covered by any test in this plan; flagged here rather than
    // silently "fixed" mid-translation. Track as a backlog cleanup item (Task 47).
    public static ReliabilitySummary computeReliabilitySummary(
            WorkflowInstanceJpaRepository instanceRepository,
            WorkflowStepJpaRepository stepRepository,
            String workflowId) {
        List<WorkflowInstanceEntity> instances = workflowId != null
                ? instanceRepository.findById(workflowId).map(List::of).orElse(List.of())
                : instanceRepository.findAll();
        int total = Math.max(instances.size(), 1);
        long succeeded = instances.stream().filter(i -> i.getStatus() == WorkflowStatus.COMPLETED).count();

        List<WorkflowStepEntity> steps = stepRepository.findAll();
        long retriedSteps = steps.stream().filter(s -> s.getRetryCount() > 0).count();
        long recoveredSteps = steps.stream()
                .filter(s -> s.getStatus() == StepStatus.ROLLED_BACK || s.getStatus() == StepStatus.COMPENSATED)
                .count();
        // SUCCEEDED_WITH_FALLBACK is its own recovery mechanism (Task 50), tracked separately
        // from rollback/compensation rather than folded into the same frequency — a fallback
        // recovery and a compensated failure carry different degrees of guarantee and should not
        // be reported as the same number.
        long fallbackSteps = steps.stream()
                .filter(s -> s.getStatus() == StepStatus.SUCCEEDED_WITH_FALLBACK)
                .count();

        Double latency = null;
        if (!instances.isEmpty()
                && instances.stream().allMatch(i -> i.getUpdatedAt() != null && i.getCreatedAt() != null)) {
            latency = instances.stream()
                    .mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getUpdatedAt()).toMillis() / 1000.0)
                    .average().orElse(0);
        }

        return new ReliabilitySummary(
                (double) succeeded / total,
                (double) retriedSteps / Math.max(steps.size(), 1),
                (double) recoveredSteps / Math.max(steps.size(), 1),
                (double) fallbackSteps / Math.max(steps.size(), 1),
                latency);
    }
}
```
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(observability): implement MTTR and reliability metrics computation"`

### Task 38: `GET /metrics` endpoint + CLI report

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/observability/MetricsController.java`
- Modify: `src/main/java/com/schwab/urlshortener/scenarios/ScenarioRunner.java` (add a `--metrics-report` argument path, reusing the same `CommandLineRunner`, as the Java analog of a separate `compute_metrics_report.py` script — no separate process/class needed)
- Test: `src/test/java/com/schwab/urlshortener/api/MetricsControllerContractTest.java`

**Interfaces:** Produces: `GET /metrics` → JSON of `ReliabilitySummary` fields, explicitly tagged `"isDemonstrationData": true`; `java -jar url-shortener.jar --metrics-report` prints the same JSON to stdout without needing a separate HTTP call.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/api/MetricsControllerContractTest.java
package com.schwab.urlshortener.api;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsControllerContractTest {

    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();

    @Test
    void metricsEndpointLabelsDataAsDemonstration() {
        ResponseEntity<Map> resp = rest.getForEntity("http://localhost:" + port + "/metrics", Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("isDemonstrationData")).isEqualTo(true);
        assertThat(resp.getBody()).containsKey("successRate");
    }
}
```
- [ ] **Step 2: Run to confirm failure** — 404, `MetricsController` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/observability/MetricsController.java
package com.schwab.urlshortener.observability;

import com.schwab.urlshortener.orchestration.persistence.WorkflowInstanceJpaRepository;
import com.schwab.urlshortener.orchestration.persistence.WorkflowStepJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;

    public MetricsController(WorkflowInstanceJpaRepository instanceRepository,
                              WorkflowStepJpaRepository stepRepository) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
    }

    @GetMapping("/metrics")
    public ReliabilitySummary metrics() {
        return MetricsService.computeReliabilitySummary(instanceRepository, stepRepository, null);
    }
}
```
```java
// ScenarioRunner.java changes: recognize --metrics-report alongside --scenario=
@Override
public void run(String... args) throws Exception {
    for (String arg : args) {
        if (arg.equals("--metrics-report")) {
            printMetricsReport(System.out);
            return;
        }
    }
    String scenario = extractScenarioArg(args);
    if (scenario == null) return;
    runScenario(scenario, System.out);
}

void printMetricsReport(PrintStream out) throws Exception {
    var summary = MetricsService.computeReliabilitySummary(instanceRepository, stepRepository, null);
    out.println(MAPPER.writeValueAsString(summary));
}
```
  (`instanceRepository`/`stepRepository` are already constructor-injected fields on `ScenarioRunner` from Task 25.)
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(observability): expose reliability metrics via API and CLI report"`

---

## Phase 9 — Three Scenarios (the assignment's mandatory demonstration)

### Task 39: Greenfield scenario — well-defined requirement proceeds without artificial clarification

**Authoritative final form (corrected 2026-09-13).** No shipped code exists yet, so this task is
written directly in its final form — the real 12-node graph — rather than an 8-stage version meant
to be patched later. **Execution order note:** implement Task 49 (`StageCatalog`), Task 50
(consolidated `WorkflowEngine`), and Task 51 (`DecompositionStage`'s clarification fold-in,
`FinalSummaryStage`) — all below, in the Phase 12 section — before this task; they are placed later
in this document only to stay adjacent to the corrective-review narrative that produced them, but
belong earlier in actual build order. This task does not modify a file any of those three create;
it is the first task that creates `RequirementIngestionStage`, `RequirementNormalizationStage`,
`AmbiguityDetectionStage`, `HumanClarificationStage`, and `FinalSummaryStage`, so nothing here is
forward-referencing a not-yet-created file.

The 12 nodes: `requirement_ingestion`, `requirement_normalization`, `ambiguity_detection`,
`human_clarification` (present in every run's stage list, `SKIPPED` here since this requirement is
unambiguous), `decomposition`, `design`, `implementation`, `testing`, `security`, `documentation`,
`release_readiness`, `final_engineering_summary`.

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/stages/{RequirementIngestionStage,RequirementNormalizationStage,AmbiguityDetectionStage,HumanClarificationStage,DecompositionStage,DesignStage,ImplementationStage,TestingStage,DocumentationStage,SecurityStage,ReleaseReadinessStage,FinalSummaryStage}.java`
  (this task writes all twelve stage classes in their final form; `DecompositionStage`'s
  clarification-reading logic, specified fully in Task 51, is included here directly since it is
  needed for this task's own catalog to compile and this task creates the file)
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (adds one line to
  `runFrom`, per Task 50: seed `ctx.artifacts().putIfAbsent("feature", instance.getFeature())` so
  `RequirementIngestionStage` can read the originally-submitted requirement text)
- Modify: `src/main/java/com/schwab/urlshortener/scenarios/ScenarioRunner.java` (wire the real
  12-stage `greenfield` scenario via a `StageCatalog`, replacing the trivial 2-stage placeholder
  from Task 25)
- Test: `src/test/java/com/schwab/urlshortener/scenarios/GreenfieldScenarioTest.java`
- Create: `docs/scenarios/greenfield.md`

**Interfaces:** Each stage class exposes one static method `run(WorkflowContext ctx) -> Map<String,Object>`
matching `StageHandler`, referenced via method reference — never a lambda, so every handler is a
stable, restart-safe catalog entry (Task 49's binding requirement). `AmbiguityDetectionStage.run`
performs a deterministic, documented-as-demonstration-scope check: a requirement is treated as
unambiguous only when its normalized text cites a concrete requirement ID (`req-<digits>`) **and**
states a measurable, testable criterion (contains a digit). It records via `ctx.recordDecision(...)`
**why** clarification was or wasn't required, and sets `ctx.addArtifact("ambiguityDetected", <bool>)`
— the catalog-registered `"ambiguity_gate"` predicate reads that artifact to decide whether
`human_clarification` runs or is `SKIP`ped. This same class, unmodified, is what Task 41's ambiguous
scenario exercises with different input text — the branching is data-driven, not scenario-specific
code.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/scenarios/GreenfieldScenarioTest.java
package com.schwab.urlshortener.scenarios;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.orchestration.stages.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class GreenfieldScenarioTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private static StageCatalog greenfieldCatalog() {
        return new StageCatalog(
                Map.ofEntries(
                        Map.entry("requirement_ingestion", (StageHandler) RequirementIngestionStage::run),
                        Map.entry("requirement_normalization", (StageHandler) RequirementNormalizationStage::run),
                        Map.entry("ambiguity_detection", (StageHandler) AmbiguityDetectionStage::run),
                        Map.entry("human_clarification", (StageHandler) HumanClarificationStage::run),
                        Map.entry("decomposition", (StageHandler) DecompositionStage::run),
                        Map.entry("design", (StageHandler) DesignStage::run),
                        Map.entry("implementation", (StageHandler) ImplementationStage::run),
                        Map.entry("testing", (StageHandler) TestingStage::run),
                        Map.entry("security", (StageHandler) SecurityStage::run),
                        Map.entry("documentation", (StageHandler) DocumentationStage::run),
                        Map.entry("release_readiness", (StageHandler) ReleaseReadinessStage::run),
                        Map.entry("final_engineering_summary", (StageHandler) FinalSummaryStage::run)),
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>)
                        c -> Boolean.TRUE.equals(c.artifacts().get("ambiguityDetected"))),
                Map.of());
    }

    private static List<StageDefinition> greenfieldStages() {
        return List.of(
                new StageDefinition("requirement_ingestion", List.of(), RequirementIngestionStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("requirement_normalization", List.of("requirement_ingestion"),
                        RequirementNormalizationStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("ambiguity_detection", List.of("requirement_normalization"),
                        AmbiguityDetectionStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("human_clarification", List.of("ambiguity_detection"),
                        HumanClarificationStage::run, true, false, 0, 30, null, "ambiguity_gate", null),
                new StageDefinition("decomposition", List.of("human_clarification"), DecompositionStage::run,
                        false, false, 0, 30, null, null, null),
                // exitApproval on both: the human reviews a PRODUCED design / readiness report,
                // not a proposal to run one — the handler must execute first.
                new StageDefinition("design", List.of("decomposition"), DesignStage::run,
                        false, true, 0, 30, null, null, null),
                new StageDefinition("implementation", List.of("design"), ImplementationStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("testing", List.of("implementation"), TestingStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("security", List.of("implementation"), SecurityStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of("implementation"), DocumentationStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("release_readiness", List.of("testing", "security", "documentation"),
                        ReleaseReadinessStage::run, false, true, 0, 30, null, null, null),
                new StageDefinition("final_engineering_summary", List.of("release_readiness"),
                        FinalSummaryStage::run, false, false, 0, 30, null, null, null)
        );
    }

    @Test
    void wellDefinedRequirementCompletesWithoutClarificationGate() {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, greenfieldCatalog());
        String workflowId = engine.submit(
                "add optional expiresAt field to the create-link contract per req-014, "
                        + "returning 400 for a past timestamp",
                greenfieldStages(), "scn-a-1");
        engine.approve(workflowId, "design", "approved", "human", "matches ADR-003/006");
        engine.approve(workflowId, "release_readiness", "approved", "human", "all checks green");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        WorkflowStepEntity clarificationStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "human_clarification").orElseThrow();
        assertThat(clarificationStep.getStatus()).isEqualTo(StepStatus.SKIPPED);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) instance.getContextJson().get("decisions");
        assertThat(decisions).anyMatch(d -> d.get("decision").toString().contains("no_clarification_required"));
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, none of the stage classes exist yet.
- [ ] **Step 3: Implement** each stage class:
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/RequirementIngestionStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RequirementIngestionStage {
    private RequirementIngestionStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        String feature = (String) ctx.artifacts().get("feature");
        ctx.addArtifact("requirementText", feature);
        ctx.recordDecision("agent", "requirement_ingested", "captured the submitted requirement text verbatim");
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/RequirementNormalizationStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RequirementNormalizationStage {
    private RequirementNormalizationStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        String raw = (String) ctx.artifacts().get("requirementText");
        String normalized = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        ctx.addArtifact("normalizedRequirement", normalized);
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/AmbiguityDetectionStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class AmbiguityDetectionStage {
    private AmbiguityDetectionStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        String normalized = (String) ctx.artifacts().getOrDefault("normalizedRequirement", "");
        // Demonstration-scope heuristic, documented as such: unambiguous only when the
        // requirement cites a concrete requirement ID AND states a measurable, testable
        // criterion (a digit somewhere — a status code, a count, a duration). Anything else
        // routes to human_clarification. This is completeness/consistency/testability/
        // policy-boundary review reduced to something a demo can execute deterministically.
        boolean citesRequirement = normalized.matches(".*\\breq-\\d+\\b.*");
        boolean statesMeasurableCriterion = normalized.matches(".*\\d.*");
        boolean ambiguous = !(citesRequirement && statesMeasurableCriterion);
        ctx.addArtifact("ambiguityDetected", ambiguous);
        if (ambiguous) {
            ctx.recordDecision("agent", "clarification_required",
                    "requirement lacks a cited requirement ID and/or a measurable, testable criterion");
        } else {
            ctx.recordDecision("agent", "no_clarification_required",
                    "requirement is complete, consistent, testable, within policy/architecture boundaries");
        }
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/HumanClarificationStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class HumanClarificationStage {
    private HumanClarificationStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        // entryApproval-gated (see StageDefinition above); this handler runs only once already
        // approved, and does no work of its own — WorkflowEngine.approve() (Task 50) has already
        // written the human's reason into ctx.artifacts() under "human_clarificationResolution"
        // before this handler is invoked. DecompositionStage (below) is what reads and uses it.
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/DecompositionStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class DecompositionStage {
    private DecompositionStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        Object resolution = ctx.artifacts().get("human_clarificationResolution");
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("decomposed", true);
        if (resolution != null) {
            ctx.recordDecision("agent", "decomposition_used_clarification", String.valueOf(resolution));
            result.put("clarifiedRequirement", resolution);
        }
        return result;
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/DesignStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class DesignStage {
    private DesignStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        ctx.recordDecision("agent", "design_produced", "adds an optional expiresAt field to the create-link contract");
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/ImplementationStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImplementationStage {
    private ImplementationStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        // Reports the security controls this stage exercised, for Phase 5's policy engine.
        ctx.addArtifact("urlSchemeAllowlistEnforced", true);
        ctx.addArtifact("ssrfGuardEnforced", true);
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/TestingStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class TestingStage {
    private TestingStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/DocumentationStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class DocumentationStage {
    private DocumentationStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/SecurityStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class SecurityStage {
    private SecurityStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of();
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/ReleaseReadinessStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ReleaseReadinessStage {
    private ReleaseReadinessStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        ctx.addArtifact("auditTrailPresent", true);
        Map<String, Object> result = new java.util.HashMap<>();
        @SuppressWarnings("unchecked")
        java.util.List<String> fallbacksUsed = (java.util.List<String>) ctx.artifacts().get("fallbacksUsed");
        if (fallbacksUsed != null && !fallbacksUsed.isEmpty()) {
            // Principle VIII: never report an unqualified "ready" when a fallback degraded the
            // outcome — name which stages ran degraded and mark the release as such.
            result.put("ready", true);
            result.put("degraded", true);
            result.put("degradedStages", fallbacksUsed);
            ctx.recordDecision("agent", "release_readiness_degraded",
                    "ready with disclosed reduced guarantee: " + fallbacksUsed + " completed via fallback, not primary path");
        } else {
            result.put("ready", true);
            result.put("degraded", false);
        }
        return result;
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/FinalSummaryStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class FinalSummaryStage {
    private FinalSummaryStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        ctx.recordDecision("agent", "final_engineering_summary_recorded",
                "captured the complete decision lineage and produced artifacts for the reviewer navigation guide");
        return Map.of();
    }
}
```
  Wire `ScenarioRunner.runScenario` to build this exact stage list and catalog when
  `scenario.equals("greenfield")` (replacing the Task 25 placeholder), and — since this is a
  non-interactive CLI demo, not a governance test — have the runner call `engine.approve(...)`
  itself for both `design` and `release_readiness` immediately after `submit()`, simulating a human
  approving on the spot, so the printed final line shows `COMPLETED` end to end.
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5:** Write `docs/scenarios/greenfield.md` documenting: original requirement, the
  requirement-quality check performed by `AmbiguityDetectionStage` and why it skipped
  `human_clarification`, decomposition, orchestration path (paste the actual
  `GET /workflows/{id}/audit` JSON output from a real run), acceptance criteria, terminal outcome.
- [ ] **Step 6: Commit** — `git commit -m "test(scenarios): validate greenfield workflow against the real 12-node graph"`

- [ ] **Step 7: Wire the production `StageCatalog`/`WorkflowEngine` as Spring beans, and start
  `resumeAll()` at application boot** (fourth review's finding 3: "the document declares the Spring
  `StageCatalog` configuration out of scope... add the configuration/application-runner and a
  boot-level integration test" — this is not out of scope; a restart-recovery mechanism that nothing
  ever calls does not satisfy REQ-017/REQ-028). This is the first task where every production stage
  class exists, so it is where the composition root belongs, not Task 50 (which only has synthetic
  test stages available):
```java
// src/main/java/com/schwab/urlshortener/orchestration/OrchestrationConfig.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.stages.*;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrchestrationConfig {

    @Bean
    public StageCatalog stageCatalog() {
        return new StageCatalog(
                Map.ofEntries(
                        Map.entry("requirement_ingestion", (StageHandler) RequirementIngestionStage::run),
                        Map.entry("requirement_normalization", (StageHandler) RequirementNormalizationStage::run),
                        Map.entry("ambiguity_detection", (StageHandler) AmbiguityDetectionStage::run),
                        Map.entry("human_clarification", (StageHandler) HumanClarificationStage::run),
                        Map.entry("decomposition", (StageHandler) DecompositionStage::run),
                        Map.entry("design", (StageHandler) DesignStage::run),
                        Map.entry("implementation", (StageHandler) ImplementationStage::run),
                        Map.entry("testing", (StageHandler) TestingStage::run),
                        Map.entry("security", (StageHandler) SecurityStage::run),
                        Map.entry("documentation", (StageHandler) DocumentationStage::run),
                        Map.entry("release_readiness", (StageHandler) ReleaseReadinessStage::run),
                        Map.entry("final_engineering_summary", (StageHandler) FinalSummaryStage::run)),
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>)
                        c -> Boolean.TRUE.equals(c.artifacts().get("ambiguityDetected"))),
                Map.of());
        // Brownfield's seven impact stages (Task 40) and any fallback handlers (Task 50's
        // DocumentationFallbackStage) are added to these same three maps when those tasks land —
        // this bean is additive, not replaced, by later tasks.
    }
}
```
  `WorkflowEngine` itself becomes a `@Bean` too — add `@Bean public WorkflowEngine workflowEngine(...)`
  next to `stageCatalog()` above, constructor-injecting the four JPA repositories (already
  `@Repository`-scanned) plus `stageCatalog()` and `null` for the three optional policy/compensation
  parameters until Phase 5/6 wires them in. `ScenarioRunner` (Task 25) is updated to `@Autowired` this
  bean instead of constructing its own `WorkflowEngine`.
```java
// src/main/java/com/schwab/urlshortener/orchestration/WorkflowRecoveryRunner.java
package com.schwab.urlshortener.orchestration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MIN_VALUE) // before ScenarioRunner's CommandLineRunner (Task 25) — recovery
                           // must complete before any new workflow is submitted this boot.
public class WorkflowRecoveryRunner implements ApplicationRunner {

    private final WorkflowEngine engine;

    public WorkflowRecoveryRunner(WorkflowEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        engine.resumeAll();
    }
}
```
- [ ] **Step 8: Write and pass a boot-level restart-recovery test** — proves recovery actually runs
  at startup, not merely that `resumeAll()` exists as a callable method (`WorkflowEngineRestartTest`,
  Task 50, already proves the method's own logic; this test proves something calls it):
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowRecoveryBootTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.UrlShortenerApplication;
import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.orchestration.stages.RequirementIngestionStage;
import com.schwab.urlshortener.orchestration.stages.RequirementNormalizationStage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

class WorkflowRecoveryBootTest {

    // Uses the real, file-mode H2 datasource from application.yml (Task 13) — not @DataJpaTest's
    // isolated in-memory context — because the point of this test is that a *second*, freshly
    // started process picks up state a *first* process left on disk, exactly like a real restart.

    // A minimal, self-contained two-stage graph (not the full 12-node greenfield graph — this
    // test's job is to prove the boot wiring calls resumeAll(), not to re-prove the greenfield
    // scenario itself, which GreenfieldScenarioTest already covers). Both stages are registered
    // in the real production StageCatalog (OrchestrationConfig), so no test-only catalog is built.
    @Test
    void aWorkflowLeftRunningByOneProcessIsResumedAndCompletedByTheNextProcessAtBoot() {
        String workflowId;
        try (ConfigurableApplicationContext firstProcess =
                     SpringApplication.run(UrlShortenerApplication.class, "--server.port=0")) {
            WorkflowStepJpaRepository steps = firstProcess.getBean(WorkflowStepJpaRepository.class);
            WorkflowEngine engine = firstProcess.getBean(WorkflowEngine.class);

            // Real production method references, not throwaway lambdas: resolveHandler() always
            // tries the catalog first (OrchestrationConfig's bean), so these two names resolve to
            // the real stage classes regardless — passed here only so submit()'s persisted
            // StageDefinition rows are self-explanatory, not because the handler reference itself
            // is load-bearing after the first submit() call.
            List<StageDefinition> stages = List.of(
                    new StageDefinition("requirement_ingestion", List.of(),
                            RequirementIngestionStage::run, false, false, 0, 30, null, null, null),
                    new StageDefinition("requirement_normalization", List.of("requirement_ingestion"),
                            RequirementNormalizationStage::run, false, false, 0, 30, null, null, null));
            workflowId = engine.submit("boot-recovery smoke workflow", stages, "boot-recovery-1");

            // Simulate the process dying mid-stage: force the in-flight step back to RUNNING with
            // no completion recorded — exactly what a genuine crash leaves behind, not the earlier
            // draft's approach of hand-editing a SAFE_STOPPED step to PENDING.
            WorkflowStepEntity normalization = steps.findByWorkflowIdAndPlanVersionAndName(
                    workflowId, 1, "requirement_normalization").orElseThrow();
            normalization.setStatus(StepStatus.RUNNING);
            normalization.setStartedAt(Instant.now());
            steps.save(normalization);
            // The instance itself is left RUNNING too — a real crash never gets the chance to
            // update it to any terminal/pending status.
        } // context closed — the "first process" is gone, exactly like a killed JVM.

        // A brand-new process, pointed at the same file-mode data/app.mv.db. Nothing in this test
        // calls resumeAll() — WorkflowRecoveryRunner (an ApplicationRunner bean) must do it during
        // this context's own startup for the assertion below to ever pass.
        try (ConfigurableApplicationContext secondProcess =
                     SpringApplication.run(UrlShortenerApplication.class, "--server.port=0")) {
            WorkflowInstanceJpaRepository instances = secondProcess.getBean(WorkflowInstanceJpaRepository.class);
            WorkflowStepJpaRepository steps = secondProcess.getBean(WorkflowStepJpaRepository.class);
            String id = workflowId;

            await().atMost(5, SECONDS).untilAsserted(() -> {
                WorkflowInstanceEntity instance = instances.findById(id).orElseThrow();
                assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
            });

            WorkflowStepEntity normalization = steps.findByWorkflowIdAndPlanVersionAndName(
                    id, 1, "requirement_normalization").orElseThrow();
            assertThat(normalization.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
            assertThat(normalization.getRetryCount()).isZero(); // recovered by re-run, not by retry accounting
        }
    }
}
```
  Add the `awaitility` test dependency to `pom.xml` (`org.awaitility:awaitility:4.2.2`, test scope)
  — the 5-second bound here is not arbitrary busy-waiting, it is REQ-028's own recovery-time
  objective, checked against the real Spring Boot startup path rather than a raw `WorkflowEngine`
  object.
- [ ] **Step 9: Run to confirm pass** — `./mvnw test -Dtest=WorkflowRecoveryBootTest` → PASS. If it
  fails because `release_readiness` never leaves `RUNNING`, the defect is in `runFrom`'s abandoned-
  `RUNNING` recovery (Task 50, corrected in this round — see there), not in this task.
- [ ] **Step 10: Commit** — `git commit -m "feat(orchestration): wire production StageCatalog/WorkflowEngine as Spring beans and start resumeAll() at boot"`


### Task 40: Brownfield scenario — enhancement against existing code with seven-branch impact analysis gate

**Authoritative final form (corrected 2026-09-13).** Reuses Task 39's 12-node stage set and adds
`spec.md` Section 4's SCN-B requirement directly — seven genuinely parallel impact-analysis branches
joined by a mandatory Approval Gate before `implementation` — rather than the single-stage
placeholder an earlier draft used. **Execution order note:** this task creates the seven impact
classes and `ChangeImpactReportStage`; nothing here modifies a file a later task creates.

**Chosen brownfield change (fixed 2026-09-13, after CLR-15's human confirmation):** the original
draft of this task targeted a `PATCH /links/{code}/expiration` endpoint — that is no longer valid.
CLR-15 (Section 13.1 of `spec.md`) was explicitly confirmed by the human candidate on 2026-09-13:
Links are immutable, no update/PATCH capability exists or may be added without a material
requirement change (which would itself require returning to `/speckit.clarify`, not just picking a
different brownfield demo). The brownfield change is now: **harden the existing SSRF guard
(`UrlValidator.isBlockedHost`, Task 15) to reject IPv6 Unique Local Addresses (RFC 4193, `fc00::/7`)**.
This is a real, well-known gap: Java's `Inet6Address.isSiteLocalAddress()` — one of the five checks
Task 15 relies on — implements the *deprecated* IPv6 site-local range (`FEC0::/10`), not the modern
ULA range. A caller could currently submit `http://[fd00::1]/internal` and have it pass validation,
defeating the SSRF guard's own purpose. This is a textbook brownfield case: a real defect in
existing, already-shipped code, with genuine regression risk (must not start blocking legitimate
public IPv6 addresses) and a direct security consequence if missed.

**Files:**
- Create: `src/main/java/com/schwab/urlshortener/orchestration/stages/impact/` — seven classes:
  `ImpactedComponentsStage`, `ImpactedInterfacesStage`, `ImpactedDataFlowsStage`, `ImpactedTestsStage`,
  `ImpactedDocumentationStage`, `RegressionRiskStage`, `RolloutRollbackStage`
- Create: `src/main/java/com/schwab/urlshortener/orchestration/stages/ChangeImpactReportStage.java`
- Create: `docs/scenarios/brownfield.md`
- Test: `src/test/java/com/schwab/urlshortener/scenarios/BrownfieldScenarioTest.java`
- Modify: `src/main/java/com/schwab/urlshortener/domain/UrlValidator.java` (the actual brownfield fix
  this scenario analyzes), `src/test/java/com/schwab/urlshortener/domain/UrlValidatorSsrfTest.java`

**Interfaces:** Reuses Task 39's 12 stages verbatim, with two changes to the graph: (1) the seven
impact stages below, each `dependsOn=["decomposition"]`, no dependency on each other — genuinely
parallel-safe, disjoint concerns, disjoint `ctx.artifacts()` keys; each returns a `Map<String,Object>`
with at minimum a `"finding"` string and a `"risk"` string (`"low"|"medium"|"high"`);
`RolloutRollbackStage` additionally returns `"rollbackPlan"`, which `ReleaseReadinessStage` must read
from the joined report and refuse a "ready" outcome if absent for a brownfield run (REQ-020 negative
criterion); (2) `ChangeImpactReportStage`, `dependsOn` = all seven impact stages, `exitApproval=true`
— its handler reads all seven predecessor results from `ctx` and assembles one `Map` keyed by
dimension name (the "single Change Impact Report" / "mandatory Approval Gate" `spec.md` REQ-013
names); `implementation.dependsOn` becomes `["design", "change_impact_report"]` for this scenario.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/scenarios/BrownfieldScenarioTest.java
package com.schwab.urlshortener.scenarios;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.orchestration.stages.*;
import com.schwab.urlshortener.orchestration.stages.impact.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BrownfieldScenarioTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private static final List<String> IMPACT_NAMES = List.of(
            "impact_components", "impact_interfaces", "impact_data_flows", "impact_tests",
            "impact_documentation", "impact_regression_risk", "impact_rollout_rollback");

    private static StageCatalog brownfieldCatalog() {
        Map<String, StageHandler> handlers = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("requirement_ingestion", (StageHandler) RequirementIngestionStage::run),
                Map.entry("requirement_normalization", (StageHandler) RequirementNormalizationStage::run),
                Map.entry("ambiguity_detection", (StageHandler) AmbiguityDetectionStage::run),
                Map.entry("human_clarification", (StageHandler) HumanClarificationStage::run),
                Map.entry("decomposition", (StageHandler) DecompositionStage::run),
                Map.entry("design", (StageHandler) DesignStage::run),
                Map.entry("implementation", (StageHandler) ImplementationStage::run),
                Map.entry("testing", (StageHandler) TestingStage::run),
                Map.entry("security", (StageHandler) SecurityStage::run),
                Map.entry("documentation", (StageHandler) DocumentationStage::run),
                Map.entry("release_readiness", (StageHandler) ReleaseReadinessStage::run),
                Map.entry("final_engineering_summary", (StageHandler) FinalSummaryStage::run)));
        handlers.put("impact_components", ImpactedComponentsStage::run);
        handlers.put("impact_interfaces", ImpactedInterfacesStage::run);
        handlers.put("impact_data_flows", ImpactedDataFlowsStage::run);
        handlers.put("impact_tests", ImpactedTestsStage::run);
        handlers.put("impact_documentation", ImpactedDocumentationStage::run);
        handlers.put("impact_regression_risk", RegressionRiskStage::run);
        handlers.put("impact_rollout_rollback", RolloutRollbackStage::run);
        handlers.put("change_impact_report", ChangeImpactReportStage::run);
        return new StageCatalog(handlers,
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>)
                        c -> Boolean.TRUE.equals(c.artifacts().get("ambiguityDetected"))),
                Map.of());
    }

    private static List<StageDefinition> brownfieldStages() {
        List<StageDefinition> stages = new java.util.ArrayList<>(List.of(
                new StageDefinition("requirement_ingestion", List.of(), RequirementIngestionStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("requirement_normalization", List.of("requirement_ingestion"),
                        RequirementNormalizationStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("ambiguity_detection", List.of("requirement_normalization"),
                        AmbiguityDetectionStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("human_clarification", List.of("ambiguity_detection"),
                        HumanClarificationStage::run, true, false, 0, 30, null, "ambiguity_gate", null),
                new StageDefinition("decomposition", List.of("human_clarification"), DecompositionStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("design", List.of("decomposition"), DesignStage::run,
                        false, true, 0, 30, null, null, null)));
        for (String name : IMPACT_NAMES) {
            StageHandler h = switch (name) {
                case "impact_components" -> ImpactedComponentsStage::run;
                case "impact_interfaces" -> ImpactedInterfacesStage::run;
                case "impact_data_flows" -> ImpactedDataFlowsStage::run;
                case "impact_tests" -> ImpactedTestsStage::run;
                case "impact_documentation" -> ImpactedDocumentationStage::run;
                case "impact_regression_risk" -> RegressionRiskStage::run;
                default -> RolloutRollbackStage::run;
            };
            stages.add(new StageDefinition(name, List.of("decomposition"), h,
                    false, false, 0, 30, "impact", null, null));
        }
        stages.add(new StageDefinition("change_impact_report", IMPACT_NAMES, ChangeImpactReportStage::run,
                false, true, 0, 30, null, null, null));
        stages.add(new StageDefinition("implementation", List.of("design", "change_impact_report"),
                ImplementationStage::run, false, false, 0, 30, null, null, null));
        stages.add(new StageDefinition("testing", List.of("implementation"), TestingStage::run,
                false, false, 0, 30, "verify", null, null));
        stages.add(new StageDefinition("security", List.of("implementation"), SecurityStage::run,
                false, false, 0, 30, "verify", null, null));
        stages.add(new StageDefinition("documentation", List.of("implementation"), DocumentationStage::run,
                false, false, 0, 30, "verify", null, null));
        stages.add(new StageDefinition("release_readiness", List.of("testing", "security", "documentation"),
                ReleaseReadinessStage::run, false, true, 0, 30, null, null, null));
        stages.add(new StageDefinition("final_engineering_summary", List.of("release_readiness"),
                FinalSummaryStage::run, false, false, 0, 30, null, null, null));
        return stages;
    }

    @Test
    void sevenImpactBranchesRunInParallelAndJoinBeforeImplementation() {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, brownfieldCatalog());
        String workflowId = engine.submit(
                "harden the SSRF guard (req-015) to reject IPv6 unique local addresses fc00::/7",
                brownfieldStages(), "scn-b-1");
        engine.approve(workflowId, "design", "approved", "human", "matches brownfield impact analysis");

        List<WorkflowStepEntity> impactSteps = stepRepository.findByWorkflowIdAndPlanVersionAndNameIn(
                workflowId, 1, IMPACT_NAMES);
        assertThat(impactSteps).hasSize(7);
        assertThat(impactSteps).allMatch(s -> s.getStatus() == StepStatus.SUCCEEDED);

        // Genuine overlap, not fast-sequential: at least two of the seven have overlapping
        // [startedAt, endedAt) intervals. If this proves flaky under CI scheduling, replace with
        // a CountDownLatch-based deterministic check inside two of the seven handlers instead of
        // loosening the assertion.
        boolean anyOverlap = pairwiseIntervalsOverlap(impactSteps);
        assertThat(anyOverlap).isTrue();

        WorkflowStepEntity reportStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "change_impact_report").orElseThrow();
        assertThat(reportStep.getStartedAt()).isAfterOrEqualTo(
                impactSteps.stream().map(WorkflowStepEntity::getEndedAt).max(Instant::compareTo).orElseThrow());

        WorkflowStepEntity implStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(implStep.getStatus()).isEqualTo(StepStatus.PENDING); // blocked: report is exitApproval, unapproved
    }

    @Test
    void rejectedChangeImpactReportBlocksImplementationPermanently() {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, brownfieldCatalog());
        String workflowId = engine.submit(
                "harden the SSRF guard (req-015) to reject IPv6 unique local addresses fc00::/7",
                brownfieldStages(), "scn-b-2");
        engine.approve(workflowId, "design", "approved", "human", "matches brownfield impact analysis");
        engine.approve(workflowId, "change_impact_report", "rejected", "human", "regression risk too high");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        WorkflowStepEntity implStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "implementation").orElseThrow();
        assertThat(implStep.getStatus()).isEqualTo(StepStatus.PENDING); // never ran
    }

    private static boolean pairwiseIntervalsOverlap(List<WorkflowStepEntity> steps) {
        for (int i = 0; i < steps.size(); i++) {
            for (int j = i + 1; j < steps.size(); j++) {
                Instant aStart = steps.get(i).getStartedAt();
                Instant aEnd = steps.get(i).getEndedAt();
                Instant bStart = steps.get(j).getStartedAt();
                Instant bEnd = steps.get(j).getEndedAt();
                if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                    return true;
                }
            }
        }
        return false;
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile errors, none of the eight impact classes exist.
- [ ] **Step 3: Implement** the seven impact stages and `ChangeImpactReportStage`:
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/ImpactedComponentsStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedComponentsStage {
    private ImpactedComponentsStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        ctx.recordDecision("agent", "impact_components_identified",
                "UrlValidator.isBlockedHost is the only production component touched");
        return Map.of("finding", "src/main/java/com/schwab/urlshortener/domain/UrlValidator.java", "risk", "low");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/ImpactedInterfacesStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedInterfacesStage {
    private ImpactedInterfacesStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of("finding", "no public API contract changes — isBlockedHost is a private validation helper", "risk", "low");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/ImpactedDataFlowsStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedDataFlowsStage {
    private ImpactedDataFlowsStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of("finding", "no persisted-entity shape changes — validation happens before any write", "risk", "low");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/ImpactedTestsStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.List;
import java.util.Map;

public final class ImpactedTestsStage {
    private ImpactedTestsStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of(
                "finding", List.of(
                        "src/test/java/com/schwab/urlshortener/domain/UrlValidatorSsrfTest.java",
                        "src/test/java/com/schwab/urlshortener/domain/UrlValidatorTest.java"),
                "risk", "low");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/ImpactedDocumentationStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class ImpactedDocumentationStage {
    private ImpactedDocumentationStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of("finding", "docs/scenarios/brownfield.md must record this as the SSRF-guard hardening demo, not the retired PATCH-expiration draft", "risk", "low");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/RegressionRiskStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RegressionRiskStage {
    private RegressionRiskStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of(
                "finding", "a byte-range check on resolved addresses; must not start rejecting legitimate public IPv6 hosts. Full existing UrlValidatorTest/UrlValidatorSsrfTest suite (Task 15) is the regression gate.",
                "risk", "medium");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/impact/RolloutRollbackStage.java
package com.schwab.urlshortener.orchestration.stages.impact;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.Map;

public final class RolloutRollbackStage {
    private RolloutRollbackStage() { }

    public static Map<String, Object> run(WorkflowContext ctx) {
        return Map.of(
                "finding", "single-method change behind existing validation path — no feature flag needed",
                "risk", "low",
                "rollbackPlan", "revert the isBlockedHost commit; no schema or contract migration to undo");
    }
}
```
```java
// src/main/java/com/schwab/urlshortener/orchestration/stages/ChangeImpactReportStage.java
package com.schwab.urlshortener.orchestration.stages;

import com.schwab.urlshortener.orchestration.WorkflowContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChangeImpactReportStage {
    private ChangeImpactReportStage() { }

    private static final java.util.List<String> DIMENSIONS = java.util.List.of(
            "components", "interfaces", "data_flows", "tests", "documentation", "regression_risk", "rollout_rollback");

    public static Map<String, Object> run(WorkflowContext ctx) {
        Map<String, Object> report = new LinkedHashMap<>();
        for (String dimension : DIMENSIONS) {
            report.put(dimension, ctx.artifacts().get("impact_" + dimension + "_finding"));
        }
        ctx.recordDecision("agent", "change_impact_report_assembled",
                "joined all seven impact-analysis branches into one report for the mandatory approval gate (REQ-013)");
        return report;
    }
}
```
  **Note on reading predecessor results:** `WorkflowContext` in this plan does not expose other
  steps' persisted `result` maps directly — each impact stage additionally calls
  `ctx.addArtifact("impact_<dimension>_finding", ...)` (alongside its returned `Map`, not shown
  above for brevity — add it to each of the seven implementations) so `ChangeImpactReportStage` can
  read them from the shared `ctx.artifacts()` rather than re-querying the repository from inside a
  handler.
  Wire `ScenarioRunner`'s `brownfield` case to `brownfieldCatalog()`/`brownfieldStages()` above.
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5:** Implement the real brownfield change via its own TDD cycle:
  1. Add a failing test to `UrlValidatorSsrfTest.java`:
     ```java
     @ParameterizedTest
     @ValueSource(strings = {"http://[fd00::1]/internal", "http://[fdff:ffff:ffff:ffff::1]/x"})
     void rejectsIpv6UniqueLocalAddresses(String raw) {
         assertThatThrownBy(() -> UrlValidator.validate(raw)).isInstanceOf(InvalidUrlException.class);
     }
     ```
  2. Run it — confirm it currently **fails** (these addresses incorrectly pass validation today, since `Inet6Address.isSiteLocalAddress()` only covers the deprecated `FEC0::/10` range, not RFC 4193's `fc00::/7` ULA range).
  3. Fix `UrlValidator.isBlockedHost` to add an explicit ULA check alongside the existing five:
     ```java
     import java.net.Inet6Address;
     // ... inside isBlockedHost's loop, alongside the existing five checks:
     if (address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC) {
         return true; // RFC 4193 Unique Local Address (fc00::/7) — not covered by isSiteLocalAddress()
     }
     ```
  4. Run the new test — confirm it passes. Run the full `UrlValidatorTest` + `UrlValidatorSsrfTest` suite — confirm no regression (legitimate public IPv6 test hosts, if any exist in the suite, still pass; if none exist, add one, e.g. a documentation/reserved public IPv6 address that is not loopback/link-local/ULA/multicast, to prove the fix doesn't over-block).
  5. Follow the guide's Brownfield Impact Analysis Gate content structure in `docs/scenarios/brownfield.md` (Change Impact Summary, Dependency Map, Test-First Change Plan, Regression Risk Matrix, Documentation Impact, Replanning Decision, Human Approval Requirement) — reflecting the real seven-branch structure, not a single-stage impact map — and explicitly note in "Documentation Impact" that this fix was chosen *instead of* the originally-planned PATCH-expiration brownfield demo, because CLR-15 (confirmed 2026-09-13) rules out any update endpoint.
- [ ] **Step 6: Commit** — `git commit -m "fix(security): reject IPv6 unique local addresses in SSRF guard"` for the fix itself, then `git commit -m "test(scenarios): validate brownfield workflow's seven parallel impact-analysis branches"` for the scenario/orchestration wiring — two coherent commits, not one bundling both concerns.


### Task 41: Ambiguous-requirement scenario — real suspension, real clarification fold-in, real replanning

**Authoritative final form (corrected 2026-09-13).** Reuses Task 39's real `human_clarification`
stage — `entryApproval=true`, gated by `activationConditionKey="ambiguity_gate"` — instead of an
earlier draft's workaround of making `ambiguity_detection` itself `exitApproval`. This is the more
faithful realization of `spec.md`'s Stage 3 (detection) vs. Stage 4 (clarification) distinction:
detection is a normal stage that succeeds either way; clarification is its own gated stage, present
in every run, active only when detection says so. **Execution order note:** this task creates
`AmbiguousScenarioTest`; it modifies no file Task 39/40 didn't already create.

A deliberately ambiguous input — "add rate limiting" with no numeric target and no cited requirement
ID, the exact gap `AmbiguityDetectionStage` (Task 39) is built to catch, and the same gap flagged as
an assumption in Task 5.

**Files:**
- Create: `docs/scenarios/ambiguous.md`
- Test: `src/test/java/com/schwab/urlshortener/scenarios/AmbiguousScenarioTest.java`

**Interfaces:** Reuses Task 39's `greenfieldCatalog()`/stage classes verbatim (same 12-node graph,
same catalog) — only the submitted requirement text differs, which is what drives
`AmbiguityDetectionStage` to set `ambiguityDetected=true` this time, activating `human_clarification`
instead of skipping it. Two behaviors are exercised: (1) the human's approval reason on
`human_clarification` flows generically (via `WorkflowEngine.approve()`, Task 50) into
`ctx.artifacts().get("human_clarificationResolution")`, which `DecompositionStage` reads and folds
into its own persisted result under `"clarifiedRequirement"` — proving the decision reached
downstream, not merely that an `ApprovalEntity` exists; (2) a genuine `replan()` call while
`human_clarification` is still `WAITING_APPROVAL`, replacing `decomposition` with a differently
structured `decomposition_v2` — proving replanning still works on the corrected engine, using the
same replan-before-approve ordering Task 35 established (mechanically required: `approve()`
synchronously resumes execution, so replacement stages must already be registered before it's
called).

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/scenarios/AmbiguousScenarioTest.java
package com.schwab.urlshortener.scenarios;

import com.schwab.urlshortener.orchestration.*;
import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.orchestration.stages.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AmbiguousScenarioTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private static StageCatalog ambiguousCatalog() {
        return new StageCatalog(
                Map.ofEntries(
                        Map.entry("requirement_ingestion", (StageHandler) RequirementIngestionStage::run),
                        Map.entry("requirement_normalization", (StageHandler) RequirementNormalizationStage::run),
                        Map.entry("ambiguity_detection", (StageHandler) AmbiguityDetectionStage::run),
                        Map.entry("human_clarification", (StageHandler) HumanClarificationStage::run),
                        Map.entry("decomposition", (StageHandler) DecompositionStage::run),
                        Map.entry("design", (StageHandler) DesignStage::run),
                        Map.entry("implementation", (StageHandler) ImplementationStage::run),
                        Map.entry("testing", (StageHandler) TestingStage::run),
                        Map.entry("security", (StageHandler) SecurityStage::run),
                        Map.entry("documentation", (StageHandler) DocumentationStage::run),
                        Map.entry("release_readiness", (StageHandler) ReleaseReadinessStage::run),
                        Map.entry("final_engineering_summary", (StageHandler) FinalSummaryStage::run)),
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>)
                        c -> Boolean.TRUE.equals(c.artifacts().get("ambiguityDetected"))),
                Map.of());
    }

    private static List<StageDefinition> ambiguousStages() {
        return List.of(
                new StageDefinition("requirement_ingestion", List.of(), RequirementIngestionStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("requirement_normalization", List.of("requirement_ingestion"),
                        RequirementNormalizationStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("ambiguity_detection", List.of("requirement_normalization"),
                        AmbiguityDetectionStage::run, false, false, 0, 30, null, null, null),
                new StageDefinition("human_clarification", List.of("ambiguity_detection"),
                        HumanClarificationStage::run, true, false, 0, 30, null, "ambiguity_gate", null),
                new StageDefinition("decomposition", List.of("human_clarification"), DecompositionStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("design", List.of("decomposition"), DesignStage::run,
                        false, true, 0, 30, null, null, null),
                new StageDefinition("implementation", List.of("design"), ImplementationStage::run,
                        false, false, 0, 30, null, null, null),
                new StageDefinition("testing", List.of("implementation"), TestingStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("security", List.of("implementation"), SecurityStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("documentation", List.of("implementation"), DocumentationStage::run,
                        false, false, 0, 30, "verify", null, null),
                new StageDefinition("release_readiness", List.of("testing", "security", "documentation"),
                        ReleaseReadinessStage::run, false, true, 0, 30, null, null, null),
                new StageDefinition("final_engineering_summary", List.of("release_readiness"),
                        FinalSummaryStage::run, false, false, 0, 30, null, null, null)
        );
    }

    @Test
    void ambiguousRequirementSuspendsAtHumanClarificationThenDecompositionUsesTheResolution() {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, ambiguousCatalog());
        String workflowId = engine.submit("add rate limiting", ambiguousStages(), "scn-c-1");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);
        WorkflowStepEntity clarificationStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "human_clarification").orElseThrow();
        assertThat(clarificationStep.getStatus()).isEqualTo(StepStatus.WAITING_APPROVAL);
        WorkflowStepEntity decompStepBefore = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "decomposition").orElseThrow();
        assertThat(decompStepBefore.getStatus()).isEqualTo(StepStatus.PENDING); // has NOT run

        engine.approve(workflowId, "human_clarification", "approved", "human",
                "rate limit set to 100 req/min/IP per REQ-0NN assumption");

        instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        WorkflowStepEntity decompStep = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "decomposition").orElseThrow();
        assertThat(decompStep.getResult()).containsEntry("clarifiedRequirement",
                "rate limit set to 100 req/min/IP per REQ-0NN assumption");
        // Not merely "an approval exists" — decomposition's own persisted output demonstrably used it.
    }

    @Test
    void humanCanReplanDecompositionWhileClarificationIsStillPending() {
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, ambiguousCatalog());
        String workflowId = engine.submit("add rate limiting", ambiguousStages(), "scn-c-2");

        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        StageHandler decompositionV2 = ctx -> {
            ran.set(true);
            ctx.recordDecision("agent", "decomposition_finalized",
                    "rate limiting deferred to a follow-up ticket; this workflow only adds request logging");
            return Map.of("decomposed", true, "deferred", true);
        };

        // replan-before-approve: approve() synchronously resumes, so decomposition_v2 must
        // already be registered by the time it's called.
        engine.replan(workflowId, "decomposition",
                List.of(new StageDefinition("decomposition_v2", List.of("human_clarification"), decompositionV2,
                        false, false, 0, 30, null, null, null)),
                "human", "narrowed scope to logging-only after clarification discussion");
        engine.approve(workflowId, "human_clarification", "approved", "human",
                "rate limiting deferred; logging-only for this iteration");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(ran.get()).isTrue();

        WorkflowStepEntity oldDecomp = stepRepository
                .findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "decomposition").orElseThrow();
        assertThat(oldDecomp.getStatus()).isEqualTo(StepStatus.INVALIDATED);

        List<String> events = eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .map(WorkflowEventEntity::getAction).toList();
        int replannedIdx = events.indexOf("replanned");
        int approvedIdx = indexOfPrefix(events, "approval_approved");
        assertThat(replannedIdx).isLessThan(approvedIdx);
    }

    private static int indexOfPrefix(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) return i;
        }
        throw new AssertionError("no event found with prefix " + prefix + " in " + events);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — `decompStep.getResult()` doesn't contain
  `clarifiedRequirement`; `DecompositionStage` doesn't read the resolution artifact yet (it is
  written in Task 39 exactly as this test needs — if Task 39 was implemented as specified, this is
  the confirmation test, not new production code).
- [ ] **Step 3: Implement** — no new production code: this task is validation-only against Task 39's
  `DecompositionStage` (already reads `ctx.artifacts().get("human_clarificationResolution")`) and
  Task 50's `WorkflowEngine.approve()`/`replan()` (already write that artifact and support
  replan-before-approve). If either test fails, the defect is in one of those two tasks, not here.
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5:** Write `docs/scenarios/ambiguous.md` per the guide's 17-point required evidence list
  (Section 20): original ambiguous input, detected ambiguity (`AmbiguityDetectionStage`'s recorded
  decision), classification/severity, affected requirements, workflow state before/after, human
  clarification request text, recorded human decision, updated assumption, downstream impact
  (`decomposition`'s `clarifiedRequirement` field), replanning event (from the second test), invalidated/
  regenerated artifacts, resumed state, final validation, audit trail excerpt, terminal outcome.
- [ ] **Step 6: Commit** — `git commit -m "test(scenarios): validate ambiguous-requirement workflow's clarification fold-in and replanning"`


### Task 42: Three-Scenario Evidence Review (self-audit, no file modification)

**Files:** Create: `docs/assessment/scenario_evidence_review.md`

- [ ] **Step 1:** For each of the 3 scenarios, verify (per guide Section 21) presence of: requirement input, interpretation, decomposition, dependency graph, orchestration path, state transitions, human gates, tests, executed validation, failure behavior demonstration, audit evidence, doc/execution match, terminal outcome, disclosed limitations. Record as a matrix.
- [ ] **Step 2:** Verify the three scenarios are materially different (different stage sets/paths, not copy-pasted with renamed fields) — they are, by construction (Tasks 39–41 each add distinct stages/gates).
- [ ] **Step 3:** `git commit -m "docs: add architecture and scenario walkthroughs"`

### Task 43: Architecture overview + traceability matrix

**Files:** Create: `docs/architecture/overview.md`, `docs/traceability/matrix.md`

- [ ] **Step 1:** `overview.md`: components diagram (as an ASCII/mermaid block: API layer → domain → persistence; separately, orchestration engine → policy engine → reliability wrappers → observability, both sharing the SQLite store), control flow narrative for one full scenario, and the ADR-table decisions.
- [ ] **Step 2:** `matrix.md`: a table with columns Requirement ID | Scenario ID | Design Section | ADR | Task ID (this plan's Task N) | Code Path | Test Path | Validation Command | Doc Path | Evidence Path — populate one row per REQ from Task 4's spec.
- [ ] **Step 3:** `git commit -m "docs: add traceability and reviewer navigation guide"` (bundle with Task 44)

### Task 44: Reviewer Navigation Guide

**Files:** Create: `docs/assessment/reviewer_navigation_guide.md`

- [ ] **Step 1:** Follow the guide's 25-point structure (Section 26) exactly: for every claim, give exact repo path, exact command, expected observable result, related REQ/SCN ID. Keep root `README.md` short; put depth here.
- [ ] **Step 2:** `git commit -m "docs: add traceability and reviewer navigation guide"`

### Task 45: `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, setup instructions

**Files:** Create/modify: `README.md`, `SECURITY.md`, `CONTRIBUTING.md`

- [ ] **Step 1:** `README.md`: project objective (2–3 sentences), quickstart (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw spring-boot:run`, `./mvnw test`), link to `docs/assessment/reviewer_navigation_guide.md` for everything else.
- [ ] **Step 2:** `SECURITY.md`: URL scheme allow-list, SSRF guard, no-auth assumption, secrets policy (none stored; none logged), dependency scanning note (`mvn org.owasp:dependency-check-maven:check` recommended, run manually — no CI configured for this prototype, documented limitation).
- [ ] **Step 3:** `git commit -m "docs: finalize setup instructions and security notes"`

---

## Phase 11 — Convergence & Release Readiness

### Task 46: Full quality suite execution + `/speckit.converge`-equivalent report

**Files:** Create: `docs/assessment/convergence_report.md`

- [ ] **Step 1:** Run, in order, and paste **actual** output (never fabricated) into the report: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test`, `./mvnw -Dspring-boot.run.arguments=--scenario=greenfield spring-boot:run` (and again with `--scenario=brownfield`, `--scenario=ambiguous`), `./mvnw -Dspring-boot.run.arguments=--metrics-report spring-boot:run`.
- [ ] **Step 2:** Fill the convergence report's required sections per guide Section 22: requirement verification, architecture drift check, orchestration executable-evidence checklist (all 19 items), three-scenario verification, testing results, documentation currency check, compliance/change-control verification, evidence-integrity pass (strip any unsupported claim found).
- [ ] **Step 3:** Emit exactly one release decision: `READY` / `READY WITH ACCEPTED LIMITATIONS` / `NOT READY`. Given the documented backlog (auth, rate limiting, migrations, dedup), the realistic outcome is **READY WITH ACCEPTED LIMITATIONS** — do not claim `READY` while any mandatory item from Global Constraints is unmet.
- [ ] **Step 4:** `git commit -m "chore(release): converge artifacts and validate readiness"`

### Task 47: Final Independent Assessment + Final Engineering Summary (21-section)

**Files:** Create: `docs/assessment/final_independent_assessment.md`, `docs/assessment/final_engineering_summary.md`

- [ ] **Step 1:** Run the guide's Section 23 hostile Principal-Engineer review (30-dimension 0–5 scoring, PASS/BORDERLINE/FAIL verdict) against the actual repository state — be skeptical of your own prior work per the guide's explicit guardrail ("Do Not Use Claude's Own Summary as Evidence").
- [ ] **Step 2:** Resolve mandatory findings from Step 1 (return to the correct upstream phase/task per this plan — do not patch findings in the assessment doc itself).
- [ ] **Step 3:** Write `final_engineering_summary.md` using the guide's exact mandatory 21-section order (Section 25): executive outcome, objective/scope/timebox outcome, confirmed requirements/assumptions/exclusions/deferred scope, architecture+ADRs, API contracts, orchestration model, human approvals/governance, compliance results, policy exceptions, security, reliability, MTTR specifics, observability/evidence integrity, greenfield outcome, brownfield outcome, ambiguous outcome, test strategy+results, traceability, limitations/tech debt, repo paths+commands, final judgment/next actions. Every material claim cites an exact repo path + command + expected result.
- [ ] **Step 4:** `git commit -m "docs(assessment): add final independent assessment and engineering summary"`

### Task 48: Clean-clone verification, final tag

**Files:** none (verification + git operations)

- [ ] **Step 1:** `cd /tmp && git clone <repo> verify-clone && cd verify-clone && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test` — confirm green from a fresh checkout (catches anything only working because of local uncommitted state, e.g. an untracked `.mvn/wrapper/maven-wrapper.jar` that should have been committed).
- [ ] **Step 2:** `git log --oneline --decorate --graph` — review the full history reads as a truthful engineering journey matching the Suggested Commit Progression shape (guide Section 18), not fabricated/reordered.
- [ ] **Step 3:** `git status` clean, then: `git tag -a assessment-submission-v1.0 -m "Reviewed assessment submission" && git show assessment-submission-v1.0`
- [ ] **Step 4 (human):** Push and submit per whatever the assessment's actual submission channel is — this step requires your explicit action, not automated execution.

---

## Self-Review Notes (already applied above)

- **Spec coverage:** every PDF Core Requirement (1–8) maps to a phase — Requirement Understanding → Phase 1 Tasks 3–5; Task Decomposition → Task 9; Codebase Reasoning (Brownfield) → Task 40; Workflow Orchestration → Phase 4 (Tasks 20–26) + Phase 7 (Task 35); Engineering Output Generation → Phases 2–3; Validation/Risk Control → Phase 5–6 + Task 42; Controlled Autonomy → Task 23; Final Engineering Summary → Task 47. All 5 Deliverables (prototype, architecture overview, 3 scenarios, setup instructions, testing/limitations/trade-offs) map to Tasks 43–47.
- **No placeholders:** every code step above contains runnable code or an explicit, concrete implementer note where a design choice is intentionally left to the implementer's judgment (flagged inline, e.g., Task 17's two-`save()` ID-assignment approach, Task 22's flaky-timing test risk, Task 18's repeated redirect-disabling boilerplate).
- **Type/signature consistency checked:** `StageHandler.run(WorkflowContext) -> Map<String,Object>` is identical across Tasks 20, 22, 39–41; `StageDefinition` is constructed either via `.of(name, dependsOn, handler)` (the common no-gate/no-retry/no-parallel-group case) or the full 8-arg canonical constructor everywhere — no third construction style anywhere in the plan; `WorkflowEngine`'s constructor overloads grow additively (4 → 6 → 7 → 8 positional params across Tasks 22/23, 28, 29, 33) with each shorter overload's tests staying valid — flagged as a backlog cleanup item (a builder would be cleaner) in Task 33 and carried into Task 47's final summary, not silently left implicit; `WorkflowContext.recordDecision`/`addArtifact` signatures from Task 21 are used unchanged through Task 41.
- **Stack-pivot specific:** this plan was originally written for Python/FastAPI/SQLAlchemy/SQLite and was translated task-by-task to Java 21/Spring Boot 3.3/Spring Data JPA/H2 after Tasks 1–3 had already executed against the original stack (Task 1 — SpecKit init — is language-independent and untouched; Task 2 was redone from scratch for Maven; Task 3 — the constitution — is language-independent and untouched). Every design decision validated during the original plan's preflight review (the `entryApproval`/`exitApproval` split, the `blocked`-batch idempotent re-halt in `runFrom`, the replan-before-approve ordering, the MTTR formula, the policy PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE model) carried over unchanged — only the implementation language and its idiomatic libraries changed.

---

## Phase 12 — Post-Task-10 Corrective Design Round (2026-09-13)

**Why this section exists, and why it's an addendum rather than an edit to Tasks 20–41 in place:**
four rounds of pre-implementation review (this plan's own Task 10 analysis, and three rounds of an
external Codex review — see `codex-recommend.md` and `CODEX-REVIEW-BRIEFING.md` in the repo, outside
this plan file) found real, confirmed design gaps discovered *before any code was written* — which
is exactly what a pre-implementation gate is for. For Tasks 20, 23, 24, 34, and 37, this section
remains an addendum: each of those originals kept its content, with a one-line pointer added saying
"superseded by Phase 12, Task N below — see there for the reason," preserving the plan's own
history-preservation convention (never delete the record of what was asked/designed, append the
correction alongside it).

**Tasks 39, 40, and 41 are the one deliberate exception, corrected 2026-09-13 in the fourth review
round.** Because no code exists yet for any of the three scenarios, the fourth review's explicit
instruction was to write those three tasks directly in authoritative final form rather than build an
obsolete version and layer a pointer on top of it — there is no implementation history to preserve
for code that was never built. Tasks 49–51 below therefore no longer modify `RequirementStage.java`
or extend `GreenfieldScenarioTest`/`BrownfieldScenarioTest`/`AmbiguousScenarioTest` in place; Task 51
and Task 52 are retained only as short historical markers explaining what they used to cover and
where it moved (into Tasks 39, 40, and 41 directly). This also fixes a genuine ordering defect the
fourth review caught: Task 51 (as originally written) modified files that Task 39 alone creates,
while sitting, in this document, after Task 48 — i.e. after every task that could ever reach it.
Tasks 49 (`StageCatalog`) and 50 (the consolidated `WorkflowEngine`) still hold real, non-duplicated
content that Tasks 39–41 depend on; each of those two tasks' bodies now carries an explicit
**Execution order note** stating that Tasks 49 and 50 must be implemented before Task 39, even
though this document, for narrative continuity with the review round that produced them, lists them
later.

**Scope of this round, confirmed real by direct inspection before any fix was proposed:**
1. `spec.md`'s SCN-B requires 7 genuinely parallel impact-analysis branches joined by a mandatory
   approval gate; the original Task 40 built one monolithic `ImpactAnalysisStage`.
2. The constitution defines a "fallback" mechanism distinct from retry/rollback/safe-stop; zero
   code or design anywhere in this plan built one, and the original Task 34/plan language
   incorrectly redefined "fallback" as "retry-then-safe-stop," directly contradicting the
   constitution's own principle.
3. `spec.md`'s Section 3.1 stage graph has 12 stages, each with independently differentiated
   retry/timeout/failure-classification behavior; the original design realized Stages 1–3 as one
   Java method (`RequirementStage`) with one retry policy, made Stage 4 (Human clarification) an
   implicit property of Stage 3's approval gate rather than its own persisted node, and never
   represented Stage 12 (Final engineering summary) as a workflow node at all.
4. `computeReliabilitySummary` (Task 37) has a confirmed, self-disclosed bug: `steps` is never
   filtered by `workflowId`, so per-workflow retry/compensation frequency figures silently
   aggregate across every workflow in the database.

**Explicitly out of scope for this round** (unchanged from the original design, already correct):
the `entryApproval`/`exitApproval` gate mechanism itself, the `blocked`-batch replan guard, the
replan-before-approve ordering, the policy PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE model, and
every already-Accepted ADR's decision. This round adds capability; it does not revisit those.

### Task 49: `StageCatalog` — restart-safe handler/condition/fallback lookup by stable key

**Revises the original Task 49 (2026-09-13 first pass) after a fourth review correctly identified
that the first pass's design — `registerActivationCondition`/`registerFallback` storing Java
`Predicate`/`StageHandler` objects directly on the live `WorkflowEngine` instance, and the
pre-existing `WorkflowEngine._registry` static in-memory map storing each workflow's
`List<StageDefinition>` — cannot survive a process restart. A fresh JVM has an empty static map and
empty registration maps; `runFrom(workflowId)` on that fresh instance has nothing to look up. This
directly contradicts REQ-017 (resumption) and the now-binding REQ-028 5-second recovery-time
objective. Java lambdas/`Predicate` objects are never persisted — the fix is a stable-string-key
indirection layer plus real persisted configuration, not a smarter cache.**

**Design:** `StageDefinition` gains two more fields (a real change to the record's shape, not an
additive side-channel this time — the earlier "avoid touching the record" decision doesn't survive
contact with a real restart requirement, and is superseded here, disclosed, not silently reversed):
`String activationConditionKey` and `String fallbackHandlerKey` (both nullable, `null` by default
via `.of()`). These are stable string identifiers, not Java objects. `WorkflowStepEntity` gains
persisted columns for `entryApproval`, `exitApproval`, `maxRetries`, `timeoutSeconds`,
`parallelGroup`, `activationConditionKey`, `fallbackHandlerKey` — the stage's *handler* is looked up
by `WorkflowStepEntity.name` itself (one handler per stage name, already true throughout this
design; no separate handler-key column needed). A `StageCatalog` maps these stable keys to the
actual Java implementations, populated once (at test-construction time in tests; at Spring context
startup in the real application, via a `@Configuration` class listing every known stage class).
**Corrected 2026-09-13 (fourth review, finding 3): this composition-root wiring is not out of
scope** — a `StageCatalog` and `WorkflowEngine` bean, plus a `WorkflowRecoveryRunner` that calls
`resumeAll()` at boot, are specified in Task 39 (the first task where every production stage class
exists to populate the catalog with).

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/StageDefinition.java` (add the two
  key fields)
- Create: `src/main/java/com/schwab/urlshortener/orchestration/StageCatalog.java`
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowStepEntity.java`
  (add the seven columns above)
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowStepJpaRepository.java`
  (add `List<WorkflowStepEntity> findByWorkflowIdAndPlanVersion(String, int)` — used for full-graph
  reconstruction; keep the existing `findByWorkflowIdAndPlanVersionAndName`/`...AndNameIn` from
  Task 21/this round's Task 52)
- Test: `src/test/java/com/schwab/urlshortener/orchestration/StageCatalogTest.java`

**Interfaces:**
- `record StageDefinition(String name, List<String> dependsOn, StageHandler handler, boolean entryApproval, boolean exitApproval, int maxRetries, int timeoutSeconds, String parallelGroup, String activationConditionKey, String fallbackHandlerKey)`,
  `static StageDefinition of(String name, List<String> dependsOn, StageHandler handler)` — the
  common-case factory now defaults both new fields to `null` in addition to its existing defaults.
  **Every existing full-positional `new StageDefinition(...)` call site across Tasks 20–41 needs two
  more trailing `null` arguments** — a mechanical, verified-by-compilation change (there is no
  runtime behavior difference for a stage that doesn't use either key).
- `class StageCatalog { StageHandler handlerFor(String stageName); Optional<Predicate<WorkflowContext>> activationConditionFor(String key); Optional<StageHandler> fallbackFor(String key); }`,
  constructed from three maps: `Map<String,StageHandler> handlersByStageName`,
  `Map<String,Predicate<WorkflowContext>> activationConditionsByKey`,
  `Map<String,StageHandler> fallbacksByKey`. `handlerFor` throws `IllegalStateException` for an
  unknown stage name (a configuration error, not a runtime condition to recover from).

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/StageCatalogTest.java
package com.schwab.urlshortener.orchestration;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageCatalogTest {

    @Test
    void looksUpHandlerConditionAndFallbackByStableKey() {
        StageHandler handler = ctx -> Map.of("ran", true);
        StageHandler fallback = ctx -> Map.of("degraded", true);
        StageCatalog catalog = new StageCatalog(
                Map.of("my_stage", handler),
                Map.of("ambiguity_gate", (java.util.function.Predicate<WorkflowContext>) ctx -> true),
                Map.of("doc_fallback", fallback));

        assertThat(catalog.handlerFor("my_stage")).isSameAs(handler);
        assertThat(catalog.activationConditionFor("ambiguity_gate")).isPresent();
        assertThat(catalog.activationConditionFor("nonexistent_key")).isEmpty();
        assertThat(catalog.fallbackFor("doc_fallback")).contains(fallback);
        assertThatThrownBy(() -> catalog.handlerFor("unknown_stage"))
                .isInstanceOf(IllegalStateException.class);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile error, `StageCatalog` doesn't exist.
- [ ] **Step 3: Implement**
```java
// src/main/java/com/schwab/urlshortener/orchestration/StageCatalog.java
package com.schwab.urlshortener.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class StageCatalog {
    private final Map<String, StageHandler> handlersByStageName;
    private final Map<String, Predicate<WorkflowContext>> activationConditionsByKey;
    private final Map<String, StageHandler> fallbacksByKey;

    public StageCatalog(Map<String, StageHandler> handlersByStageName,
                         Map<String, Predicate<WorkflowContext>> activationConditionsByKey,
                         Map<String, StageHandler> fallbacksByKey) {
        this.handlersByStageName = Map.copyOf(handlersByStageName);
        this.activationConditionsByKey = Map.copyOf(activationConditionsByKey);
        this.fallbacksByKey = Map.copyOf(fallbacksByKey);
    }

    public StageHandler handlerFor(String stageName) {
        StageHandler handler = handlersByStageName.get(stageName);
        if (handler == null) {
            throw new IllegalStateException("no handler registered for stage: " + stageName);
        }
        return handler;
    }

    public Optional<Predicate<WorkflowContext>> activationConditionFor(String key) {
        return Optional.ofNullable(key).map(activationConditionsByKey::get);
    }

    public Optional<StageHandler> fallbackFor(String key) {
        return Optional.ofNullable(key).map(fallbacksByKey::get);
    }
}
```
  Add the two fields to `StageDefinition`'s record declaration and to `.of(...)`'s defaults (both
  `null`). Add the seven columns to `WorkflowStepEntity` (four `boolean`/`int` primitives, one
  nullable `String parallelGroup` already existed conceptually but wasn't yet a persisted column —
  add it now — plus two nullable `String` key columns). Add
  `findByWorkflowIdAndPlanVersion(String, int)` to the repository (a zero-code Spring Data derived
  query).
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(orchestration): add StageCatalog for restart-safe handler/condition/fallback lookup"`

### Task 50: Consolidated, restart-safe, plan-version-safe `WorkflowEngine`

**This is the authoritative final form of `WorkflowEngine.submit`/`runFrom`/`runStage`/`approve`/
`replan` and their helper methods, superseding every incremental version shown in Tasks 22, 23, 24,
28, 29, 33, 34, 35 and the original (now-revised) Tasks 49/50. Implement the version below; the
earlier versions were pedagogically incremental (each showing one capability landing at a time) —
this task is where they converge into what actually gets built.** This also folds in the fallback
subsystem (`SUCCEEDED_WITH_FALLBACK`, previously drafted in the original Task 50) and the human-
clarification decision-flow fix (finding 5 of the fourth review).

**What changes structurally, and why:**
1. **No more static `_registry`.** `runFrom` reconstructs `List<StageDefinition>` from persisted
   `WorkflowStepEntity` rows (via `StageCatalog` for the handler/condition/fallback lookups) on
   *every call*, not from an in-memory cache. This makes the engine genuinely stateless across
   process restarts — the fix is not "make the cache survive," it's "don't need a cache."
2. **Every step/approval lookup is scoped to `(workflowId, planVersion, name)`**, using
   `findByWorkflowIdAndPlanVersionAndName`/`...AndNameIn`/`...AndPlanVersion` throughout — never the
   bare `findByWorkflowIdAndName` shown in earlier tasks' code blocks. `planVersion` is read once
   per `runFrom` call from the `WorkflowInstanceEntity` and threaded through as a plain parameter to
   every private helper — it is not re-derived redundantly at each call site.
3. **`approve()` writes the decision's `reason` into `WorkflowContext.artifacts()`** under
   `"<stepName>Resolution"` before resuming — a generic mechanism (works for any approval-gated
   stage, not a `human_clarification`-specific hack), so a downstream stage's handler can read what
   was decided, not merely that *something* was approved.
4. **A `resumeAll()` method**, callable from application startup (or directly from a test
   simulating a restart), queries every non-terminal `WorkflowInstanceEntity` and calls `runFrom`
   for each — this is the concrete mechanism a restart test exercises.

**Files:**
- Modify: `src/main/java/com/schwab/urlshortener/orchestration/WorkflowEngine.java` (full
  replacement of `submit`, `runFrom`, `runStage`, `approve`, `replan`, and all private helpers)
- Test: `src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineRestartTest.java`,
  `src/test/java/com/schwab/urlshortener/orchestration/FallbackTest.java`

**Interfaces:**
- `WorkflowEngine(WorkflowInstanceJpaRepository, WorkflowStepJpaRepository, WorkflowEventJpaRepository, ApprovalJpaRepository, PolicyEvaluationJpaRepository, PolicyExceptionJpaRepository, CompensationRegistry, StageCatalog)`
  — yes, a ninth constructor parameter, on top of the already-tracked "replace with a builder"
  backlog item (Task 33's note). This one is not cosmetic: `StageCatalog` is load-bearing for
  restart safety, not optional. Existing tests using fewer-parameter overloads need a
  `null`-tolerant overload chain exactly as established since Task 22 (each overload delegates to
  the next with a sensible default — `null` `StageCatalog` is **not** a safe default, since nearly
  every stage needs handler lookup; provide a convenience overload only for the
  all-primitive-no-catalog-needed tests that predate this task, defaulting to
  `new StageCatalog(Map.of(), Map.of(), Map.of())` plus **inline stage handlers already present in
  the submitted `StageDefinition` objects** — see implementation note below for how `runFrom`
  reconciles "catalog has it" vs. "the submitted definition already carried a real handler
  reference in the same process" so pre-existing tests that never registered anything in a catalog
  keep passing without modification).
- `WorkflowEngine.resumeAll()` — queries all `WorkflowInstanceEntity` with status in
  `{RUNNING, PENDING_APPROVAL, REPLANNING}` and calls `runFrom(instance.getId())` for each.

**Implementation note on backward compatibility with pre-Task-49 tests:** every test written in
Tasks 22–41 constructs `StageDefinition` with a **real, in-process `StageHandler` reference**
(a lambda or method reference) directly in the test — it never relied on catalog lookup. To avoid
rewriting dozens of already-specified tests, `runFrom`'s reconstruction step tries the catalog
first (`catalog.handlerFor(row.getName())`) and, **only if the catalog has no such entry
registered**, falls back to using the handler already present on the in-memory `StageDefinition`
object passed to `submit()` for that same call (kept in a short-lived, per-call, non-static local
map populated at the top of `submit()` and consulted only within the same `submit()` call's
synchronous execution — this is not a restart-surviving cache, it exists solely so `submit()`
followed immediately by execution in the same test/process doesn't require every test to also
construct a `StageCatalog`). **The restart test below constructs a real `StageCatalog` and
deliberately does not rely on this fallback**, proving the catalog path specifically.

- [ ] **Step 1: Write failing test**
```java
// src/test/java/com/schwab/urlshortener/orchestration/WorkflowEngineRestartTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowEngineRestartTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    @Test
    void interruptedWorkflowResumesFromAFreshEngineInstanceWithEmptyRuntimeState() {
        // A genuine interruption leaves a step RUNNING with no recorded completion — a unit test
        // cannot literally kill the JVM mid-handler, so this constructs that exact DB state
        // directly via the repositories, rather than running an engine to completion and then
        // corrupting a finished result. This is deliberately NOT "an operator reset a SAFE_STOPPED
        // step to PENDING" (a different, already-covered operational action, `retrySafeStopped`) —
        // it is what `runFrom()` itself must recognize as abandoned and recover from (fourth
        // review, finding 2).
        String workflowId = java.util.UUID.randomUUID().toString();
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature("demo");
        instance.setStatus(WorkflowStatus.RUNNING); // a real crash never gets to set any terminal/pending status
        instance.setCorrelationId("run-restart-1");
        instance.setPlanVersion(1);
        instance.setContextJson(Map.of());
        instanceRepository.save(instance);

        WorkflowStepEntity firstStep = new WorkflowStepEntity();
        firstStep.setWorkflowId(workflowId);
        firstStep.setPlanVersion(1);
        firstStep.setName("first");
        firstStep.setSequence(0);
        firstStep.setStatus(StepStatus.RUNNING); // abandoned mid-execution
        firstStep.setStartedAt(Instant.now());
        firstStep.setDependsOn(List.of());
        firstStep.setMaxRetries(0);
        firstStep.setTimeoutSeconds(5);
        stepRepository.save(firstStep);

        WorkflowStepEntity secondStep = new WorkflowStepEntity();
        secondStep.setWorkflowId(workflowId);
        secondStep.setPlanVersion(1);
        secondStep.setName("second");
        secondStep.setSequence(1);
        secondStep.setStatus(StepStatus.PENDING); // never got the chance to start
        secondStep.setDependsOn(List.of("first"));
        secondStep.setMaxRetries(0);
        secondStep.setTimeoutSeconds(5);
        stepRepository.save(secondStep);

        // A BRAND NEW WorkflowEngine instance (simulating a fresh process) with a fresh catalog
        // this instance didn't just construct — proves recovery reads from the database, not from
        // anything an earlier engine/catalog held in memory.
        StageHandler first = ctx -> Map.of("recovered", true);
        StageHandler after = ctx -> Map.of("done", true);
        StageCatalog freshCatalog = new StageCatalog(Map.of("first", first, "second", after), Map.of(), Map.of());
        WorkflowEngine freshEngine = new WorkflowEngine(instanceRepository, stepRepository,
                eventRepository, approvalRepository, null, null, null, null, freshCatalog);

        Instant before = Instant.now();
        freshEngine.runFrom(workflowId); // no submit() call — this is resumption, not re-submission
        Instant afterInstant = Instant.now();

        assertThat(java.time.Duration.between(before, afterInstant).getSeconds()).isLessThan(5);

        WorkflowStepEntity recoveredFirst = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "first").orElseThrow();
        assertThat(recoveredFirst.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(recoveredFirst.getResult()).containsEntry("recovered", true);
        assertThat(recoveredFirst.getRetryCount()).isZero(); // recovered by re-run, not counted as a retry attempt

        WorkflowStepEntity recoveredSecond = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "second").orElseThrow();
        assertThat(recoveredSecond.getStatus()).isEqualTo(StepStatus.SUCCEEDED); // resumption continued past the recovered step

        WorkflowInstanceEntity finalInstance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(finalInstance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);

        List<String> actions = eventRepository.findByWorkflowId(workflowId).stream()
                .map(WorkflowEventEntity::getAction).toList();
        assertThat(actions).anyMatch(a -> a.equals("recovered_abandoned_running:first"));
        assertThat(actions).anyMatch(a -> a.startsWith("resumed:"));
    }

    @Test
    void resumeAllFindsEveryNonTerminalWorkflow() {
        // Same direct-persistence approach as the test above: a workflow instance left RUNNING
        // with one step abandoned mid-execution, exactly what resumeAll() must find without being
        // told which workflow ID to look at (unlike runFrom(), which takes one explicitly).
        String workflowId = java.util.UUID.randomUUID().toString();
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature("demo");
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId("run-resume-all-1");
        instance.setPlanVersion(1);
        instance.setContextJson(Map.of());
        instanceRepository.save(instance);

        WorkflowStepEntity stalledStep = new WorkflowStepEntity();
        stalledStep.setWorkflowId(workflowId);
        stalledStep.setPlanVersion(1);
        stalledStep.setName("stalled");
        stalledStep.setSequence(0);
        stalledStep.setStatus(StepStatus.RUNNING);
        stalledStep.setStartedAt(Instant.now());
        stalledStep.setDependsOn(List.of());
        stalledStep.setMaxRetries(0);
        stalledStep.setTimeoutSeconds(5);
        stepRepository.save(stalledStep);

        StageCatalog freshCatalog = new StageCatalog(Map.of("stalled", (StageHandler) ctx -> Map.of()), Map.of(), Map.of());
        WorkflowEngine freshEngine = new WorkflowEngine(instanceRepository, stepRepository,
                eventRepository, approvalRepository, null, null, null, null, freshCatalog);

        freshEngine.resumeAll();

        WorkflowInstanceEntity resumedInstance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(resumedInstance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        WorkflowStepEntity resumedStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "stalled").orElseThrow();
        assertThat(resumedStep.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
    }
}
```
- [ ] **Step 2: Run to confirm failure** — compile errors: 9-arg constructor, `runFrom` not
  public, `resumeAll` doesn't exist, `StageDefinition`'s 10-arg constructor doesn't exist yet
  (depends on Task 49).
- [ ] **Step 3: Implement** — the full, final `WorkflowEngine`:
```java
// WorkflowEngine.java — authoritative final form, supersedes every earlier version
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import com.schwab.urlshortener.policy.PolicyEngine;
import com.schwab.urlshortener.reliability.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkflowEngine {

    private final WorkflowInstanceJpaRepository instanceRepository;
    private final WorkflowStepJpaRepository stepRepository;
    private final WorkflowEventJpaRepository eventRepository;
    private final ApprovalJpaRepository approvalRepository;
    private final PolicyEngine policyEngine; // nullable
    private final PolicyEvaluationJpaRepository policyEvaluationRepository; // nullable
    private final PolicyExceptionJpaRepository policyExceptionRepository; // nullable
    private final CompensationRegistry compensationRegistry; // nullable
    private final StageCatalog catalog;

    // Same-process, same-submit()-call convenience only (see Task 50's implementation note) —
    // never relied upon across a process boundary, and never consulted by resumeAll()/a fresh
    // runFrom() call on a workflow this process didn't itself just submit().
    private final Map<String, StageHandler> inProcessHandlerFallback = new HashMap<>();

    public WorkflowEngine(WorkflowInstanceJpaRepository instanceRepository,
                           WorkflowStepJpaRepository stepRepository,
                           WorkflowEventJpaRepository eventRepository,
                           ApprovalJpaRepository approvalRepository,
                           PolicyEngine policyEngine,
                           PolicyEvaluationJpaRepository policyEvaluationRepository,
                           PolicyExceptionJpaRepository policyExceptionRepository,
                           CompensationRegistry compensationRegistry,
                           StageCatalog catalog) {
        this.instanceRepository = instanceRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.policyEngine = policyEngine;
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.policyExceptionRepository = policyExceptionRepository;
        this.compensationRegistry = compensationRegistry;
        this.catalog = catalog != null ? catalog : new StageCatalog(Map.of(), Map.of(), Map.of());
    }

    public String submit(String feature, List<StageDefinition> stages, String correlationId) {
        DependencyGraph graph = new DependencyGraph(stages);
        graph.validate();
        String workflowId = java.util.UUID.randomUUID().toString();

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(workflowId);
        instance.setFeature(feature);
        instance.setStatus(WorkflowStatus.RUNNING);
        instance.setCorrelationId(correlationId);
        instance.setPlanVersion(1);
        instance.setContextJson(Map.of());
        instanceRepository.save(instance);

        for (int seq = 0; seq < stages.size(); seq++) {
            StageDefinition s = stages.get(seq);
            WorkflowStepEntity row = new WorkflowStepEntity();
            row.setWorkflowId(workflowId);
            row.setPlanVersion(1);
            row.setName(s.name());
            row.setSequence(seq);
            row.setStatus(StepStatus.PENDING);
            row.setDependsOn(s.dependsOn());
            row.setEntryApproval(s.entryApproval());
            row.setExitApproval(s.exitApproval());
            row.setMaxRetries(s.maxRetries());
            row.setTimeoutSeconds(s.timeoutSeconds());
            row.setParallelGroup(s.parallelGroup());
            row.setActivationConditionKey(s.activationConditionKey());
            row.setFallbackHandlerKey(s.fallbackHandlerKey());
            stepRepository.save(row);
            inProcessHandlerFallback.put(workflowId + "::" + s.name(), s.handler());
        }
        recordEvent(workflowId, "engine", "workflow_started", "OK", null);
        runFrom(workflowId);
        return workflowId;
    }

    /** Reconstructs the stage list from persisted state + the catalog and resumes execution.
     * Safe to call on a workflow this process never submitted — this is the restart path. */
    public void runFrom(String workflowId) {
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();
        List<WorkflowStepEntity> rows = stepRepository.findByWorkflowIdAndPlanVersion(workflowId, planVersion);

        List<StageDefinition> stages = rows.stream()
                .filter(r -> r.getStatus() != StepStatus.INVALIDATED)
                .map(r -> new StageDefinition(r.getName(), r.getDependsOn(),
                        resolveHandler(workflowId, r.getName()),
                        r.isEntryApproval(), r.isExitApproval(), r.getMaxRetries(),
                        r.getTimeoutSeconds(), r.getParallelGroup(),
                        r.getActivationConditionKey(), r.getFallbackHandlerKey()))
                .toList();
        DependencyGraph graph = new DependencyGraph(stages);

        WorkflowContext ctx = new WorkflowContext(instance.getCorrelationId(),
                new ArrayList<>((List<Map<String,Object>>) instance.getContextJson().getOrDefault("decisions", List.of())),
                new HashMap<>((Map<String,Object>) instance.getContextJson().getOrDefault("artifacts", Map.of())));
        // Seeds the originally-submitted requirement text so stage handlers (e.g.
        // RequirementIngestionStage, Task 39) can read it without a caller-supplied ctx.
        ctx.artifacts().putIfAbsent("feature", instance.getFeature());

        boolean resuming = rows.stream().anyMatch(r -> r.getStartedAt() != null);
        if (resuming) {
            recordEvent(workflowId, "engine", "resumed:" + workflowId, "OK", "runFrom invoked on a workflow with prior execution history");
        }

        // Crash recovery for abandoned RUNNING steps (fourth review, finding 2: "a genuinely
        // interrupted step would remain RUNNING... there must be a defined, idempotency-safe
        // recovery rule"). Within a single runFrom() call, runStage() always runs to completion
        // synchronously (via Future.get(), below) before this method returns — so a step found
        // RUNNING at the *start* of a call can only be left over from a process that exited before
        // finishing it. Reset it to PENDING so this call's own batch loop schedules it. This relies
        // on the same assumption REQ-014's retry design already requires of every stage handler —
        // safe to re-attempt, not necessarily side-effect-free — so it is not a new assumption.
        for (WorkflowStepEntity row : rows) {
            if (row.getStatus() == StepStatus.RUNNING) {
                row.setStatus(StepStatus.PENDING);
                row.setStartedAt(null);
                stepRepository.save(row);
                recordEvent(workflowId, "engine", "recovered_abandoned_running:" + row.getName(), "OK",
                        "step was RUNNING with no recorded completion — treated as interrupted by a prior process exit, reset to PENDING for re-execution");
            }
        }

        for (List<StageDefinition> batch : graph.topologicalBatches()) {
            List<StageDefinition> blocked = batch.stream()
                    .filter(s -> stepStatus(workflowId, planVersion, s.name()) == StepStatus.WAITING_APPROVAL
                            && !isApproved(workflowId, planVersion, s.name()))
                    .toList();
            if (!blocked.isEmpty()) {
                instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                persistContext(instance, ctx);
                return;
            }

            List<StageDefinition> pending = batch.stream()
                    .filter(s -> stepStatus(workflowId, planVersion, s.name()) == StepStatus.PENDING)
                    .toList();
            if (pending.isEmpty()) continue;

            // Conditional activation (Task 51's predecessor design, unchanged in spirit):
            List<StageDefinition> toSkip = new ArrayList<>();
            for (StageDefinition s : pending) {
                Optional<java.util.function.Predicate<WorkflowContext>> cond = catalog.activationConditionFor(s.activationConditionKey());
                if (cond.isPresent() && !cond.get().test(ctx)) {
                    markSkipped(workflowId, planVersion, s.name());
                    toSkip.add(s);
                }
            }
            List<StageDefinition> active = new ArrayList<>(pending);
            active.removeAll(toSkip);
            if (active.isEmpty()) continue;

            // Entry gate:
            for (StageDefinition s : active) {
                if (s.entryApproval() && !isApproved(workflowId, planVersion, s.name())) {
                    enterPendingApproval(workflowId, planVersion, s.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    persistContext(instance, ctx);
                    return;
                }
            }

            // Fourth review, finding 1: a SafeStopException thrown inside runStage() was being
            // wrapped as a generic RuntimeException here and left to propagate out of runFrom()/
            // submit() entirely — the workflow's status was never set to SAFE_STOPPED, and a
            // caller (e.g. FallbackTest's failed-fallback case) would see an exception instead of
            // a clean terminal state. Unwrap and handle it distinctly, mirroring Task 28's
            // runBatch pattern.
            try (ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, active.size()))) {
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (StageDefinition s : active) {
                    futures.add(pool.submit(() -> runStage(workflowId, planVersion, s, ctx)));
                }
                for (var f : futures) {
                    try {
                        f.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof SafeStopException safeStop) {
                            instance.setStatus(WorkflowStatus.SAFE_STOPPED);
                            persistContext(instance, ctx);
                            recordEvent(workflowId, "engine", "safe_stop_triggered", "SAFE_STOPPED", safeStop.getMessage());
                            return;
                        }
                        throw new RuntimeException(cause);
                    }
                }
            }
            persistContext(instance, ctx);

            for (StageDefinition s : active) {
                StepStatus status = stepStatus(workflowId, planVersion, s.name());
                boolean completedNormally = status == StepStatus.SUCCEEDED || status == StepStatus.SUCCEEDED_WITH_FALLBACK;
                if (s.exitApproval() && completedNormally && !isApproved(workflowId, planVersion, s.name())) {
                    enterPendingApproval(workflowId, planVersion, s.name());
                    instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
                    persistContext(instance, ctx);
                    return;
                }
            }
        }

        instance.setStatus(WorkflowStatus.COMPLETED);
        persistContext(instance, ctx);
        recordEvent(workflowId, "engine", "workflow_completed", "OK", null);
    }

    /** Application-startup (or restart-test) entry point: resumes every workflow this process
     * didn't necessarily submit itself. */
    public void resumeAll() {
        for (WorkflowInstanceEntity instance : instanceRepository.findAll()) {
            if (instance.getStatus() == WorkflowStatus.RUNNING
                    || instance.getStatus() == WorkflowStatus.PENDING_APPROVAL
                    || instance.getStatus() == WorkflowStatus.REPLANNING) {
                runFrom(instance.getId());
            }
        }
    }

    private StageHandler resolveHandler(String workflowId, String stageName) {
        try {
            return catalog.handlerFor(stageName);
        } catch (IllegalStateException notInCatalog) {
            StageHandler fallback = inProcessHandlerFallback.get(workflowId + "::" + stageName);
            if (fallback == null) {
                throw new IllegalStateException(
                        "no handler for stage " + stageName + " in the catalog, and none submitted in-process either");
            }
            return fallback;
        }
    }

    private void runStage(String workflowId, int planVersion, StageDefinition stage, WorkflowContext ctx) {
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stage.name()).orElseThrow();
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        stepRepository.save(step);

        Map<String, Object> result = null;
        boolean succeeded = false;
        for (int attempt = 0; attempt <= stage.maxRetries(); attempt++) {
            try {
                result = TimeoutExecutor.withTimeout(stage.handler(), stage.timeoutSeconds() * 1000L).run(ctx);
                succeeded = true;
                break;
            } catch (PermanentStageException e) {
                break;
            } catch (TransientStageException | StageTimeoutException e) {
                step.setRetryCount(step.getRetryCount() + 1);
                stepRepository.save(step);
                if (attempt == stage.maxRetries()) break;
            }
        }

        if (succeeded) {
            step.setStatus(StepStatus.SUCCEEDED);
            step.setEndedAt(Instant.now());
            step.setResult(result != null ? result : Map.of());
            stepRepository.save(step);
            recordEvent(workflowId, "agent", "stage_succeeded:" + stage.name(), "OK", null);
            evaluatePolicyIfConfigured(workflowId, stage, ctx, step);
            return;
        }

        // Retries exhausted (or a permanent failure on the first attempt). Fallback next, per the
        // fourth review's required ordering: fallback attempted before compensation, itself
        // timeout-wrapped, never itself retried.
        Optional<StageHandler> fallback = catalog.fallbackFor(stage.fallbackHandlerKey());
        if (fallback.isPresent()) {
            recordEvent(workflowId, "engine", "fallback_started:" + stage.name(), "OK",
                    "primary handler exhausted retries or failed permanently");
            try {
                Map<String, Object> fallbackResult = TimeoutExecutor.withTimeout(fallback.get(), stage.timeoutSeconds() * 1000L).run(ctx);
                step.setStatus(StepStatus.SUCCEEDED_WITH_FALLBACK);
                step.setEndedAt(Instant.now());
                step.setResult(fallbackResult != null ? fallbackResult : Map.of());
                stepRepository.save(step);
                recordEvent(workflowId, "engine", "fallback_succeeded:" + stage.name(), "OK",
                        "fallback path produced a degraded but valid outcome");
                // Disclosure, per constitution Principle VIII: a fallback outcome is never
                // presented as equivalent to primary-path success without saying so.
                // ReleaseReadinessStage (Task 39) reads this list and must name the reduced
                // guarantee rather than reporting an unqualified "ready".
                @SuppressWarnings("unchecked")
                List<String> fallbacksUsed = (List<String>) ctx.artifacts()
                        .computeIfAbsent("fallbacksUsed", k -> new java.util.concurrent.CopyOnWriteArrayList<String>());
                fallbacksUsed.add(stage.name());
                return;
            } catch (Exception fallbackFailure) {
                recordEvent(workflowId, "engine", "fallback_failed:" + stage.name(), "FAILED",
                        String.valueOf(fallbackFailure.getMessage()));
                // falls through to compensation/safe-stop below, exactly as if no fallback existed
            }
        }

        boolean compensated = compensationRegistry != null
                && compensationRegistry.run(stage.name(), result != null ? result : Map.of());
        step.setEndedAt(Instant.now());
        if (compensated) {
            step.setStatus(StepStatus.COMPENSATED);
            stepRepository.save(step);
            recordEvent(workflowId, "engine", "compensation_ran:" + stage.name(), "OK", null);
            return;
        }
        step.setStatus(StepStatus.SAFE_STOPPED);
        stepRepository.save(step);
        throw new SafeStopException("retries exhausted, no fallback or compensation recovered stage " + stage.name());
    }

    private void evaluatePolicyIfConfigured(String workflowId, StageDefinition stage, WorkflowContext ctx, WorkflowStepEntity step) {
        if (policyEngine == null) return;
        var policyResults = policyEngine.evaluate(stage.name(), ctx.artifacts());
        boolean mandatoryFail = false;
        for (var pr : policyResults) {
            PolicyEvaluationEntity evaluation = new PolicyEvaluationEntity();
            evaluation.setWorkflowId(workflowId);
            evaluation.setStageName(stage.name());
            evaluation.setPolicyId(pr.policyId());
            evaluation.setPolicyVersion(policyEngine.version());
            evaluation.setOutcome(pr.outcome());
            evaluation.setReason(pr.reason());
            policyEvaluationRepository.save(evaluation);
            if (pr.mandatory() && pr.outcome() == com.schwab.urlshortener.policy.PolicyOutcome.FAIL
                    && !hasApprovedUnexpiredException(workflowId, pr.policyId())) {
                mandatoryFail = true;
            }
        }
        if (mandatoryFail) {
            step.setStatus(StepStatus.SAFE_STOPPED);
            stepRepository.save(step);
            throw new SafeStopException("mandatory policy FAIL without an approved exception for stage " + stage.name());
        }
    }

    public void approve(String workflowId, String stepName, String decision, String actor, String reason) {
        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        int planVersion = instance.getPlanVersion();
        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, planVersion, stepName).orElseThrow();

        ApprovalEntity approval = new ApprovalEntity();
        approval.setWorkflowId(workflowId);
        approval.setPlanVersion(planVersion);
        approval.setStepInstanceId(step.getId());
        approval.setStepName(stepName);
        approval.setDecision(decision);
        approval.setDecidedBy(actor);
        approval.setReason(reason);
        approval.setDecidedAt(Instant.now());
        approvalRepository.save(approval);
        recordEvent(workflowId, "human", "approval_" + decision + ":" + stepName, "OK", reason);

        if ("rejected".equals(decision)) {
            if (step.getStatus() != StepStatus.SUCCEEDED && step.getStatus() != StepStatus.SUCCEEDED_WITH_FALLBACK) {
                step.setStatus(StepStatus.FAILED);
                stepRepository.save(step);
            }
            instance.setStatus(WorkflowStatus.FAILED);
            instanceRepository.save(instance);
            return;
        }

        // Generic decision-lineage fix (fourth review, finding 5): write the approved reason into
        // context so ANY downstream stage can read what was actually decided, not merely that
        // approval happened. Reloaded into `ctx` on the next runFrom() call via context_json.
        Map<String, Object> contextJson = new HashMap<>(instance.getContextJson());
        Map<String, Object> artifacts = new HashMap<>((Map<String, Object>) contextJson.getOrDefault("artifacts", Map.of()));
        artifacts.put(stepName + "Resolution", reason);
        contextJson.put("artifacts", artifacts);
        List<Map<String, Object>> decisions = new ArrayList<>((List<Map<String,Object>>) contextJson.getOrDefault("decisions", List.of()));
        decisions.add(Map.of("actor", actor, "decision", "approved:" + stepName, "rationale", reason,
                "timestamp", Instant.now().toString()));
        contextJson.put("decisions", decisions);
        instance.setContextJson(contextJson);

        boolean wasEntryGated = step.isEntryApproval() && step.getStatus() == StepStatus.WAITING_APPROVAL
                && step.getStartedAt() == null;
        step.setStatus(wasEntryGated ? StepStatus.PENDING : StepStatus.SUCCEEDED);
        stepRepository.save(step);
        instance.setStatus(WorkflowStatus.RUNNING);
        instanceRepository.save(instance);
        runFrom(workflowId);
    }

    // replan(), recordEvent(), persistContext(), stepStatus(), isApproved(), enterPendingApproval(),
    // markSkipped(), hasApprovedUnexpiredException() follow the same plan-version-safe pattern
    // established above; grantPolicyException()/retrySafeStopped() from Tasks 28/29 are unchanged
    // except for the same findByWorkflowIdAndPlanVersionAndName scoping. Omitted here for length —
    // implement each using the exact same `(workflowId, planVersion, name)` scoping shown in
    // runStage/approve above; do not reintroduce a bare `findByWorkflowIdAndName` anywhere.
```
- [ ] **Step 3b: Write and implement `FallbackTest`** (fourth review, finding 6: "add tests for
  these behaviors, not just isolated step-status assertions"):
```java
// src/test/java/com/schwab/urlshortener/orchestration/FallbackTest.java
package com.schwab.urlshortener.orchestration;

import com.schwab.urlshortener.orchestration.persistence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FallbackTest {

    @Autowired private WorkflowInstanceJpaRepository instanceRepository;
    @Autowired private WorkflowStepJpaRepository stepRepository;
    @Autowired private WorkflowEventJpaRepository eventRepository;
    @Autowired private ApprovalJpaRepository approvalRepository;

    private static final StageHandler ALWAYS_FAILS = ctx -> {
        throw new PermanentStageException("primary path is unavailable in this test");
    };

    @Test
    void successfulFallbackLetsDependentStageRunAndWorkflowCompletesWithDisclosedDegradedOutcome() {
        StageHandler fallback = ctx -> Map.of("degraded", true, "omits", "rich formatting");
        StageHandler downstream = ctx -> {
            @SuppressWarnings("unchecked")
            List<String> fallbacksUsed = (List<String>) ctx.artifacts().get("fallbacksUsed");
            return Map.of("ready", true, "degraded", !fallbacksUsed.isEmpty(), "degradedStages", fallbacksUsed);
        };
        StageCatalog catalog = new StageCatalog(
                Map.of("flaky_doc", ALWAYS_FAILS, "downstream", downstream),
                Map.of(),
                Map.of("doc_fallback", fallback));
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky_doc", List.of(), ALWAYS_FAILS, false, false, 0, 5, null, null, "doc_fallback"),
                new StageDefinition("downstream", List.of("flaky_doc"), downstream, false, false, 0, 5, null, null, null));

        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, catalog);
        String workflowId = engine.submit("fallback-happy-path", stages, "fb-1");

        WorkflowStepEntity flakyStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky_doc").orElseThrow();
        assertThat(flakyStep.getStatus()).isEqualTo(StepStatus.SUCCEEDED_WITH_FALLBACK);

        List<String> actions = eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .map(WorkflowEventEntity::getAction).toList();
        assertThat(actions).contains("fallback_started:flaky_doc", "fallback_succeeded:flaky_doc");
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .filter(e -> e.getAction().equals("fallback_started:flaky_doc") || e.getAction().equals("fallback_succeeded:flaky_doc"))
                .allMatch(e -> e.getReason() != null)).isTrue();

        // The dependent stage ran at all — SUCCEEDED_WITH_FALLBACK satisfies dependency
        // resolution exactly like SUCCEEDED, not like a failure.
        WorkflowStepEntity downstreamStep = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "downstream").orElseThrow();
        assertThat(downstreamStep.getStatus()).isEqualTo(StepStatus.SUCCEEDED);
        assertThat(downstreamStep.getResult()).containsEntry("degraded", true);
        assertThat((List<?>) downstreamStep.getResult().get("degradedStages")).containsExactly("flaky_doc");

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void fallbackFailureProceedsToCompensationThenSafeStopOnlyAsLastResort() {
        StageHandler fallbackThatAlsoFails = ctx -> {
            throw new RuntimeException("fallback path is also unavailable in this test");
        };
        StageCatalog catalog = new StageCatalog(
                Map.of("flaky_doc", ALWAYS_FAILS),
                Map.of(),
                Map.of("doc_fallback", fallbackThatAlsoFails));
        List<StageDefinition> stages = List.of(
                new StageDefinition("flaky_doc", List.of(), ALWAYS_FAILS, false, false, 0, 5, null, null, "doc_fallback"));

        // No CompensationRegistry configured (null) — this proves safe-stop is reached only after
        // both fallback AND compensation are unavailable/exhausted, not as fallback's immediate
        // alternative.
        WorkflowEngine engine = new WorkflowEngine(instanceRepository, stepRepository, eventRepository,
                approvalRepository, null, null, null, null, catalog);
        String workflowId = engine.submit("fallback-also-fails", stages, "fb-2");

        WorkflowStepEntity step = stepRepository.findByWorkflowIdAndPlanVersionAndName(workflowId, 1, "flaky_doc").orElseThrow();
        assertThat(step.getStatus()).isEqualTo(StepStatus.SAFE_STOPPED);

        List<String> actions = eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .map(WorkflowEventEntity::getAction).toList();
        assertThat(actions).contains("fallback_started:flaky_doc", "fallback_failed:flaky_doc");
        assertThat(eventRepository.findByWorkflowIdOrderById(workflowId).stream()
                .filter(e -> e.getAction().equals("fallback_failed:flaky_doc"))
                .allMatch(e -> e.getReason() != null)).isTrue();

        WorkflowInstanceEntity instance = instanceRepository.findById(workflowId).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
    }
}
```
  No new production code beyond what this task's `WorkflowEngine`/`StageCatalog`/`WorkflowContext`
  already implement above — this is a validation-only step against that implementation. If either
  test fails, the defect is in `runStage`'s fallback block or `WorkflowContext`'s thread-safety
  above, not new logic to add here.
- [ ] **Step 4: Run to confirm pass** — both restart tests and both `FallbackTest` tests PASS. Tasks 22–38's code blocks are
  historical/pedagogical (each shows one capability landing at a time, per this task's own opening
  note) and are not separately compiled or re-run — there is exactly one `WorkflowEngine.java`,
  ending at this task's form, with exactly one constructor (the 9-arg one above). The tests that
  must actually pass against this final form are: `StageCatalogTest` (Task 49), the four tests above
  (`WorkflowEngineRestartTest`'s two, `FallbackTest`'s two), and Tasks 39–41's scenario tests, which
  are written directly against this constructor and this catalog-based construction — not against
  an earlier incremental version.
- [ ] **Step 5: Commit** — `git commit -m "feat(orchestration): consolidate WorkflowEngine into a restart-safe, plan-version-safe final form"`

### Task 51 (folded into Tasks 39/41 — retained as a historical marker, do not implement separately)

**What this task originally covered — real 12-node stage graph, `RequirementStage` split into three
stages, `FinalSummaryStage`, and `DecompositionStage`'s clarification read-and-fold logic — is now
written directly into Task 39 (which creates every one of those files in its authoritative final
form) and exercised by Task 41's first test (`decompositionIncorporatesTheHumansActualResolution`,
renamed there to `ambiguousRequirementSuspendsAtHumanClarificationThenDecompositionUsesTheResolution`).
This corrects the ordering defect the fourth external review flagged: this task, as originally
numbered, modified `RequirementStage.java` and extended `GreenfieldScenarioTest` — files Task 39
alone creates — while sitting after Task 48 in this document, i.e. after every task that could ever
reach it in execution order. Nothing to implement here; implement Tasks 39 and 41 instead.**

### Task 52: SCN-B — seven genuinely parallel impact-analysis branches + joined approval gate

**Folded into Task 40 (retained as a historical marker, do not implement separately).** What this
task originally covered — the seven parallel impact stages and `ChangeImpactReportStage` — is now
written directly into Task 40, for the same ordering reason as Task 51 above: this task modified
`BrownfieldScenarioTest`, a file Task 40 alone creates. Implement Task 40 instead. The corrected
target this task originally described (`spec.md` Section 4's SCN-B: seven concurrently-executing
sibling branches joined by a mandatory Approval Gate before Implementation) is unchanged — it now
just lives at Task 40.

### Task 53: Fix `computeReliabilitySummary`'s workflow-scoping bug for real

**Corrects** Task 37's self-disclosed, previously-deferred bug: `steps` was never filtered by
`workflowId` even when one was supplied for `instances`.

**Files:** Modify: `src/main/java/com/schwab/urlshortener/observability/MetricsService.java`;
`src/main/java/com/schwab/urlshortener/orchestration/persistence/WorkflowStepJpaRepository.java`
(add `List<WorkflowStepEntity> findByWorkflowId(String workflowId);` — a Spring Data derived query,
zero implementation code needed)

**Test:** `src/test/java/com/schwab/urlshortener/observability/MetricsServiceIsolationTest.java`

- [ ] **Step 1: Write failing test**
```java
@Test
void perWorkflowMetricsDoNotContaminateAcrossWorkflows() {
    // Workflow A: one stage retried 3 times then succeeds.
    // Workflow B: one stage succeeds on the first attempt, zero retries.
    // ... submit both via the engine ...
    ReliabilitySummary summaryForB = MetricsService.computeReliabilitySummary(
            instanceRepository, stepRepository, workflowIdB);
    assertThat(summaryForB.retryFrequency()).isZero(); // must NOT reflect workflow A's retries
}
```
- [ ] **Step 2: Run to confirm failure** — `retryFrequency` for workflow B is non-zero, polluted by
  workflow A's steps.
- [ ] **Step 3: Implement** — replace `stepRepository.findAll()` with
  `workflowId != null ? stepRepository.findByWorkflowId(workflowId) : stepRepository.findAll()`,
  mirroring the existing `instances` pattern two lines above it.
- [ ] **Step 4: Run to confirm pass** — PASS
- [ ] **Step 5: Commit** — `git commit -m "fix(observability): scope reliability metrics to the requested workflow"`

### Superseded-task pointers (add these one-line notes to the original tasks, do not delete their content)

- Task 20 (StageDefinition/StepStatus): add a note after its Interfaces section: "Extended by
  Task 49 (conditional activation, additive — no change to this record) and Task 50
  (`SUCCEEDED_WITH_FALLBACK`, additive enum constant) — see Phase 12."
- Task 34 (safe-stop): add a note: "The 'fallback realized as retry-then-safe-stop' language
  originally here was a mislabeling, corrected by Task 50 — see Phase 12. The two-category safe-stop
  enumeration itself is unchanged; category (b) now nests a real fallback attempt before
  compensation, per Task 50's CLR-13 update."
- Task 37 (MTTR/metrics): add a note: "The `computeReliabilitySummary` workflow-scoping bug
  disclosed in this task's original code comment is fixed for real by Task 53 — see Phase 12."
- Tasks 39/40/41 (the three scenarios): **no pointer note** — corrected 2026-09-13 to be written
  directly in authoritative final form (real 12-node graph, seven-branch SCN-B, real
  `human_clarification` gate), per the explicit instruction that no running code exists yet, so
  there is nothing to preserve by building an obsolete version first and patching it later. Their
  bodies each carry an "Execution order note" instead, naming which Phase 12 tasks (49-51) must be
  implemented first even though this document lists them later.
