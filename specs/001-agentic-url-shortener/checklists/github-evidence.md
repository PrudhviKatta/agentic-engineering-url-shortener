# GitHub Evidence Quality Checklist

**Category 17 of 19 — `/speckit.checklist` (Task 8).**
**Applies to:** the full commit history of this repository, from its first commit through final
submission.
**Constitution principles:** X (Traceability and Repository Integrity — commit-coherence rules),
XI (Evidence-Based Completion).
**Status:** All items are checkable NOW against existing history and MUST be re-checked at every
future commit. This project has one disclosed, real precedent (see CHK-GH-001) that these items are
written specifically to catch.

## How to read this checklist
- **[MANDATORY]** blocks release readiness if failed/unverified. **[OPTIONAL]** does not, but must
  be disclosed.

## Items

- **[MANDATORY] GH-CHK-001** — For every commit, verify its diff contains changes belonging to
  exactly one describable unit of work — i.e., every file touched in the commit is either (a)
  directly part of the change the commit message describes, or (b) explicitly called out in the
  commit message as an intentionally bundled, related cleanup. Concretely: run `git show
  --stat <sha>` for the commit and confirm every listed file's presence is explained by the commit
  message; a commit message about "spec: resolve ambiguities" must not silently carry deletions of
  unrelated scaffold files (e.g., a prior task's discarded language/framework files) with no mention
  of them. **Fail if**: a commit's file list contains a file whose relationship to the stated commit
  purpose cannot be explained from the commit message alone. **Cites**: Principle X (second bullet:
  "Commits are small and logically coherent — one commit does one describable thing"). **This
  project's own precedent**: commit `12f1353` ("docs(spec): resolve critical ambiguities and record
  assumptions") is a real, disclosed example this exact check would have caught before the fact — a
  concurrent-subagent git-index race caused an earlier task's unrelated Python-scaffold file
  deletions (`git rm` staged but not yet committed) to be swept into this commit by a later task's
  `git add . && git commit`. The deletions were independently correct (dead files scheduled for
  removal anyway) but mis-attributed to the wrong commit — exactly the "diff contains an
  unexplained, unrelated file" failure mode this item exists to catch. See
  `.superpowers/sdd/2026-09-12-agentic-url-shortener-sdlc/progress.md` for the full incident record.
  Do not amend/rewrite the historical commit to hide this — Principle X favors a disclosed, truthful
  imperfection over a silently rewritten history; the requirement going forward is to catch this
  class of problem *before* it lands, via this checklist item run prior to any commit produced by
  a concurrent/parallel agent dispatch.

- **[MANDATORY] GH-CHK-002** — Verify no commit is produced by two concurrently dispatched agents
  operating on the same git working tree/index without an explicit serialization point (one agent's
  `git add`/`git commit` completing in full before another's `git rm`/`git add` begins). **Fail
  if**: any future task uses parallel/concurrent subagent dispatch against a shared git working
  directory without a documented serialization or isolation mechanism (e.g., separate worktrees).
  **Cites**: Principle X; this project's disclosed `12f1353` incident (CHK-GH-001) is the concrete
  root cause this item targets.

- **[MANDATORY] GH-CHK-003** — Verify every commit message states engineering intent (why) rather
  than only a mechanical description of the diff (what) — e.g., "docs(quality): add assessment and
  engineering checklists" states what changed, but a good commit body/footer additionally explains
  why the checklists take the shape they do when that is not self-evident. **Fail if**: a commit
  message is a bare mechanical label with no accompanying rationale for a non-trivial change.
  **Cites**: Principle X (second bullet).

- **[MANDATORY] GH-CHK-004** — Verify no commit message, PR description, or task report claims a
  test passed, a build succeeded, or a scenario completed without an attached, reproducible
  execution artifact (command output, CI log, or a specific file/line a reviewer can independently
  re-run). **Fail if**: a commit message says "all tests pass" with no accompanying evidence
  reference. **Cites**: Principle X ("No approval, test result, execution log... is ever fabricated,
  paraphrased into existence, or asserted without having actually been produced"); primary detection
  also in `tdd.md` CHK-TDD-006.

- **[MANDATORY] GH-CHK-005** — Verify a disclosed process incident (like `12f1353`) is named
  explicitly in the final engineering summary (Task 47) rather than omitted because "no one asked."
  **Fail if**: the final summary's process-discipline section makes no mention of a known,
  previously-disclosed git/process incident. **Cites**: Principle X, Principle XI (#8: residual risk
  disclosure); cross-reference `submission.md` CHK-SUB-004.

- **[MANDATORY] GH-CHK-006** — Verify commit granularity matches task granularity where practical —
  a single task's deliverable should generally correspond to one commit (or a small, clearly
  sequenced set, as `plan.md` Section 11.2 explicitly plans for the brownfield scenario's two
  commits), not one giant commit spanning multiple unrelated task IDs with no way to attribute a
  specific line of diff to a specific task. **Fail if**: a commit's diff cannot be attributed to a
  single named task ID from `tasks.md`. **Cites**: Principle X, Principle I (task decomposition
  intent).

- **[MANDATORY] GH-CHK-007** — Verify no destructive git operation (force-push, history rewrite,
  `git reset --hard` discarding uncommitted work) is used to retroactively "clean up" a disclosed
  imperfection like `12f1353`, per this project's own explicit ruling to leave it as a disclosed,
  truthful record rather than a silently rewritten one. **Fail if**: git history shows evidence of a
  rewrite targeting a previously disclosed commit. **Cites**: Principle X, this project's own
  `progress.md` ruling on `12f1353`.

- **[OPTIONAL] GH-CHK-008** — Verify commit review artifacts (the `review-<sha>..<sha>.diff` files
  under `.superpowers/sdd/`) exist for every commit range in the project's history, giving an
  independent, timestamped record of what was reviewed and when, separate from the commits
  themselves. **Fail if**: a commit range has no corresponding review artifact and no explanation
  for its absence. **Cites**: Principle IX (audit-grade evidence), Principle X.

- **[MANDATORY] GH-CHK-009** — Verify no commit message or PR description overstates what the diff
  actually contains — e.g., a commit claiming "add reliability tests" whose diff contains no test
  files. **Fail if**: a spot-check of any commit's message against `git show --stat` reveals a
  mismatch between claimed and actual content. **Cites**: Principle X, Principle XI (#3: acceptance
  criteria actually satisfied).
