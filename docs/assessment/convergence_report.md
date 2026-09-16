# Convergence report

Date: 2026-09-15 (America Chicago)

This report converges the implementation, specification, tests, scenarios, documentation, and
release evidence for the local assessment prototype. Evidence comes from repository inspection and
commands executed against this working revision.

## 1 Requirement verification

The canonical matrix at `docs/traceability/matrix.md` contains one row for every identifier from
REQ-001 through REQ-031. Each row names a design location, implementation path, test path,
validation command, documentation path, and evidence destination. No requirement is left without
executable or inspectable evidence.

Acceptance behavior is covered across controller contracts, domain tests, persistence tests,
orchestration transitions, policy/reliability tests, and the three end-to-end scenarios. The final
inspection found one requirement gap before convergence: MTTR was computed only from caller-supplied
events and was absent from the HTTP/CLI report. That was corrected in `MetricsService`,
`ReliabilitySummary`, `MetricsController`, the OpenAPI contract, and contract tests before the final
run.

## 2 Architecture drift check

- API/domain/persistence and orchestration/policy/reliability/observability remain separate packages.
- Both planes share H2 but do not share domain services.
- `StageCatalog` is the boot-time mapping for durable handler, activation, and fallback keys; no
  process-local per-workflow handler registry remains.
- Production composition now includes the versioned policy engine and compensation registry through
  `OrchestrationConfig`; policy behavior is not test-only.
- The OpenAPI step-status enumeration includes `SUCCEEDED_WITH_FALLBACK`, and the metrics schema
  includes the MTTR report exposed by code.
- The brownfield graph is seven sibling analyses plus a real join gate, matching specification SCN-B.

No unapproved material architecture change was found. Accepted decisions are recorded in ADR-001
through ADR-010.

## 3 Orchestration executable-evidence checklist

| # | Required behavior | Result | Executable evidence |
|---:|---|---|---|
| 1 | Explicit dependencies | Pass | `DependencyGraphTest` rejects unknown dependencies and cycles |
| 2 | Persisted state | Pass | `WorkflowEngineRestartTest`, JPA entity/repository tests |
| 3 | Sequential execution | Pass | `WorkflowEngineExecutionTest` dependency ordering |
| 4 | Parallel execution | Pass | engine overlap test and seven-way `BrownfieldScenarioTest` |
| 5 | Synchronization | Pass | brownfield join starts after all seven branches end |
| 6 | Branching | Pass | conditional clarification skip/run plus brownfield fan-out |
| 7 | Entry gates | Pass | clarification handler cannot run before approval |
| 8 | Exit gates | Pass | design, impact report, and release results gate dependents |
| 9 | Approval | Pass | `WorkflowEngineApprovalTest` and three scenario approval paths |
| 10 | Rejection | Pass | rejection is terminal for the affected plan and blocks dependents |
| 11 | Bounded retry | Pass | `RetryTest` and reliability integration tests |
| 12 | Timeout | Pass | `TimeoutTest` proves bounded termination and transient classification |
| 13 | Fallback | Pass | `FallbackTest` proves success disclosure, union, and failed fallback |
| 14 | Rollback or compensation | Pass | `CompensationRegistryTest` and integration paths |
| 15 | Safe-stop | Pass | mandatory policy/failure paths converge on persisted safe-stop |
| 16 | Resume | Pass | fresh-engine and application-boot recovery tests |
| 17 | Dynamic replanning | Pass | invalidation/versioning and 20 repeated approval/replan races |
| 18 | Decision lineage | Pass | context lineage, approval rationale, audit, and API contract tests |
| 19 | Terminal status | Pass | scenarios end `COMPLETED`; failure/rejection tests end fail-closed |

## 4 Three-scenario verification

The detailed 14-category comparison is in `docs/assessment/scenario_evidence_review.md`.

- Greenfield CLI: workflow `e23de386-72b7-45d7-94b5-d778ac930b9c`; clarification was skipped;
  design and release approvals were recorded; final output was `COMPLETED`.
- Brownfield CLI: workflow `72bf7a9c-ebfa-4226-a065-d4b77a67e088`; all seven named impact-stage
  success events preceded the joined impact report; three approvals were recorded; final output was
  `COMPLETED`.
- Ambiguous CLI: workflow `47e66014-ee03-42e0-b6f8-3ac74d7797cd`; `replanned` preceded
  `approval_approved:human_clarification`; `decomposition_v2` ran; final output was `COMPLETED`.

These are materially different paths: conditional skip, concurrent brownfield fan-out/join, and
ambiguity suspension with versioned replacement.

## 5 Test and validation summary

Final unfiltered command:

```text
$ DEBUG=false JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw test \
  -Dspring.jpa.properties.hibernate.show_sql=false
[INFO] Tests run: 231, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 10.800 s
[INFO] Finished at: 2026-09-15T21:18:54-05:00
```

The suite includes unit, JPA integration, HTTP contract, application-boot, orchestration transition,
security, failure-path, and end-to-end scenario tests. The approval/replan race executes 20 times in
the normal suite.

Evidence integrity note: the preceding full run exposed one race failure (`REPLANNING` visible after
both concurrent callers returned). The engine was corrected to compare-and-set the durable crash
marker to `RUNNING` before resume; the focused class then passed 35 tests, including 20 race
repetitions, before the final 231-test run above. The failed run is not presented as success.

## 6 Actual CLI output

Greenfield terminal excerpt:

```json
{"result":"OK","action":"stage_skipped:human_clarification","actorType":"orchestrator"}
{"result":"OK","action":"approval_approved:design","actorType":"human"}
{"result":"OK","action":"approval_approved:release_readiness","actorType":"human"}
{"workflowId":"e23de386-72b7-45d7-94b5-d778ac930b9c","status":"COMPLETED"}
```

Brownfield terminal excerpt:

```json
{"result":"OK","action":"stage_succeeded:impacted_components","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:impacted_interfaces","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:impacted_data_flows","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:impacted_tests","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:impacted_documentation","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:regression_risk","actorType":"agent"}
{"result":"OK","action":"stage_succeeded:rollout_rollback","actorType":"agent"}
{"result":"OK","action":"approval_approved:change_impact_report","actorType":"human"}
{"workflowId":"72bf7a9c-ebfa-4226-a065-d4b77a67e088","status":"COMPLETED"}
```

Ambiguous terminal excerpt:

```json
{"action":"replanned","result":"OK","actorType":"human"}
{"action":"approval_approved:human_clarification","result":"OK","actorType":"human"}
{"action":"stage_succeeded:decomposition_v2","result":"OK","actorType":"agent"}
{"workflowId":"47e66014-ee03-42e0-b6f8-3ac74d7797cd","status":"COMPLETED"}
```

Final metrics output:

```json
{"successRate":0.6257693885925318,"failureRate":0.3742306114074682,"retryFrequency":0.0,"rollbackOrCompensationFrequency":0.0,"fallbackFrequency":0.0,"endToEndLatencySeconds":0.0,"mttr":{"mttrSeconds":null,"recoveredCount":0,"unrecoveredCount":0,"excludedCount":0,"populationNote":"Demonstration data from a small, non-statistically-representative local test population; not production statistics or an SLA."},"isDemonstrationData":true}
```

The metric rates reflect the accumulated local file-backed demonstration/test population, including
deliberate failure scenarios; they are not product targets. MTTR is `null` because that population
contains no audit-paired recovery interval, not because recovery time is zero.

## 7 Documentation currency

The root README contains quickstart and evidence links. `SECURITY.md` states the loopback/no-auth
posture without softening it. `CONTRIBUTING.md` defines test-first and evidence rules. Architecture,
31-row traceability, the source-guide-aligned 25-point navigation guide, three scenario narratives,
this convergence report, and final assessment documents match the implemented paths and commands.

## 8 Compliance and change control

`policies/v1.yaml` is versioned. Production `WorkflowEngine` receives `PolicyEngine`, policy
evaluation and exception repositories, and `CompensationRegistry`. Tests prove mandatory failure
blocks progression, explicit exception requests do not self-approve, and valid exceptions require
rationale, scope, expiry, compensating control, and authority. Brownfield code change is preceded by
seven impact analyses and the joined approval gate. Contract/status/metric changes were reflected in
OpenAPI and regression tests.

No policy exception is required for the three successful demonstration scenarios. Optional license
evidence remains non-mandatory in v1 policy; production release would require a real dependency scan.

## 9 Security summary

Implemented: HTTP(S) allow-list; DNS resolution; loopback, any-local, link-local, private/site-local,
multicast, and IPv6 ULA rejection; bounded code collision handling; fail-closed policy/gate behavior;
loopback binding. Residual: no authentication/authorization/TLS/rate limiting, validation-time-only
DNS checks, no production secrets/log-redaction system, and no automated dependency scanning.

## 10 Reliability summary

Bounded retry distinguishes transient from permanent failure. Timeouts are bounded. Fallback is one
attempt and visibly degraded. Compensation is distinct from rollback. Unsafe continuation persists a
safe-stop reason. Restart reconstructs from persisted rows/catalog and fails closed on an unknown
stage key. Metrics expose success/failure, retry, compensation, fallback, latency, and audit-derived
MTTR populations with demonstration labeling.

## 11 Risk register

| Risk | Current control | Residual/disposition |
|---|---|---|
| Unauthenticated approval mutation | Loopback binding and explicit disclosure | Must add identity and authorization before network deployment |
| SSRF/DNS rebinding | Scheme/address-class checks before persistence | Re-resolve/pin destination at redirect time for production |
| File H2 and `ddl-auto:update` | Real schema boot test and ignored data files | Replace with managed production database/migrations |
| Local process concurrency only | Row locks, claim checks, repeated race tests | Distributed workers require leases/fencing/idempotency design |
| Metric population contamination | Demonstration label and population note | Use isolated telemetry store and defined reporting window |
| Dependency vulnerability drift | Manual scan recommendation | Add CI scanning and remediation SLA |
| Mutable nested artifact values | Top-level map is read-only and attempt writes are attributed | Deep-copy/immutable artifact schema before untrusted handlers |

## 12 Known limitations and residual risks

This revision intentionally does not provide production authentication, authorization, TLS, rate
limiting, distributed execution, managed schema migrations, continuous vulnerability scanning,
production telemetry/SLOs, or redirect-time DNS rebinding resistance. H2, local threads, self-asserted
actors, and accumulated demonstration metrics define the accepted prototype boundary.

## 13 Assumption status

- Java 21 and local Maven execution: confirmed by final run.
- Loopback trusted-caller environment: accepted for this demonstration only.
- Links are immutable and creation requests are not deduplicated: implemented and documented.
- Reliability targets are demonstration acceptance targets, not SLAs: explicitly labeled.
- Scenario approvals use the self-asserted `scenario-reviewer`: valid demonstration evidence, not an
  authenticated organizational approval.
- Production deployment readiness: excluded.

## 14 Scenario evidence index

- `docs/scenarios/greenfield.md` and `GreenfieldScenarioTest`
- `docs/scenarios/brownfield.md` and `BrownfieldScenarioTest`
- `docs/scenarios/ambiguous.md` and `AmbiguousScenarioTest`
- Cross-scenario matrix: `docs/assessment/scenario_evidence_review.md`

## 15 Evidence-integrity pass

No generated test or scenario output is claimed without an executed command. Simulated approvals are
identified by their actor. Demonstration metrics are labeled. The initial full-suite race failure and
its correction are disclosed. The repository-wide branding scan returns no matches. Generated H2 and
build output remain ignored/untracked. History is not rewritten.

## 16 Release-readiness decision

**READY WITH ACCEPTED LIMITATIONS**

This decision covers the local interview-assessment prototype only. The 31 requirements and three
scenarios have evidence, all final tests and CLI modes pass, and no mandatory in-scope blocker remains.
The security, persistence, distributed-execution, and operational limitations above prevent a
production deployment claim.
