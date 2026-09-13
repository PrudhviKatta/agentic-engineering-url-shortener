# Human Approvals Quality Checklist

**Category 6 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-013, ADR-006 (Human Approval Mechanism), the
`POST /workflows/{id}/approvals/{step}` endpoint, `PolicyExceptionEntity`'s approval fields.
**Constitution principles:** III (Human Governance) — the primary principle this file enforces.
**Status:** All items UNVERIFIED/PENDING — the approval endpoint and gate mechanics are not yet
implemented.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] APPR-CHK-001** — Verify every point in the orchestration graph carrying
  `entryApproval=true` or `exitApproval=true` (Design/Stage 6, Release-readiness/Stage 11, per
  `plan.md` Section 5) is a formal, pre-declared Approval Gate with defined approval criteria,
  rejection criteria, escalation path, and timeout-behavior — not an informal checkpoint discovered
  only by reading code. **Fail if**: a gate exists in code with no corresponding entry in `plan.md`
  Section 5's Human-in-the-Loop table. **Cites**: Principle III, REQ-013, plan.md Section 5.

- **[MANDATORY] APPR-CHK-002 [ANTI-PATTERN: missing approval enforcement]** — Verify a dependent
  branch cannot proceed past an unresolved mandatory Approval Gate under any code path, including a
  batch-advance, a retry, or a resumption. **Fail if**: any test or manual trace shows a
  dependent-of-a-gated-stage executing while the gate remains `WAITING_APPROVAL`/`PENDING_APPROVAL`.
  **Cites**: REQ-013 (negative criterion), Principle III, ADR-006 ("blocked-batch re-halt guard").

- **[MANDATORY] APPR-CHK-003 [ANTI-PATTERN: approval inferred from silence]** — Verify no timeout,
  absence of a response, or elapsed duration on a pending gate ever resolves to an implicit
  "approved" state. Verify the `POST /workflows/{id}/approvals/{step}` endpoint requires an explicit
  `approved`/`rejected` decision field with no default value, and that a gate left unanswered stays
  in `WAITING_APPROVAL` (safe-stop) indefinitely rather than auto-resolving. **Fail if**: any code
  path transitions a gate to an approved-equivalent state without an explicit decision record
  bearing a human actor. **Cites**: Principle III ("Absence of a human response is not approval
  under any circumstance"), REQ-013 (negative criterion), ADR-006.

- **[MANDATORY] APPR-CHK-004** — Verify every recorded approval/rejection decision captures actor,
  decision, rationale (`reason`), and timestamp (`approvals` table: `decision`/`decided_by`/
  `reason`/`decided_at`), and that a decision with a missing `reason` is rejected by the endpoint
  rather than persisted with a blank rationale. **Fail if**: the endpoint accepts a request with no
  `reason` field, or persists one. **Cites**: REQ-013, REQ-019, Principle IX, ADR-006.

- **[MANDATORY] APPR-CHK-005** — Verify the `entryApproval` vs. `exitApproval` distinction (ADR-004,
  ADR-006) is implemented correctly and differently: an `entryApproval` rejection means the
  handler never runs (no partial output produced); an `exitApproval` approval releases
  already-produced output to dependents without re-running the stage handler. **Fail if**: approving
  an `exitApproval` gate causes the stage handler to re-execute (violating REQ-017's
  no-double-execution rule via a different code path), or an `entryApproval` gate's handler is found
  to have run before approval was recorded. **Cites**: ADR-004, ADR-006, REQ-017 (negative
  criterion).

- **[MANDATORY] APPR-CHK-006** — Verify an agent can never call `approve()`/`reject()` on its own
  authority against any gate the constitution designates human-owned — the endpoint's `actor` field
  being agent-attributed for a human-owned gate is a defect, not a valid input. **Fail if**: a test
  or code review finds a code path where an agent role, not a human actor, resolves a mandatory
  gate. **Cites**: Principle III, REQ-013 (negative criterion).

- **[MANDATORY] APPR-CHK-007** — Verify `WorkflowEngine.replan()` completes (and its replacement
  stages are registered) before `approve()` is called when a gate's resolution is "replace what
  comes next," per the fixed ordering rule ADR-004/ADR-006 establish. **Fail if**: a test can
  trigger the reverse order and observe execution resuming against a graph that does not yet include
  the intended replacement. **Cites**: ADR-004, ADR-006, REQ-013, REQ-018.

- **[MANDATORY] APPR-CHK-008** — Verify REQ-013's evidence obligation is met: an approval-gate test
  exists covering all three paths — approve, reject, and unanswered/timeout — not only the approve
  happy path. **Fail if**: the test suite for REQ-013 lacks a reject-path or timeout/non-response
  test. **Cites**: REQ-013 (Evidence), Principle IV.

- **[MANDATORY] APPR-CHK-009** — Verify a policy `EXCEPTION-REQUESTED` outcome only becomes
  permitted-to-proceed via this same human-approval mechanism (a `PolicyExceptionEntity` grant with
  a recorded `approvingAuthority`), never via an agent deciding the failure is acceptable. **Fail
  if**: any code path allows an `EXCEPTION-REQUESTED` policy outcome to unblock progression without
  a `PolicyExceptionEntity` row being created and populated. **Cites**: Principle VI, REQ-013;
  cross-reference `compliance.md` CHK-COMP-004.

- **[OPTIONAL] APPR-CHK-010** — Verify a human reviewer can discover a pending gate without a
  notification/paging mechanism (a disclosed simplification per `plan.md` Section 5/ADR-006's
  Operational consequences) by querying `GET /workflows/{id}` and seeing an explicit
  `PENDING_APPROVAL`/`WAITING_APPROVAL` status — i.e., inspection is a viable discovery path even
  without push notification. **Fail if**: the only way to know a gate is pending is to have been
  told out-of-band. **Cites**: REQ-012, ADR-006 (Operational).

- **[MANDATORY] APPR-CHK-011** — Verify the Human Gate 2 and Human Gate 3 records in `spec.md`
  (Sections 12–13) themselves follow this same non-silence standard: each records an explicit human
  approver's name and date, not an assumed sign-off. **Fail if**: a Human Gate section in `spec.md`
  or the constitution is treated as "passed" with unchecked boxes and no named approver. **Cites**:
  Principle III, spec.md Section 12, constitution Section 6.
