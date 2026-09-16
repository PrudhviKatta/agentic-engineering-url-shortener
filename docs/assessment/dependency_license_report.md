# Dependency license review

Date: 2026-09-15

## Executed check

The Maven license inventory was generated with:

```bash
./mvnw --batch-mode \
  org.codehaus.mojo:license-maven-plugin:2.7.1:add-third-party \
  -Dlicense.addOutputDirectoryAsResourceDir=false
```

The command completed successfully and reported 90 compile, runtime, and test dependencies. It
found no dependency with missing or unknown license metadata. The generated detail file is build
output at `target/generated-sources/license/THIRD-PARTY.txt` and is intentionally not committed.

## Review result

The inventory is predominantly Apache-2.0, MIT, BSD, Eclipse/EDL/EPL, and dual-licensed artifacts.
It also identifies Hibernate components under LGPL-2.1-or-later and Logback under a dual
EPL-1.0/LGPL option. No dependency is labeled AGPL, unlicensed, missing-license, or unknown-license.

This repository does not commit or publish the generated application JAR. Before distributing a
binary release, regenerate the inventory, preserve applicable copyright/license notices, and review
the selected terms for every redistributed dependency. This engineering inventory is not legal
advice.

## Disposition

The dependency-license check passes for the source-only assessment submission. Vulnerability status
is a separate concern covered by `security_scan_report.md` and its temporary hosted-review
exception.
