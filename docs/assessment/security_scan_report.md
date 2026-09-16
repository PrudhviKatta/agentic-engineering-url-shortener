# Security scan report

Date: 2026-09-15

## Repository-content checks

The tracked working tree and reachable Git history were searched for common private-key headers,
credential assignments, bearer tokens, cloud access-key patterns, and embedded user-directory paths.
No application secret, private key, access token, or password was found. Candidate attribution and
local usernames, paths, and email addresses were removed from the submission working-tree content for
the public repository. Reachable Git history retains its original author identities and four old
review-tool references in commit subjects; that history is intentionally preserved rather than
rewritten. These are identity/provenance metadata, not application secrets.

The application requires no runtime credential in its local demonstration configuration. Generated
H2 database files, lock files, build output, and local environment files are excluded by `.gitignore`.

## Dependency checks

The Maven verification suite is the local correctness gate. Dependabot is configured to monitor Maven
and GitHub Actions dependencies after the repository is hosted.

An OWASP Dependency-Check run was attempted with a CVSS failure threshold of 7. The command stopped
before dependency analysis because the NVD feed had no usable API key or cached vulnerability data.
That outcome is **inconclusive**, not a clean scan and not evidence that a vulnerability exists.

## Approved temporary disposition

The human candidate approved a scoped exception on 2026-09-15 for the initial assessment submission.
GitHub CI and Dependabot are the compensating controls. The exception does not authorize production
or untrusted-network deployment, and it expires on 2026-09-22 or when the first hosted dependency
result becomes available, whichever occurs first. Any actionable hosted finding blocks readiness
until it is resolved or separately dispositioned. The approval is recorded in
`docs/governance/human-gates-log.md`.

To complete the local scan without exposing the key, export `NVD_API_KEY` in the shell and run:

```bash
./mvnw org.owasp:dependency-check-maven:check \
  -DnvdApiKeyEnvironmentVariable=NVD_API_KEY \
  -Dformat=HTML \
  -DfailBuildOnCVSS=7
```

Do not commit the generated report or the API key. Review the report and disposition any high-severity
finding before marking release readiness complete.
