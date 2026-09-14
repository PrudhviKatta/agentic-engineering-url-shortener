# Implementation Tasks: Agentic Software Engineering System — URL Shortener

**Feature branch**: none created — despite the `001-agentic-url-shortener` feature name used
throughout this SpecKit artifact set, all work to date has been performed directly on `main`, and
no `001-agentic-url-shortener` branch has ever existed in this repository. **Corrected by the Task
10 corrective batch** (`pre_implementation_analysis.md` / `codex-recommend.md` Minor findings):
this line previously implied a branch strategy that was never followed. Actual practice: every
commit in this repository's history (`git log --oneline`) has landed directly on `main`; Tasks
11+ should continue that actual practice rather than a branch name that exists only in this
document's own metadata.
**Stage**: `/speckit.tasks` (Task 9 of the controller's 48-task runbook)
**Source**: this document transcribes Phases 2–12 (runbook Task 11 through Task 48) of
`docs/engineering-runbook.md` into SpecKit's task format.
Every `T0NN` ID below equals the runbook's own "Task N" plus a `T0` prefix (e.g. runbook Task 20 =
`T020`), so a reviewer can cross-reference either document without a lookup table.

## Authority

Per `plan.md`'s own Authority note, this document — once written — is the authoritative SpecKit
task list. The runbook (`docs/engineering-runbook.md`) is this
document's **source draft**, not a competing shadow process: where the two ever appear to disagree
after this point, `tasks.md` governs, and the disagreement should be resolved by editing this file
(with a note), not by reverting to the runbook.

## Task 7 reconciliation (Step 1 of the Task 9 brief)

Before transcribing, this document checked whether Task 7's ADR Generation Gate (the human
candidate's acceptance of ADR-003 through ADR-010 on 2026-09-13) changed anything the runbook
assumed when it was written immediately before Task 7. **Finding: no changes required.** All eight
ADRs (`docs/adr/ADR-003-short-code-generation.md` through `ADR-010-authentication-assumption.md`)
were drafted at Task 7 to formalize positions `plan.md` Section 12 and `research.md`'s D3–D10
entries had already recorded as "Proposed for ADR at Task 7," and each was accepted by the human
candidate without amendment. The runbook's task-level design choices (the `entryApproval`/
`exitApproval` split per ADR-006, the thread-pool-per-batch concurrency model per ADR-008, the
compensation-only reliability model per ADR-009, the YAML policy format per ADR-007, the base62
short-code scheme per ADR-003, the in-process orchestrator per ADR-004/005, and the disclosed
no-auth posture per ADR-010) all match the Accepted ADR text verbatim. This transcription proceeds
against the runbook exactly as written, with no upstream corrections needed.

## Required task-group cross-reference (source material's four mandatory groups)

The Task 9 source material requires four specific task groups to exist in `tasks.md`. Rather than
invent parallel tasks that duplicate what the runbook already built, each group is satisfied by
specific `T0NN` tasks below, cross-referenced here so a reviewer can find the group without reading
every task:

1. **Timebox and scope-control group.** Already established in `plan.md` §14 (Delivery Sequence &
   Scope Control) at Task 6, restated here as the governing frame for every task below: must-have
   scope = Phases 2–10 in full (§14.2); deferred scope = auth, rate limiting, migrations, custom
   aliases, link mutation, multi-region ID generation, retention policy (§14.3, unchanged by any
   T0NN task — none of T011–T048 implement deferred-backlog items); day-level milestones = Day 1
   Phases 2–4 (T011–T026), Day 2 Phases 5–9 (T027–T041), Day 3 Phases 10–11 (T042–T048) (§14.4);
   critical path = orchestration engine (T020–T026) blocks policy (T027–T029), reliability
   (T030–T034), and replanning (T035), and all three scenarios (T039–T041) block on all of the
   above (§14.5); stop condition = if T020–T026 are not green by end of Day 1, cut SCN-C's replan
   depth (§14.6) before cutting any governance/audit task — see T041's guardrail.
2. **API/schema deliverable group.** **Corrected by the Task 10 corrective batch**
   (`pre_implementation_analysis.md` Section 7 HIGH-1 / `codex-recommend.md` Findings #4, #9): this
   group previously claimed `POST /workflows` and `GET /links/{code}` were already covered by
   T017–T019/T026; they were not — no task implemented or contract-tested either operation. T017a
   and T026a (added below) now close that gap. Contract generation: `contracts/openapi.yaml`
   (Task 6, pre-existing, corrected by Task 10) + T017/T017a/T018/T019/T026/T026a/T038 (the
   concrete endpoints). Schema versioning: `data-model.md` (Task 6, corrected by Task 10 with
   `plan_version`/`step_instance_id`, see Section 3 there) + T012/T021 (entity/table shape) +
   T024's `contextJson` column addition (an explicit, tested schema change to an already-shipped
   entity). Compatibility review: T043 (traceability matrix cross-checks every endpoint against
   `openapi.yaml`). Examples: T039–T041's `docs/scenarios/*.md` each paste real
   `GET /workflows/{id}/audit` output. Contract tests: `LinkControllerContractTest`
   (T017–T019, T017a), `WorkflowControllerContractTest` (T026, T026a),
   `MetricsControllerContractTest` (T038). Documentation: T045 (`README.md`). Change-control
   approval: REQ-030, enforced by T029 (policy exceptions) and T035 (replan versioning).
3. **MTTR instrumentation and reporting group.** Timestamps: `RecoveryEvent`'s three `Instant`
   fields (T037). Recovered-event population: `MetricsService.computeMttr`'s `recovered` filter
   (T037). Exclusions: `MttrReport.excludedCount` + `populationNote` (T037). Unrecovered failures:
   `MttrReport.unrecoveredCount` (T037, tested by `mttrAveragesOnlyRecoveredEventsAndExcludesUnrecovered`).
   Calculation validation: `MetricsMttrTest` (T037) asserts the exact averaging formula. Demonstration-
   data labeling: `ReliabilitySummary.isDemonstrationData` surfaced at `GET /metrics` (T038, tested by
   `metricsEndpointLabelsDataAsDemonstration`).
4. **Greenfield "no unnecessary clarification" group.** T039 is this group in full: requirement-
   quality checks (`AmbiguityDetectionStage.run`'s completeness/consistency/testability/policy-boundary
   check — corrected 2026-09-13, this used to be a combined `RequirementStage` before the real
   12-node graph split it into three), traceability (the recorded `no_clarification_required`
   decision with its rationale),
   policy enforcement (the same stage graph runs through T027–T028's `PolicyEngine` via
   `ImplementationStage`'s reported artifacts), and escalation-if-ambiguity-later-appears (T041's
   ambiguous-requirement scenario is the explicit contrast case proving the engine *can* escalate
   when a requirement is genuinely ambiguous — the greenfield path's non-escalation is a deliberate,
   evidenced choice, not an inability to escalate).

## Evidence-task cross-reference (source material's evidence-task list)

| Required evidence | Produced by |
|---|---|
| Requirement-to-test traceability | T043 (`docs/traceability/matrix.md`) |
| Architecture-decision evidence | T043 (`docs/architecture/overview.md`), ADRs (Task 7, unchanged) |
| Approval evidence | T023, T026 (`ApprovalEntity` rows + `/workflows/{id}/audit`), T039 |
| Policy-version evidence | T027 (`PolicyEngine.version()`), T028 (`PolicyEvaluationEntity.policyVersion`) |
| Compliance evaluation evidence | T028 (`policy_evaluations` table, persisted every stage run) |
| Failed-policy blocking evidence | T028 test `mandatoryPolicyFailureSafeStopsWorkflow` |
| Approved-exception evidence | T029 (`PolicyExceptionEntity` + `PolicyExceptionTest`) |
| Compensating-control evidence | T032/T033 (`CompensationRegistry`, `StepStatus.COMPENSATED`) |
| Change-request/impact-analysis evidence | T040 (`ChangeImpactReportStage`'s joined seven-branch result map), T035 (`replanned` event payload) |
| State-transition evidence | T020/T021 (`StepStatus`/`WorkflowStatus`), T026 (`GET /workflows/{id}`) |
| Sequential-path evidence | T022 test `submitRunsSequentialStagesToCompletion` |
| Parallel-path/synchronization evidence | T022 test `submitRunsParallelGroupConcurrently` |
| Retry evidence | T030 (`RetryTest`), T033 (`step.retryCount`) |
| Compensation/rollback evidence | T032/T033 (`COMPENSATED` status; no `ROLLED_BACK` producer exists — disclosed, see T033 guardrail) |
| Safe-stop evidence | T028, T033, T034 (`WorkflowEngineSafeStopTest`) |
| Resume evidence | T023 (`approve()` resumes), T029 (`retrySafeStopped()`) |
| Replanning evidence | T035 (`WorkflowEngineReplanningTest`), T041 (`AmbiguousScenarioTest`) |
| Stage-boundary-violation evidence (REQ-031) | **T036a** (`WorkflowEngineStageBoundaryTest`) — added by the Task 10 corrective batch, see T036a's entry |
| Measured demonstration metrics | T037/T038 (`MttrReport`, `ReliabilitySummary`, `isDemonstrationData`) |
| Limitations | T045 (`SECURITY.md`), T047 (final summary §19) |
| Residual risks | ADR-010 (unchanged), T047 |
| Reviewer navigation | T044 (`docs/assessment/reviewer_navigation_guide.md`) |

## Field legend

Every task below carries: **ID**, **Objective**, **REQ/SCN/ADR**, **Files**, **Prerequisites**,
**Parallel**, **Expected artifact**, **TDD**, **Validation command**, **Doc impact**,
**Traceability update**, **Risk/guardrail**, **Completion criteria**, **Human approval**.

`Validation command` assumes the JDK pinned by Task 2: prefix every `./mvnw` invocation with
`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` if that variable is not
already exported in the shell (the runbook shows this prefix explicitly on Tasks 11, 17, 46, 48;
it is omitted below on repeated lines for readability, not because it stops being required).

`Human approval` is **not** a per-task human gate — this session's own reviewer-subagent-per-task
process is the checkpoint for ordinary implementation work, per the guide's own gate list (ADRs,
constitution, and spec get dedicated Human Gates; implementation tasks do not). It is flagged
`true` only where the task's completion itself exercises one of constitution Principle III's
human-owned decision classes (security-sensitive decisions, a material policy exception,
release-readiness, or final quality sign-off) — seven tasks qualify: T015, T026, T029, T040, T046,
T047, T048. See each task's entry for the specific justification.

---

## Phase 2 — Engineering Baseline

### T011: App skeleton — health endpoints via Spring MVC

- **Objective**: Expose `GET /healthz`/`GET /readyz` so the walking skeleton is observably alive
  and DB-reachable before any domain code exists.
- **REQ/SCN/ADR**: REQ-010 (operational health); SCN-A, SCN-B; ADR-002 (readyz probes the
  H2 `DataSource` ADR-002 selected).
- **Files**: Create `src/main/java/com/schwab/urlshortener/api/HealthController.java`; Test
  `src/test/java/com/schwab/urlshortener/api/HealthControllerTest.java`.
- **Prerequisites**: none (Task 2's Spring Boot skeleton is the only precondition, already
  committed).
- **Parallel**: `true` — disjoint files/package from T012; no shared interface.
- **Expected artifact**: `HealthController` bean; `/healthz` returns `200 {"status":"ok"}`;
  `/readyz` returns `200 {"status":"ready"}` or a 5xx if the DataSource is unreachable.
- **TDD**: yes — `HealthControllerTest` written and observed to fail (404/compile error) first.
- **Validation command**: `./mvnw test -Dtest=HealthControllerTest`
- **Doc impact**: none.
- **Traceability update**: seeds the REQ-010 row's Task/Code/Test columns in the eventual
  traceability matrix (T043) with `T011` / `HealthController.java` / `HealthControllerTest`.
- **Risk/guardrail**: none called out in the runbook.
- **Completion criteria**: `HealthControllerTest` passes; `git commit -m "feat: establish walking
  skeleton with health endpoints"` recorded.
- **Human approval**: false.

### T012: `ShortLinkEntity`/`RedirectEventEntity` JPA schema + repository round-trip

- **Objective**: Establish the persisted schema for short links and redirect events via Spring
  Data JPA, proven by a save/read round-trip.
- **REQ/SCN/ADR**: REQ-001–REQ-003 (creation/uniqueness groundwork), REQ-008/009 (persistence
  reliability); SCN-A, SCN-B; ADR-002.
- **Files**: Create `persistence/ShortLinkEntity.java`, `persistence/RedirectEventEntity.java`,
  `persistence/ShortLinkJpaRepository.java`; Test `persistence/ShortLinkRepositoryTest.java`.
- **Prerequisites**: none.
- **Parallel**: `true` — disjoint from T011 (different package, no shared interface).
- **Expected artifact**: `short_links`/`redirect_events` tables via Hibernate `ddl-auto: update`;
  `ShortLinkJpaRepository.findByCode(String)`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=ShortLinkRepositoryTest`
- **Doc impact**: none (schema already specified in `data-model.md` from Task 6; this task
  implements it, doesn't redefine it).
- **Traceability update**: REQ-001–003, REQ-008/009 rows gain `T012`.
- **Risk/guardrail**: `@DataJpaTest` uses an isolated in-memory H2 context, distinct from the
  real file-mode `data/app` datasource `application.yml` configures — deliberate, not a gap (T013
  is the checkpoint that verifies the real datasource separately).
- **Completion criteria**: test passes; `git commit -m "feat(persistence): add short_links and
  redirect_events schema"`.
- **Human approval**: false.

### T013: Verify real (file-mode H2) schema creation on full application startup

- **Objective**: Prove Hibernate's `ddl-auto: update` actually creates the schema against the
  real, file-mode datasource on a full `@SpringBootTest` boot — not just the `@DataJpaTest` slice.
- **REQ/SCN/ADR**: REQ-009 (persistence failures/real schema), REQ-010; SCN-A, SCN-B; ADR-002.
- **Files**: Test `src/test/java/com/schwab/urlshortener/UrlShortenerSchemaBootTest.java`; Modify
  `.gitignore` (verify only — Task 2 already ignores `data/*.mv.db`/`data/*.trace.db`).
- **Prerequisites**: T012 (needs `ShortLinkJpaRepository` to exist).
- **Parallel**: `true` with T014, T015, T016 — this task only adds a verification test file, no
  production code, and shares no file with any of the three.
- **Expected artifact**: `data/app.mv.db` exists on disk after the test run, proving real
  file-mode schema creation (no separate `create_all()`/init step needed, unlike the original
  SQLAlchemy design).
- **TDD**: yes (in the checkpoint sense — the test is written to prove a property, not to drive
  new production code, since none is expected).
- **Validation command**: `./mvnw test -Dtest=UrlShortenerSchemaBootTest`
- **Doc impact**: none.
- **Traceability update**: none beyond T012's rows (this is a verification checkpoint on the same
  requirement, not a new capability).
- **Risk/guardrail**: confirm `git status` shows the newly-created `data/app.mv.db` as
  untracked/ignored, not accidentally staged.
- **Completion criteria**: test passes; `data/*.mv.db` confirmed ignored; `git commit -m "test:
  verify schema auto-creation against the real file-mode datasource"`.
- **Human approval**: false.

---

## Phase 3 — URL Shortener Domain

### T014: `ShortLink` domain record (expiry rule)

- **Objective**: Define the pure-domain `ShortLink` record and its `isExpired()` rule, bridging
  from `ShortLinkEntity` at controller boundaries.
- **REQ/SCN/ADR**: REQ-004, REQ-005 (redirect resolution/expiration); SCN-A, SCN-B; no ADR.
- **Files**: Create `domain/ShortLink.java`; Test `domain/ShortLinkTest.java`.
- **Prerequisites**: T012 (needs `ShortLinkEntity` for `fromEntity(...)`).
- **Parallel**: `true` with T015 and T016 — three disjoint domain files, no shared interface
  between any pair (T014 depends on T012, not on T015/T016).
- **Expected artifact**: `ShortLink` record with `isExpired()`, `fromEntity(ShortLinkEntity)`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=ShortLinkTest`
- **Doc impact**: none. Explicit design note carried into T043: no separate `ShortLinkRepository`
  wrapper class — `ShortLinkJpaRepository` (T012) is already the persistence abstraction.
- **Traceability update**: REQ-004/005 rows gain `T014`.
- **Risk/guardrail**: none.
- **Completion criteria**: test passes; `git commit -m "feat(domain): define URL and short-link
  domain model"`.
- **Human approval**: false.

### T015: URL validation (security-critical; parallel-safe with T016)

- **Objective**: Enforce an http/https scheme allow-list and block SSRF targets (loopback,
  link-local, site-local, multicast, unresolvable hosts) before any URL is persisted.
- **REQ/SCN/ADR**: REQ-002 (invalid URL rejection), REQ-021 (security); SCN-A, SCN-B; ADR-010
  (authentication/security-posture ADR; this task is the concrete control that ADR-010's
  surrounding threat-model discussion assumes exists).
- **Files**: Create `domain/UrlValidator.java`, `domain/InvalidUrlException.java`; Test
  `domain/UrlValidatorTest.java`, `domain/UrlValidatorSsrfTest.java`.
- **Prerequisites**: none.
- **Parallel**: `true` — explicitly parallel-safe with T016 per the runbook; also parallel-safe
  with T014 (see T014's entry).
- **Expected artifact**: `UrlValidator.validate(String) -> String` throwing `InvalidUrlException`
  on disallowed scheme, malformed URL, or blocked target host.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=UrlValidatorTest,UrlValidatorSsrfTest`
- **Doc impact**: feeds `SECURITY.md` (T045)'s "URL scheme allow-list / SSRF guard" section.
- **Traceability update**: REQ-002/021 rows gain `T015`.
- **Risk/guardrail**: this is a security-sensitive control per constitution Principle V
  ("all externally supplied input... validated... before use"; SSRF explicitly named as an
  in-scope threat, not an edge case). It is also the exact control T040 later discovers is
  incomplete for IPv6 ULA — that gap is real, disclosed, and fixed there, not here.
- **Completion criteria**: both test classes pass; `git commit -m "feat(security): validate URL
  schemes and block SSRF targets"`.
- **Human approval**: **true** — constitution Principle III reserves "security-sensitive
  decisions" for humans; this task defines the system's entire abuse/SSRF threat-model boundary
  (which schemes are trusted, which address ranges are blocked). The reviewer-subagent process
  covers ordinary code review; this specific control's design should also be visible to the human
  candidate before Task 16/17 build on top of it, since a wrong boundary here is expensive to
  discover later (as T040 later demonstrates for the IPv6 case that slipped through).

### T016: Short-code generation (parallel-safe with T015)

- **Objective**: Deterministic, zero-padded base62 short-code generation from a numeric ID.
- **REQ/SCN/ADR**: REQ-001 (valid URL creation), REQ-003 (short-code uniqueness); SCN-A, SCN-B;
  ADR-003 (short-code generation strategy).
- **Files**: Create `domain/ShortCodeGenerator.java`; Test `domain/ShortCodeGeneratorTest.java`.
- **Prerequisites**: none.
- **Parallel**: `true` — explicitly parallel-safe with T015 per the runbook; also with T014.
- **Expected artifact**: `ShortCodeGenerator.toBase62(long)`, `generateCode(long)`,
  `generateCode(long, int width)`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=ShortCodeGeneratorTest`
- **Doc impact**: none.
- **Traceability update**: REQ-001/003 rows gain `T016`; ADR-003 row gains `T016`.
- **Risk/guardrail**: uniqueness relies on the DB `IDENTITY` sequence being single-writer
  (documented in `research.md`'s D3 caveat and `plan.md`'s deferred-backlog §14.3 — multi-region
  ID generation is explicitly out of scope this iteration).
- **Completion criteria**: test passes; `git commit -m "feat(domain): implement deterministic
  base62 short-code generation"`.
- **Human approval**: false.

### T017: `POST /links` API (contract test first)

- **Objective**: Expose short-link creation over HTTP, composing T012/T014/T015/T016.
- **REQ/SCN/ADR**: REQ-001, REQ-002, REQ-003; SCN-A, SCN-B; ADR-003.
- **Files**: Create `api/dto/CreateLinkRequest.java`, `api/dto/LinkResponse.java`,
  `api/dto/ErrorResponse.java`, `api/LinkController.java`, `api/ApiExceptionHandler.java`; Test
  `api/LinkControllerContractTest.java`.
- **Prerequisites**: T012, T014, T015, T016 (all four are directly consumed — see the runbook's
  own "Consumes" line).
- **Parallel**: false — this is the first task that fans dependencies back in from the parallel
  domain-layer group (T014/T015/T016); it cannot start until all three land.
- **Expected artifact**: `POST /links` → `201 {code, shortUrl, longUrl, expiresAt}`; `422` with
  `{"error":{"reason": "..."}}` on invalid URL.
- **TDD**: yes.
- **Validation command**:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test -Dtest=LinkControllerContractTest`
- **Doc impact**: matches `contracts/openapi.yaml`'s `POST /links` operation (Task 6); no schema
  change needed.
- **Traceability update**: REQ-001–003 rows gain `T017`; feeds T043's API/schema compatibility
  cross-check against `openapi.yaml`.
- **Risk/guardrail**: `repository.save(entity)` called twice (once for the `IDENTITY`-generated
  id, once for the derived code) is intentional — Hibernate's `IDENTITY` strategy runs the
  `INSERT` synchronously inside `save()`, so no placeholder-value hack is needed; this is not a
  bug to "optimize away" into one call.
- **Completion criteria**: test passes; `git commit -m "feat(api): implement validated short-link
  creation"`.
- **Human approval**: false.

### T017a: `GET /links/{code}` metadata endpoint (contract test first)

**Added by the Task 10 corrective batch** (`pre_implementation_analysis.md` Section 7 HIGH-1 /
`codex-recommend.md` Findings #4, #9): `plan.md` Section 2.1 specified this endpoint as a
plan-level design addition with its own implementation obligation, and `contracts/openapi.yaml`
already defines it (`operationId: getLinkMetadata`), but no task in the original transcription
implemented or contract-tested it — `tasks.md`'s own cross-reference table incorrectly implied
T017–T019 already covered it. This task closes that gap.

- **Objective**: Expose read-only link metadata lookup over HTTP, distinct from the
  redirect-performing `GET /r/{code}` (T018) and from the analytics endpoint (T019) — this
  endpoint never writes `redirect_count`/`last_accessed_at` and never redirects.
- **REQ/SCN/ADR**: REQ-004 (resolution — metadata analog), REQ-012 (inspection-without-mutation
  pattern, by analogy to the orchestration engine's own read-only inspection endpoints); SCN-A,
  SCN-B; no new ADR.
- **Files**: Modify `api/LinkController.java` (add `GET /links/{code}`); Test
  `api/LinkControllerContractTest.java` (extend).
- **Prerequisites**: T017 (same controller/test file; needs `ShortLinkJpaRepository.findByCode`
  from T012 and `LinkResponse` from T017).
- **Parallel**: false — same file, same test class as T017/T018/T019.
- **Expected artifact**: `GET /links/{code}` → `200 {code, longUrl, shortUrl, expiresAt}` for a
  known code (including an already-expired one — this endpoint reports metadata, it does not
  enforce expiration); `404 {"error":{"reason":"short_code_not_found"}}` for an unknown code.
  Never updates `redirect_count`/`last_accessed_at` (that remains exclusively `GET /r/{code}`'s
  job, T018) and never issues a redirect.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=LinkControllerContractTest`
- **Doc impact**: matches `contracts/openapi.yaml`'s `GET /links/{code}` operation (Task 6,
  corrected by Task 10); no schema change needed.
- **Traceability update**: REQ-004/012 rows gain `T017a`; closes the API/schema deliverable
  group's `GET /links/{code}` gap flagged by Task 10's corrective batch.
- **Risk/guardrail**: do not let this endpoint's handler share code with `GET /r/{code}`'s
  redirect-performing path in a way that risks accidentally incrementing analytics counters or
  issuing a redirect from this endpoint — the two must remain observably distinct per
  `contracts/openapi.yaml`'s own description of the split.
- **Completion criteria**: test passes; `git commit -m "feat(api): expose read-only link metadata
  lookup"`.
- **Human approval**: false.

### T018: Redirect resolution + expiration behavior

- **Objective**: Add `GET /r/{code}` — 307 on success, 404 unknown code, 410 expired — recording a
  `RedirectEventEntity` per attempt.
- **REQ/SCN/ADR**: REQ-004, REQ-005; SCN-A, SCN-B; no new ADR.
- **Files**: Modify `api/LinkController.java` (add `GET /r/{code}`); Create
  `persistence/RedirectEventJpaRepository.java`; Test `api/LinkControllerContractTest.java`
  (extend).
- **Prerequisites**: T017a (modifies the same controller and test file T017/T017a created; see
  Task 10 corrective batch note at T017a).
- **Parallel**: false — same file, same test class as T017/T017a and T019; strictly sequential.
- **Expected artifact**: 307 redirect with `Location` header on success; 404/410 via
  `ResponseStatusException`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=LinkControllerContractTest`
- **Doc impact**: matches `openapi.yaml`'s `GET /r/{code}` operation.
- **Traceability update**: REQ-004/005 rows gain `T018`.
- **Risk/guardrail**: the redirect-disabling `SimpleClientHttpRequestFactory` test boilerplate is
  repeated three times in the runbook's own test code — extracting a private `noRedirectClient()`
  helper is an explicitly pre-approved simplification, not scope creep.
- **Completion criteria**: test passes; `git commit -m "feat(redirect): implement resolution and
  expiration"`.
- **Human approval**: false.

### T019: Analytics endpoint

- **Objective**: Expose `GET /links/{code}/analytics` reporting redirect count and last-access
  time.
- **REQ/SCN/ADR**: REQ-006 (analytics capture); SCN-A; no new ADR.
- **Files**: Modify `api/LinkController.java` (add endpoint); Create
  `api/dto/AnalyticsResponse.java`; Test `api/LinkControllerContractTest.java` (extend).
- **Prerequisites**: T018 (same controller/test file; also logically depends on T018's
  redirect-counting behavior to have data to report).
- **Parallel**: false.
- **Expected artifact**: `GET /links/{code}/analytics` → `{code, redirectCount, lastAccessedAt}`;
  404 unknown code.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=LinkControllerContractTest`
- **Doc impact**: matches `openapi.yaml`'s analytics operation.
- **Traceability update**: REQ-006 row gains `T019`. Closes Phase 3 (URL-shortener domain
  complete); the API/schema deliverable group's "contract tests" line item for the domain API
  surface is now fully covered.
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(analytics): capture and expose
  redirect analytics"`.
- **Human approval**: false.

---

## Phase 4 — Orchestration Engine (the critical differentiator)

**Sequencing note**: T020 through T026 are strictly sequential — each task modifies or directly
extends the state `WorkflowEngine.java` accumulates from the previous task. None of the seven is
marked `parallel: true` against another task in this block.

### T020: Stage/graph model + topological + parallel-group resolution

- **Objective**: Define the stage/graph vocabulary (`StepStatus`, `WorkflowStatus`,
  `StageHandler`, `StageDefinition`) and `DependencyGraph`'s topological/parallel-batch resolution.
- **REQ/SCN/ADR**: REQ-011, REQ-012 (workflow creation/inspection); SCN-A, SCN-B, SCN-C; ADR-004
  (orchestration model).
- **Files**: Create `orchestration/StepStatus.java`, `WorkflowStatus.java`, `StageHandler.java`,
  `StageDefinition.java`, `DependencyGraph.java`, `CycleException.java`,
  `UnknownDependencyException.java`; Test `orchestration/DependencyGraphTest.java`.
- **Prerequisites**: none.
- **Parallel**: `true` relative to T027 (PolicyEngine) — see T027's entry; **not** parallel with
  T021 in the usual sense — `StageHandler.run(WorkflowContext ctx)` forward-references a class
  (`WorkflowContext`) that T021 defines. The runbook explicitly allows either (a) a minimal empty
  `WorkflowContext` stub committed with T020 so it compiles standalone, or (b) implementing T020
  and T021 together in one sitting. Whichever is chosen must be noted at the task-group checkpoint.
- **Expected artifact**: `DependencyGraph(stages).topologicalBatches()` groups independent stages
  into the same batch; `validate()` throws `CycleException`/`UnknownDependencyException`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=DependencyGraphTest`
- **Doc impact**: none yet (feeds T043's architecture overview).
- **Traceability update**: REQ-011/012 rows gain `T020`; ADR-004 row gains `T020`.
- **Risk/guardrail**: see Prerequisites — the `WorkflowContext` forward-reference is a known,
  documented compile-order wrinkle, not an oversight.
- **Completion criteria**: test passes; `git commit -m "feat(orchestration): establish dependency
  graph model"`.
- **Human approval**: false.

### T020b: `StageCatalog` — restart-safe handler/condition/fallback lookup by stable key
*(revised 2026-09-13, fourth review round: "do not attempt to persist Java lambdas or Predicate
objects" — the first pass's `registerActivationCondition(...)`/`registerFallback(...)` storing
`Predicate`/`StageHandler` objects directly on the live `WorkflowEngine` instance does not survive a
process restart, contradicting REQ-017 and the binding REQ-028 5-second recovery-time objective)*

**Status: PARTIAL (corrected 2026-09-13, Round-6, Codex REQUEST AMENDMENTS item 4).** `StageCatalog`
itself exists (`orchestration/StageCatalog.java`) with `handlerFor`/`activationConditionFor`/
`fallbackFor`, and `StageDefinition`/`WorkflowStepEntity` both carry the two new
`activationConditionKey`/`fallbackHandlerKey` fields/columns. **`WorkflowEngine` does not yet use any
of this**: `runFrom`/`runStage` still resolve handlers exclusively from the in-memory `registry` map
populated at `submit()` time, never from `StageCatalog`; no `SKIPPED`-via-activation-condition path
exists; nothing reconstructs a stage list from persisted `WorkflowStepEntity` rows (a process
restart still loses the registry, exactly the failure mode this task exists to prevent). Full wiring
is deferred to Task 50 (restart-safety epic) per the runbook's own established scoping for that
epic. The traceability update below is corrected accordingly — REQ-017/REQ-028 (restart-safety /
recovery-time objective) are NOT satisfied by this task alone; only the catalog scaffolding is.

- **Objective**: Let a stage be marked `SKIPPED` (never runs, satisfies dependents' preconditions
  anyway) when a catalog-registered predicate over `WorkflowContext` evaluates false at the point
  its batch is reached, and let a stage register a fallback handler — both looked up by a stable
  string key from a `StageCatalog` built at application boot, not from anything stored on a live
  engine instance. `StageDefinition` gains two `String` fields (`activationConditionKey`,
  `fallbackHandlerKey`); `WorkflowStepEntity` persists both, so `runFrom` can reconstruct the full
  stage list — handlers, conditions, and fallbacks alike — from the database plus this catalog on
  every call, including after a process restart.
- **REQ/SCN/ADR**: REQ-011, REQ-013, REQ-016, REQ-017, REQ-028; SCN-C; ADR-004 (extends, does not
  amend).
- **Files**: Create `orchestration/StageCatalog.java`; Modify `orchestration/StageDefinition.java`
  (add the two key fields, both defaulted to `null` in `.of(...)`); Modify
  `orchestration/persistence/WorkflowStepEntity.java` (add `activationConditionKey`,
  `fallbackHandlerKey` columns); Modify `orchestration/WorkflowEngine.java` (`runFrom`'s batch loop
  reads `catalog.activationConditionFor(...)`, `runStage` reads `catalog.fallbackFor(...)` — no
  `Map<String,Predicate<...>>` or `Map<String,StageHandler>` field lives on the engine itself); Test
  `orchestration/StageCatalogTest.java`.
- **Prerequisites**: T021 (`WorkflowStepEntity`), T023 (this task's activation check sits alongside
  the existing entry-gate check in the same batch loop T023 established).
- **Parallel**: false.
- **Expected artifact**: a stage with a catalog-registered condition that evaluates false reaches
  `SKIPPED`, never invoking its handler; a dependent stage runs normally regardless of whether its
  prerequisite ended `SUCCEEDED`, `SUCCEEDED_WITH_FALLBACK`, or `SKIPPED`; `catalog.handlerFor(...)`
  throws for an unregistered stage name (a configuration error, not a runtime condition).
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=StageCatalogTest`
- **Doc impact**: `plan.md` Section 3 gains a description of this mechanism; `data-model.md` gains
  the two new `WorkflowStepEntity` columns.
- **Traceability update**: REQ-011/013/016 rows gain `T020b` (catalog scaffolding only).
  REQ-017/028 do NOT gain a completed `T020b` row — restart-safe reconstruction is not wired into
  `WorkflowEngine` yet; see the PARTIAL status note above and Task 50.
- **Risk/guardrail**: this revises T020b's original design (see the runbook's Phase 12, Task 49) —
  the earlier "don't touch the `StageDefinition` record, use a registration side-channel" decision
  is superseded here, disclosed, not silently reversed, because it does not survive a real restart
  requirement. Every full-positional `new StageDefinition(...)` call site across T020–T041 needs two
  more trailing `null` arguments — mechanical, verified by compilation, no runtime behavior change
  for a stage that uses neither key.
- **Completion criteria**: test passes; `git commit -m "feat(orchestration): add a restart-safe
  StageCatalog for handler/condition/fallback lookup"`.
- **Human approval**: false.

### T020c (folded into T039 — retained as a historical marker, do not implement separately)
*(originally: "Real 12-node stage graph — split `RequirementStage`, persist Stages 4 and 12";
corrected 2026-09-13, fourth review round)*

This task, as originally written, modified `orchestration/stages/RequirementStage.java` and
extended `GreenfieldScenarioTest`/rewrote `AmbiguousScenarioTest` — files that T039/T041 alone
create — while sitting here, immediately after T020, i.e. before either of those tasks could ever
run. No task should modify a file a later task creates. Because no code exists yet for any of the
three scenarios, the fix is to write T039 directly in its authoritative final form (the real
12-node graph, `RequirementIngestionStage`/`RequirementNormalizationStage`/`AmbiguityDetectionStage`
split, `FinalSummaryStage`, and the human-clarification decision-flow fold-in into
`DecompositionStage`) rather than build an eight-stage placeholder here and patch it eleven tasks
later. Implement T039 and T041 instead of this entry; the REQ/SCN/ADR coverage this task named
(REQ-011/012/013/016/019; SCN-A, SCN-B, SCN-C; ADR-004) is unchanged — it now travels with T039/T041.

### T021: Workflow persistence tables + context/decision-lineage object

- **Objective**: Persist workflow instances/steps/events and define `WorkflowContext` as the
  carrier of decision lineage and artifacts across stage handlers.
- **REQ/SCN/ADR**: REQ-011, REQ-012, REQ-017, REQ-028 (context/decision lineage, recoverability);
  SCN-A, SCN-B, SCN-C; ADR-004.
- **Files**: Create `orchestration/WorkflowContext.java`, `JsonMapAttributeConverter.java`,
  `JsonListAttributeConverter.java`,
  `orchestration/persistence/{WorkflowInstanceEntity,WorkflowStepEntity,WorkflowEventEntity}.java`,
  `orchestration/persistence/{WorkflowInstanceJpaRepository,WorkflowStepJpaRepository,WorkflowEventJpaRepository}.java`;
  Test `orchestration/WorkflowContextTest.java`.
- **Prerequisites**: T020 (uses `StepStatus`/`WorkflowStatus` enums in the entities).
- **Parallel**: false (resolves T020's forward reference; see T020's guardrail).
- **Expected artifact**: `WorkflowContext.recordDecision(actor, decision, rationale)`,
  `addArtifact(name, value)`; `WorkflowStepJpaRepository.findByWorkflowIdAndPlanVersionAndName(...)`
  — **corrected by the Task 10 corrective batch** (`data-model.md` Section 3 post-Task-10 note /
  `codex-recommend.md` Finding #6): `WorkflowStepEntity` must carry a `plan_version` field (default
  `1` at `submit()`, incremented copies inserted by `replan()`), and uniqueness lookups must be
  scoped to `(workflowId, planVersion, name)`, not `(workflowId, name)` alone — the original
  `findByWorkflowIdAndName(...)` signature is superseded by this corrected one.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowContextTest`
- **Doc impact**: extends `data-model.md`'s schema (Task 6, corrected by Task 10 with
  `plan_version`) with the concrete JPA shape.
- **Traceability update**: REQ-011/012/017/028 rows gain `T021`.
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(orchestration): add workflow state
  persistence and context lineage"`.
- **Human approval**: false.

### T022: `WorkflowEngine.submit()` — sequential + parallel execution with persistence

- **Objective**: Execute a stage graph batch-by-batch, sequentially between batches and
  concurrently within a batch, persisting step/instance state throughout.
- **REQ/SCN/ADR**: REQ-011, REQ-012, REQ-022, REQ-023 (reliability/scalability via concurrent
  fan-out); SCN-A, SCN-B, SCN-C; ADR-004, ADR-008 (concurrency model).
- **Files**: Create `orchestration/WorkflowEngine.java`; Test
  `orchestration/WorkflowEngineExecutionTest.java`.
- **Prerequisites**: T020, T021 (consumes `DependencyGraph`, `StageDefinition`, `WorkflowContext`,
  entities/repos).
- **Parallel**: false.
- **Expected artifact**: `WorkflowEngine.submit(feature, stages, correlationId) -> workflowId`, a
  plain constructor-injected class per ADR-004 (not a Spring singleton), so it is testable without
  booting the full context.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineExecutionTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-011/012/022/023 rows gain `T022`; ADR-008 row gains `T022`; this is
  the sequential-path and parallel-path/synchronization evidence source (see Evidence table).
- **Risk/guardrail**: **flaky-timing-test risk, explicitly flagged in the runbook** —
  `submitRunsParallelGroupConcurrently`'s ordering assertion (`fast` finishes before `slow`) is
  inherently timing-based (`Thread.sleep`-driven). If it proves flaky in CI-equivalent runs,
  replace it with a `CountDownLatch`-based deterministic synchronization check instead of relying
  on sleep-based ordering — record this replacement at the task-group checkpoint if done. Also:
  `runFrom` deliberately builds a fresh, unpersisted `WorkflowContext` on every call at this stage
  — this is a known, intentional gap that T024 closes; do not "fix" it here.
- **Completion criteria**: test passes; `git commit -m "feat(orchestration): implement sequential
  and parallel stage execution"`.
- **Human approval**: false.

### T023: Human approval gate — `WAITING_APPROVAL` state + resume

- **Objective**: Implement both `entryApproval` (blocks the handler itself) and `exitApproval`
  (blocks only dependents) gate semantics, plus `approve()`'s resume path.
- **REQ/SCN/ADR**: REQ-013 (approvals and rejection); SCN-A, SCN-B, SCN-C; ADR-006 (human approval
  mechanism).
- **Files**: Modify `orchestration/WorkflowEngine.java` (add `approve()`, gate checks; constructor
  gains `ApprovalJpaRepository`); Create `orchestration/persistence/ApprovalEntity.java`,
  `ApprovalJpaRepository.java`; Test `orchestration/WorkflowEngineApprovalTest.java`; Modify
  `WorkflowEngineExecutionTest.java` (update its `engine()` helper for the new constructor arg).
- **Prerequisites**: T022.
- **Parallel**: false.
- **Expected artifact**: `WorkflowEngine.approve(workflowId, stepInstanceId, decision, actor,
  reason)` — **corrected by the Task 10 corrective batch** (`data-model.md` Section 6 post-Task-10
  note / `codex-recommend.md` Finding #6): `ApprovalEntity` must carry `planVersion` and a
  `stepInstanceId` foreign-key-shaped reference to the specific `WorkflowStepEntity` row it
  governs, not only a bare `stepName` string, so a decision made under one plan version cannot be
  read as governing a same-named gate reintroduced by a later replan (ADR-006's own "blocks a
  branch for that plan version" rule). `stepName` is retained on the entity for human-legible
  display only; `stepInstanceId` is the authoritative reference the engine checks. `"rejected"`
  always sets the workflow `FAILED` (and the step `FAILED` if entry-gated, or leaves it
  `SUCCEEDED` if exit-gated, since the handler already ran).
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineApprovalTest,WorkflowEngineExecutionTest`
  (both — Task 22's suite is an explicit regression target here).
- **Doc impact**: none yet (feeds T045's SECURITY.md via T026's exposure of this mechanism over
  HTTP).
- **Traceability update**: REQ-013 row gains `T023`; ADR-006 row gains `T023`; this is the
  approval-evidence and resume-evidence source.
- **Risk/guardrail**: a single boolean cannot express both gate types — do not collapse
  `entryApproval`/`exitApproval` back into one flag; the guide requires both entry AND exit gates
  to exist distinctly.
- **Completion criteria**: both new and regressed tests pass; `git commit -m "feat(governance):
  enforce human approval transitions"`.
- **Human approval**: false — this task *builds* the human-approval mechanism; it does not itself
  exercise a human-owned decision class requiring a checkpoint before T024 starts (see T026 and
  T029 for where that flag applies instead).

### T024: Dependency-safe context propagation across resume boundary

- **Objective**: Persist and reload `WorkflowContext` (decisions/artifacts) via the workflow
  instance's `contextJson` column so decision lineage survives an approval-gate halt/resume cycle.
- **REQ/SCN/ADR**: REQ-017, REQ-028 (context/decision lineage, recoverability); SCN-A, SCN-C;
  ADR-004.
- **Files**: Modify `orchestration/WorkflowEngine.java` (reload persisted context in `runFrom`
  instead of constructing empty), `orchestration/persistence/WorkflowInstanceEntity.java` (add
  `contextJson`); Test `orchestration/WorkflowEngineContextLineageTest.java`.
- **Prerequisites**: T023 (this task closes a gap T023's `runFrom` deliberately left open).
- **Parallel**: false.
- **Expected artifact**: `instance.getContextJson()` round-trips `decisions`/`artifacts` across an
  `approve()`-triggered resume.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineContextLineageTest`
- **Doc impact**: extends `data-model.md`'s `workflow_instances` schema with `context_json`.
- **Traceability update**: REQ-017/028 rows gain `T024` (this is the concrete test the plan.md
  traceability table already cited for these two requirements).
- **Risk/guardrail**: none new — this task is itself the fix for a risk T022 flagged.
- **Completion criteria**: test passes, plus T022/T023's suites regression-pass; `git commit -m
  "fix(orchestration): persist decision lineage across approval resume boundary"`.
- **Human approval**: false.

### T025: `ScenarioRunner` CLI harness (thin, reusable by all 3 scenarios)

- **Objective**: A `CommandLineRunner` that runs a named scenario and prints its audit trail as
  JSON lines, testable without a subprocess or full server.
- **REQ/SCN/ADR**: REQ-011, REQ-012 (demonstration harness for workflow creation/inspection); no
  new ADR; supports SCN-A/B/C generically.
- **Files**: Create `scenarios/ScenarioRunner.java`; Test `scenarios/ScenarioRunnerSmokeTest.java`.
- **Prerequisites**: T023 (its constructor takes `ApprovalJpaRepository`, the 4-arg
  `WorkflowEngine` constructor T023 introduced).
- **Parallel**: `true` with T026 — both are leaf consumers of T023's engine, touching disjoint
  packages (`scenarios` vs `observability`) with no shared file or interface.
- **Expected artifact**: `runScenario(String scenario, PrintStream out)` — a no-op unless invoked
  with `--scenario=greenfield|brownfield|ambiguous`, so normal server boot is unaffected.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=ScenarioRunnerSmokeTest`
- **Doc impact**: none yet.
- **Traceability update**: none new (infrastructure for T039–T041).
- **Risk/guardrail**: this task wires a trivial 2-stage smoke graph only; real stage wiring is
  Phase 9's job (T039–T041) — do not treat this task's placeholder graph as scenario content.
- **Completion criteria**: test passes; `git commit -m "feat(tooling): add scenario runner CLI
  harness"`.
- **Human approval**: false.

### T026: `GET /workflows/{id}` and `GET /workflows/{id}/audit` API

- **Objective**: Expose workflow state, audit trail, and the approval decision itself over HTTP.
- **REQ/SCN/ADR**: REQ-012, REQ-013, REQ-019, REQ-026 (inspection, approvals, audit); SCN-A,
  SCN-B, SCN-C; ADR-006, ADR-010 (see Human approval below).
- **Files**: Create `observability/WorkflowController.java`,
  `observability/dto/{WorkflowStateResponse,AuditEventResponse,ApprovalRequest}.java`; Test
  `api/WorkflowControllerContractTest.java`.
- **Prerequisites**: T023 (uses the 4-arg `WorkflowEngine` constructor and `approve()`).
- **Parallel**: `true` with T025 (see T025's entry).
- **Expected artifact**: `GET /workflows/{id}` → state+steps, **including, per the Task 10
  corrective batch's schema correction (`contracts/openapi.yaml` `WorkflowStateResponse`,
  `codex-recommend.md` Finding #5): the workflow's `correlationId` and `planVersion` at the top
  level, `decisionLineage` (from `context_json.decisions[]`), and each step's `inputs`/`outputs`
  (from `workflow_steps.depends_on`/context artifacts and `workflow_steps.result`
  respectively)** — all read from persisted state, not reconstructed narratively; `GET
  /workflows/{id}/audit` → event list, **including, per the same corrective batch
  (`AuditEventResponse`, `codex-recommend.md` Finding #10), each event's `workflowId`, a concrete
  `actorId` distinct from `actorType`, `affectedArtifact`, and structured `payload`**; `POST
  /workflows/{id}/approvals/{step}` → invokes `WorkflowEngine.approve`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowControllerContractTest`
- **Doc impact**: matches `openapi.yaml`'s workflow-inspection operations (Task 6, corrected by
  Task 10 per the fields above); feeds `SECURITY.md` (T045)'s disclosure of this endpoint's
  unauthenticated status.
- **Traceability update**: REQ-012/013/019/025/026 rows gain `T026`.
- **Risk/guardrail**: `POST /workflows/{id}/approvals/{step}` is, per ADR-006's own Consequences
  section and ADR-010's Context, **the single highest-severity unauthenticated endpoint in the
  system** — "the only path that releases a blocked branch," with `actor`/`decidedBy` self-
  asserted and unverified against any identity system. This is a disclosed, human-accepted residual
  risk (ADR-010, accepted 2026-09-13), not a defect to silently fix here.
- **Completion criteria**: test passes; `git commit -m "feat(api): expose workflow state and audit
  inspection endpoints"`.
- **Human approval**: **true** — this task is what actually exposes ADR-006/ADR-010's named
  highest-risk endpoint to the network. The human candidate accepted the *design* at Task 7; this
  is the point the design becomes a live, callable HTTP surface, which constitution Principle III
  ("security-sensitive decisions" are human-owned) and Principle V ("undocumented trade-offs are
  indistinguishable from oversights") both bear on directly. Flagging ensures the exposure is
  visible before scenario tasks (T039–T041) start calling it in anger.

### T026a: `POST /workflows` — generic Workflow Run creation over HTTP (contract test first)

**Added by the Task 10 corrective batch** (`pre_implementation_analysis.md` Section 7 HIGH-1 /
`codex-recommend.md` Findings #4, #9): `plan.md` Section 2.1 flagged `POST /workflows` as a
plan-level design addition with its own implementation obligation, `contracts/openapi.yaml`
already defines it (`operationId: createWorkflow`), but no task in the original transcription
implemented or contract-tested it, and REQ-011's "the system MUST allow a new Workflow Run to be
created... via a new requirement input" is not satisfied by `ScenarioRunner`'s CLI-only creation
path alone. This task closes that gap. Per Fix 2's contract correction, **there is no `501`
stub path** — this task must deliver full generic graph instantiation, not a documented
not-implemented response.

- **Objective**: Expose generic Workflow Run creation over HTTP: given a `feature`/
  `requirementText` pair, instantiate and persist the baseline 12-node stage graph (`plan.md`
  Section 3.2) and return its assigned `id`/`status`/`correlationId`/`planVersion`.
- **REQ/SCN/ADR**: REQ-011 (workflow creation as an externally reachable capability); SCN-A,
  SCN-B, SCN-C (all three scenarios' workflows must be creatable through this same generic path,
  not only through `ScenarioRunner`); ADR-004 (orchestration model).
- **Files**: Modify `observability/WorkflowController.java` (add `POST /workflows`); Create
  `observability/dto/{CreateWorkflowRequest,WorkflowCreatedResponse}.java`; Test
  `api/WorkflowControllerContractTest.java` (extend).
- **Prerequisites**: T026 (same controller and test file `WorkflowController`/
  `WorkflowControllerContractTest` T026 created); T020/T021 (`DependencyGraph`,
  `StageDefinition`, the baseline stage graph shape) for the graph this endpoint instantiates.
- **Parallel**: false — same file, same test class as T026; strictly sequential.
- **Expected artifact**: `POST /workflows` with a `requirementText`-bearing body → `201
  {id, status, correlationId, planVersion}` with a persisted, inspectable 12-node graph
  (verifiable immediately afterward via `GET /workflows/{id}`, T026); `422` for unreadable/absent
  requirement input (REQ-016 safe-stop at Stage 1). No `501` response exists for this operation.
- **TDD**: yes — including a positive contract test asserting the created workflow's persisted
  step count/shape matches the baseline graph, not merely that `201` was returned.
- **Validation command**: `./mvnw test -Dtest=WorkflowControllerContractTest`
- **Doc impact**: matches `contracts/openapi.yaml`'s `POST /workflows` operation (Task 6,
  corrected by Task 10 — `requirementText` required, no `501` path).
- **Traceability update**: REQ-011 row gains `T026a`; closes the API/schema deliverable group's
  `POST /workflows` gap flagged by Task 10's corrective batch; this is now REQ-011's
  externally-reachable-creation evidence source (previously only `ScenarioRunner`, T025, covered
  creation, and only via CLI).
- **Risk/guardrail**: do not special-case this endpoint's instantiation logic separately from
  `ScenarioRunner`'s (T025) — both must build the same baseline graph from the same
  `DependencyGraph`/`StageDefinition` machinery, or the two creation paths will silently drift.
- **Completion criteria**: test passes; `git commit -m "feat(api): implement generic workflow
  creation over HTTP"`.
- **Human approval**: false — this task implements the mechanism ADR-006/ADR-010's
  human-accepted design already covers; it does not itself expose a new class of risk beyond what
  T026's human-approval checkpoint already covered for this controller.

---

## Phase 5 — Policy / Compliance Engine

### T027: Versioned policy file + `PolicyEngine.evaluate()`

- **Objective**: Load a versioned YAML policy set and evaluate per-stage checks to one of
  `PASS`/`FAIL`/`EXCEPTION_REQUESTED`/`NOT_APPLICABLE`.
- **REQ/SCN/ADR**: REQ-020 (release-readiness outcome), REQ-030 (change safety — versioned
  policy is itself the change-control substrate); no direct SCN (cross-cutting); ADR-007 (policy
  engine format).
- **Files**: Create `policy/{PolicyOutcome,PolicyCheckResult,PolicyDefinition,PolicyEngine}.java`,
  `src/main/resources/policies/v1.yaml`; Test `policy/PolicyEngineTest.java`.
- **Prerequisites**: none — `PolicyEngine` has no dependency on any orchestration class; it is a
  standalone YAML-loading evaluator.
- **Parallel**: `true` relative to the entire T020–T026 orchestration block — genuinely
  independent files/interfaces at this point (the two are only wired together in T028).
- **Expected artifact**: `PolicyEngine("policies/v1.yaml").version() == "1.0.0"`;
  `evaluate(stageName, context)` returns per-policy outcomes.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=PolicyEngineTest`
- **Doc impact**: `v1.yaml` is itself a versioned governance artifact — any future edit is a
  policy-version bump, tracked per constitution Principle VI.
- **Traceability update**: REQ-020/030 rows gain `T027`; ADR-007 row gains `T027`; this is the
  policy-version-evidence source (`PolicyEngine.version()`).
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(compliance): add versioned policy
  model and evaluation engine"`.
- **Human approval**: false.

### T028: Policy enforcement inside the orchestration engine (blocks on mandatory FAIL)

- **Objective**: Evaluate policy inside `runStage`, persist every `PolicyCheckResult`, and
  transition the workflow to `SAFE_STOPPED` (not `FAILED`) on an unwaived mandatory `FAIL`.
- **REQ/SCN/ADR**: REQ-020, REQ-030; SCN-A, SCN-B; ADR-007.
- **Files**: Modify `orchestration/WorkflowEngine.java` (overloaded constructor accepting an
  optional `PolicyEngine`); Create `orchestration/SafeStopException.java`,
  `orchestration/persistence/{PolicyEvaluationEntity,PolicyEvaluationJpaRepository}.java`; Test
  `orchestration/WorkflowEnginePolicyEnforcementTest.java`.
- **Prerequisites**: T024 (modifies the post-T024 `runStage`/`runFrom`), T027 (needs
  `PolicyEngine`).
- **Parallel**: false.
- **Expected artifact**: `policy_evaluations` table populated every stage run; mandatory `FAIL`
  with no approved exception → `WorkflowStatus.SAFE_STOPPED`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowEnginePolicyEnforcementTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-020/030 rows gain `T028`; this is the compliance-evaluation-
  evidence and failed-policy-blocking-evidence source.
- **Risk/guardrail**: `hasApprovedUnexpiredException(...)` is a deliberate stub that always
  returns `false` at this task — T029 replaces it with the real check. Do not implement exception
  logic early here; the test suite depends on this exact staging.
- **Completion criteria**: test passes, T022–T024's suites regress clean (their 4-arg constructor
  now delegates to this task's new overload with `policyEngine=null`); `git commit -m
  "feat(compliance): enforce mandatory policy checks with safe-stop on failure"`.
- **Human approval**: false.

### T029: Policy exception workflow (human-approved override)

- **Objective**: Allow a human-approved, expiring, scoped exception to a mandatory policy `FAIL`
  to unblock a safe-stopped workflow.
- **REQ/SCN/ADR**: REQ-030 (change safety); no SCN specific; ADR-007. Constitution Principle III
  ("any material exception to a policy or principle... remains human-owned") and Principle VI
  (exception must capture policy, reason, scope, approving authority, compensating control,
  timestamp, and expiry) are both directly implemented by this task's entity shape.
- **Files**: Create `orchestration/persistence/{PolicyExceptionEntity,PolicyExceptionJpaRepository}.java`;
  Modify `orchestration/WorkflowEngine.java` (add `grantPolicyException(...)`,
  `retrySafeStopped(...)`, replace T028's stub); Test `policy/PolicyExceptionTest.java`.
- **Prerequisites**: T028 (replaces its stub; needs `PolicyEvaluationJpaRepository` and the
  policy-aware `WorkflowEngine`).
- **Parallel**: false.
- **Expected artifact**: `grantPolicyException(workflowId, policyId, reason, scope,
  approvingAuthority, compensatingControl, expiry)`; `retrySafeStopped(workflowId)` resumes past
  the now-excepted mandatory FAIL.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=PolicyExceptionTest`
- **Doc impact**: feeds T047's final summary "Policy exceptions" section (mandatory per the
  guide's 21-section structure).
- **Traceability update**: REQ-030 row gains `T029`; this is the approved-exception-evidence
  source (`PolicyExceptionEntity` + `PolicyExceptionTest`).
- **Risk/guardrail**: the exception check lives in exactly one place
  (`hasApprovedUnexpiredException`), reused by both real-time gating and `retrySafeStopped` — do
  not duplicate policy-recomputation logic in the retry path.
- **Completion criteria**: test passes; `git commit -m "feat(compliance): support human-approved
  policy exceptions with expiry"`.
- **Human approval**: **true** — this is the literal implementation of constitution Principle
  III's "material exception to a policy" human-owned decision class. The mechanism itself must be
  visible to and understood by the human candidate before any scenario task (T039–T041, and
  especially T040's brownfield SSRF fix, which could in principle be "fixed" by granting an
  exception instead of actually fixing the code) starts using it, to guard against the mechanism
  being used to paper over an unresolved mandatory FAIL rather than to record a genuinely accepted
  trade-off.

---

## Phase 6 — Reliability

### T030: Bounded retry with classification (transient vs permanent) + backoff

- **Objective**: Wrap a `StageHandler` to retry `TransientStageException`s up to a bound with
  exponential backoff, never retrying `PermanentStageException`s.
- **REQ/SCN/ADR**: REQ-014 (retries and timeouts), REQ-022 (reliability); SCN-A, SCN-B; no
  dedicated ADR (retry/timeout are generic reliability mechanisms `plan.md` Section 6 specifies
  directly, not a distinct ADR-level architectural choice).
- **Files**: Create `reliability/{TransientStageException,PermanentStageException,RetryExecutor}.java`;
  Test `reliability/RetryTest.java`.
- **Prerequisites**: T020 (uses the `StageHandler` type).
- **Parallel**: `true` — mutually parallel-safe with T031 and T032; fully disjoint files, no
  shared interface (each of the three reliability primitives is independently composable).
- **Expected artifact**: `RetryExecutor.withBoundedRetry(handler, maxRetries, backoffMillis)`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=RetryTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-014/022 rows gain `T030`; retry-evidence source.
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(resilience): add bounded retry with
  transient/permanent classification"`.
- **Human approval**: false.

### T031: Timeout wrapper

- **Objective**: Bound a stage handler's execution time, raising `StageTimeoutException` past a
  millisecond threshold (milliseconds, not seconds, so unit tests stay fast).
- **REQ/SCN/ADR**: REQ-014; SCN-A, SCN-B; no dedicated ADR.
- **Files**: Create `reliability/{StageTimeoutException,TimeoutExecutor}.java`; Test
  `reliability/TimeoutTest.java`.
- **Prerequisites**: T020 (uses `StageHandler`).
- **Parallel**: `true` — mutually parallel-safe with T030 and T032.
- **Expected artifact**: `TimeoutExecutor.withTimeout(handler, timeoutMillis)`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=TimeoutTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-014 row gains `T031`.
- **Risk/guardrail**: `WorkflowEngine` (T033) converts `StageDefinition.timeoutSeconds()` to
  millis at the call site — this class itself takes millis, deliberately, for fast unit tests.
- **Completion criteria**: test passes; `git commit -m "feat(resilience): add stage timeout
  wrapper"`.
- **Human approval**: false.

### T032: Compensation registry (rollback vs. compensation, per ADR-009)

- **Objective**: A named-stage compensation-function registry, distinct from rollback, per
  ADR-009's decision to implement compensation only.
- **REQ/SCN/ADR**: REQ-015 (rollback or compensation); SCN-B; ADR-009 (rollback vs. compensation
  mechanism).
- **Files**: Create `reliability/CompensationRegistry.java`; Test
  `reliability/CompensationRegistryTest.java`.
- **Prerequisites**: none — no dependency on `StageHandler` or any orchestration type.
- **Parallel**: `true` — mutually parallel-safe with T030 and T031; this one has no prerequisite
  at all.
- **Expected artifact**: `CompensationRegistry.register(stageName, compensateFn)`,
  `run(stageName, stageResult) -> boolean`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=CompensationRegistryTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-015 row gains `T032`; ADR-009 row gains `T032`; this is the
  compensating-control-evidence source. **Disclosed limitation**: no `ROLLED_BACK`-producing
  mechanism exists anywhere in T011–T048 — `StepStatus.ROLLED_BACK` is a defined enum value with no
  producer, consistent with ADR-009's decision to implement compensation only; carry this into
  T047's limitations section, not silently.
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(recovery): implement compensation
  registry distinct from rollback"`.
- **Human approval**: false.

### T033: Wire retry/timeout/compensation into `WorkflowEngine.runStage`

- **Objective**: Replace `runStage` with a retry-then-compensate-or-safe-stop pipeline, timeout-
  wrapped per attempt.
- **REQ/SCN/ADR**: REQ-014, REQ-015, REQ-022; SCN-A, SCN-B; ADR-009.
- **Files**: Modify `orchestration/WorkflowEngine.java` (new `CompensationRegistry` field + one
  more constructor overload; full `runStage` rewrite).
- **Prerequisites**: T029 (extends the latest `WorkflowEngine` state), T030, T031, T032 (all three
  reliability primitives).
- **Parallel**: false — this is the fan-in task that requires all three parallel-safe reliability
  primitives (T030/T031/T032) plus the full policy-aware engine (T029) to exist first.
- **Expected artifact**: exhausted, uncompensated retries → `SafeStopException` →
  `SAFE_STOPPED`; exhausted, compensated retries → `StepStatus.COMPENSATED`, workflow continues.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineReliabilityIntegrationTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-014/015/022 rows gain `T033`; retry-evidence and
  compensation-evidence sources are now exercised end-to-end through the real engine (not just the
  standalone T030/T032 unit tests).
- **Risk/guardrail**: **explicit constructor-growth backlog item, per the runbook's own note**:
  this is the fourth `WorkflowEngine` constructor overload added across T022/T023, T028, T029, T033
  (4 → 6 → 7 → 8 positional parameters) — a real code smell. Do not retrofit a builder now (it
  would touch five already-green test files for a cosmetic win); instead, carry "replace
  `WorkflowEngine`'s constructor overloads with a builder" into T047's final engineering summary as
  a named backlog item.
- **Completion criteria**: test passes, T022–T029's suites regress clean; `git commit -m
  "feat(resilience): wire retry, timeout, and compensation into stage execution"`.
- **Human approval**: false.

### T034: Safe-stop conditions consolidated

- **Objective**: Prove, with a dedicated test, that both safe-stop triggers (mandatory policy FAIL
  from T028, uncompensated retry exhaustion from T033) converge on the same `SAFE_STOPPED`
  terminal state via the same `SafeStopException` path.
- **REQ/SCN/ADR**: REQ-016 (safe-stop); SCN-A, SCN-B, SCN-C; no new ADR (constitution Principle
  VIII: safe-stop must be a single, well-defined terminal path).
- **Files**: no production code expected; Test `orchestration/WorkflowEngineSafeStopTest.java`.
- **Prerequisites**: T033 (exercises the retry-exhaustion trigger T033 introduced), T028
  (exercises the policy-FAIL trigger).
- **Parallel**: false.
- **Expected artifact**: a passing test proving the uncompensated-exhaustion case lands on
  `SAFE_STOPPED`, not `FAILED`.
- **TDD**: yes (verification-oriented — if it fails, the fix belongs in T028 or T033, not here).
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineSafeStopTest`
- **Doc impact**: none.
- **Traceability update**: REQ-016 row gains `T034`; this is the safe-stop-evidence source
  (alongside T028/T033).
- **Risk/guardrail**: if this test fails, the correct fix is at the upstream task (T028 or T033)
  whose `SafeStopException` throw site was bypassed — never patch this test to accept `FAILED`
  instead.
- **Completion criteria**: test passes (T033's own test only ever covers the *compensated* case,
  so this is genuinely new coverage, not a duplicate); `git commit -m "test: verify uncompensated
  failures converge on safe-stop terminal state"`.
- **Human approval**: false.

### T034a: Fallback subsystem, distinct from retry/compensation/safe-stop *(added post-Task-10,
Codex round 3: "a mere placeholder is insufficient... add explicit fallback start/success/failure
audit events, a distinct SUCCEEDED_WITH_FALLBACK outcome, timeout/failure behavior for the fallback
itself, release-readiness disclosure, safe-stop if both primary and fallback fail")*

- **Objective**: Build the fallback mechanism the constitution's Principle VIII defines and that,
  before this task, existed nowhere in the codebase — a distinct, declared-in-advance, lower-risk
  alternate path attempted after retry exhaustion and before compensation/safe-stop, never
  presented as equivalent to primary-path success without disclosure.
- **REQ/SCN/ADR**: constitution Principle VIII (primary normative source — not REQ-014, whose own
  text is about retries/timeouts; REQ-014 only carries the spec's characteristics-table
  cross-reference); REQ-016 (safe-stop, category (b) now nests this); SCN-A (documentation fallback
  example). No new ADR — extends ADR-004/ADR-009's existing reliability model.
- **Files**: Modify `orchestration/StepStatus.java` (add `SUCCEEDED_WITH_FALLBACK`); Modify
  `orchestration/WorkflowEngine.java` (`runStage` looks up the fallback via
  `catalog.fallbackFor(stage.fallbackHandlerKey())` — T020b's `StageCatalog`, not an in-memory
  `fallbackHandlers` map/`registerFallback(...)` on the live engine instance, so a fallback
  registration survives a process restart exactly like every other stage configuration); Create
  `orchestration/stages/DocumentationFallbackStage.java`; Test `orchestration/FallbackTest.java`.
- **Prerequisites**: T020b (`StageCatalog` — this task's fallback lookup depends on it), T030, T031,
  T032, T033 (the retry/timeout/compensation stack this task's new path sits inside), T034 (the
  safe-stop path this task's failure case falls through to).
- **Parallel**: false.
- **Expected artifact**: a stage whose primary handler exhausts retries, with a catalog-registered
  fallback that succeeds, reaches `SUCCEEDED_WITH_FALLBACK` (not plain `SUCCEEDED`) with
  `fallback_started`/`fallback_succeeded` audit events (both with a non-null reason); the dependent
  stage runs, and the workflow reaches `COMPLETED`, not blocked. If the fallback also fails, the
  stage reaches `SAFE_STOPPED` (after compensation is attempted and unavailable) with
  `fallback_started`/`fallback_failed` events, exactly as if no fallback had been registered (no
  third recovery path invented).
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=FallbackTest`
- **Doc impact**: `spec.md` CLR-13 updated (terminology correction: the mechanism CLR-13 always
  meant to describe now actually exists; safe-stop's category (b) restated as nesting fallback then
  compensation, not a new category). This task does not itself modify `ReleaseReadinessStage` — that
  file does not exist yet (T039 creates it). Record here only that T039's `ReleaseReadinessStage`
  must scan `ctx.artifacts().get("fallbacksUsed")` (written by this task's `runStage` change) and
  disclose any reduced guarantee in its result map; the actual class body is written in T039, not
  here.
- **Traceability update**: Principle VIII, REQ-016 rows gain `T034a`.
- **Risk/guardrail**: `DocumentationFallbackStage`'s output must satisfy a deterministic minimum
  (the design's real API surface, explicitly labeled `degraded: true` and naming what's omitted) —
  a placeholder with no defined minimum content does not satisfy this task; see the runbook's
  Phase 12, Task 50 for the exact minimum-criteria specification. `WorkflowContext`'s internal
  `decisions`/`artifacts` collections must be thread-safe (`CopyOnWriteArrayList`/
  `ConcurrentHashMap`) before this task's parallel-batch fallback disclosure write is safe — fixed
  in the runbook's Task 50, carried here as a dependency, not re-specified.
- **Completion criteria**: test passes; `spec.md` CLR-13 updated; `git commit -m
  "feat(reliability): implement fallback distinct from retry/compensation/safe-stop"`.
- **Human approval**: false.

---

## Phase 7 — Dynamic Replanning

### T035: Replan API — invalidate downstream stages, version the plan, resume

- **Objective**: Invalidate all stages reachable from a given stage, insert replacement stages,
  bump `planVersion`, and resume — while preserving lineage accumulated before the replan point.
- **REQ/SCN/ADR**: REQ-018 (dynamic replanning), REQ-030 (change safety — replanning is itself a
  versioned, audited change); SCN-A, SCN-C; ADR-004.
- **Files**: Modify `orchestration/WorkflowEngine.java` (add `replan(...)`, a `recordEvent`
  overload accepting a payload, and a required companion fix to `runFrom`'s batch-blocking check);
  Test `orchestration/WorkflowEngineReplanningTest.java`.
- **Prerequisites**: T034 (the full engine state through Phase 6 must exist; `replan` specifically
  depends on T024's context persistence and T023's exit-gate mechanics).
- **Parallel**: false.
- **Expected artifact**: `replan(workflowId, fromStage, newStages, actor, reason)` marks reachable
  steps `INVALIDATED`, inserts new `PENDING` steps **stamped with the newly-incremented
  `plan_version`** (corrected by the Task 10 corrective batch, `data-model.md` Section 4/6
  post-Task-10 note / `codex-recommend.md` Finding #6 — every step row this method inserts must
  carry the `plan_version` it was created under, and any new `ApprovalEntity` rows this replan's
  downstream gates create must carry both `planVersion` and the new step's `stepInstanceId`, never
  only its `stepName`), increments `instance.planVersion`, records a `"replanned"` event with
  `{fromVersion, toVersion, fromStage}`.
- **TDD**: yes — include a test asserting an approval decided under `planVersion=1` cannot be read
  as governing a same-named gate re-created at `planVersion=2` by this method.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineReplanningTest`
- **Doc impact**: none yet (feeds `docs/scenarios/ambiguous.md` in T041).
- **Traceability update**: REQ-018/030 rows gain `T035`; this is the replanning-evidence and
  change-request/impact-analysis-evidence source (the `replanned` event payload).
- **Risk/guardrail**: **governance-critical companion fix, explicitly called out in the runbook**:
  without an idempotent, per-batch re-halt check for stages still sitting at `WAITING_APPROVAL`
  from an *earlier, unresolved* gate, a `replan()`-triggered `runFrom` call could let downstream
  work slip past an ancestor's still-open approval gate — described in the runbook as "a
  governance hole." This must be present, not just the headline `replan()` logic itself.
- **Completion criteria**: test passes, T022–T034's suites regress clean; `git commit -m
  "feat(replanning): support governed downstream replanning with lineage preservation"`.
- **Human approval**: false — the mechanism enables a human-directed replan; granting it does not
  itself require a checkpoint (the calling human decision is what the mechanism records, not what
  building the mechanism requires).

---

## Phase 8 — Observability

### T036: Audit recorder consolidation + `GET /workflows/{id}/audit` completeness

- **Objective**: Consolidate the ad-hoc `recordEvent` bodies scattered across `WorkflowEngine`
  into one `AuditRecorder` class so every audit call site is consistent.
- **REQ/SCN/ADR**: REQ-019, REQ-026 (audit inspection/auditability); SCN-A, SCN-B, SCN-C; no new
  ADR.
- **Files**: Create `observability/AuditRecorder.java`; Modify
  `orchestration/WorkflowEngine.java` (delegate to it); Test
  `observability/AuditRecorderTest.java`.
- **Prerequisites**: T035 (refactors the `recordEvent` overload T035 introduced).
- **Parallel**: `true` with T037 — `AuditRecorder`/the `WorkflowEngine` edit touch no file T037
  touches (T037 is entirely new `observability` files with no dependency on `WorkflowEngine`).
- **Expected artifact**: `AuditRecorder.record(workflowId, actorType, action, result, reason,
  payload)`, the single call site every `recordEvent` overload now delegates to.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=AuditRecorderTest`
- **Doc impact**: none.
- **Traceability update**: REQ-019/026 rows gain `T036`.
- **Risk/guardrail**: purely a refactor — full orchestration regression suite must stay green;
  no behavior change is intended.
- **Completion criteria**: test passes, orchestration suite regresses clean; `git commit -m
  "feat(observability): consolidate audit event recording"`.
- **Human approval**: false.

### T036a: Stage-boundary violation test (REQ-031's own named negative evidence)

**Added by the Task 10 corrective batch** (`pre_implementation_analysis.md` Section 7 HIGH-2 /
`codex-recommend.md` Finding #8): REQ-031's own Evidence line names "a stage-boundary violation
test in which an out-of-scope agent action is rejected," restated identically in `plan.md`'s
traceability table (Section 13) and required as `[MANDATORY]` by `orchestration.md` ORCH-CHK-015
and `autonomy.md` AUTO-CHK-006 — but no task in the original transcription created this test; T039
exercises the happy-path stage-attribution property, not this negative case. This task closes
that gap, sequenced before T046 (convergence) so REQ-031 has real negative evidence before the
release-readiness gate runs.

- **Objective**: Prove that a stage handler given a stage-scoped capability cannot perform an
  action outside that scope, that the attempt is rejected **before** any side effect commits, and
  that the rejected attempt is itself recorded in the audit trail — not silently dropped.
- **REQ/SCN/ADR**: REQ-031 (controlled autonomy — negative criterion); no dedicated SCN (this is
  a cross-cutting orchestration-engine property, not a scenario narrative); ADR-004 (orchestration
  model, since stage-scoping is a property of `StageHandler`/`WorkflowEngine`'s execution
  boundary).
- **Files**: Test `orchestration/WorkflowEngineStageBoundaryTest.java`. No new production class is
  expected by default; if the existing `StageHandler`/`WorkflowEngine` boundary does not already
  reject an out-of-scope write, the minimal enforcement needed to make this test pass (e.g.
  scoping a stage handler's persistence access so it cannot address another stage's
  `workflow_steps` row directly) belongs in `orchestration/WorkflowEngine.java` or a new
  `orchestration/StageScopeViolationException.java`, added here as the fix this test drives, per
  TDD (constitution Principle IV) — not deferred to a later task.
- **Prerequisites**: T022 (stage execution), T023 (approval/gate mechanics needed to construct a
  realistic multi-stage graph), T024 (context propagation, so the test can assert no context
  corruption from a rejected attempt), T036 (`AuditRecorder`, so the rejected attempt's audit
  event can be asserted directly against the consolidated recorder rather than an ad-hoc
  `recordEvent` call site).
- **Parallel**: `true` with T037 — disjoint files, no shared interface.
- **Expected artifact**: a test that (1) constructs a stage handler holding a capability scoped to
  its own stage's `workflow_steps` row, (2) has that handler attempt to write a **different**
  stage's `workflow_steps` row directly (an out-of-scope action), (3) asserts the write is
  rejected and no persisted state changed as a result (the attempted row is unchanged — rejection
  happens before any side effect commits), and (4) asserts a `workflow_events` row exists
  recording the rejected attempt (`actorType`, the offending `action`, a non-null `reason`,
  `result` indicating rejection) via `AuditRecorder` (T036).
- **TDD**: yes — write the test first; if the current `WorkflowEngine` design already prevents
  cross-stage writes structurally (e.g. because stage handlers never receive a repository
  reference broad enough to address another stage's row), the test should still exist to prove
  that boundary explicitly and pin it against regression, per REQ-031's own evidence requirement.
- **Validation command**: `./mvnw test -Dtest=WorkflowEngineStageBoundaryTest`
- **Doc impact**: feeds T043's traceability matrix (REQ-031's Evidence column) and T047's final
  summary; no `openapi.yaml`/`data-model.md` change.
- **Traceability update**: REQ-031 row gains `T036a` as its stage-boundary-violation evidence
  source, closing the gap `pre_implementation_analysis.md` Section 7 HIGH-2 and
  `codex-recommend.md` Finding #8 both named.
- **Risk/guardrail**: if this test reveals that no scoping mechanism currently exists to reject an
  out-of-scope write (i.e., any stage handler can already address any `workflow_steps` row), the
  correct fix is a real, minimal scope-enforcement mechanism added in this task — not narrowing
  the test until it passes trivially against an unenforced design.
- **Completion criteria**: test passes; `git commit -m "test(orchestration): verify stage-boundary
  violations are rejected before commit and audited"`.
- **Human approval**: false.

### T037: MTTR and reliability metrics computation

- **Objective**: Compute MTTR (averaged only over recovered events, excluding unrecovered
  failures) and a reliability summary (success rate, retry frequency, compensation frequency,
  latency), explicitly labeled as demonstration data.
- **REQ/SCN/ADR**: REQ-022 (reliability), REQ-027 (performance); SCN-A, SCN-B; no new ADR. Also
  satisfies the source material's dedicated MTTR-instrumentation required group (see
  cross-reference table above).
- **Files**: Create `observability/{RecoveryEvent,MttrReport,ReliabilitySummary,MetricsService}.java`;
  Test `observability/MetricsMttrTest.java`.
- **Prerequisites**: T021 (uses `WorkflowInstanceJpaRepository`/`WorkflowStepJpaRepository` and
  `StepStatus`/`WorkflowStatus`); no dependency on T036.
- **Parallel**: `true` with T036 (disjoint files; see T036's entry).
- **Expected artifact**: `MetricsService.computeMttr(events)` averaging only recovered events;
  `computeReliabilitySummary(...)` tagging `isDemonstrationData=true`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=MetricsMttrTest`
- **Doc impact**: none yet.
- **Traceability update**: REQ-022/027 rows gain `T037`; measured-demonstration-metrics evidence
  source.
- **Risk/guardrail**: **fixed for real by T037a, not carried as backlog** — see T037a immediately
  below. (Originally disclosed here as a deferred scoping inconsistency; an external review
  correctly argued the fix was smaller than the comment explaining why it wasn't fixed.)
- **Completion criteria**: test passes; `git commit -m "feat(observability): implement MTTR and
  reliability metrics computation"`.
- **Human approval**: false.

### T037a: Fix `computeReliabilitySummary`'s workflow-scoping bug *(added post-Task-10, Codex round
2: "remove known, intentionally untested metric defects from the delivery plan")*

- **Objective**: Scope `steps` to the requested `workflowId` exactly as `instances` already is, so
  per-workflow retry/compensation frequency figures stop silently aggregating across every
  workflow in the database.
- **REQ/SCN/ADR**: REQ-022, REQ-027; no new ADR.
- **Files**: Modify `orchestration/persistence/WorkflowStepJpaRepository.java` (add
  `findByWorkflowId(String)` — a zero-code Spring Data derived query);
  Modify `observability/MetricsService.java`; Test
  `observability/MetricsServiceIsolationTest.java`.
- **Prerequisites**: T037.
- **Parallel**: false.
- **Expected artifact**: `computeReliabilitySummary(repo1, repo2, workflowIdB)` reflects only
  workflow B's steps even when workflow A (submitted earlier, in the same database) has unrelated
  retries/compensations.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=MetricsServiceIsolationTest`
- **Doc impact**: none.
- **Traceability update**: REQ-022/027 rows' `T037` note updated to reflect the fix landed, not
  deferred.
- **Risk/guardrail**: trivial, one-line fix — the risk was always in *not* fixing it, not in the
  fix itself.
- **Completion criteria**: test passes, proving a two-workflow isolation scenario; `git commit -m
  "fix(observability): scope reliability metrics to the requested workflow"`.
- **Human approval**: false.

### T038: `GET /metrics` endpoint + CLI report

- **Objective**: Expose the reliability summary over HTTP and via a `--metrics-report` CLI flag.
- **REQ/SCN/ADR**: REQ-022, REQ-025 (observability); SCN-A, SCN-B; no new ADR.
- **Files**: Create `observability/MetricsController.java`; Modify `scenarios/ScenarioRunner.java`
  (add `--metrics-report` path); Test `api/MetricsControllerContractTest.java`.
- **Prerequisites**: T037 (uses `MetricsService.computeReliabilitySummary`), T025 (modifies
  `ScenarioRunner`, whose constructor-injected fields it reuses).
- **Parallel**: false (depends directly on T037's freshly-created class).
- **Expected artifact**: `GET /metrics` → `ReliabilitySummary` JSON with `"isDemonstrationData":
  true`; `java -jar url-shortener.jar --metrics-report` prints the same JSON to stdout.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=MetricsControllerContractTest`
- **Doc impact**: matches `openapi.yaml`'s metrics operation if present; otherwise noted as an
  additive, backward-compatible endpoint in T043's compatibility review.
- **Traceability update**: REQ-022/025 rows gain `T038`; closes the MTTR-instrumentation required
  group's "demonstration-data labeling" line item.
- **Risk/guardrail**: none new.
- **Completion criteria**: test passes; `git commit -m "feat(observability): expose reliability
  metrics via API and CLI report"`.
- **Human approval**: false.

---

## Phase 9 — Three Scenarios (the assignment's mandatory demonstration)

### T039: Greenfield scenario — well-defined requirement proceeds without artificial clarification

**Authoritative final form (corrected 2026-09-13, fourth review round).** Written directly against
the real 12-node graph — `requirement_ingestion`, `requirement_normalization`,
`ambiguity_detection`, `human_clarification` (present in every run, `SKIPPED` here), `decomposition`,
`design`, `implementation`, `testing`, `security`, `documentation`, `release_readiness`,
`final_engineering_summary` — instead of an 8-stage placeholder meant to be split by a later task;
no code exists yet, so there is nothing gained by building the placeholder first.

- **Objective**: Wire the full 12-node graph proving a well-defined requirement proceeds without an
  unnecessary clarification gate, while recording *why* clarification was skipped, and disclosing
  via `ReleaseReadinessStage` if any stage completed via fallback.
- **REQ/SCN/ADR**: REQ-011, REQ-012, REQ-013, REQ-016, REQ-017, REQ-019, REQ-020, REQ-028, REQ-031
  (controlled autonomy — each stage is attributable and exit-criteria-checked); SCN-A; ADR-004,
  ADR-006. Satisfies the source material's greenfield required group in full (see cross-reference
  table above).
- **Files**: Create
  `orchestration/stages/{RequirementIngestionStage,RequirementNormalizationStage,AmbiguityDetectionStage,HumanClarificationStage,DecompositionStage,DesignStage,ImplementationStage,TestingStage,DocumentationStage,SecurityStage,ReleaseReadinessStage,FinalSummaryStage}.java`;
  Modify `orchestration/WorkflowEngine.java` (one line in `runFrom`: seed
  `ctx.artifacts().putIfAbsent("feature", instance.getFeature())`); Modify `scenarios/ScenarioRunner.java`
  (replace T025's placeholder graph with a `StageCatalog`-backed 12-stage graph); Test
  `scenarios/GreenfieldScenarioTest.java`; Create `docs/scenarios/greenfield.md`.
- **Prerequisites**: T020b (`StageCatalog`), T038 (latest `ScenarioRunner.java` state), T026
  (`approve()` exercised via HTTP-equivalent engine calls), T028 (`ImplementationStage` reports
  policy-relevant artifacts consumed by the policy engine), T034a (`SUCCEEDED_WITH_FALLBACK` and the
  `fallbacksUsed` artifact this task's `ReleaseReadinessStage` reads).
- **Parallel**: not parallel with T040/T041 — both reuse this task's stage classes and
  `StageCatalog` pattern directly (read-only reuse; no shared file writes, but T039 must land
  first).
- **Expected artifact**: a `COMPLETED` workflow whose decision lineage contains
  `no_clarification_required` with a stated rationale, and a `human_clarification` step that reaches
  `SKIPPED` rather than running.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=GreenfieldScenarioTest`
- **Doc impact**: `docs/scenarios/greenfield.md` — original requirement, the requirement-quality
  check `AmbiguityDetectionStage` performed, reason clarification was skipped, decomposition,
  orchestration path (actual `GET /workflows/{id}/audit` output pasted in), acceptance criteria,
  terminal outcome.
- **Traceability update**: REQ-011/012/013/016/017/019/020/028/031 rows gain `T039`; SCN-A's row
  across the matrix gains `T039` as its primary evidence task.
- **Risk/guardrail**: this is the highest-risk **content** task in Phase 9 per `plan.md` §14.6's
  stop condition — if Phase 4 slipped, this is where it would first become visible; per the stop
  condition, cutting SCN-C's (T041) replan depth is the approved fallback, never cutting this
  scenario's release-readiness gate. `AmbiguityDetectionStage`'s ambiguity check is a documented
  demonstration-scope heuristic (cites a `req-<digits>` ID and states a measurable criterion), not a
  claim of general NLP-grade requirement analysis.
- **Completion criteria**: test passes; `docs/scenarios/greenfield.md` written; `git commit -m
  "test(scenarios): validate greenfield workflow against the real 12-node graph"`.
- **Human approval**: false.

### T040: Brownfield scenario — enhancement against existing code with seven-branch impact analysis gate

**Authoritative final form (corrected 2026-09-13, fourth review round).** Written directly with
`spec.md` Section 4's SCN-B requirement — seven genuinely parallel impact-analysis branches joined
by a mandatory Approval Gate before Implementation — rather than a single-stage placeholder folded
in by a later task; no code exists yet, so there is nothing gained by building the placeholder first.

- **Objective**: Demonstrate a real brownfield change (hardening `UrlValidator`'s SSRF guard to
  reject IPv6 Unique Local Addresses, RFC 4193 `fc00::/7` — a genuine gap since
  `Inet6Address.isSiteLocalAddress()` only covers the deprecated `FEC0::/10` range) preceded by
  seven independently-executing sibling impact stages (components, interfaces, data flows, tests,
  documentation, regression risk, rollout/rollback) joined by one `ChangeImpactReportStage` that is
  itself a mandatory Approval Gate before Implementation.
- **REQ/SCN/ADR**: REQ-011, REQ-012, REQ-013, REQ-015, REQ-020, REQ-021, REQ-030; SCN-B (primary);
  ADR-010. **Chosen change is fixed by CLR-15** (`spec.md` §13.1, human-confirmed 2026-09-13: links
  are immutable, so the originally-planned `PATCH /links/{code}/expiration` demo is no longer
  valid).
- **Files**: Create `orchestration/stages/impact/{ImpactedComponentsStage,ImpactedInterfacesStage,
  ImpactedDataFlowsStage,ImpactedTestsStage,ImpactedDocumentationStage,RegressionRiskStage,
  RolloutRollbackStage}.java`; Create `orchestration/stages/ChangeImpactReportStage.java`;
  Create `docs/scenarios/brownfield.md`; Test `scenarios/BrownfieldScenarioTest.java`; Modify
  `domain/UrlValidator.java` (the actual fix), `domain/UrlValidatorSsrfTest.java` (new
  failing-then-passing case).
- **Prerequisites**: T039 (reuses its stage classes and `StageCatalog` pattern directly), T020b
  (`StageCatalog`), T015 (modifies the code and test T015 created).
- **Parallel**: `true` with T041 (disjoint files); not parallel with T039.
- **Expected artifact**: all seven impact stages reach `SUCCEEDED` with genuine overlapping
  `[startedAt, endedAt)` intervals (same overlap-proof pattern as T022's parallel test, with the
  same documented `CountDownLatch` fallback if timing proves flaky); `ChangeImpactReportStage`
  starts only after all seven finish (never early); a rejected report leaves `implementation` at
  `PENDING` permanently for that plan version; `UrlValidator` now rejects
  `http://[fd00::1]/...`-class addresses it previously passed.
- **TDD**: yes — two TDD cycles: one for the orchestration wiring (the seven impact stages plus
  `ChangeImpactReportStage`), one for the actual security fix (new `UrlValidatorSsrfTest` case
  observed failing, then passing).
- **Validation command**:
  `./mvnw test -Dtest=BrownfieldScenarioTest,UrlValidatorSsrfTest,UrlValidatorTest`
- **Doc impact**: `docs/scenarios/brownfield.md` per the guide's Brownfield Impact Analysis Gate
  structure (Change Impact Summary, Dependency Map, Test-First Change Plan, Regression Risk
  Matrix, Documentation Impact — explicitly noting the CLR-15 pivot away from the PATCH-endpoint
  demo, Replanning Decision, Human Approval Requirement), reflecting the real seven-branch
  structure; `plan.md` Section 3.2 gains an SCN-B-only sub-table (the baseline 12-stage table
  doesn't carry brownfield-only nodes).
- **Traceability update**: REQ-011/012/013/015/020/021/030 rows gain `T040`; ADR-010 row gains
  `T040`; SCN-B's primary evidence task; change-request/impact-analysis-evidence source.
- **Risk/guardrail**: real regression risk is named explicitly — the fix must not start rejecting
  legitimate public IPv6 hosts; the full pre-existing `UrlValidatorTest`/`UrlValidatorSsrfTest`
  suite (T015) is the regression gate, and a public-IPv6-host positive case should be added if none
  already exists. The rollout/rollback branch's output is a required exit-criterion input to
  `ReleaseReadinessStage` for brownfield runs specifically — it must refuse a "ready" outcome if
  that finding is absent, per `spec.md`'s own stated rule.
- **Completion criteria**: both the security-fix test and the scenario test pass, with no
  regression in T015's original suite; two separate commits per the runbook's own instruction —
  `git commit -m "fix(security): reject IPv6 unique local addresses in SSRF guard"` for the fix,
  then `git commit -m "test(scenarios): validate brownfield workflow's seven parallel impact-analysis
  branches"` for the orchestration wiring (not bundled into one commit).
- **Human approval**: **true** — explicit runbook example of a security-sensitive decision
  (constitution Principle III/V), on top of being a live fix to a previously-shipped security
  control (T015) with genuine regression risk. The human candidate should see this fix distinctly
  from ordinary feature work before it ships, exactly as T015 was flagged for the same reason.

### T040a (folded into T040 — retained as a historical marker, do not implement separately)
*(originally: "SCN-B's seven genuinely parallel impact-analysis branches"; corrected 2026-09-13,
fourth review round)*

This task, as originally written, rewrote `BrownfieldScenarioTest` and depended on the broken T020c
(itself folded into T039 — see there). Since no code exists yet, the seven-branch design it
specified is now written directly into T040 above, rather than building T040's original single
`ImpactAnalysisStage` first and superseding it here. Implement T040 instead of this entry; the
REQ/SCN/ADR coverage this task named is unchanged — it now travels with T040.

### T041: Ambiguous-requirement scenario — real suspension, real clarification fold-in, real replanning

**Authoritative final form (corrected 2026-09-13, fourth review round).** Reuses T039's real
`human_clarification` stage (`entryApproval=true`, gated by `activationConditionKey="ambiguity_gate"`)
instead of an `exitApproval`-on-`requirement` workaround — the more faithful realization of
`spec.md`'s Stage 3 (detection) vs. Stage 4 (clarification) distinction.

- **Objective**: Demonstrate genuine suspension at a real, always-present `human_clarification`
  node (active only when `AmbiguityDetectionStage` says so); prove the human's approval reason flows
  into `DecompositionStage`'s own persisted output (not merely that an approval exists); and
  demonstrate a real `replan()` that swaps `decomposition` for a differently-scoped
  `decomposition_v2` before the human's approval resumes execution.
- **REQ/SCN/ADR**: REQ-011, REQ-013, REQ-016, REQ-017, REQ-018, REQ-028 (context lineage,
  replanning, recoverability); SCN-C; ADR-004.
- **Files**: Create `docs/scenarios/ambiguous.md`; Test `scenarios/AmbiguousScenarioTest.java`. No
  modification to `WorkflowEngine.java` — `human_clarification`'s entry-gate mechanics (T023), the
  generic approval-reason-into-context write (T020b/T034a's `StageCatalog`-based design, finalized
  in the runbook's Task 50), and `replan()` (T035) are all already in place.
- **Prerequisites**: T039 (reuses its stage classes, `StageCatalog` pattern, and
  `human_clarification`/`DecompositionStage` directly — this task submits the *same* stage set with
  different input text), T035 (replan capability), T023 (`entryApproval`/`approve()` semantics).
- **Parallel**: not parallel with T039 (depends on it); `true` with T040 (disjoint files, both
  independently depend on T039).
- **Expected artifact**: workflow halts at `PENDING_APPROVAL` with `human_clarification` at
  `WAITING_APPROVAL` and `decomposition` unrun; after `approve()`, `decomposition`'s persisted
  result contains `clarifiedRequirement` equal to the human's stated reason. A second test: after
  `replan()` (registering `decomposition_v2`, called while `human_clarification` is still pending)
  then `approve()`, the workflow reaches `COMPLETED` with the new stage having actually run and the
  original `decomposition` row `INVALIDATED`. Audit event ordering: `replanned` < `approval_approved`.
- **TDD**: yes.
- **Validation command**: `./mvnw test -Dtest=AmbiguousScenarioTest`
- **Doc impact**: `docs/scenarios/ambiguous.md` per the guide's 17-point evidence list: original
  ambiguous input, detected ambiguity (`AmbiguityDetectionStage`'s recorded decision), classification/
  severity, affected requirements, workflow state before/after, human clarification request text,
  recorded human decision, updated assumption, downstream impact
  (`decomposition`'s `clarifiedRequirement` field), replanning event, invalidated/regenerated
  artifacts, resumed state, final validation, audit trail excerpt, terminal outcome.
- **Traceability update**: REQ-011/013/016/017/018/028 rows gain `T041`; SCN-C's primary evidence
  task; replanning-evidence source (alongside T035).
- **Risk/guardrail**: **the runbook's own mechanically-required ordering** — `replan()` must be
  called before `approve()`, because `approve()` synchronously resumes execution; replacement
  stages must already be registered when that resume happens. This is also the task `plan.md`
  §14.6's stop condition names explicitly: if Phase 4 slipped on Day 1, cut *this* scenario's
  replan depth to a single-stage substitution (already what this task demonstrates) before cutting
  any governance or audit requirement elsewhere.
- **Completion criteria**: both tests pass; `docs/scenarios/ambiguous.md` written; `git commit -m
  "test(scenarios): validate ambiguous-requirement workflow's clarification fold-in and replanning"`.
- **Human approval**: false (the scenario *demonstrates* a human decision point; building/testing
  it is ordinary implementation work).

---

## Phase 10 — Cross-Scenario Evidence Review, Documentation, Traceability

### T042: Three-Scenario Evidence Review (self-audit, no file modification)

- **Objective**: Verify, per scenario, the presence of requirement input, interpretation,
  decomposition, dependency graph, orchestration path, state transitions, human gates, tests,
  executed validation, failure-behavior demonstration, audit evidence, doc/execution match,
  terminal outcome, and disclosed limitations — and confirm the three scenarios are materially
  different, not copy-pasted.
- **REQ/SCN/ADR**: REQ-019, REQ-020, REQ-026, REQ-029 (testability); SCN-A, SCN-B, SCN-C; all
  ADRs indirectly (this is a synthesis checkpoint).
- **Files**: Create `docs/assessment/scenario_evidence_review.md`.
- **Prerequisites**: T039, T040, T041 (all three scenarios must exist to review).
- **Parallel**: false.
- **Expected artifact**: a completed evidence matrix (14 columns × 3 scenario rows).
- **TDD**: no — this is a documentation/self-audit task, not new behavior; no test file.
- **Validation command**: n/a (manual review against T039/T040/T041's actual test output and
  `docs/scenarios/*.md`; completion is evidenced by the filled matrix, not a `./mvnw` command).
- **Doc impact**: `docs/assessment/scenario_evidence_review.md` created.
- **Traceability update**: confirms SCN-A/B/C rows are each independently evidenced before T043
  builds the full requirement-level matrix on top.
- **Risk/guardrail**: this is a self-audit, not independent verification — flag anything
  suspicious rather than smoothing it over; genuine differences between the three scenarios'
  stage sets (already true by construction per T039/T040/T041) must be confirmed, not assumed.
- **Completion criteria**: matrix complete; `git commit -m "docs: add architecture and scenario
  walkthroughs"`.
- **Human approval**: false.

### T043: Architecture overview + traceability matrix

- **Objective**: Produce the component/control-flow architecture narrative and the full
  requirement-to-evidence traceability matrix.
- **REQ/SCN/ADR**: all REQs (this task's own deliverable is the traceability of every one of
  them); all ADRs.
- **Files**: Create `docs/architecture/overview.md`, `docs/traceability/matrix.md`.
- **Prerequisites**: T042.
- **Parallel**: false.
- **Expected artifact**: `overview.md` — components diagram (API → domain → persistence,
  separately orchestration → policy → reliability → observability, both sharing the H2 store),
  one full-scenario control-flow narrative, ADR decision table. `matrix.md` — one row per REQ-001
  through REQ-031 with columns Requirement ID | Scenario ID | Design Section | ADR | Task ID |
  Code Path | Test Path | Validation Command | Doc Path | Evidence Path.
- **TDD**: no.
- **Validation command**: n/a (documentation deliverable; correctness is checked by cross-
  referencing each row against the actual `T0NN` task it cites, not by running tests).
- **Doc impact**: this task's entire output *is* documentation.
- **Traceability update**: this task produces the canonical traceability matrix every other task
  in this document has been feeding rows into via its own "Traceability update" field.
- **Risk/guardrail**: a row citing a task or path that doesn't actually exist in the repository is
  worse than an incomplete matrix — verify every cell against the real file tree before
  committing.
- **Completion criteria**: both documents complete; `git commit -m "docs: add traceability and
  reviewer navigation guide"` (this commit is shared with T044 per the runbook's own instruction
  to bundle them — both files land in the same commit).
- **Human approval**: false.

### T044: Reviewer Navigation Guide

- **Objective**: A single guide giving, for every material claim in the repository, an exact path,
  exact command, expected observable result, and related REQ/SCN ID.
- **REQ/SCN/ADR**: REQ-029 (testability — this guide is what makes every other requirement's
  evidence independently checkable); no new ADR.
- **Files**: Create `docs/assessment/reviewer_navigation_guide.md`.
- **Prerequisites**: T043 (the guide is built from the traceability matrix's rows).
- **Parallel**: false.
- **Expected artifact**: a 25-point structured guide (per the source guide's Section 26)
  keeping `README.md` deliberately short and putting all depth here.
- **TDD**: no.
- **Validation command**: n/a (documentation deliverable; validated by a reviewer actually
  following each cited command and confirming the stated result).
- **Doc impact**: this task's output is documentation; it is also the reviewer-navigation-evidence
  source in the Evidence table above.
- **Traceability update**: none beyond T043 (this is the human-readable index over that matrix).
- **Risk/guardrail**: every claim must cite something checkable — a claim with no path/command is
  not acceptable in this document, per its own purpose.
- **Completion criteria**: guide complete; `git commit -m "docs: add traceability and reviewer
  navigation guide"` (same commit as T043, per the runbook's explicit bundling instruction — see
  T043's Completion criteria).
- **Human approval**: false.

### T045: `README.md`, `SECURITY.md`, `CONTRIBUTING.md`, setup instructions

- **Objective**: Finalize the top-level entry-point documentation: quickstart, security posture,
  contribution norms.
- **REQ/SCN/ADR**: REQ-021 (security — `SECURITY.md`'s disclosed posture), REQ-024
  (maintainability); ADR-010.
- **Files**: Create/modify `README.md`, `SECURITY.md`, `CONTRIBUTING.md`.
- **Prerequisites**: T044 (README links to the reviewer navigation guide).
- **Parallel**: false.
- **Expected artifact**: `README.md` — 2–3 sentence objective, quickstart (`./mvnw
  spring-boot:run`, `./mvnw test`), link to the reviewer navigation guide for depth.
  `SECURITY.md` — URL scheme allow-list, SSRF guard, no-auth assumption (ADR-010, including the
  approval-endpoint risk from T026), secrets policy (none stored/logged), a manual dependency-scan
  recommendation (`mvn org.owasp:dependency-check-maven:check`, no CI configured — a disclosed
  limitation, not a hidden gap).
- **TDD**: no.
- **Validation command**: n/a (documentation deliverable).
- **Doc impact**: this task's entire output is documentation.
- **Traceability update**: REQ-021/024 rows gain `T045`; closes the API/schema deliverable
  group's "documentation" line item.
- **Risk/guardrail**: `SECURITY.md` must not soften ADR-010's residual risk — it should state
  plainly that the approval endpoint is unauthenticated, per ADR-006/ADR-010's own language.
- **Completion criteria**: all three files complete; `git commit -m "docs: finalize setup
  instructions and security notes"`.
- **Human approval**: false.

---

## Phase 11 — Convergence & Release Readiness

### T046: Full quality suite execution + `/speckit.converge`-equivalent report

- **Objective**: Run the entire test suite and all three scenarios plus the metrics report, paste
  **actual** (never fabricated) output into a convergence report, and emit exactly one release
  decision.
- **REQ/SCN/ADR**: REQ-020 (release-readiness outcome), REQ-029 (testability); all REQs
  indirectly (this is the release gate).
- **Files**: Create `docs/assessment/convergence_report.md`.
- **Prerequisites**: T045 (last doc task) — and, transitively, every implementation task
  T011–T041 (including `T017a`, `T026a`, `T036a`, added by the Task 10 corrective batch) and every
  doc task T042–T045, since this report's content is the actual, current state of all of them.
- **Parallel**: false.
- **Expected artifact**: pasted real output from
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test`; each of
  `./mvnw -Dspring-boot.run.arguments=--scenario=greenfield spring-boot:run` (and `brownfield`,
  `ambiguous`); `./mvnw -Dspring-boot.run.arguments=--metrics-report spring-boot:run`. Filled
  sections per guide Section 22: requirement verification, architecture drift check, orchestration
  executable-evidence checklist (all 19 items), three-scenario verification, testing results,
  documentation currency check, compliance/change-control verification, evidence-integrity pass.
- **TDD**: no (this task *runs* tests, it does not add new ones).
- **Validation command**: `./mvnw test` (full suite, not a single `-Dtest=` filter — this is the
  one task in this document that must run everything).
- **Doc impact**: `convergence_report.md` created.
- **Traceability update**: this is the point every prior task's "Completion criteria" claim gets
  cross-checked against a single, real, full-suite run.
- **Risk/guardrail**: given the documented deferred backlog (auth, rate limiting, migrations,
  dedup — `plan.md` §14.3), the release decision must be `READY WITH ACCEPTED LIMITATIONS`, never
  a bare `READY`, while any mandatory Global Constraint item remains unmet — the runbook is
  explicit about this.
- **Completion criteria**: report complete with real (not fabricated) output; one of
  `READY`/`READY WITH ACCEPTED LIMITATIONS`/`NOT READY` recorded; `git commit -m "chore(release):
  converge artifacts and validate readiness"`.
- **Human approval**: **true** — constitution Principle III reserves "release readiness (the
  decision to ship)" for humans. This task produces the evidence and a recommended decision; the
  decision of record still belongs to the human candidate, not to this task's own completion.

### T047: Final Independent Assessment + Final Engineering Summary (21-section)

- **Objective**: Run a hostile, skeptical Principal-Engineer-style review against the actual
  repository state (not the agent's own prior summary as evidence), resolve mandatory findings at
  their correct upstream task, and write the mandatory 21-section final engineering summary.
- **REQ/SCN/ADR**: REQ-020, REQ-029; all REQs (this is the terminal synthesis document).
- **Files**: Create `docs/assessment/final_independent_assessment.md`,
  `docs/assessment/final_engineering_summary.md`.
- **Prerequisites**: T046 (the convergence report's real output is this task's raw material).
- **Parallel**: false.
- **Expected artifact**: a 30-dimension 0–5-scored assessment with a PASS/BORDERLINE/FAIL verdict;
  a 21-section summary in the guide's exact mandatory order (executive outcome ... final judgment/
  next actions), every material claim citing an exact repo path + command + expected result.
- **TDD**: no.
- **Validation command**: n/a (synthesis document; validated by spot-checking its claims against
  T046's real command output and the repository state directly).
- **Doc impact**: this task's output is documentation, but it is also where every backlog item
  flagged inline across T032, T033, T037 (and any others found during the hostile review) must be
  consolidated, not left scattered.
- **Traceability update**: closes the traceability chain's final "Evidence" column for every REQ.
- **Risk/guardrail**: **explicit guardrail from the guide, restated by the runbook**: "Do Not Use
  Claude's Own Summary as Evidence" — mandatory findings must be resolved by returning to the
  correct upstream phase/task, not by patching the assessment document itself to explain away a
  gap.
- **Completion criteria**: both documents complete, mandatory findings resolved at their source
  task; `git commit -m "docs(assessment): add final independent assessment and engineering
  summary"`.
- **Human approval**: **true** — constitution Principle III reserves "final quality sign-off" for
  humans. This task produces the assessment; the human candidate's acceptance of it (not the
  agent's own hostile self-review, however rigorous) is what actually constitutes sign-off.

### T048: Clean-clone verification, final tag

- **Objective**: Confirm the repository is green from a fresh checkout (catching anything that
  only worked due to local uncommitted state), review the commit history for narrative integrity,
  and cut the final tag.
- **REQ/SCN/ADR**: REQ-020, REQ-029; no new ADR.
- **Files**: none (verification + git operations only).
- **Prerequisites**: T047 (everything must be committed and assessed first).
- **Parallel**: false.
- **Expected artifact**: a green `./mvnw test` run from `/tmp/verify-clone` (a fresh `git clone`,
  not the working tree); `git tag -a assessment-submission-v1.0`.
- **TDD**: no.
- **Validation command**: `cd /tmp && git clone <repo> verify-clone && cd verify-clone &&
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw test`
- **Doc impact**: none.
- **Traceability update**: none new — this is a final integrity check, not new evidence.
- **Risk/guardrail**: specifically catches an untracked `.mvn/wrapper/maven-wrapper.jar` (or
  similar) that should have been committed but happened to already exist locally; `git log
  --oneline --decorate --graph` must read as a truthful engineering journey matching the guide's
  Suggested Commit Progression, not a fabricated/reordered history.
- **Completion criteria**: clean-clone test run green; `git status` clean; tag created and shown
  via `git show assessment-submission-v1.0`. **Step 4 of this task (push/submit) is explicitly a
  human action per the runbook** — this task's own completion does not include pushing.
- **Human approval**: **true** — the runbook itself marks this task's final step "(human)":
  pushing and submitting through the assessment's actual submission channel requires the human
  candidate's explicit action, not automated execution. This is also, functionally, the
  constitution's "destructive/irreversible" and "release readiness" classes converging on one
  action (an irreversible public submission).

---

## Summary

**Updated twice by post-Task-10 corrective rounds.** 38 tasks (T011–T048) were originally
transcribed from the runbook's Phases 2–12.

**Round 1 (Task 10 corrective batch):** three tasks — `T017a`, `T026a`, `T036a` — added to close
two HIGH findings both Task 10's own `/speckit.analyze` + Independent Reviewer Gate
(`docs/assessment/pre_implementation_analysis.md` Section 7) and an external Codex review
(`codex-recommend.md` round 2, Findings #4, #8, #9) independently confirmed: `POST /workflows` and
`GET /links/{code}` had no implementing/contract-testing task despite being contract-mandated
(`T017a`, `T026a`), and REQ-031's own named stage-boundary-violation test had no implementing task
(`T036a`). Brought the total to 41 tasks.

**Round 2 (post-Codex-round-3, 2026-09-13):** five more tasks — `T020b`, `T020c`, `T034a`, `T037a`,
`T040a` — added after a third Codex review pass on the corrected repository found the round-1 fixes
sound but flagged real design gaps still open: `spec.md`'s SCN-B requires seven genuinely parallel
impact-analysis branches, not the single stage originally built (`T040a`); the constitution defines
a fallback mechanism distinct from retry/rollback/safe-stop that existed nowhere in the design
(`T034a`); `spec.md`'s 12-stage graph was realized as 8 stages with Stage 4 as an implicit property
of an approval gate rather than its own persisted node (`T020b` adds the conditional-activation
mechanism this requires; `T020c` uses it to realize the real 12-node graph); and
`computeReliabilitySummary`'s disclosed workflow-scoping bug should be fixed for real, not carried
as backlog (`T037a`). Brings the total to 46 tasks.

Letter-suffixed IDs (rather than a full renumbering) were used for both rounds deliberately so
every pre-existing `T0NN` cross-reference elsewhere in this document, in `plan.md`'s traceability
table, and in `pre_implementation_analysis.md` itself stays valid without a repository-wide
renumbering pass.

**Round 3 (fourth review round, 2026-09-13):** no new task IDs added; five existing entries
corrected. A fourth review found that Round 2's `T020c` and `T040a` both modified files
(`RequirementStage.java`/`GreenfieldScenarioTest`; `BrownfieldScenarioTest`) that only `T039`/`T040`
create — a genuine forward-reference defect, since no task should modify a file a later task
creates. Because no application code exists yet for any of the three scenarios, the fix was to
write `T039`, `T040`, and `T041` directly in their authoritative final form (the real 12-node graph,
the seven-branch SCN-B design, the real `human_clarification` gate) rather than build an obsolete
placeholder and patch it later; `T020c` and `T040a` are retained in place only as short historical
markers pointing to where their content moved, per this plan's own convention of never deleting the
record of what was asked. `T020b` and `T034a` were also corrected in place: their original design —
`registerActivationCondition(...)`/`registerFallback(...)` storing `Predicate`/`StageHandler`
objects directly on the live `WorkflowEngine` instance — does not survive a process restart,
contradicting REQ-017 and the binding REQ-028 5-second recovery-time objective; both now describe a
`StageCatalog`-based, restart-safe lookup by stable string key instead. Total remains 46 tasks —
this round corrected task *content*, not task *count*.

Sequential-blocking groups: T011–T019 (URL-shortener domain, mostly sequential after the
T014/T015/T016 parallel trio; `T017a` inserted between T017 and T018, same file/test class,
strictly sequential), T020–T026 (orchestration engine core, strictly sequential; `T020b` inserted
after T020 but sequenced after T023 per its own Prerequisites — `T020b` now specifies `StageCatalog`,
the restart-safe lookup mechanism T039/T040/T041 depend on directly; `T020c` is **not** part of this
sequence — corrected 2026-09-13, its content is folded into T039 and it is retained only as a
historical marker, carrying no dependency edges of its own), `T026a` (sequential immediately after
T026, same file/test class), T027 (independent) → T028–T029 (sequential, depend on the full
engine), T030–T032 (mutually parallel) → T033–T034 (sequential fan-in) → `T034a` (fallback, depends
on all four), T035 (replanning, depends on the full Phase 4–6 engine), T036–T038 (observability,
T036/T037 parallel, T038 depends on T037; `T036a` inserted after T036, parallel with T037, depending
on T022–T024/T036; `T037a` inserted immediately after T037, sequential, fixes T037's own disclosed
bug), T039–T041 (three scenarios, corrected 2026-09-13: T039 depends directly on `T020b`
(`StageCatalog`) and `T034a` (`SUCCEEDED_WITH_FALLBACK`/`fallbacksUsed`), not on `T020c`, which
carries no live dependency edges; T040 and T041 both depend on T039 — T040 for its reused
`StageCatalog` pattern, T041 for its reused `human_clarification`/`DecompositionStage` — so neither
is parallel-safe with T039, though T040/T041 are mutually parallel-safe with each other (disjoint
files); `T040a` is **not** part of this sequence — its seven-branch SCN-B content is folded directly
into T040, and it is retained only as a historical marker), T042–T048 (fully sequential convergence
chain). Seven tasks carry `human-approval: true`: T015,
T026, T029, T040, T046, T047, T048 — see each task's own entry above and the Task 9 report for the
per-task justification. All eight letter-suffixed tasks (`T017a`, `T026a`, `T036a`, `T020b`,
`T020c`, `T034a`, `T037a`, `T040a`) are `human-approval: false` (see each task's own entry for the
specific reasoning).
