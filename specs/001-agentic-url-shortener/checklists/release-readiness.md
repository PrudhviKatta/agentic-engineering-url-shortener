# Release Readiness Quality Checklist

**Category 18 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-020, `ReleaseReadinessStage` (Stage 11), constitution Section 4.6.
**Constitution principles:** VI (Compliance and Change-Control), III (Human Governance — release
readiness is human-owned sign-off), XI (Evidence-Based Completion).
**Status:** All items UNVERIFIED/PENDING — `ReleaseReadinessStage` does not exist yet.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified (recursively: an unmet item on
  *this* checklist is itself a release-readiness blocker). **[OPTIONAL]** does not, but must be
  disclosed.

## Items

- **[MANDATORY] RR-CHK-001** — Verify release readiness is computed as a boolean/small-enumeration
  outcome derived from the *actual current* state of every applicable policy check, test result, and
  open exception — never a subjective judgment call asserted at the end. **Fail if**: a
  release-readiness report contains a "ready" claim not traceable to specific policy/test/exception
  records from the same run. **Cites**: constitution Section 4.6, REQ-020.

- **[MANDATORY] RR-CHK-002** — Verify any mandatory policy check (`SEC-001`, `SEC-002`, `COMP-001`)
  in a `FAIL` state blocks release readiness outright, with no override path other than a fully
  recorded, human-approved exception. **Fail if**: a mandatory-`FAIL` run reports "ready." **Cites**:
  constitution Section 4.6, REQ-020; primary detection in `compliance.md` CHK-COMP-004.

- **[MANDATORY] RR-CHK-003** — Verify any `EXCEPTION_REQUESTED` that has not received recorded human
  approval blocks release readiness as if it were a `FAIL` — never treated as "pending, but okay for
  now." **Fail if**: an unapproved exception state coexists with a "ready" outcome. **Cites**:
  constitution Section 4.6.

- **[MANDATORY] RR-CHK-004** — Verify any non-waivable principle (I, III, IV, XI) found violated
  blocks release readiness and cannot be overridden by any exception, regardless of who approves it.
  **Fail if**: a release-readiness report shows "ready" alongside a disclosed violation of a
  non-waivable principle. **Cites**: constitution Section 4.3, Section 4.6.

- **[MANDATORY] RR-CHK-005** — Verify any waivable principle (II, V, VI, VII, VIII, IX, X) violated
  without a properly recorded, approved, unexpired exception blocks release readiness. **Fail if**:
  a known violation of one of these principles exists with no exception record, yet release
  readiness reports "ready." **Cites**: constitution Section 4.6.

- **[MANDATORY] RR-CHK-006** — Verify evidence gaps under Principle XI (missing test execution,
  missing approval record, missing traceability link) are treated as non-compliance for the specific
  item they concern — not as a documentation nice-to-have that release readiness can proceed past.
  **Fail if**: a release-readiness "ready" outcome coexists with a REQ that has no test-execution
  evidence. **Cites**: constitution Section 4.6 (closing bullet), Principle XI.

- **[MANDATORY] RR-CHK-007** — Verify release readiness is re-evaluated whenever the set of
  applicable checks changes (new code, new policy version, an exception expiring) — not treated as a
  one-time gate that, once passed, is assumed to still hold indefinitely. **Fail if**: a "ready"
  determination from an earlier point in time is still relied upon after a policy version bump or an
  exception's expiry, with no re-evaluation. **Cites**: constitution Section 4.6 (closing paragraph).

- **[MANDATORY] RR-CHK-008 [ANTI-PATTERN: unsupported completion claims]** — Verify a
  release-readiness "ready" outcome is never produced without an evidence trail supporting it — the
  outcome must cite the specific policy/test/exception state that produced it, per REQ-020's
  negative criterion, not assert readiness as a standalone claim. **Fail if**: a "ready" outcome's
  supporting evidence cannot be independently reproduced by querying the same persisted state.
  **Cites**: REQ-020 (negative criterion), Principle XI.

- **[MANDATORY] RR-CHK-009** — Verify REQ-020's combined-gate design (CLR-16: one gate covering both
  the orchestration engine and the URL-shortener application) is implemented as literally one
  synchronization outcome, not two independently-reported gates that a reviewer must manually
  reconcile. **Fail if**: two separate "ready" signals exist for the two halves of the system with
  no single combined outcome. **Cites**: CLR-16, REQ-020.

- **[MANDATORY] RR-CHK-010** — Verify a release-readiness test exists covering both a passing run
  (all mandatory checks `PASS`/`NOT_APPLICABLE`, all tests green) and a run blocked by an unresolved
  exception or a failing mandatory policy — per REQ-020's Evidence pointer. **Fail if**: only the
  passing-run case is tested. **Cites**: REQ-020 (Evidence).

- **[MANDATORY] RR-CHK-011** — Verify security release-readiness inputs (dependency-vulnerability
  scan, secret scan) are attached to the release-readiness evidence and are clean or carry a
  recorded, approved exception before any "ready" outcome. **Fail if**: no scan evidence is attached
  to a "ready" determination. **Cites**: Principle V, REQ-021; cross-reference `security.md`
  CHK-SEC-012.

- **[MANDATORY] RR-CHK-012** — Verify the final release-readiness decision itself remains
  human-owned sign-off (Principle III) — an agent may compute and present the derived outcome, but
  the decision to actually present the system for final review belongs to a human, recorded as
  such. **Fail if**: the system is presented as "ready for review" with no recorded human sign-off
  distinct from the agent-computed outcome. **Cites**: Principle III (human-ownership list, "release
  readiness... and final quality sign-off").
