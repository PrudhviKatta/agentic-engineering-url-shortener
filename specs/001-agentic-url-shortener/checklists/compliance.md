# Compliance and Change-Control Policy Enforcement Checklist

**Category 8 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-030, ADR-007 (Policy Engine Format), `plan.md` Section 9,
`src/main/resources/policies/v{N}.yaml`, `PolicyExceptionEntity`.
**Constitution principles:** VI (Compliance and Change-Control Policy Enforcement) — the primary
principle this file enforces.
**Status:** Final review: **PASS WITH LIMITATION** (prototype policies, not certification against a
named external regime). See `docs/assessment/final_checklist_status.md`.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] COMP-CHK-001 [ANTI-PATTERN: missing compliance policy evaluation]** — Verify Stage
  10 (Security and risk validation) actually invokes `PolicyEngine.evaluate(stageName, context)` for
  every applicable policy (`SEC-001`, `SEC-002`, `COMP-001`, `LIC-001` per `plan.md` Section 9) on
  every workflow run, not only when convenient or for a subset of runs. **Fail if**: a completed
  workflow run's evidence trail contains no `policy_evaluations` rows, or is missing an evaluation
  for a policy that should apply to that run's stage. **Cites**: Principle VI, REQ-030.

- **[MANDATORY] COMP-CHK-002** — Verify every policy check produces exactly one of `PASS`, `FAIL`,
  `EXCEPTION_REQUESTED`, or `NOT_APPLICABLE` — never a fifth implicit outcome such as "skipped" or
  an absent row standing in for "assumed fine." **Fail if**: a policy applicable to a given stage
  has no corresponding `policy_evaluations` row for a run that reached that stage. **Cites**:
  Principle VI ("There is no fifth, implicit outcome"), ADR-007.

- **[MANDATORY] COMP-CHK-003 [ANTI-PATTERN: policy execution without a recorded policy version]** —
  Verify every `policy_evaluations` row records the `version` string of the policy file
  (`src/main/resources/policies/v{N}.yaml`) active at evaluation time. **Fail if**: any
  `policy_evaluations` row has a null/blank `version` field, or the recorded version does not match
  a real, identifiable policy-file revision. **Cites**: Principle VI, ADR-007, REQ-030.

- **[MANDATORY] COMP-CHK-004 [ANTI-PATTERN: compliance failure that does not block progression]** —
  Verify a `FAIL` on a mandatory policy check (`SEC-001`, `SEC-002`, `COMP-001` per `plan.md`
  Section 9's mandatory/advisory split) actually blocks the dependent branch — specifically, that
  `ReleaseReadinessStage` cannot report "ready" when a mandatory policy's most recent applicable
  evaluation is `FAIL`. **Fail if**: a run with a mandatory-`FAIL` policy state reaches a "ready"
  release-readiness outcome, or continues past Stage 10 to Stage 11 without a safe-stop or
  compensating record. **Cites**: Principle VI ("A `FAIL`... blocks further progress... It does not
  become a warning that implementation proceeds past"), CLR-13, REQ-016, REQ-020.

- **[MANDATORY] COMP-CHK-005** — Verify `LIC-001` (advisory) correctly does NOT trigger safe-stop on
  `FAIL`, while still being recorded as a first-class `policy_evaluations` row — i.e., the
  mandatory/advisory distinction (`PolicyDefinition.mandatory`) is real and testable, not merely
  documented. **Fail if**: an advisory `FAIL` triggers safe-stop, or an advisory `FAIL` is not
  recorded at all. **Cites**: plan.md Section 9, Principle VI.

- **[MANDATORY] COMP-CHK-006 [ANTI-PATTERN: unapproved policy exception]** — Verify an
  `EXCEPTION_REQUESTED` outcome never becomes permitted-to-proceed except through a fully populated,
  human-approved `PolicyExceptionEntity` — `reason`, `scope`, `approvingAuthority`,
  `compensatingControl`, and `expiry` must all be present before `grantPolicyException` will persist
  a row, per `plan.md` Section 9. **Fail if**: any code path allows `retrySafeStopped` to succeed
  against a policy for which no `PolicyExceptionEntity` row exists, or `grantPolicyException`
  accepts a partially specified request. **Cites**: Principle VI, Section 4.2, REQ-013.

- **[MANDATORY] COMP-CHK-007 [ANTI-PATTERN: missing compensating control]** — Verify every recorded
  `PolicyExceptionEntity` carries a non-empty, non-inferred `compensatingControl` field — recorded
  verbatim, never defaulted or auto-filled by the system. **Fail if**: an exception row has a blank,
  placeholder, or system-generated (rather than human-supplied) `compensatingControl` value. **Cites**:
  Principle VI (Section 4.2's required exception fields), plan.md Section 9.

- **[MANDATORY] COMP-CHK-008 [ANTI-PATTERN: expired policy exception]** — Verify
  `hasApprovedUnexpiredException` re-checks `expiry.isAfter(now)` on every evaluation (not only at
  grant time), so an exception past its `expiry` silently stops covering its policy again without
  requiring an explicit revocation action, and a subsequent evaluation against an expired exception
  correctly returns to `FAIL`/blocks progression. **Fail if**: a workflow continues to treat an
  expired exception as still covering a policy `FAIL`. **Cites**: Principle VI (Section 4.2:
  "expiry or review condition"), plan.md Section 9.

- **[MANDATORY] COMP-CHK-009 [ANTI-PATTERN: material change implemented without impact analysis]** —
  Verify any change to an already-approved requirement, architecture decision, schema, orchestration
  state definition, security control, or release criterion is preceded by a recorded impact analysis
  and change approval (REQ-030) before it takes effect — never introduced as an incidental side
  effect of an unrelated task. **Fail if**: a diff changes one of these categories with no
  accompanying impact-analysis/approval artifact referenced in the commit or an updated ADR/spec
  section. **Cites**: REQ-030, Principle VI, Principle I.

- **[MANDATORY] COMP-CHK-010** — Verify every policy outcome and every exception is a first-class
  entry in the project's audit/traceability evidence (`workflow_events` rows,
  `policy_exception_granted:<policyId>` events with `actorType="human"`) — never a side file
  excluded from the final review package. **Fail if**: an exception grant produces no corresponding
  `workflow_events` row. **Cites**: Principle VI (closing bullet), Principle IX, plan.md Section 9.

- **[MANDATORY] COMP-CHK-011** — Verify release readiness fails outright if any mandatory policy
  remains `FAIL`, or if any `EXCEPTION_REQUESTED` is unapproved, expired, or out of scope for the
  situation being applied to — this is re-evaluated whenever the applicable-check set changes (new
  code, new policy version, expired exception), not treated as a one-time gate. **Fail if**:
  release-readiness is computed once and cached rather than re-derived from current policy/exception
  state on each evaluation. **Cites**: constitution Section 4.6, REQ-020.

- **[MANDATORY] COMP-CHK-012** — Verify the four defined policies (`SEC-001`, `SEC-002`, `COMP-001`,
  `LIC-001`, `src/main/resources/policies/v1.yaml`) actually correspond to the four minimum policy
  domains constitution Principle VI names (security, compliance, privacy, audit retention, approved
  dependencies, software licensing, change control) — or that any gap (e.g., no explicit "privacy"
  or "audit retention" check) is explicitly disclosed as a scoped-down prototype decision, not a
  silent omission. **Fail if**: a named Principle VI policy domain has no corresponding policy
  entry and no disclosure explaining the gap. **Cites**: Principle VI (first bullet).

- **[OPTIONAL] COMP-CHK-013** — Verify the 90-day audit/analytics retention placeholder (CLR-17) is
  tagged, wherever it appears in code/config/docs, as an approved temporary assumption requiring
  re-justification before production use — not presented as a compliance-derived figure. **Fail
  if**: the 90-day figure appears anywhere without its "approved temporary assumption" qualifier.
  **Cites**: CLR-17, Principle VI.

- **[MANDATORY] COMP-CHK-014** — Verify a malformed or invalid policy YAML file fails fast (at
  startup or first evaluation) rather than being silently ignored, treated as "no policies apply,"
  or defaulting every check to `PASS`. **Fail if**: an invalid policy file results in a workflow run
  proceeding as though no mandatory policies existed. **Cites**: ADR-007 (Risks and Mitigations),
  Principle VI.
