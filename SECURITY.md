# Security posture

## Supported deployment scope

The application is a local demonstration bound to `127.0.0.1`. It is not approved for exposure to
an untrusted network. No endpoint authenticates callers in this iteration, including the workflow
approval and policy-exception operations. Actor names are self-asserted audit attributes, not verified
identities. Deploying beyond loopback requires authentication, authorization, TLS, rate limiting, and
operational secret management first.

## Implemented controls

- Link targets permit only `http` and `https`.
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

## Secrets and logging

The project requires no application secrets and none should be committed. Do not place credentials,
tokens, or private URL query data in requirements, approval rationales, or logs. The repository does
not configure a production log-scrubbing pipeline.

## Dependency review

No automated dependency scan is configured. Before any release candidate, run the full tests and a
manual OWASP Dependency-Check review:

```bash
mvn org.owasp:dependency-check-maven:check
```

The dependency scan may require network access and a vulnerability-feed download. Review results;
do not equate command completion with risk acceptance.

## Reporting

For this assessment repository, report a suspected vulnerability privately to the repository owner
with reproduction steps and affected revision. Do not include exploit secrets or sensitive target data.
