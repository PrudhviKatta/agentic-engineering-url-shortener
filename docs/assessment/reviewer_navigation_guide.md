# Reviewer navigation guide

Run commands from the repository root with Java 21. Start the application in one terminal when a
`curl` command is shown. Each point follows the assessment guide's required order.

| # | Verification point and exact path | Command | Expected observable result | Related ID |
|---:|---|---|---|---|
| 1 | Project objective in `README.md` and `specs/001-agentic-url-shortener/spec.md` | `sed -n '1,80p' README.md` | URL-shortener plus durable engineering-orchestration objective and local-demo scope | all |
| 2 | Run the application from `pom.xml` | `./mvnw spring-boot:run` | Spring Boot starts on `127.0.0.1:8080` with file-backed H2 | REQ-009, REQ-010 |
| 3 | Run tests under `src/test/java` | `./mvnw test` | 229 tests pass with zero failures/errors on the recorded revision | REQ-029 |
| 4 | Exercise the URL shortener through `api/LinkController.java` | `curl -s -X POST http://127.0.0.1:8080/links -H 'Content-Type: application/json' -d '{"url":"https://example.com"}'` | HTTP 201 JSON contains a short code and original URL | REQ-001–REQ-006 |
| 5 | Initiate orchestration through `observability/WorkflowController.java` | `curl -s -X POST http://127.0.0.1:8080/workflows -H 'Content-Type: application/json' -d '{"feature":"review-demo","requirementText":"REQ-023 accept 20 concurrent requests"}'` | HTTP 201 returns workflow ID, status, correlation ID, plan version | REQ-011 |
| 6 | Inspect workflow state through `GET /workflows/{id}` | `curl -s http://127.0.0.1:8080/workflows/{id}` | Persisted steps, status, inputs/outputs, decisions, correlation ID, and plan version | REQ-012 |
| 7 | Perform an approval through `POST /workflows/{id}/approvals/{step}` | `curl -s -X POST http://127.0.0.1:8080/workflows/{id}/approvals/design -H 'Content-Type: application/json' -d '{"decision":"approved","actor":"reviewer","reason":"design evidence reviewed"}'` | HTTP 204 and the recorded gate releases its dependent work | REQ-013 |
| 8 | Demonstrate bounded retry in `reliability/RetryExecutor.java` | `./mvnw test -Dtest=RetryTest` | Transient failures retry with bounded backoff; permanent failures do not | REQ-014 |
| 9 | Demonstrate compensation in `reliability/CompensationRegistry.java` | `./mvnw test -Dtest=CompensationRegistryTest,WorkflowEngineReliabilityIntegrationTest` | Registered compensation runs after exhausted execution failure | REQ-015 |
| 10 | Demonstrate safe-stop in `orchestration/WorkflowEngine.java` | `./mvnw test -Dtest=WorkflowEngineSafeStopTest,FallbackTest` | Unsafe continuation reaches `SAFE_STOPPED`; failed fallback does not proceed | REQ-016 |
| 11 | Demonstrate dynamic replanning in `WorkflowEngine.replan` | `./mvnw test -Dtest=WorkflowEngineReplanningTest` | Reachable rows invalidate, plan version advances, replacement work preserves governance | REQ-018 |
| 12 | Greenfield scenario in `docs/scenarios/greenfield.md` | `./mvnw -Dspring-boot.run.arguments=--scenario=greenfield spring-boot:run` | Audit JSON ends with workflow `COMPLETED`; clarification is skipped with reason | SCN-A |
| 13 | Brownfield scenario in `docs/scenarios/brownfield.md` | `./mvnw -Dspring-boot.run.arguments=--scenario=brownfield spring-boot:run` | Seven impact events precede joined report approval and terminal `COMPLETED` | SCN-B, REQ-030 |
| 14 | Ambiguous scenario in `docs/scenarios/ambiguous.md` | `./mvnw -Dspring-boot.run.arguments=--scenario=ambiguous spring-boot:run` | Replan event precedes clarification approval; v2 decomposition completes | SCN-C |
| 15 | Architecture in `docs/architecture/overview.md` | `sed -n '1,220p' docs/architecture/overview.md` | Component boundaries, control flow, persistence, and failure boundaries are explicit | REQ-024 |
| 16 | Decisions in `docs/adr/ADR-001-*.md` through `ADR-010-*.md` | `ls docs/adr/ADR-*.md` | Ten accepted decision records are present | ADR-001–010 |
| 17 | Requirement traceability in `docs/traceability/matrix.md` | `rg '^\\| REQ-' docs/traceability/matrix.md` | Exactly 31 requirement rows point to design, code, test, command, docs, and evidence | REQ-001–031 |
| 18 | Audit evidence through `GET /workflows/{id}/audit` and `AuditRecorder` | `curl -s http://127.0.0.1:8080/workflows/{id}/audit` | Ordered events include actor, action, affected artifact, result, reason, and payload | REQ-019, REQ-026 |
| 19 | Reliability measurements in `observability/MetricsService.java` | `./mvnw -Dspring-boot.run.arguments=--metrics-report spring-boot:run` | JSON includes rates, frequencies, latency, MTTR populations, and `isDemonstrationData:true` | REQ-022, REQ-025, REQ-027 |
| 20 | Security controls and residual risks in `SECURITY.md` | `./mvnw test -Dtest=UrlValidatorTest,UrlValidatorSsrfTest,WorkflowEnginePolicyEnforcementTest` | Scheme/SSRF/ULA and policy-blocking tests pass; no-auth limitation remains explicit | REQ-021 |
| 21 | Known limitations in `docs/assessment/final_engineering_summary.md` §19 | `rg -n '^## 19|authentication|rate limiting|migration|distributed' docs/assessment/final_engineering_summary.md` | Deferred production gaps and their release impact are visible | REQ-020, REQ-024 |
| 22 | Final engineering summary in `docs/assessment/final_engineering_summary.md` | `rg '^## [0-9]+ ' docs/assessment/final_engineering_summary.md` | Exactly 21 sections appear in the mandated order | all |
| 23 | API/schema/version/compatibility evidence in `contracts/openapi.yaml` and `data-model.md` | `./mvnw test -Dtest=LinkControllerContractTest,WorkflowControllerContractTest,MetricsControllerContractTest,UrlShortenerSchemaBootTest` | Controller contracts and real schema boot pass; pre-release compatibility rule is documented | REQ-001–012, REQ-025 |
| 24 | Compliance/change control in `policies/v1.yaml`, policy tests, and brownfield evidence | `./mvnw test -Dtest=PolicyEngineTest,PolicyExceptionTest,BrownfieldScenarioTest` | Mandatory failures block; exception scope/expiry/control rules and impact gate pass | REQ-013, REQ-020, REQ-030 |
| 25 | MTTR population and exclusions in `MetricsService.computeMttr` | `./mvnw test -Dtest=MetricsMttrTest,MetricsServiceIsolationTest` | Only recovered valid intervals are averaged; unrecovered/invalid records are counted separately | REQ-022, REQ-027 |

Actual final-run outputs are recorded in `docs/assessment/convergence_report.md`; the skeptical
scorecard is in `docs/assessment/final_independent_assessment.md`.
