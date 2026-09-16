# Security posture

## Supported deployment scope

The application is a local demonstration bound to `127.0.0.1`. It is not approved for exposure to
an untrusted network. No endpoint authenticates callers in this iteration, including the workflow
approval and policy-exception operations. Actor names are self-asserted audit attributes, not verified
identities. Deploying beyond loopback requires authentication, authorization, TLS, rate limiting, and
operational secret management first.

## Implemented controls

- Link targets permit only `http` and `https`.
- Submitted URLs are limited to 2,048 characters.
- The validator resolves host addresses before persistence and rejects loopback, any-local,
  link-local, site-local/private, multicast, and IPv6 Unique Local Address (`fc00::/7`) targets.
- Malformed and unresolvable hosts fail closed.
- Short-code uniqueness is enforced by the database and collision retry is bounded.
- Approval silence never becomes approval; rejection blocks the affected workflow version.
- Mandatory policy failures safe-stop unless a matching, scoped, unexpired exception is recorded.
- Audit events include workflow, actor type and identity, action, result, affected artifact, and reason.

DNS rebinding protection is limited to validation-time resolution; the service does not pin or
revalidate an address at redirect time. That limitation, plus the unauthenticated approval surface,
prevents production classification.

## Abuse cases and remaining risks

- **Short-code enumeration:** seven-character codes are identifiers, not access controls. Anyone who
  can reach the service may probe codes and discover a valid redirect. Private links require an
  authenticated access model that is outside this demonstration.
- **Open-redirect or phishing relay:** the service intentionally redirects to caller-supplied public
  HTTP(S) destinations. URL validation blocks local-network targets but cannot establish whether a
  public destination is trustworthy. Production use needs abuse reporting, reputation controls, and
  takedown operations.
- **Mass creation and redirect traffic:** endpoints have no caller authentication, quotas, or rate
  limits. Keep the application loopback-only; add authenticated quotas and edge rate limiting before
  network exposure.
- **DNS rebinding:** host resolution is checked when a link is created, not pinned and revalidated at
  redirect time.

## Secrets and logging

The project requires no application secrets and none should be committed. Do not place credentials,
tokens, or private URL query data in requirements, approval rationales, or logs. The repository does
not configure a production log-scrubbing pipeline.

## Dependency review

GitHub Actions runs the Maven verification suite, and Dependabot is configured for Maven and workflow
dependencies. Before a release candidate, also run OWASP Dependency-Check:

```bash
./mvnw org.owasp:dependency-check-maven:check -DnvdApiKeyEnvironmentVariable=NVD_API_KEY
```

The NVD key must be supplied through the local environment or a protected CI secret, never committed.
The 2026-09-15 local attempt stopped before analysis because no NVD API key/data feed was available;
therefore it is not a clean vulnerability result. A temporary, assessment-only exception through
2026-09-22 relies on GitHub CI and Dependabot as compensating controls; see
`docs/assessment/security_scan_report.md`.

## Reporting

For this assessment repository, report a suspected vulnerability privately to the repository owner
with reproduction steps and affected revision. Do not include exploit secrets or sensitive target data.
