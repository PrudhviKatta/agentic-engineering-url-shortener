# ADR-009: Rollback vs. Compensation Mechanism

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D9 already recorded as "Proposed for ADR at Task 7." Reviewed and accepted by the
human candidate (Prudhvidhar Reddy Katta) on 2026-09-13.

## Context

Constitution Principle VIII requires that rollback and compensation be distinguished, never
conflated. REQ-015 makes this a functional requirement: the system MUST recover from a failed stage
using rollback (undo of a reversible, not-yet-committed effect) or compensation (a corrective,
forward action for an already-committed external effect) — never attempting to "roll back" an
effect that has already been externally observed, and never leaving a partially applied effect with
neither rollback nor compensation performed. CLR-14 sharpens this with a concrete, confirmed
rollback-impossible case: once a redirect has been served to a caller, that delivery is a completed,
externally observed effect and can never be rolled back — recovery for a problem discovered after
must use compensation (e.g., an incident/audit note or a corrective follow-up action), never a
rollback attempt against the already-served redirect. This ADR decides the concrete mechanism that
implements this distinction inside the orchestration engine.

## Decision Drivers

- Must never conflate rollback and compensation into a single mechanism (REQ-015's negative
  criterion, constitution Principle VIII).
- Must correctly classify a stage's own effect as rollback-eligible (never left the transaction
  boundary) or compensation-eligible (already externally observed) — CLR-14's redirect example is
  the canonical case this classification must get right.
- Must never leave a partially applied effect with neither recovery mechanism performed — a stage
  that fails must always reach either a rollback or a compensation outcome, not silently do nothing.
- Should keep the cheaper, simpler recovery path (transaction-abort rollback) available for
  genuinely reversible effects rather than always compensating even when rollback would suffice.

## Options Considered

### Option A: Treat all stage effects as rollback-eligible
- **Approach:** on any stage failure, attempt to undo the effect via transaction abort or an
  explicit undo operation, regardless of whether the effect was already externally observed.
- **Advantages:** a single, simple recovery mechanism to implement.
- **Disadvantages:** directly conflates rollback and compensation, which REQ-015's negative
  criterion explicitly forbids; incorrect for CLR-14's confirmed case — a served redirect cannot be
  "undone" by any rollback mechanism, since the caller has already received and potentially acted on
  it. Attempting to "roll back" an already-observed effect would be either a no-op that misleadingly
  reports success, or an attempt to fabricate an undo that does not actually reverse what the caller
  already experienced.
- **Risks:** would produce audit records claiming a rollback occurred for an effect that, in
  reality, cannot be undone — a direct violation of REQ-019's audit-integrity requirement (never
  treating a narrative summary, or in this case a mischaracterized recovery record, as a substitute
  for what actually happened).
- **Rejected** — incorrect by construction against REQ-015/CLR-14.

### Option B: Distinguish in-memory/DB effects (rollback) from externally-visible effects (compensation) — selected
- **Approach:** a `CompensationRegistry` maps a stage name to a registered
  `Consumer<Map<String,Object>>` compensating action. A stage whose effect never left the
  transaction boundary rolls back for free (the JPA transaction simply isn't committed). A stage
  whose effect was already externally observed (the canonical example: a redirect already served to
  a caller, CLR-14) runs its registered compensation instead — logging an incident/audit note or an
  equivalent corrective action, never attempting to "undo" the already-observed effect.
- **Advantages:** directly implements CLR-14's confirmed rule and REQ-015's distinction without
  conflating them; rollback is free (no extra code) for the common case of a purely internal,
  not-yet-committed effect, while compensation is explicit and auditable for the externally-visible
  case; a stage author must actively classify their own effect by choosing whether to register a
  compensating action, making the classification decision visible in code rather than implicit.
- **Disadvantages:** relies on each stage author correctly classifying their own effect — an opt-in
  design where a stage that forgets to register a compensating action and later needs one falls
  through to a different code path (see Rationale) rather than silently doing nothing.
- **Risks:** a misclassification (treating an externally-visible effect as rollback-eligible, or
  vice versa) would only surface at test time.
- **Implementation impact:** `CompensationRegistry.register(stageName, action)` /
  `CompensationRegistry.run(stageName, context)`; `WorkflowEngine` calls `run` for a failed stage
  whose effect requires compensation, or simply aborts the transaction for a stage whose effect does
  not.
- **Assessment implications:** directly satisfies the constitutional distinction (Principle VIII)
  and its concrete confirmed instance (CLR-14) as a first-class, testable mechanism.

### Option C: Always compensate, never roll back
- **Approach:** treat every stage failure as requiring a registered compensating action, even for
  effects that never left the transaction boundary.
- **Advantages:** a single mechanism (compensation) to implement and reason about; no risk of
  misclassifying an effect as "rollback-eligible" when it should have been compensated.
- **Disadvantages:** loses the cheaper rollback path for genuinely reversible effects — every stage
  handler would need to author a compensating action even for effects a database transaction abort
  already correctly undoes for free, adding unnecessary implementation burden with no corresponding
  correctness benefit; also somewhat conflates the two concepts in the other direction (treating a
  reversible effect as if it were an already-observed one), working against the spirit of REQ-015's
  distinction even though it does not produce an incorrect audit claim the way Option A does.
- **Risks:** unnecessary implementation and test surface for stages whose effects are trivially
  rollback-eligible.
- **Rejected** — unnecessarily conflates the two recovery paths in the opposite direction from
  Option A, and adds implementation burden without a corresponding requirement benefit.

## Decision

**Option B: a `CompensationRegistry` distinguishing rollback-eligible (in-transaction, not-yet-
committed) effects from compensation-eligible (already externally observed) effects.** A stage
whose effect never left the transaction boundary rolls back via JPA transaction abort; a stage whose
effect was already externally observed runs its registered compensating action instead.

## Rationale

Option B is the only option that correctly implements REQ-015's rollback/compensation distinction
without conflating the two in either direction: Option A incorrectly claims an already-observed
effect can be rolled back (directly violating CLR-14's confirmed rule and risking a misleading audit
record), while Option C discards the legitimately free rollback path for reversible effects, adding
implementation burden with no correctness benefit. `CompensationRegistry`'s opt-in registration model
makes each stage author's classification decision an explicit, reviewable piece of code (a
`register(...)` call, or its absence) rather than an implicit assumption — a reviewer can see exactly
which stages have registered a compensating action and infer that every other stage's effects are
being treated as rollback-eligible by omission, making the classification auditable rather than
buried in a runtime decision tree.

## Consequences

**Positive:**
- Directly and correctly implements CLR-14's confirmed rule: a served redirect's compensation path
  (logging an incident/audit note or an equivalent corrective action) is the only path taken for
  that effect — no rollback is ever attempted against it.
- Rollback is free (a database transaction simply is not committed) for the common case of a
  purely internal effect — no extra code needed per stage for that path.
- The classification decision (rollback-eligible vs. compensation-eligible) is visible in source as
  an explicit registration call, supporting Principle IX's audit-grade-evidence goal at the design
  level, not only the runtime-evidence level.

**Negative:**
- Requires each stage handler that produces an externally-visible effect to explicitly register its
  compensating action — an opt-in design, so a stage that forgets to register one and later needs
  compensation instead falls through to safe-stop (rather than silently doing nothing) — this
  fail-safe behavior is itself the intended design, not a gap, but it does mean a missing
  registration produces a safe-stop rather than a successful compensation, which a reviewer must
  understand is deliberate.

**Operational:**
- `CompensationRegistry` is a plain in-process registry (a map from stage name to action) — no
  additional infrastructure, consistent with ADR-004/ADR-005's no-external-infrastructure stance.
- Compensating actions run synchronously as part of the same recovery path `WorkflowEngine` already
  drives for retry/safe-stop, so no separate compensation-processing loop or queue is needed.

**Testing:**
- `CompensationRegistryTest` validates the registry's `register`/`run` contract in isolation.
- `WorkflowEngineReliabilityIntegrationTest` and `WorkflowEngineSafeStopTest` (Task 32/33 per
  `research.md` D9) exercise one rollback-eligible and one compensation-eligible scenario each,
  specifically covering CLR-14's served-redirect case for the compensation path and a purely
  in-transaction effect for the rollback path.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13, per the same
  governance standing as the other seven ADRs — see `docs/governance/human-gates-log.md`.
  **Corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): this bullet
  previously read "is proposed pending... accept/reject at Task 7" after Task 7 had already run and
  this ADR's own Status header above had already moved to Accepted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| A stage author misclassifies an effect (treats an externally-visible effect as rollback-eligible, forgetting to register compensation) | Would only surface at test time; Task 32/33's tests explicitly cover one of each case (rollback-eligible and compensation-eligible), and a missing registration for an effect that actually needs compensation results in a safe-stop (a visible failure), not a silent incorrect success |
| A registered compensating action itself fails while running | Falls through to the same safe-stop path (`SafeStopException` → `WorkflowStatus.SAFE_STOPPED`, CLR-13) as an exhausted retry — there is no third, divergent failure path for a failed compensation, per Task 34's convergence check |
| Audit records could mischaracterize which recovery mechanism actually ran | `workflow_events` records the specific mechanism (`"retry"` \| `"compensation"` \| `"rollback"`, per `plan.md` Section 7.2's `RecoveryEvent.mechanism` field) rather than a generic "recovered" flag |

## Reversibility

**High.** `CompensationRegistry`'s contract (`register`/`run`, keyed by stage name) would not need
to change under a different registration mechanism later (e.g., annotation-driven registration
instead of explicit calls) — the external contract other components depend on (`WorkflowEngine`
calling `run` for a given stage name) stays stable regardless of how registration itself is
authored.

## Traceability

- Requirements: REQ-015 (rollback or compensation), REQ-016 (safe-stop — the convergent path for a
  failed compensation), REQ-019 (audit inspection — recording which mechanism ran).
- Clarifications: CLR-14 (confirmed rollback-impossible case — a served redirect).
- Specification sections: `spec.md` Section 6.2 (REQ-015), Section 13.1 (CLR-14), constitution
  Principle VIII (rollback/compensation distinction).
- Plan sections: `plan.md` Section 3.4 (rollback/compensation cross-cutting state), Section 6
  (Reliability table — Rollback/Compensation rows), Section 7.2 (MTTR mechanics — `RecoveryEvent.
  mechanism` field), Section 12 (Technology Decisions, ADR-009 row), Section 13 (Traceability,
  REQ-015 row citing ADR-009), `research.md` D9.
- Expected task identifiers: Tasks 32–34 (compensation registry implementation and convergence-
  check tests, per the runbook).

## Validation

`CompensationRegistryTest` validates the registry's registration and invocation contract in
isolation. `WorkflowEngineReliabilityIntegrationTest` validates one rollback-eligible scenario (a
purely in-transaction effect correctly rolls back via transaction abort with no compensating action
invoked) and one compensation-eligible scenario (CLR-14's served-redirect case correctly invokes its
registered compensating action, with no rollback attempted against the already-served redirect).
`WorkflowEngineSafeStopTest` validates that a stage requiring compensation but lacking a registered
action correctly falls through to safe-stop rather than silently succeeding.
