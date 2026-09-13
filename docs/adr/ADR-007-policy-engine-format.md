# ADR-007: Policy Engine Format

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D7 already recorded as "Proposed for ADR at Task 7." Reviewed and accepted by the
human candidate (Prudhvidhar Reddy Katta) on 2026-09-13.

## Context

Constitution Principle VI requires versioned policy guardrails: every check yields
PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE, a failed mandatory check blocks progression, and any
exception requires human approval plus a recorded rationale/scope/authority/compensating
control/timestamp/expiry. `spec.md` Stage 10 (Security and risk validation) requires every
applicable policy check resolve to one of exactly those four outcomes (REQ-021's security controls,
REQ-020's release-readiness gate). This ADR decides the concrete format and evaluation mechanism for
those policy checks — how they are declared, versioned, and evaluated against a running workflow's
context.

## Decision Drivers

- Must be human-readable and diffable, so a policy change is itself a reviewable commit, not a
  hidden runtime mutation.
- Must be versioned without requiring a database migration per policy change.
- Must avoid a heavyweight rules-engine dependency disproportionate to roughly four policy checks
  at this prototype's scale.
- The evaluated policy version must be recorded on every evaluation so a later policy-file edit
  never silently reinterprets a historical evaluation (REQ-030, change safety).
- Should reuse tooling already present in the dependency tree rather than introduce a second
  parsing library for the same data format.

## Options Considered

### Option A: Versioned YAML files parsed at evaluation time (selected)
- **Approach:** `src/main/resources/policies/v{N}.yaml`, parsed via Jackson's `YAMLMapper`
  (`jackson-dataformat-yaml`). Each policy declares `id`, `domain`, `stage`, `mandatory`,
  `description`, and `check_key` (the `WorkflowContext` artifact key whose truthiness the check
  reads). The evaluated file's `version` string is recorded on every `policy_evaluations` row.
- **Advantages:** human-readable and git-diffable — a policy change is a visible, reviewable diff in
  the same commit/PR process as code; versioned by filename (`v1.yaml`, `v2.yaml`, ...) without a
  database migration; reuses Jackson, already a dependency via `spring-boot-starter-web`, avoiding a
  second YAML tooling family.
- **Disadvantages:** a hand-rolled `check_key`-truthiness model is less expressive than a real rules
  engine — no compound conditions across multiple artifact keys in this iteration.
- **Risks:** acceptable at the ~4-policy prototype scale; would need re-evaluation if the policy set
  grows materially (documented, not silently assumed to scale indefinitely).
- **Implementation impact:** one YAML file per version, one `PolicyEngine.evaluate(stage, context)`
  method, one `policy_evaluations` table recording outcome + version per check per stage evaluation.
- **Assessment implications:** demonstrates a policy mechanism whose history is itself part of this
  project's change-control story (`plan.md` Section 9), not a mechanism whose state is invisible to
  git history.

### Option B: Policy rows in the database, editable at runtime
- **Approach:** store policy definitions as rows in a `policies` table, editable via an
  administrative operation rather than a file.
- **Advantages:** would allow a policy change without a deployment.
- **Disadvantages:** a runtime-editable policy is not git-diffable — a policy change could happen
  without appearing in any commit history, directly working against constitution Principle VI's
  spirit of policy guardrails being a governed, reviewable artifact, and against REQ-030's change-
  safety requirement that policy edits be traceable. Would also require its own migration/versioning
  scheme layered on top of the database schema itself.
- **Risks:** a policy edit made directly against a running database, outside of code review, is a
  materially different governance posture than this project's "every material change goes through
  the same review process" stance applied elsewhere (e.g., ADR-007's own sibling ADRs).
- **Implementation impact:** requires an administrative API/UI to edit rows safely, plus its own
  versioning/audit scheme for policy changes themselves — a form of infrastructure this prototype
  does not otherwise need.
- **Assessment implications:** would read as trading away the exact auditability property this
  project treats as a design constraint, for a convenience (no-redeploy policy edits) this
  assignment's scope does not require.

### Option C: A rules-engine library (e.g., Drools)
- **Approach:** express policy checks as rules evaluated by a dedicated rules-engine library.
- **Advantages:** far more expressive condition language than Option A's `check_key`-truthiness
  model — could express compound, cross-artifact conditions natively.
- **Disadvantages:** a heavyweight dependency and its own DSL/authoring model, disproportionate to
  evaluating roughly four policy checks; a reviewer would need to learn the rules engine's own
  semantics to audit a policy, rather than reading a short YAML file directly.
- **Risks:** setup/learning-curve cost within the timebox for a capability (compound rule
  expressiveness) this project's actual policy set does not currently need.
- **Implementation impact:** highest of the three options for a benefit not required at this scale.
- **Assessment implications:** would read as over-engineering relative to the actual policy
  complexity this iteration has (per `research.md` D7, roughly four checks).

## Decision

**Option A: versioned YAML files under `src/main/resources/policies/v{N}.yaml`, parsed via
Jackson's `YAMLMapper` (`jackson-dataformat-yaml`) at evaluation time.** Every policy evaluation
resolves to exactly one of PASS, FAIL, EXCEPTION_REQUESTED, or NOT_APPLICABLE (Principle VI's
closed outcome enumeration), and the evaluated file's `version` string is recorded on every
`policy_evaluations` row.

## Rationale

Option A is the only option that is simultaneously human-readable/diffable (satisfying REQ-030's
change-traceability requirement and Principle VI's governance intent), versioned without a database
migration, and proportionate to the actual policy complexity this iteration has (`research.md` D7:
roughly four checks). Option B trades away diffability/auditability for a no-redeploy convenience
this project's scope does not need; Option C adds expressiveness this project's policy set does not
currently require, at the cost of a heavyweight dependency and a DSL a reviewer must separately
learn. Reusing Jackson (already present via `spring-boot-starter-web`) rather than introducing a
second YAML library (e.g., raw SnakeYAML) keeps the JSON/YAML tooling family singular across the
codebase.

## Consequences

**Positive:**
- Every policy change is a visible git diff, reviewable in the same commit/PR process as code — the
  policy history is itself part of this project's change-control evidence (`plan.md` Section 9).
- No second YAML parsing library introduced; one JSON/YAML tooling family (Jackson) covers both this
  and any other structured-config needs.
- The recorded `version` string on every `policy_evaluations` row means a later policy-file edit
  never silently reinterprets a historical evaluation — a reviewer can always determine which policy
  version produced a given historical PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE outcome.

**Negative:**
- Policy changes require a code deployment (a new YAML file on the classpath), not a live database
  edit — an intentional constraint (keeping every policy change in the same review/commit process as
  code), not a limitation the project failed to anticipate.
- The `check_key`-truthiness model cannot express compound conditions across multiple artifact keys
  in this iteration; a policy needing that expressiveness would need a design extension, not just a
  new YAML entry.

**Operational:**
- No new infrastructure — policy files are packaged classpath resources, loaded the same way any
  other Spring Boot configuration resource is loaded.
- A policy-file typo or malformed YAML would be caught at evaluation time (or ideally at a startup
  validation pass); this is a normal configuration-error class already familiar to Spring Boot
  deployments, not a novel operational risk this ADR introduces.

**Testing:**
- `PolicyEngineTest` validates the four-outcome evaluation model against representative
  `check_key`/context combinations.
- `PolicyExceptionTest` validates that `EXCEPTION_REQUESTED` outcomes require a fully populated
  `PolicyExceptionEntity` (reason, scope, approvingAuthority, compensatingControl, expiry) before
  persisting, per constitution Principle VI/Section 4.2.
- `WorkflowEnginePolicyEnforcementTest` validates that a mandatory `FAIL` actually blocks the
  dependent branch (Stage 11) end-to-end through the orchestration engine, not just at the
  `PolicyEngine` unit level.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13 — see
  `docs/governance/human-gates-log.md`. Because policy format directly implements a constitutional
  principle (VI), its acceptance carried the same governance weight as the other seven ADRs — no
  exemption for being "just a file format." **Corrected by the Task 10 corrective batch**
  (`codex-recommend.md` Finding #12): this bullet previously read "is proposed pending...
  accept/reject at Task 7" after Task 7 had already run and this ADR's own Status header above had
  already moved to Accepted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| A hand-rolled `check_key`-truthiness model cannot express compound, cross-artifact conditions | Acceptable at the ~4-policy prototype scale (`research.md` D7); flagged explicitly as a scaling limitation if the policy set grows materially — not silently assumed to scale indefinitely |
| A policy-file edit could unintentionally change the interpretation of already-recorded historical evaluations | Mitigated structurally: the evaluated file's `version` string is recorded on every `policy_evaluations` row, so a historical outcome is always attributable to the specific policy version that produced it |
| Malformed or invalid YAML on the classpath could cause a startup or evaluation-time failure | Standard Spring Boot classpath-resource failure mode; caught by the same `./mvnw clean verify` context-load discipline already applied to every other configuration resource in this project (see ADR-001's Validation section) |

## Reversibility

**Moderate.** `PolicyEngine.evaluate()`'s external contract — a `List<PolicyCheckResult>` derived
from a stage name plus a context map — would not need to change if the backing format were swapped
later (e.g., to Option B or C); only the internal loading/parsing mechanism would change. Existing
policy files' `id`/`domain`/`stage`/`check_key` shape could be migrated into a database schema or a
rules-engine DSL without changing how `WorkflowEngine` or `ReleaseReadinessStage` consume the
engine's output.

## Traceability

- Requirements: REQ-020 (release-readiness outcome), REQ-021 (security), REQ-030 (change safety —
  the versioned-recording requirement).
- Specification sections: `spec.md` Section 6.2 (REQ-020), Section 7 (REQ-021), constitution
  Principle VI (versioned policy guardrails, four-outcome model, exception requirements).
- Plan sections: `plan.md` Section 3.2 (Stage 10 node table row: "Resolve every applicable policy
  check to PASS/FAIL/EXCEPTION_REQUESTED/NOT_APPLICABLE"), Section 9 (Compliance and Change
  Control), Section 12 (Technology Decisions, ADR-007 row), `research.md` D7.
- Expected task identifiers: the runbook's policy-engine implementation and test tasks
  (`PolicyEngineTest`, `PolicyExceptionTest`, `WorkflowEnginePolicyEnforcementTest`).

## Validation

`PolicyEngineTest` validates the evaluation function against representative policy/context
combinations covering all four outcomes. `PolicyExceptionTest` validates that an
`EXCEPTION_REQUESTED` outcome cannot persist without a fully populated exception record (reason,
scope, approvingAuthority, compensatingControl, expiry) — a direct test of constitution Principle
VI's exception requirements. `WorkflowEnginePolicyEnforcementTest` validates the end-to-end
enforcement path: a mandatory `FAIL` actually blocks Stage 11 from proceeding, exercised through the
orchestration engine rather than the policy engine in isolation.
