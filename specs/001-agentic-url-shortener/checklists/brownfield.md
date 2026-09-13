# Brownfield Scenario (SCN-B) Quality Checklist

**Category 13 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** `spec.md` Section 4 (SCN-B), `plan.md` Section 11.2, the `ImpactAnalysisStage`, the
IPv6 ULA SSRF-guard fix, `BrownfieldScenarioTest`, `docs/scenarios/brownfield.md`.
**Constitution principles:** I, III, VI, VIII, X.
**Status:** All items UNVERIFIED/PENDING — SCN-B (the `UrlValidator` IPv6 ULA fix) has not been
executed.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] BF-CHK-001 [ANTI-PATTERN: material change implemented without impact analysis]**
  (scenario instantiation) — Verify the `ImpactAnalysisStage` runs and produces a non-empty Change
  Impact Report *before* `implementation` begins — `implementation.dependsOn` must include
  `impact_analysis` (alongside `design`), and a test must confirm implementation cannot start while
  impact analysis is incomplete. **Fail if**: `BrownfieldScenarioTest` (or manual trace) shows the
  `UrlValidator.java` fix committed/implemented before the impact-analysis stage produced its
  report. **Cites**: spec.md Section 4 (SCN-B), plan.md Section 11.2, REQ-030.

- **[MANDATORY] BF-CHK-002** — Verify the Change Impact Report addresses all seven impact dimensions
  named in `spec.md` Section 4 (SCN-B): impacted components, impacted interfaces, impacted data
  flows, impacted tests, impacted documentation, regression risks, and rollout/rollback
  considerations — either via structured `ImpactAnalysisStage` output fields (`impactedModules`,
  `impactedTests`, `regressionRisk`) or via `docs/scenarios/brownfield.md`'s narrative, and that a
  dimension found to have "none" impact records that as an explicit finding, not a silent omission.
  **Fail if**: any of the seven dimensions has neither a structured field nor a narrative entry.
  **Cites**: spec.md Section 4 (SCN-B), plan.md Section 11.2.

- **[MANDATORY] BF-CHK-003** — Verify the joined Change Impact Report is a mandatory Approval Gate
  (`exitApproval=true` or equivalent) that blocks `implementation` until a human has reviewed and
  approved it — distinguishing this scenario from greenfield precisely because existing behavior and
  consumers are at risk. **Fail if**: implementation proceeds without a recorded human approval of
  the Change Impact Report. **Cites**: spec.md Section 4 (SCN-B), REQ-013.

- **[MANDATORY] BF-CHK-004 [ANTI-PATTERN: implementation preceding specification or tests]**
  (scenario instantiation) — Verify the actual `UrlValidator` IPv6 ULA fix follows a real TDD cycle:
  a failing `UrlValidatorSsrfTest` case for `http://[fd00::1]/internal` observed to fail for the
  right reason, then the fix, then the same test observed passing, then the full regression suite
  (`UrlValidatorTest` + `UrlValidatorSsrfTest`) green. **Fail if**: git history shows the fix
  committed before the failing test, or no evidence exists that the test was ever observed failing.
  **Cites**: plan.md Section 11.2, Principle IV.

- **[MANDATORY] BF-CHK-005** — Verify that if the regression suite fails after the fix, the branch
  safe-stops rather than proceeding to Documentation or Release-readiness. **Fail if**: a regression
  failure is injected in a test and the workflow nonetheless proceeds past Testing. **Cites**: spec.md
  Section 4 (SCN-B), REQ-016.

- **[MANDATORY] BF-CHK-006** — Verify the rollout/rollback branch's output becomes a required
  exit-criterion input to Release-readiness (Stage 11) — a brownfield change cannot reach a "ready"
  outcome without a recorded rollback or compensation plan (REQ-015). **Fail if**: SCN-B's
  release-readiness evaluation reports "ready" with no rollback/compensation plan attached to the
  run's evidence. **Cites**: spec.md Section 4 (SCN-B), REQ-015, REQ-020.

- **[MANDATORY] BF-CHK-007** — Verify `docs/scenarios/brownfield.md` contains all seven named
  evidence artifacts: Change Impact Summary, Dependency Map, Test-First Change Plan, Regression Risk
  Matrix, Documentation Impact, Replanning Decision, Human Approval Requirement — each backed by
  real run output, not narrative description alone. **Fail if**: any of the seven is missing or is
  prose with no traceable evidence backing it. **Cites**: plan.md Section 11.2.

- **[MANDATORY] BF-CHK-008 [ANTI-PATTERN: small coherent commits / github-evidence cross-check]** —
  Verify the brownfield scenario's delivery is split into the two coherent commits `plan.md` Section
  11.2 specifies (the fix itself, then the scenario/orchestration wiring) — not bundled into one
  commit mixing the security fix with unrelated scaffolding, and not scattered across commits that
  also touch unrelated files. **Fail if**: the fix commit's diff includes files unrelated to
  `UrlValidator`/its tests, or the scenario-wiring commit includes unrelated domain-code changes.
  **Cites**: plan.md Section 11.2, Principle X (small, logically coherent commits); primary
  detection in `github-evidence.md` CHK-GH-001.

- **[MANDATORY] BF-CHK-009** — Verify the brownfield scenario's chosen defect (`UrlValidator`'s
  IPv6 ULA gap) is treated, and disclosed, as a real, disclosed pre-existing defect rather than a
  contrived example — i.e., that `security.md` CHK-SEC-003 and this scenario's fix are the same real
  gap, not two independently invented stories. **Fail if**: the security checklist and the
  brownfield scenario describe inconsistent versions of the same defect. **Cites**: plan.md Section
  8/11.2; cross-reference `security.md` CHK-SEC-003.

- **[MANDATORY] BF-CHK-010** — Verify `BrownfieldScenarioTest` asserts impact analysis precedes
  implementation and that its findings are non-empty (not merely that the stage ran). **Fail if**:
  the test only checks that `ImpactAnalysisStage` executed without asserting on ordering or content.
  **Cites**: plan.md Section 11.2.

- **[OPTIONAL] BF-CHK-011** — Verify the decision to scope SCN-B as a security-hardening fix rather
  than the originally planned link-mutation demo (which CLR-15 forecloses) is itself explicitly
  recorded as a scenario-design decision with rationale, not silently substituted. **Fail if**: no
  document explains why the scenario changed from its earlier planned form. **Cites**: plan.md
  Section 11.2, CLR-15, Principle I.
