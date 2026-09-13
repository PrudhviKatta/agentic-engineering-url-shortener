# Security Quality Checklist

**Category 7 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** REQ-002, REQ-021, `plan.md` Section 8, CLR-1/10/11, ADR-010.
**Constitution principles:** V (Security and Privacy by Design).
**Status:** All items UNVERIFIED/PENDING — no validation/security code exists yet.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] SEC-CHK-001** — Verify `UrlValidator` rejects every scheme other than exactly
  `http` and `https` (CLR-1), including `javascript:`, `data:`, `file:`, and any scheme not on the
  allow-list, and that the check runs before any `short_links` row is written. **Fail if**: any
  non-`http(s)` scheme results in a created mapping, or the check runs only after a row is
  persisted. **Cites**: REQ-002, REQ-021, CLR-1.

- **[MANDATORY] SEC-CHK-002** — Verify a long URL whose host resolves to a loopback, link-local, or
  private RFC1918 address is rejected at creation time (SSRF guard), including via
  `UrlValidatorSsrfTest`. **Fail if**: a URL resolving to `127.0.0.1`, `169.254.x.x`, or a
  `10.x/172.16-31.x/192.168.x` address is accepted and shortened. **Cites**: REQ-002, REQ-021,
  CLR-1.

- **[MANDATORY] SEC-CHK-003** — Verify the brownfield-hardened SSRF check also rejects IPv6 Unique
  Local Addresses (RFC 4193, `fc00::/7`) — the real gap in `Inet6Address.isSiteLocalAddress()`'s
  deprecated site-local coverage that `plan.md` Section 8/Section 11.2 names as a genuine (not
  hypothetical) finding. **Fail if**: an IPv6 ULA-resolving host is accepted where an equivalent
  IPv4 private address would be rejected. **Cites**: plan.md Section 8/11.2, REQ-021.

- **[MANDATORY] SEC-CHK-004** — Verify a submitted long URL exceeding 2048 characters is rejected
  the same way as a malformed URL (CLR-5), including under high-volume/burst submission (per
  REQ-021's CLR-11-scoped framing that input-size bounds must hold even without rate-limiting).
  **Fail if**: an over-length URL is accepted, or size-limit enforcement degrades/bypasses under
  concurrent high-volume submission. **Cites**: REQ-002, CLR-5, REQ-021.

- **[MANDATORY] SEC-CHK-005** — Verify the security test suite includes at least the four named
  abuse classes from REQ-021: open-redirect chaining, shortener-as-phishing-relay, short-code
  enumeration, and mass-creation denial-of-service — each with a demonstrated safe-failure or
  explicitly disclosed residual-risk outcome. **Fail if**: any of the four classes has no
  corresponding test or disclosure. **Cites**: REQ-021 (proposed validation target).

- **[MANDATORY] SEC-CHK-006** — Verify short-code enumeration is explicitly disclosed as an
  undefended residual risk (base62 encoding of a sequential auto-increment ID, per ADR-003 and
  `research.md` D3) rather than silently left undiscussed, since REQ-021 does not require it be
  prevented but does require it be treated as an in-scope threat to design against or explicitly
  disclose. **Fail if**: no documentation anywhere names sequential short-code enumeration as a
  known, accepted risk. **Cites**: plan.md Section 8, ADR-003, REQ-021, Principle V.

- **[MANDATORY] SEC-CHK-007** — Verify mass-creation DoS is explicitly disclosed as an unenforced
  residual risk (CLR-11: no rate-limiting in this iteration) rather than the security suite silently
  omitting the class without comment. **Fail if**: the absence of rate-limiting is discoverable only
  by an absence of code, with no explicit disclosure in `spec.md`/`plan.md`/security docs. **Cites**:
  CLR-11, REQ-021, Principle V ("undocumented trade-offs are indistinguishable from oversights").

- **[MANDATORY] SEC-CHK-008** — Verify no secret, credential, or disproportionate personal data
  appears in logs, error messages, or telemetry — spot-checked by reviewing logging call sites in
  the approval, policy, and audit-recording code paths specifically. **Fail if**: an exception
  handler logs a full request body containing a policy-exception `reason` that could include
  sensitive text without redaction review, or any log line contains a secret. **Cites**: Principle
  V (logging bullet).

- **[MANDATORY] SEC-CHK-009** — Verify configuration defaults are secure by default: the two named
  exceptions (`ddl-auto: update`, and the system-wide absence of authentication) are each explicitly
  disclosed as deliberate, reviewed defaults — not silent gaps discovered by a reviewer reading
  `application.yml`. **Fail if**: `ddl-auto: update` or the no-auth posture is found in
  configuration with no corresponding disclosure in an ADR or `plan.md` Section 8. **Cites**:
  Principle V, ADR-010, plan.md Section 8.

- **[MANDATORY] SEC-CHK-010** — Verify the `POST /workflows/{id}/approvals/{step}` endpoint's
  unauthenticated status is explicitly named as the single largest security caveat of the design
  (per `plan.md` Section 8: "exactly as unauthenticated as `GET /healthz`") and that this asymmetry
  (a governance-releasing endpoint being no more protected than a health check) is disclosed to a
  reviewer, not discoverable only by code inspection. **Fail if**: `plan.md`/ADR-010 discusses
  authentication only in aggregate without calling out this specific endpoint's elevated risk.
  **Cites**: ADR-010, plan.md Section 8, Principle III (security-sensitive decisions are
  human-owned), Principle V.

- **[MANDATORY] SEC-CHK-011 [ANTI-PATTERN: architecture, schema, security, or workflow change
  without formal approval]** (security angle) — Verify any change to a security control (scheme
  allow-list, SSRF guard, authentication posture) after ADR-010/plan.md's baseline is recorded
  through formal impact analysis and change approval (REQ-030) before taking effect. **Fail if**: a
  security-control change lands with no REQ-030 impact-analysis artifact. **Cites**: REQ-030,
  Principle VI; primary detection lives in `adr.md` CHK-ADR-002.

- **[MANDATORY] SEC-CHK-012** — Verify a dependency-vulnerability scan and a secret-scan of the
  repository are run before any release-readiness "ready" outcome, and are either clean or carry a
  recorded, approved exception (Principle V, Principle VI). **Fail if**: release-readiness reports
  "ready" with no scan evidence attached, or with a `FAIL`/unapproved-exception scan result. **Cites**:
  Principle V, REQ-021, REQ-020.

- **[MANDATORY] SEC-CHK-013** — Verify trust boundaries are explicit (`plan.md` Section 1.4) and
  each component operates under least privilege relative to the resources it touches (e.g., the
  orchestration engine's repositories cannot query `short_links` tables directly). **Fail if**: a
  component is found with data access broader than its documented role requires. **Cites**:
  Principle V, plan.md Section 1.4/1.6.

- **[OPTIONAL] SEC-CHK-014** — Verify the authentication assumption's scope note (CLR-10/ADR-010)
  distinguishes the demonstrated-scope decision from a production recommendation, so a reviewer does
  not mistake "no auth in this assessment" for "no auth is the recommended production posture."
  **Fail if**: no document states the production-readiness caveat explicitly. **Cites**: ADR-010,
  Principle IX (labeling demonstration artifacts).
