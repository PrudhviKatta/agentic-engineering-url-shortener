# Final engineering summary

## 1 Executive engineering outcome and release-readiness status

The project is **READY WITH ACCEPTED LIMITATIONS** for the local interview-assessment prototype.
All 31 traced requirements and all three required scenarios have implementation and executable
evidence. The final unfiltered local suite passes 231 tests. This is not a production-readiness
claim; the explicit production blockers are listed in Sections 10, 19, and 21.

## 2 Project objective, scope, and 2–3-day timebox outcome

The objective was to build a Java 21/Spring Boot URL shortener whose engineering lifecycle is driven
by a persisted agentic orchestration engine. The deliverable includes link creation, redirect,
analytics, a workflow API/CLI, policy and governance controls, reliability mechanics, and evidence
for greenfield, brownfield, and ambiguous requirements. The 2–3-day constraint is retained as the
assignment timebox; repository evidence proves the delivered scope and test results, not independent
wall-clock accounting. Production deployment and a general-purpose workflow platform are excluded.

## 3 Confirmed requirements, assumptions, exclusions, and deferred scope

`specs/001-agentic-url-shortener/spec.md` defines REQ-001–REQ-031, clarifications, assumptions, and
scenario acceptance criteria. Confirmed assumptions include immutable links, Java 21, a loopback
trusted-caller demonstration, and demonstration-scale targets rather than SLAs. Deferred production
scope includes authentication/authorization, TLS, rate limiting, managed migrations, distributed
workers, a compliance-derived retention period, continuous scanning, and production SLO telemetry.

## 4 Architecture overview and accepted ADRs

`docs/architecture/overview.md` separates API, domain, persistence, orchestration, policy,
reliability, observability, and scenario adapters. ADR-001 through ADR-010 record Java/Spring,
H2/JPA, short-code generation, a persisted DAG, the choice not to hide mechanics in an external
engine, approval semantics, YAML policy, local concurrency, rollback versus compensation, and the
no-authentication prototype boundary. The custom engine favors inspectability over platform breadth.

## 5 API contracts and schema deliverables

`specs/001-agentic-url-shortener/contracts/openapi.yaml` defines link creation, redirect/analytics,
workflow state/audit, approval, replan, and metrics contracts. JPA entities implement short links,
redirect events, workflow instances/steps/events, approvals, policy evaluations, and exceptions.
`specs/001-agentic-url-shortener/data-model.md` documents the field-level model. Controller contract
tests validate status codes and payload shapes, including fallback state and MTTR populations.

## 6 Orchestration model, dependency graph, and state persistence

`WorkflowEngine`, `StageCatalog`, and the three graph definitions implement a persisted DAG. The
engine stores dependencies and stable handler/activation/fallback keys, reconstructs stages from
rows plus the boot catalog, claims eligible work, runs independent siblings concurrently, and waits
for their join. Greenfield uses the 12-stage baseline. Brownfield adds seven concurrent impact
branches and a joined report. Ambiguous work pauses for clarification and replaces downstream work
through versioned replanning. `WorkflowEngineStageBoundaryTest` verifies a stage cannot mutate a
different step through its scoped persistence capability.

## 7 Human approvals, governance gates, and decision lineage

Approval records contain workflow, stage, decision, actor, rationale, plan version, and timestamps.
Entry and exit gates block until explicit approval; silence is never approval, and rejection blocks
the affected plan. `docs/governance/human-gates-log.md` separates human decisions from delegated
technical review. Demonstration scenarios use a disclosed self-asserted reviewer; this proves
mechanics and lineage but not authenticated identity.

## 8 Compliance and change-control policy results

`policies/v1.yaml` is loaded by the production `PolicyEngine`. Mandatory evidence failures block
progression and persist evaluations. Brownfield implementation cannot proceed until all seven impact
analyses converge into the approved change-impact report. Tests cover pass, mandatory failure,
non-mandatory behavior, reevaluation, and policy enforcement during normal and resumed execution.

## 9 Policy exceptions, compensating controls, expiry, and approvals

An exception request cannot approve itself. A usable exception requires a rationale, defined scope,
expiry, compensating control, and approving authority; expired or scope-mismatched records do not
unblock policy. `PolicyExceptionTest` and `WorkflowEnginePolicyEnforcementTest` exercise those rules.
The successful demonstration scenarios require no active policy exception.

## 10 Security controls, findings, and residual risks

`UrlValidator` accepts only HTTP(S), resolves the host, and rejects loopback, any-local, link-local,
private/site-local, multicast, and IPv6 ULA addresses. Short-code uniqueness is enforced at the
database boundary with bounded collision handling. Workflow gates and policy checks fail closed.
Residual risks are material: no auth, TLS, rate limiting, redirect-time rebinding defense, production
secrets/log-redaction platform, or continuous dependency scanning. `SECURITY.md` states this boundary.

## 11 Reliability, retry, fallback, rollback/compensation, and safe-stop

Retries are bounded and limited to classified transient failures. Stage timeouts terminate within a
bound. A fallback is one separately registered attempt after primary exhaustion and succeeds as
`SUCCEEDED_WITH_FALLBACK`, with audit and release disclosure. Transaction rollback covers local
effects; `CompensationRegistry` handles registered external-style actions. Unrecoverable or unsafe
continuation persists a reason and converges on `SAFE_STOPPED`. `RetryTest`, `TimeoutTest`,
`FallbackTest`, `CompensationRegistryTest`, and reliability integration tests provide evidence.

## 12 MTTR definition, measurement population, calculation, exclusions, and unrecovered failures

`MetricsService` derives recovery intervals from persisted audit actions. `MttrReport` returns the
mean duration only for paired failure-to-recovery events, plus recovered, unrecovered, and excluded
counts and a population note. An absent interval is `null`, never a fabricated zero. The final local
file-backed demonstration population returned `recoveredCount:0`, `unrecoveredCount:0`,
`excludedCount:0`, and `mttrSeconds:null`; it is small, accumulated, and not an SLA population.

## 13 Observability, auditability, and evidence integrity

`AuditRecorder` centralizes actor, action, result, detail, timestamp, and workflow attribution.
Workflow state and audit endpoints expose durable evidence. Metrics report success/failure, retry,
compensation, fallback, end-to-end latency, and MTTR population fields with
`isDemonstrationData:true`. The convergence report discloses a race found by a full run and the
subsequent fix; failed evidence was not relabeled as success.

## 14 Greenfield well-defined requirement scenario outcome

`GreenfieldScenarioTest` and `docs/scenarios/greenfield.md` show the 12-stage path. Because the
requirement is clear, clarification is skipped. Design and release gates are explicitly approved,
implementation/test/security/documentation stages execute, and the workflow ends `COMPLETED`.
The executed CLI workflow ID recorded in `docs/assessment/convergence_report.md` is
`e23de386-72b7-45d7-94b5-d778ac930b9c`.

## 15 Brownfield scenario outcome

`BrownfieldScenarioTest` and `docs/scenarios/brownfield.md` show seven named impact analyses running
as siblings: components, interfaces, data flows, tests, documentation, regression risk, and
rollout/rollback. Persisted timestamps prove all seven end before the joined change-impact report
starts. Approval then unblocks implementation and the workflow ends `COMPLETED`. The recorded CLI
workflow ID is `72bf7a9c-ebfa-4226-a065-d4b77a67e088`.

## 16 Ambiguous-requirement scenario outcome

`AmbiguousScenarioTest` and `docs/scenarios/ambiguous.md` show the unreadable requirement entering
human clarification instead of being guessed. The reviewer decision precedes `replanned`; downstream
version-one work is invalidated, `decomposition_v2` executes, and the workflow ends `COMPLETED` with
lineage intact. The recorded CLI workflow ID is `47e66014-ee03-42e0-b6f8-3ac74d7797cd`.

## 17 Test strategy, executed validation, and actual results

The suite covers domain units, JPA persistence, HTTP contracts, application boot, workflow
transitions, approvals/rejections, policy, retry/timeout/fallback/compensation, recovery, security,
race conditions, and end-to-end scenarios. The approval/replan race is repeated 20 times in the
normal suite. Final command and result:

```text
$ DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test
[INFO] Tests run: 231, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Focused recovery verification also passed:

```text
$ DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test \
  -Dtest=WorkflowRecoveryBootTest,WorkflowEngineRestartTest
```

## 18 Requirement-to-design-to-task-to-code-to-test traceability

`docs/traceability/matrix.md` contains exactly one row for every REQ-001 through REQ-031. Each row
names the scenario, specification/design source, ADR, task, implementation path, test path,
reproduction command, documentation, and evidence destination. The mapping includes application-
boot recovery for REQ-017/REQ-028 and audit-derived metric/MTTR evidence for REQ-022/REQ-025/REQ-027.

## 19 Known limitations, technical debt, and deferred enhancements

Known limitations are H2 plus `ddl-auto:update`, local-thread execution, self-asserted actors,
synthetic external compensation, an accumulated demonstration metric population, validation-time
DNS checks, and top-level rather than deep immutability for nested artifact values. `WorkflowEngine`
is large and compatibility constructors should be removed after callers converge. Distributed
leases/fencing, authenticated governance, immutable artifact schemas, production telemetry, and
managed migrations are deferred enhancements.

## 20 Repository paths, reproducible commands, and reviewer verification points

Start with `README.md`, then `docs/architecture/overview.md`,
`docs/assessment/reviewer_navigation_guide.md`, and `docs/traceability/matrix.md`. Run:

```bash
DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test
DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=greenfield
DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=brownfield
DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--scenario=ambiguous
DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--metrics-report
```

The 25-point reviewer sequence, expected evidence, and requirement links are in the navigation guide.

## 21 Final engineering judgment, unresolved blockers, and recommended next actions

There is no unresolved blocker for the local assessment submission. The implementation is coherent,
the three paths are materially distinct, and final evidence is reproducible. Do not deploy it to an
untrusted network. Before production consideration, add authenticated/authorized governance, TLS and
abuse controls, redirect-time DNS protection, managed migrations, distributed execution semantics,
production telemetry/SLOs, automated security scanning, and hosted CI. Then rerun the complete
traceability and scenario verification against the production-like topology.
