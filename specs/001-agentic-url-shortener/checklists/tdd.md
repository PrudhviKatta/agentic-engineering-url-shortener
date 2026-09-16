# TDD and Testing Quality Checklist

**Category 11 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `plan.md` Section 10 (Testing table), every test class named across the plan/ADRs,
REQ-029, Principle IV's red-green-refactor discipline.
**Constitution principles:** IV (Test-Driven Engineering) — non-waivable — and XI (Evidence-Based
Completion).
**Status:** Final review: **PASS WITH EVIDENCE LIMITATION**. The complete multi-level suite and
task-level red/green records exist, but Git history alone does not prove an observed-red run for
every historical behavior. See `docs/assessment/tdd_execution_evidence.md` and
`docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] TDD-CHK-001 [ANTI-PATTERN: implementation preceding specification or tests]** —
  Verify, for every unit of behavior (domain function, API endpoint, orchestration state
  transition), that a test exercising the intended behavior was committed before or in the same
  commit as the implementation it targets — verified by commit-order inspection (a test file's first
  appearance in git history precedes or coincides with, never follows, its corresponding
  implementation file's first appearance). **Fail if**: git history shows an implementation file
  added in a commit with no accompanying or preceding test, and a test for that behavior appears
  only in a later commit. **Cites**: Principle IV (non-waivable), Principle I.

- **[MANDATORY] TDD-CHK-002** — Verify each test was observed to fail first, for the expected reason
  (missing behavior, not a typo/import error), before the corresponding implementation was written —
  evidenced by a task/PR record, commit sequence, or CI run history showing a red run predating the
  green run. **Fail if**: no evidence exists that a test was ever run in a failing state before
  passing — a test with no observed red run is treated as untrustworthy per Principle IV. **Cites**:
  Principle IV ("A test that has never been watched to fail is not trustworthy evidence").

- **[MANDATORY] TDD-CHK-003** — Verify implementation was written to the minimum extent needed to
  make the failing test pass, not more — spot-checked by confirming no implementation exists for a
  behavior with no corresponding test. **Fail if**: a class/method has no test coverage and its
  existence cannot be traced to any REQ/test requiring it. **Cites**: Principle IV.

- **[MANDATORY] TDD-CHK-004** — Verify refactors are sequenced only while the full relevant test
  suite remains green — any refactor that required temporarily breaking tests was instead sequenced
  as its own red-green cycle. **Fail if**: a commit message or diff shows tests disabled/skipped to
  land a refactor, with no follow-up commit restoring and passing them. **Cites**: Principle IV
  (refactoring bullet).

- **[MANDATORY] TDD-CHK-005** — Verify every test level named in Principle IV is actually present
  and none is skipped where technically practical: unit (domain logic), integration (components
  wired together, e.g. persistence via `@DataJpaTest`), API contract (`LinkControllerContractTest`,
  `WorkflowControllerContractTest`, `MetricsControllerContractTest`), orchestration transition
  (`DependencyGraphTest`, `WorkflowEngineExecutionTest`), reliability (fault injection,
  `WorkflowEngineReliabilityIntegrationTest`), security (`UrlValidatorSsrfTest`, abuse-class tests),
  and end-to-end (`GreenfieldScenarioTest`, `BrownfieldScenarioTest`, `AmbiguousScenarioTest`).
  **Fail if**: any named test class from `plan.md` Section 10 does not exist once implementation
  claims to be complete. **Cites**: Principle IV (test-surface bullet), plan.md Section 10.

- **[MANDATORY] TDD-CHK-006 [ANTI-PATTERN: fabricated results]** — Verify every claimed test-pass
  result is backed by an actual, reproducible test run in this environment (e.g. `./mvnw clean
  verify` output, a CI log), not a narrative assertion ("tests pass") with no attached execution
  evidence. **Fail if**: a task report or commit message claims tests pass with no command output,
  log excerpt, or CI link substantiating the claim. **Cites**: Principle XI (#4, #10), Principle X
  ("No approval, test result, execution log... is ever fabricated... This is treated as a
  severity-one violation").

- **[MANDATORY] TDD-CHK-007** — Verify a task is never marked complete solely because code was
  generated that appears to implement a requirement — completion requires the corresponding tests to
  have actually been executed, in this environment, and observed passing. **Fail if**: a task
  report describes implementation as "done" while its test-execution evidence section is empty or
  missing. **Cites**: Principle IV (closing bullet), Principle XI.

- **[MANDATORY] TDD-CHK-008** — Verify every REQ-001..031 acceptance criterion has a traceable
  test (unit, contract, or scenario-level) that specifically exercises it, per REQ-029's testability
  requirement, and that the test's assertion actually matches the stated positive/negative criterion
  (not a superficially similar but weaker check). **Fail if**: a REQ's test asserts something
  narrower than its stated negative criterion (e.g., testing only the happy path of a requirement
  whose negative criterion demands a specific rejection behavior). **Cites**: REQ-029, Principle IV.

- **[MANDATORY] TDD-CHK-009** — Verify negative-path tests exist for every requirement's negative
  acceptance criterion, not only positive-path tests for the positive criterion — spot-checked
  against REQ-002 (rejection), REQ-004 (unknown-code), REQ-005 (expired), REQ-009
  (persistence-failure), REQ-013 (reject/timeout), REQ-016 (safe-stop). **Fail if**: any of these
  REQs' test suite contains only a positive-path assertion. **Cites**: DER-3, Principle IV.

- **[OPTIONAL] TDD-CHK-010** — Verify test names/descriptions state the behavior under test in
  business/requirement terms (traceable to a REQ ID) rather than only implementation-detail terms,
  so a reviewer can map test → requirement without reading the test body. **Fail if**: a test class
  has no discoverable mapping to a REQ ID anywhere (test name, comment, or traceability matrix row).
  **Cites**: Principle X, REQ-029.

- **[MANDATORY] TDD-CHK-011** — Verify the orchestration engine's own state-transition behavior
  (gates, retries, replans, resumption) receives the same red-green-refactor discipline as domain
  code — i.e., orchestration transition tests are not treated as a lower-priority or optional test
  category. **Fail if**: `WorkflowEngine`-related test classes are missing while domain-only test
  classes are complete, with no disclosed reason. **Cites**: Principle IV (opening paragraph:
  "orchestration transitions are behavior, and behavior gets a red-green-refactor cycle like
  anything else").
