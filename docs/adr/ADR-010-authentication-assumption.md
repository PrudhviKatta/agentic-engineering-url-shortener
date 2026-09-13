# ADR-010: Authentication Assumption

## Status

**Accepted** — drafted 2026-09-13 at Task 7, formalizing the position `plan.md` Section 12 and
`research.md` D10 already recorded as "Proposed for ADR at Task 7." **This ADR documents a residual
security risk, not a closed, "fine as-is" matter** — see Risks and Mitigations and Consequences
below. The human candidate (Prudhvidhar Reddy Katta) reviewed this specific risk — not a routine
technology choice under constitution Principle III — and explicitly accepted it on 2026-09-13: no
authentication is enforced anywhere in this prototype, including the human-approval endpoint this
ADR governs alongside ADR-006.

## Context

`spec.md` CLR-10 (Section 13.1) already resolved, at the specification level, that no
authentication or authorization is enforced in this iteration — this promoted ASM-1 from an
assumption to a confirmed specification decision, made by the human candidate directly. This ADR
does not re-open that specification-level question; it formalizes the technology-level consequence
of that already-confirmed decision, and — separately — makes explicit exactly which endpoints that
consequence touches and how material the exposure is, since "no auth" has materially different
severity depending on which endpoint it applies to. Most significantly, ADR-006 established that the
single most security-sensitive endpoint in this system — `POST /workflows/{id}/approvals/{step}`,
the only path that releases a blocked branch and satisfies REQ-013's human-owned-gate requirement —
is, under this decision, also unauthenticated. That combination (the system's own governance
mechanism has no access control) is the specific fact this ADR exists to surface plainly, not to
soften.

## Decision Drivers

- Must match the specification's already-confirmed decision (CLR-10) — this ADR is not a venue to
  re-litigate that resolution, only to formalize its technology-level scope and consequences.
- Building real authentication was explicitly out of scope for this assessment's demonstrated
  capabilities (`spec.md` Section 9.6).
- The decision's severity is not uniform across endpoints — the approval-decision endpoint (ADR-006)
  is materially higher-risk unauthenticated than, say, the URL-shortener's own creation/resolution
  endpoints, and that asymmetry must be named explicitly, not flattened into a single "auth is out
  of scope" statement.
- Must not be silently accepted as "fine" — a residual risk requires an explicit, recorded human
  acknowledgment (constitution Principle III: humans own security-sensitive decisions), not a
  default the agent treats as self-evidently acceptable.

## Options Considered

### Option A: None enforced, documented as a trusted-caller-only assumption (selected)
- **Approach:** no authentication/authorization mechanism exists anywhere in the system. Every
  endpoint in `contracts/openapi.yaml` — including domain endpoints (`POST /links`, `GET /r/{code}`)
  and orchestration endpoints (`POST /workflows`, `POST /workflows/{id}/approvals/{step}`) — is
  callable by any caller who can reach the process. `actor`/`decidedBy`/`approvingAuthority` fields
  throughout `data-model.md` are self-asserted strings supplied in the request body, not verified
  identities.
- **Advantages:** matches the already-confirmed specification decision (CLR-10) exactly; avoids
  building a capability explicitly out of scope for this assessment (`spec.md` Section 9.6); no
  timebox spent on an authentication layer that would not itself be evaluated by this assessment's
  stated scope.
- **Disadvantages:** every endpoint, including the approval-decision endpoint (the system's own
  governance/gate-release mechanism), is unauthenticated; a real deployment of this exact codebase
  would be trivially able to have its approval gates released by any caller, not only an authorized
  reviewer.
- **Risks:** in a real deployment, the unauthenticated approval endpoint would be the highest-
  severity finding in this design — explicitly flagged here, not discovered later by a reviewer
  performing an independent security review.
- **Implementation impact:** none — this is the absence of a control, not a built mechanism.
- **Assessment implications:** matches the specification's own confirmed scope; the residual risk is
  disclosed rather than hidden, which is itself part of what this assessment evaluates (constitution
  Principle IX, audit-grade evidence, applied to the project's own limitations, not only its
  successes).

### Option B: A minimal API-key header check
- **Approach:** require a shared-secret API key header on every request; reject requests missing or
  presenting an invalid key.
- **Advantages:** closes the most acute exposure (an unauthenticated approval endpoint) with minimal
  implementation cost; would not require a full identity/authorization system.
- **Disadvantages:** directly contradicts CLR-10's already-confirmed specification decision that no
  authentication is enforced in this iteration — implementing this without a specification amendment
  would be the controlling agent unilaterally overriding a human-owned decision (constitution
  Principle III), which this project's own governance model (see ADR-001's "Process note" on the
  original Python-to-Java pivot) treats as a process violation to disclose, not a shortcut to take
  quietly. A shared API key also does not provide per-caller identity — `actor`/`decidedBy` would
  still be self-asserted even with a key check in place, only gated by possession of the shared
  secret rather than verified identity.
- **Risks:** would require its own key-distribution/rotation story, itself an unscoped design
  question this assessment's timebox does not budget for.
- **Implementation impact:** a request filter/interceptor plus configuration for the shared key —
  small, but out of scope per CLR-10.
- **Assessment implications:** **rejected**, specifically because implementing it would contradict an
  already-confirmed specification-level human decision (CLR-10), not because the mechanism itself is
  technically unreasonable — the rejection reason is a governance one, not a technical one.

### Option C: Full OAuth2/OIDC integration
- **Approach:** integrate a real identity provider and enforce token-based authentication/
  authorization across all endpoints.
- **Advantages:** would provide verified caller identity, closing the self-asserted-`actor` risk
  entirely.
- **Disadvantages:** same governance objection as Option B (contradicts CLR-10) at far greater
  implementation cost; would require standing up or integrating an identity provider —
  infrastructure disproportionate to a local 2–3 day prototype, and explicitly out of this
  assessment's demonstrated-capabilities scope (`spec.md` Section 9.6).
- **Risks:** highest implementation/timebox risk of the three options for a capability this
  assessment does not ask to be demonstrated.
- **Implementation impact:** highest of the three options by a wide margin.
- **Assessment implications:** **rejected** for the same governance reason as Option B, at
  substantially higher cost.

## Decision

**Option A: no authentication or authorization is enforced anywhere in this iteration**, matching
CLR-10's already-confirmed specification decision. Every endpoint — including `POST /workflows/
{id}/approvals/{step}` — is trusted-caller-only. This is recorded as an explicit, disclosed
assumption requiring the human candidate's own acknowledgment of the residual risk at this ADR's
review, not merely a restatement of CLR-10 for its own sake.

## Rationale

CLR-10 already settled the specification-level question with the human candidate's direct
ownership; this ADR's job is not to re-decide that question but to make its full technology-level
consequence explicit and unavoidable at review time — specifically, that the consequence extends to
the approval-decision endpoint (ADR-006), the single mechanism this entire project relies on to
satisfy constitution Principle III's "humans own security-sensitive decisions" requirement. There is
a genuine tension worth naming directly: this project's orchestration engine exists specifically to
enforce that approvals are explicit, human-owned, and non-inferred (REQ-013), yet the endpoint that
records those approvals has no mechanism to verify the caller claiming to be a human reviewer
actually is one. Options B and C would close that gap but would do so by unilaterally overriding a
decision the human candidate already made explicitly (CLR-10) — a process violation this project's
own precedent (ADR-001's disclosed "process note" on the original stack pivot) treats as something
to surface, not to route around quietly. The correct resolution is Option A, formalized here with
the residual risk stated plainly, so that accepting this ADR is itself the human candidate's
explicit, informed acknowledgment of that specific risk — not a rubber-stamp of a assumption already
treated as closed.

## Consequences

**Positive:**
- No timebox spent building a capability explicitly out of scope for this assessment.
- Matches the specification's own confirmed decision exactly — no drift between what `spec.md` says
  and what the implementation does.

**Negative:**
- Every endpoint in `contracts/openapi.yaml` is trusted-caller-only, with no verification of caller
  identity anywhere in the system.
- `actor`/`decidedBy`/`approvingAuthority` fields throughout `data-model.md` are self-asserted
  strings, not verified identities — an audit record's "who" is only as trustworthy as the caller's
  own honesty in the request body.
- The approval-decision endpoint (ADR-006) — the system's own governance/gate-release mechanism —
  is, under this decision, callable by any reachable caller, not only an authorized reviewer. This
  is the single most material consequence of this ADR and is named explicitly here rather than left
  implicit in a general "no auth" statement.

**Operational:**
- No auth-related infrastructure (identity provider, key store, session management) to run, monitor,
  or patch — the operational footprint of this decision is exactly zero, which is also why the
  corresponding risk exposure is not zero: there is no compensating operational control (e.g.,
  network-level access restriction) documented as part of this design either. A reviewer running
  this prototype locally is the implicit compensating control in practice (only the reviewer's own
  machine can reach the process), but that is a deployment-topology fact, not a control this
  codebase itself provides or enforces.

**Testing:**
- N/A for this iteration — there is no auth mechanism to test. The backlog item is "when
  authentication is added, add an auth-bypass-attempt test," which is not testable before the
  mechanism it targets exists.

**Governance:**
- **Accepted** by the human candidate (Prudhvidhar Reddy Katta) on 2026-09-13 — see
  `docs/governance/human-gates-log.md`. Because this decision touches a security-sensitive control
  (or the absence of one) over the system's own governance mechanism (the approval endpoint),
  constitution Principle III's "humans own security-sensitive decisions" applied to this ADR's
  review with more weight than a typical technology
  choice — accepting it should be read as the human candidate explicitly accepting the residual risk
  named above, not as a formality.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| The approval-decision endpoint (the system's own human-approval enforcement mechanism) is itself unauthenticated | Explicitly named as the highest-severity residual risk in this design (this ADR, ADR-006 Consequences, `plan.md` Section 8); if a future iteration revisits authentication, this endpoint is documented as the top priority to secure first |
| `actor`/`decidedBy`/`approvingAuthority` audit fields are self-asserted, not verified | Disclosed explicitly in `data-model.md`'s table notes and this ADR; a reviewer inspecting the audit trail must understand these fields as claimed identity, not verified identity |
| A reviewer or future maintainer could mistake "no auth is documented" for "no auth is a problem" | This ADR's Status and Context sections state directly that this is a residual risk requiring explicit human acknowledgment, not a closed, self-evidently acceptable matter |
| Reintroducing authentication later could be done inconsistently across endpoints if not planned for | Reversibility (below) notes the additive nature of the likely future fix (a filter/interceptor layer), but explicitly flags the approval endpoint as the one that cannot wait if this is ever revisited |

## Reversibility

**Moderate.** Adding authentication later is additive — a filter/interceptor layer in front of the
existing controllers — rather than a rewrite of business logic, since no code path currently depends
on caller identity for correctness (only for the self-asserted audit fields, which would need to be
re-derived from verified identity once auth exists, a small but real migration of those fields'
semantics). The approval-decision endpoint (ADR-006) should be the first endpoint secured if this
decision is ever revisited, given its outsized severity relative to the domain endpoints.

## Traceability

- Requirements: REQ-021 (security — the overall threat model this decision is part of), REQ-013
  (approvals and rejection — the endpoint most materially affected).
- Clarifications: CLR-10 (authentication assumption — the specification-level decision this ADR
  formalizes the technology consequence of).
- Specification sections: `spec.md` Section 6.2 (REQ-013), Section 7 (REQ-021), Section 8 (Deferred
  Technology Decisions — forward pointer to this ADR), Section 13.1 (CLR-10), Section 9.6 (no-UI/
  scope exclusions referenced for Options B/C's rejection).
- Plan sections: `plan.md` Section 8 (Security — discusses the unauthenticated approval endpoint
  directly, not as a footnote), Section 12 (Technology Decisions, ADR-010 row), `research.md` D10.
- Related ADR: ADR-006 (Human Approval Mechanism — the endpoint whose exposure this ADR names as the
  most material consequence of the no-auth decision).
- Expected task identifiers: none for this iteration (nothing to build or test); the backlog item
  ("add an auth-bypass-attempt test when authentication is added") has no task identifier yet since
  it depends on a not-yet-scheduled future iteration.

## Validation

N/A for this iteration — there is no mechanism to validate, only an assumption to disclose. This
ADR's own explicit accept/reject decision by the human candidate is itself the validation act: it
converts the specification's already-confirmed CLR-10 decision into an explicitly acknowledged
architectural risk acceptance at the technology level, specifically covering the approval-endpoint
exposure this ADR's Rationale names. No test can "pass" for the absence of a security control; the
validation this ADR requires is human sign-off on the risk, not a green test suite.
