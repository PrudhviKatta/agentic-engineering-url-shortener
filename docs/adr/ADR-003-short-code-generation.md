# ADR-003: Short-Code Generation Strategy

## Status

**Accepted** — drafted 2026-09-13 at Task 7 (the ADR Generation Gate), formalizing the position
`plan.md` Section 12 and `research.md` D3 already recorded as "Proposed for ADR at Task 7." Reviewed
and accepted by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13, per the project's
guardrail (guide Section 29, "Do Not Allow Claude to Approve Its Own Decisions") that only the human
candidate may move this to Accepted or Rejected.

## Context

`spec.md` REQ-003 requires every active short code be unique among currently active mappings at
creation time, and CLR-3 (Section 13.1) confirms the short code's testable *properties* — exactly
7 characters, base62 (`[a-zA-Z0-9]`) — while explicitly deferring the generation *algorithm* itself
to this ADR ("the concrete generation algorithm ... is not decided here — that is a Section 8
deferred technology/implementation decision, forward-pointed to ADR-003 at the plan stage"). CLR-4
additionally confirms all short codes are always system-generated (no caller-supplied aliases are
in scope), which removes any need to reconcile this algorithm with adversarial caller-chosen input.
REQ-008 further requires that short-code uniqueness survive concurrent creation requests without
corrupting or cross-assigning a mapping.

## Decision Drivers

- Must never surface a collision as a successful creation (REQ-003 negative criterion) — a
  collision must be structurally impossible, not merely retried until avoided.
- Must remain correct under concurrent creation load (REQ-008) without a database-level retry loop
  or advisory lock beyond what the persistence layer already provides for primary-key uniqueness.
- Must be simple enough to implement, test, and audit within the 2–3 day timebox.
- Must be auditable/predictable for a reviewer reading the source, not a probabilistic argument
  ("collision probability is negligible at N codes") that a reviewer has to trust rather than
  verify.
- Must satisfy the confirmed 7-character base62 format (CLR-3) without constraining a future
  algorithm change (the `code` column's shape must not be coupled to the generation mechanism).

## Options Considered

### Option A: Base62 encoding of an auto-increment integer primary key (selected)
- **Approach:** `short_links.id` is an `IDENTITY`-strategy auto-increment PK; the short code is a
  base62 encoding of that integer, computed once at insert time.
- **Advantages:** deterministic and collision-free *by construction* — uniqueness follows directly
  from the primary key's own uniqueness guarantee (already enforced by the database), not from a
  separate probabilistic argument. No retry loop is needed under concurrent load: two concurrent
  inserts simply receive two different auto-increment values, by definition. Trivial to test
  (`ShortCodeGeneratorTest` is a pure function of an integer). Fully auditable — a reviewer can
  read the encoding function and verify uniqueness follows from PK uniqueness, with no probability
  math required.
- **Disadvantages:** short codes are sequential/guessable in relative order — an attacker who
  observes one recently issued code can infer roughly how many links exist (an information-leak
  trade-off, disclosed, not hidden).
- **Risks:** single-writer sequence is a scaling constraint at real production volume (not a
  concern at demonstration scale; documented as backlog, not silently assumed away).
- **Implementation impact:** requires the PK to be assigned (a DB round-trip) before the code can
  be computed; the code column still carries its own `UNIQUE` constraint as a defense-in-depth
  check, not the primary uniqueness mechanism.
- **Assessment implications:** simplest option that is still fully testable and auditable;
  demonstrates picking the mechanism whose correctness argument is a database guarantee rather than
  a mitigated probability.

### Option B: Random generation with collision-retry
- **Approach:** generate a random 7-character base62 string; on `UNIQUE` constraint violation,
  retry generation.
- **Advantages:** short codes are not sequential/guessable, closing the enumeration information
  leak that Option A discloses.
- **Disadvantages:** collision handling becomes a retry loop that must itself be bounded, tested,
  and reasoned about under concurrent load (REQ-008) — introducing exactly the kind of "detected
  collision must never surface as a successful creation" risk surface REQ-003's negative criterion
  is written to guard against, where Option A has none by construction. Requires a documented
  collision-probability argument (birthday-paradox reasoning over the ~3.5 trillion 7-char base62
  space) that a reviewer must trust rather than verify structurally.
- **Risks:** a retry loop that is not correctly bounded/tested could theoretically stall or, worse,
  silently drop a creation under adversarial or high-volume conditions — a materially larger
  surface for a subtle bug than Option A's zero-retry design.
- **Implementation impact:** requires a `SecureRandom`-backed generator, a bounded retry wrapper
  (itself needing its own test coverage distinct from `RetryExecutor`'s stage-level retry, since
  this is a persistence-layer retry, not a stage-level one), and a documented collision-probability
  rationale.
- **Assessment implications:** more defensible against enumeration, but disproportionate
  implementation and test surface for a 2–3 day prototype relative to what REQ-003/REQ-008 actually
  require to pass.

### Option C: Hash-based (e.g., truncated SHA-256 of the long URL + salt)
- **Approach:** derive the short code from a cryptographic hash of the destination URL (plus a
  salt/counter to handle CLR-2's confirmed no-dedup rule, since the same URL must still produce a
  *new* independent code each time per CLR-2).
- **Advantages:** no dependency on the PK's assignment timing; conceptually decouples code
  generation from storage.
- **Disadvantages:** still needs a collision-retry fallback (hash truncation is not injective), so
  it inherits Option B's collision-handling complexity while adding hash-computation overhead for
  no corresponding benefit in this domain; the salt/counter needed to satisfy CLR-2 (no URL-based
  dedup) largely reduces to "hash of an already-unique input," which is more complex than just
  encoding that unique input (the PK) directly, as Option A does.
- **Risks:** same collision-handling risk surface as Option B, plus added complexity for a benefit
  (URL-independence) this domain does not need — CLR-2 has already rejected URL-based deduplication
  as a design goal.
- **Implementation impact:** highest of the three options for no corresponding requirement benefit.
- **Assessment implications:** would read as over-engineering relative to what CLR-2/REQ-003 ask
  for.

## Decision

**Base62 encoding of `short_links.id`**, an `IDENTITY`-strategy auto-increment integer primary key,
computed once after the row's PK is assigned and stored in the `code` column (which retains its own
`UNIQUE` constraint as defense-in-depth, not as the primary uniqueness mechanism).

## Rationale

Option A is the only option whose uniqueness guarantee is structural (inherited directly from the
primary key) rather than probabilistic-and-retried. This satisfies REQ-003's negative criterion —
"a detected collision never surfaces as a successful creation" — trivially, because there is no
collision-detection path to reason about at all: two rows cannot share a PK, therefore they cannot
share a code. It also satisfies REQ-008 (concurrent correctness) for free, since the database's own
PK-assignment mechanism is already the thing enforcing concurrent-safe uniqueness; no additional
locking or retry logic needs to be introduced, tested, or audited specifically for short-code
generation. The disclosed information-leak trade-off (sequential codes are order-guessable) is
real but out of REQ-003/REQ-008's scope — those requirements govern uniqueness and concurrent
correctness, not unguessability — and is explicitly carried forward as a documented consequence
rather than silently accepted.

## Consequences

**Positive:**
- Zero collision-handling code path exists to write, test, or have a latent bug in.
- `ShortCodeGeneratorTest` is a pure, deterministic unit test of an integer-to-base62 function —
  no concurrency harness needed to prove REQ-003 at the generation-algorithm level (concurrency
  correctness is proven instead by the persistence layer's PK-uniqueness guarantee under
  `@DataJpaTest`/API contract concurrency tests).
- Fully auditable: a reviewer verifies correctness by reading one small function plus the schema's
  PK constraint, not by evaluating a probability argument.

**Negative:**
- Short codes are sequential/guessable in relative order: an attacker who knows one recently issued
  code can infer roughly how many links exist. This is a genuine, disclosed information-leak
  trade-off (`research.md` D3), not an oversight.
- Enumeration of the code space (guessing adjacent codes to discover other callers' links) is not
  prevented by code unpredictability in this design; per `plan.md` Section 8, it would need to be
  prevented by a future rate-limiting/monitoring control instead — itself backlog per CLR-11.

**Operational:**
- Code generation is coupled to the PK-assignment round-trip; no separate generation service or
  cache is needed, keeping the operational footprint at zero beyond the existing H2/JPA path (ADR-
  002).

**Testing:**
- `ShortCodeGeneratorTest` (pure function, Task 16) plus REQ-003's N-way concurrency test (Task
  12/17 persistence + API contract tests) are sufficient — no dedicated collision-retry test suite
  is needed, unlike Options B/C.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13, per the same
  governance standing as ADR-001/002 (no self-approval) — see `docs/governance/human-gates-log.md`.
  **Corrected by the Task 10 corrective batch** (`codex-recommend.md` Finding #12): this bullet
  previously read "is proposed pending... accept/reject at Task 7" after Task 7 had already run and
  this ADR's own Status header above had already moved to Accepted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Sequential codes allow relative-volume inference and enumeration by an attacker who obtains one code | Disclosed explicitly in this ADR and `plan.md` Section 8; enumeration-class mitigation (rate limiting/monitoring) is deferred to backlog per CLR-11, not silently assumed unnecessary |
| Single-writer auto-increment sequence is a scaling constraint at real production volume | Documented as backlog (`research.md` Backlog section); not a concern at demonstration scale and not silently assumed away |
| A future requirement for unguessable codes would require revisiting this ADR | Reversibility (below) — the `code` column's shape is unaffected, so the change is localized to the generation function |

## Reversibility

**Moderate.** Changing the generation algorithm later (e.g., to Option B or C) does not change the
`code` column's shape (still 7-character base62) or any consumer-facing API contract — only the
internal generation function inside the creation path changes. No migration of already-issued codes
would be required since existing rows keep their already-assigned codes; only newly created rows
would use the new algorithm.

## Traceability

- Requirements: REQ-003 (short-code uniqueness), REQ-008 (concurrent requests).
- Clarifications: CLR-3 (short-code format: 7-char base62 — this ADR selects the algorithm CLR-3
  explicitly deferred), CLR-4 (system-generated-only, removing adversarial-input concerns).
- Specification sections: `spec.md` Section 6.1 (REQ-003, REQ-008), Section 8 (Deferred Technology
  Decisions, forward-pointing to this ADR), Section 13.1 (CLR-3, CLR-4).
- Plan sections: `plan.md` Section 12 (Technology Decisions, ADR-003 row), Section 13
  (Traceability, REQ-001–REQ-003 row citing ADR-003), `research.md` D3.
- Expected task identifiers: Task 16 (`ShortCodeGeneratorTest`), Tasks 12/17 (persistence + API
  contract concurrency tests).

## Validation

`ShortCodeGeneratorTest` validates the encoding function is deterministic and produces valid
7-character base62 output for representative integer inputs (including boundary values). REQ-003's
N-way concurrency test (Task 12/17) validates that concurrent creation requests never produce a
duplicate active code, by exercising the real persistence path rather than the generation function
in isolation — proving the structural uniqueness argument holds end-to-end, not just at the unit
level.
