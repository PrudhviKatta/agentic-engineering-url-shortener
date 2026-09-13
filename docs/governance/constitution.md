# Agentic Software Engineering System: URL Shortener — Engineering Constitution

> **STATUS: RATIFIED (Human Gate 1 cleared 2026-09-13).**
> This document was authored by an AI agent acting under the project's SpecKit lifecycle and
> ratified by the human candidate per Section 6. It is now in effect for the purposes of Principle
> I. Amendments after ratification consume a new version number per Section 4.4 and must themselves
> be approved before taking effect.

**Version:** 1.0.0 (ratified) &nbsp;|&nbsp; **Status:** Ratified — Human Gate 1 cleared &nbsp;|&nbsp; **Drafted:** 2026-09-12 &nbsp;|&nbsp; **Ratified:** 2026-09-13

---

## 1. Project Context

This repository is a software-engineering-interview assessment submission. The artifact under
review is not merely a working URL shortener — it is the process that produced it. Reviewers are
expected to evaluate this project along at least ten dimensions:

1. Working behavior of the delivered system.
2. Architecture and its fitness for the stated requirements.
3. The quality of engineering decisions and the reasoning behind them.
4. The discipline of the AI-assisted development process itself.
5. Task decomposition — whether work was broken into reviewable, independently verifiable units.
6. Testing discipline, including whether tests preceded implementation and whether they were
   actually executed.
7. Governance — whether the project had real guardrails, or governance theater.
8. Traceability from requirement through implementation to evidence.
9. Change history — whether commits tell an honest, legible story.
10. Documentation currency and engineering judgment under ambiguity.

The URL shortener is deliberately a simple domain. Its simplicity is intentional: it exists so
that the *process* — a governed, stateful, non-linear agentic software-engineering orchestration
system that turns requirements into reviewable engineering outcomes across the full SDLC — is the
thing actually being demonstrated and graded. Every principle in this constitution exists to keep
that process honest, inspectable, and resistant to the specific failure modes that make
AI-assisted engineering untrustworthy: unverified claims of completion, silent scope drift,
unauditable autonomy, and undocumented departures from what was agreed.

This constitution is written for two audiences simultaneously: the human reviewer who will judge
the submission, and the agents (orchestrator and sub-agents) who will execute the SDLC stages
under it. Where the two audiences would read a rule differently, the rule must be tightened, not
left ambiguous — ambiguity in a governance document is itself a defect.

## 2. Purpose, Authority, and Scope

This constitution is the top of the project's authority hierarchy. Every specification,
clarification record, plan, architecture decision record (ADR), checklist, task, line of
implementation code, and piece of evidence produced under this project must be consistent with it.
Where a downstream artifact conflicts with this constitution, the downstream artifact is wrong and
must be corrected — not the other way around — unless the conflict is resolved by a formal
amendment to this document (Section 4.4).

This constitution governs:

- The behavior of the agentic orchestration system that drives the SDLC (planning, execution,
  human gates, retries, replanning, recovery).
- The engineering standards applied to the URL shortener domain code itself.
- The evidentiary standard by which "done" is judged, for both individual tasks and the project
  as a whole.

This constitution does not itself contain application code, architecture decisions, or spec-level
requirements. Those belong in later SpecKit stages (`/speckit.specify`, `/speckit.plan`, ADRs,
`/speckit.tasks`) and must trace back to the principles established here.

## 3. Core Principles

The eleven principles below are numbered I–XI. Numbering is stable across amendments: a principle
may be revised in place, but its numeral is not reassigned to a different principle, so that
external references (task briefs, ADRs, reviewer notes) remain valid across versions.

### Principle I — Specification Before Implementation

No production implementation may begin until a specification for that unit of work has been
written and approved through the applicable human gate. "Approved" means a human has reviewed it,
not that an agent generated it and moved on.

- Every requirement carried into implementation must be phrased so it is testable: a reviewer or
  a test suite must be able to determine, without further interpretation, whether the requirement
  was met.
- Functional requirements (what the system does) and non-functional requirements (performance,
  security, reliability, operability) must be tracked as distinguishable categories, not
  collapsed into an undifferentiated list — non-functional requirements are otherwise the first
  thing silently dropped under schedule pressure.
- An ambiguity discovered while turning a requirement into a spec or a task must not be resolved
  by silent agent judgment call alone. It must either be escalated through `/speckit.clarify` (or
  the human gate applicable to that stage) or recorded explicitly as a stated assumption with its
  rationale, so a reviewer can find and challenge it later.
- When an upstream artifact changes materially (the spec, an ADR, a policy), every downstream
  artifact that depended on the prior version must be re-evaluated for impact before further work
  proceeds on it. A changed requirement does not "grandfather in" work already done against the
  old version.
- Existing code is never an acceptable substitute for a missing or stale specification. If the
  code and the spec disagree, that disagreement is itself a defect to be resolved — by updating
  the spec through a recorded change, or by fixing the code — not by treating the code as the
  authoritative source of truth going forward.

### Principle II — Explicit Agentic Orchestration

The differentiator of this project is that its SDLC orchestration is not a script that calls
agents one after another. It is a stateful system with an explicit model of how work depends on
other work, and it must be able to do everything a linear pipeline cannot.

**Non-linear orchestration model.** The orchestrator must represent the SDLC as an explicit
dependency graph (or an equivalent formal, inspectable model) over discrete stages — not as an
implicit sequence baked into agent prompts. That graph must be able to express and execute:

- sequential paths, where a stage cannot start until its declared predecessor(s) finish;
- parallel paths, where independent stages execute concurrently because nothing in the graph
  orders them relative to each other;
- synchronization points, where a stage must wait on the join of multiple upstream branches;
- conditional branching, where the path taken depends on a runtime outcome (e.g., a policy check
  result, a clarification answer);
- dynamic replanning (defined below); and
- interruption and resumption (defined under Principle VIII), including across process restarts.

Every stage in the graph must declare, explicitly and in advance: its inputs, its outputs, the
entry criteria that must hold before it may start, the exit criteria that must hold before its
output is considered valid, who or what owns it (an agent role, or "human"), and what happens on
its failure. A stage without a defined failure behavior is an incomplete stage definition and may
not be scheduled.

**State persistence.** The orchestrator's run state — which stages have started, finished,
failed, or are waiting; what each stage produced; which human decisions are pending or recorded;
and the full provenance chain of every artifact — must be persisted outside of any single agent's
transient context or conversation memory. Losing an orchestrator process (crash, restart, host
change) must not lose this state: a persisted run must be reconstructible and resumable from
storage alone. Cross-stage context (an output that stage B needs from stage A) is a first-class,
persisted object, not something recovered by re-reading an agent's chat transcript. Decision
lineage — why a branch was taken, why a replan happened, who approved what — is part of this
persisted state, not a side effect that can be discarded once a stage completes.

**Dynamic replanning.** The orchestrator must support altering the remaining, not-yet-executed
portion of the dependency graph in response to information discovered mid-run — for example, a
clarification that changes what downstream stages are needed, or a policy failure that requires
inserting a remediation stage. Replanning must: apply only to stages that have not yet started
(never silently rewrite the record of what already happened); preserve the existing audit trail
of everything executed before the replan; and itself be recorded as an auditable event (what
changed, why, and under whose authority) rather than an invisible mutation of the graph.

Agent autonomy inside this graph is bounded by construction, not by good behavior. An agent may
only act within the stage it currently owns, may only produce outputs the graph's exit criteria
can validate, and every action it takes must be observable in the persisted run state described
above and auditable after the fact (see Principle IX).

### Principle III — Human Governance

Agentic automation accelerates work; it does not transfer ownership. Certain classes of decision
belong to a human, permanently, regardless of how capable the orchestration system becomes.

**Human ownership.** The following remain human-owned and may not be delegated to an agent's
unsupervised judgment: requirements (what the system must do), architecture (how it is built),
security-sensitive decisions, any material exception to a policy or principle in this
constitution, destructive changes (data loss, irreversible external effects, force-push-class git
operations), release readiness (the decision to ship), and final quality sign-off. An agent may
draft, recommend, or propose in every one of these areas — this constitution document is itself
an example — but the decision of record belongs to a human.

**Approval gates and safe-stop on non-response.** Every point in the orchestration graph where a
human-owned decision is required is a formal approval gate, not an informal checkpoint. For each
gate, the following must be defined in advance: what constitutes approval, what constitutes
rejection, how rejection is escalated, what happens on timeout, and what "safe-stop" means for
that specific gate (typically: the orchestrator halts forward progress on the dependent branch,
leaves already-completed work and its evidence intact, and surfaces the pending decision rather
than guessing). A mandatory approval gate may never be silently skipped, auto-approved by an
agent, or bypassed because a downstream deadline is at risk. Absence of a human response is not
approval under any circumstance — a gate that times out must resolve to a safe-stop state that
blocks the dependent branch, never to an assumed "yes." This is the same safe-stop discipline
required of orchestration failures under Principle VIII, applied specifically to the human-in-
the-loop case: the default on doubt is to stop and surface, never to proceed and hope.

### Principle IV — Test-Driven Engineering (TDD)

Tests are not a verification step performed after the fact; they are how behavior gets specified
precisely enough to build against. This applies to the URL shortener's domain code and to the
orchestration engine itself — orchestration transitions are behavior, and behavior gets a
red-green-refactor cycle like anything else.

- For any unit of behavior — a domain function, an API endpoint, an orchestration state
  transition — a test that exercises the intended behavior is written and run *before* the
  corresponding implementation exists.
- That test must first be observed to fail, and for the expected reason (missing behavior, not a
  typo or import error). A test that has never been watched to fail is not trustworthy evidence
  that it tests anything.
- Implementation is written to the minimum extent needed to make the failing test pass — not
  more, and not by weakening the test to fit convenient code.
- Refactoring is permitted only while the full relevant test suite remains green; a refactor that
  requires temporarily breaking tests is sequenced as its own red-green cycle instead.
- The applicable test surface for this project spans multiple levels and none may be skipped
  where technically practical: unit tests (domain logic in isolation), integration tests
  (components wired together, e.g. persistence), API contract tests (the HTTP surface behaves as
  specified), orchestration transition tests (state machine behavior — gates, retries, replans,
  resumption), reliability tests (failure injection, retry/backoff behavior), security tests
  (input validation, scheme restrictions, abuse scenarios), and end-to-end tests (a realistic
  user-facing flow).
- A task is not complete because code was generated that appears to implement the requirement.
  It is complete only when the corresponding tests were actually executed, in this environment,
  and observed to pass — generated-but-unexecuted code is treated as not done (see also
  Principle XI).

### Principle V — Security and Privacy by Design

Security is a design input, not a post-implementation audit checklist.

- All externally supplied input — a long URL to shorten, a short code to resolve, any query
  parameter or header the system trusts — is validated and normalized before use, not merely
  before storage.
- The set of URL schemes the system will accept for shortening is an explicit allow-list (e.g.
  `http`, `https`); anything else, including schemes chosen specifically to enable abuse
  (`javascript:`, `data:`, `file:`, and similar), is rejected by default.
- Malicious-redirect and abuse scenarios — open-redirect chaining, shortener-as-phishing-relay,
  enumeration of short codes to discover private links, denial-of-service via mass creation — are
  treated as in-scope threats to design against, not edge cases to defer.
- Secrets, credentials, and sensitive user data must never appear in logs, error messages, or
  telemetry. Logging code is reviewed with this specifically in mind.
- Configuration defaults are secure by default; an operator has to deliberately opt into a less
  secure posture, not deliberately opt into a safe one.
- Trust boundaries are explicit, and every component operates under least privilege relative to
  the resources it touches.
- Authentication assumptions (including the explicit assumption of "none, for this assessment
  scope" if that is the decision reached in the spec/plan), rate-limiting behavior, the threat
  model considered, and any accepted security trade-off must be written down where a reviewer can
  find them — undocumented trade-offs are indistinguishable from oversights.
- Release readiness (Principle VI, Principle XI) includes a dependency vulnerability scan and a
  secret-scan of the repository; both must be clean or have a recorded, approved exception.

### Principle VI — Compliance and Change-Control Policy Enforcement

Governance without enforcement is a document nobody checks. This project defines versioned policy
guardrails and requires every orchestration run to evaluate against them.

- Policy guardrails are versioned and cover, at minimum: security, compliance, privacy, audit
  retention, approved dependencies, software licensing, and change control. The version of the
  policy set evaluated is recorded as part of every orchestration run's evidence.
- Every applicable policy check produces exactly one of four outcomes: `PASS`, `FAIL`,
  `EXCEPTION-REQUESTED`, or `NOT-APPLICABLE`. There is no fifth, implicit outcome such as
  "skipped" or "assumed fine."
- A `FAIL` on a mandatory policy check blocks further progress of the orchestration graph on the
  dependent branch. It does not become a warning that implementation proceeds past.
- An `EXCEPTION-REQUESTED` outcome may only become permitted-to-proceed through explicit human
  approval (Principle III) — never through an agent deciding the failure is acceptable.
- Every recorded exception must capture, at minimum: the specific policy it deviates from, the
  reason, the scope of the deviation (what it does and does not cover), the approving human
  authority, the compensating control adopted in lieu of compliance, the timestamp of approval,
  and an expiry or review condition after which the exception must be re-justified or retired.
- Any change to already-approved requirements, architecture, schemas, orchestration state
  definitions, security controls, or release criteria must go through formal impact analysis and
  change approval before it takes effect — it cannot be introduced as an incidental side effect
  of an unrelated task.
- Release readiness fails outright if any mandatory compliance or change-control policy remains in
  a `FAIL` state, or has an exception that is unapproved, expired, or out of scope for the
  situation it's being applied to.
- All policy outcomes and exceptions are first-class entries in the project's audit and
  traceability evidence (Principle IX, Principle X) — not a side file that gets left out of the
  final review package.

### Principle VII — Architecture and Maintainability

- Domain logic, API delivery, persistence, orchestration, policy enforcement, telemetry, and
  infrastructure concerns are kept in clearly separated layers or modules. A change to how links
  are persisted should not require touching how they are validated, and vice versa.
- External dependencies (a database, an HTTP client, a clock, a source of randomness/entropy for
  short-code generation) are accessed through explicit interfaces the domain layer defines, so
  they can be substituted or faked in tests without contorting the domain code.
- Material architectural decisions — and the alternatives that were considered and rejected — are
  recorded as ADRs. A decision with no visible alternative considered reads, to a reviewer, as a
  decision that wasn't actually deliberated.
- Complexity is justified by a requirement or by a demonstrable need of the assessment, not by
  anticipated future flexibility that nothing in the spec asks for. When in doubt, the simpler
  design that satisfies the current spec wins, and a note is left explaining what was deliberately
  deferred.
- Code is kept modular, readable, independently testable, and replaceable — a component should be
  rewritable without triggering a rewrite of everything around it.

### Principle VIII — Reliability and Recovery

The orchestration system, and the URL shortener service it produces, must fail in predictable,
recoverable ways rather than unpredictable ones.

**Failure classification and bounded retry.** Failures are classified, at the point they are
handled, as transient (worth retrying — e.g. a momentary connection error) or permanent (retrying
will not help — e.g. a validation error, a rejected policy check). Retries apply only to
classified-transient failures, are always bounded by an explicit maximum attempt count, and follow
a defined backoff strategy with a defined timeout — an operation may not retry indefinitely, and
an operator or reviewer must be able to read, in advance, how many attempts and how long a given
operation is allowed to take before it gives up. Operations that may be retried are designed to be
idempotent, so a retried operation does not double-apply its effect (e.g., creating the same short
link twice).

**Rollback vs. compensation.** These are treated as distinct recovery strategies, and the
distinction is documented per failure scenario. Rollback means undoing a not-yet-committed or
locally reversible change (e.g., discarding an in-flight transaction). Compensation means issuing
a corrective, forward-moving action to counteract an already-committed effect that cannot simply
be undone (e.g., a compensating record when an external side effect already occurred). A recovery
plan that conflates the two — attempting to "roll back" something already externally observable —
is treated as a design defect.

**Fallback behavior.** A fallback is distinct from a retry and from a rollback/compensation: it is
a different, lower-risk or degraded way of achieving a stage's intent when the primary approach has
been judged unable to succeed as attempted — not a repetition of the same operation (that is a
retry) and not an undo of an effect (that is rollback or compensation). A fallback is only taken
after the primary approach's failure has been classified per this principle's failure
classification, and only when a defined fallback path exists for that stage; a stage with no
defined fallback path that cannot succeed goes to safe-stop instead of inventing an ad hoc
degraded behavior. Every fallback path is declared in advance as part of the stage's design (not
improvised at run time), states explicitly what capability or guarantee is reduced relative to the
primary path, and is recorded in the run's audit trail with the reason the primary path was judged
unable to succeed and which fallback path was taken. A fallback outcome is never presented as
equivalent to a primary-path success without that reduction being disclosed.

**Safe-stop conditions.** For every stage and every long-running operation, the conditions under
which the system must stop rather than continue are defined explicitly: exhausted retries,
detection of a non-recoverable state, or an unresolved human approval gate (Principle III). A
safe-stop always leaves the system in a state a human can inspect and act on — it never leaves
partial, silently-inconsistent state behind and never proceeds past the stop condition "just to
make progress."

**Deterministic terminal outcomes.** Every stage and every orchestration run, however it ends —
success, permanent failure, or safe-stop — resolves to one of a small, explicitly enumerated set
of terminal outcomes. There is no "it just stopped and nobody knows why" outcome; the terminal
state itself is evidence.

**Controlled resumption.** An interrupted run (host restart, safe-stop, an approval gate resolved
after a delay) can be resumed from its persisted state (Principle II) without re-executing work
that already completed and without silently skipping work that didn't. Resumption is itself a
recorded event.

Evidence of every one of the above — success, failure, retry attempts, fallback actions,
compensation actions, recovery, and latency — is captured as part of the run's audit trail, not
reconstructed after the fact from memory or inferred from logs that weren't designed to support it.

### Principle IX — Observability and Auditability

If an execution cannot be reconstructed after the fact, it did not happen in a way this project
can take credit for.

- Every orchestration execution is assigned a correlation or run identifier at the moment it
  starts, and that identifier threads through every stage, log line, and artifact the run
  produces.
- The run's history records, at minimum: state transitions, decisions (including which branch of
  a conditional was taken and why), approvals and rejections, retries, failures, replanning
  events, and the run's terminal outcome.

**Audit-grade evidence.** "Audit-grade" means a named human reviewer, with no other context, can
open the evidence trail and answer: what happened, in what order, who or what caused it, when it
happened, and what the result was — without having to trust an agent's own narrative summary of
its work. Concretely, every audit record captures: the actor (human, or which agent/role), the
action taken, a timestamp, the artifact or state affected, the result, and the reason. Logs,
metrics, traces, and the persisted orchestration history together must be sufficient to
reconstruct any given execution independently of whatever chat transcript produced it — the
transcript is convenience, the persisted record is the evidence of record.
- Metrics gathered for the purposes of this assessment's demonstration are explicitly labeled as
  such and are never presented, in any report or dashboard, in a way that could be mistaken for a
  production-grade measurement (e.g., load-test numbers from a laptop are not "production
  latency").

### Principle X — Traceability and Repository Integrity

- A live thread connects requirement → acceptance scenario → decision (ADR/clarification) →
  design → task → implementation → test → validation evidence → documentation. Given any one link
  in that chain, a reviewer should be able to walk to the others.
- Commits are small and logically coherent — one commit does one describable thing. Commit
  messages state engineering intent (why the change exists), not just a mechanical description of
  the diff.
- No approval, test result, execution log, or operational evidence is ever fabricated,
  paraphrased into existence, or asserted without having actually been produced by running the
  thing it claims to describe. This is treated as a severity-one violation of this constitution:
  fabricated evidence invalidates every other claim in the same submission and must never occur.
- Every AI-generated artifact — code, spec text, this constitution itself — is subject to human
  review before it is treated as approved, and a human is ultimately accountable for what ships,
  regardless of which agent drafted it.
- Documentation is kept current with implementation; a stale doc describing behavior the code no
  longer has is a defect to fix in the same change that caused the drift, not a follow-up ticket.

### Principle XI — Evidence-Based Completion

A feature, task, or the project as a whole is "done" only when all of the following hold, and the
evidence for each is available for a reviewer to check without taking an agent's word for it:

1. The requirements applicable to the unit of work have been identified and are traceable back to
   the spec.
2. Any decisions and assumptions made while doing the work are recorded, with rationale.
3. The acceptance criteria defined for the work have actually been satisfied, not approximately
   satisfied.
4. The required tests (per Principle IV's test-level requirements) have been executed — not just
   written — and observed passing, in this environment.
5. Applicable security and reliability checks (Principles V and VIII) have been performed and are
   clean, or have a recorded, approved exception.
6. Documentation affected by the change has been updated to match.
7. Traceability (Principle X) from requirement through evidence is intact and unbroken.
8. Any residual risk or known limitation is disclosed explicitly, rather than left for a reviewer
   to discover.
9. Any approval required by Principle III or Principle VI has actually been recorded, not merely
   assumed to have happened.
10. All of the above evidence is actually assembled and available — in the repository, in the
    run's audit trail, or in the task report — for independent reviewer verification.

A task report, commit message, or agent summary that asserts completion without this evidence
being available is not itself evidence — it is a claim, and this constitution treats unverified
claims of completion as equivalent to non-completion for the purpose of gating further work.

## 4. Governance

### 4.1 How Constitutional Compliance Is Assessed During Planning

Every SpecKit planning stage (`/speckit.plan`, ADR authoring, `/speckit.tasks`) must include an
explicit self-check against the eleven principles above before its output is presented for human
review. The check is a named step in that stage's output, not an implicit assumption. At minimum,
the check must state, for each principle plausibly relevant to the artifact being produced,
whether it is satisfied, not applicable, or in tension with the proposal — and if in tension, that
tension must be surfaced to the human gate for that stage rather than silently resolved by the
agent. `/speckit.analyze` is the dedicated stage for a cross-artifact constitutional consistency
sweep prior to implementation, and its output must reference this document by version number
(Section 5) so a reviewer knows which revision of the constitution the analysis was run against.

### 4.2 How Exceptions Are Proposed and Approved

An exception is a documented, bounded, time-limited deviation from a principle or a policy in this
constitution — it is not a redefinition of the principle. Any agent or human may propose an
exception. A proposal must state: which principle or policy it deviates from, why compliance is
not currently achievable or advisable, the scope of the deviation, the compensating control
adopted in the interim, and a review or expiry condition. No exception takes effect until a human
records approval (Principle III); an agent-proposed exception that has not received recorded human
approval is treated as `EXCEPTION-REQUESTED`, not as granted, and behaves as a blocking `FAIL` for
release-readiness purposes (Section 4.6) until resolved.

### 4.3 Which Principles Cannot Be Waived

Principles I (Specification Before Implementation), III (Human Governance), IV
(Test-Driven Engineering), and XI (Evidence-Based Completion) are non-waivable: no exception
process may suspend them, in whole, for this project. The rationale is that each anchors a
category of trust this assessment specifically exists to test — that implementation follows
agreed intent, that humans retain real control, that behavior is verified rather than assumed, and
that "done" means something concrete. A narrow, well-scoped procedural adjustment within one of
these principles (for example, agreeing that a specific low-risk stage's approval gate may default
to a named human's async review rather than synchronous sign-off) is permitted through the normal
exception process in Section 4.2, provided it does not remove the principle's substance — but the
principle itself, and its non-waivable status, cannot be exception'd away. All other principles
(II, V, VI, VII, VIII, IX, X) may be subject to a narrowly scoped, human-approved exception under
Section 4.2 when justified, but exceptions against them are expected to be rare and are weighted
against release readiness as described in Section 4.6.

### 4.4 How Amendments Are Versioned

This constitution is versioned using semantic versioning (`MAJOR.MINOR.PATCH`), starting at
`1.0.0` (Section 4.7).

- **MAJOR** — a change that removes, weakens, or redefines the substance of an existing principle,
  or that changes which principles are non-waivable (Section 4.3).
- **MINOR** — a new principle, a new governance mechanism, or a materially expanded rule within an
  existing principle that does not weaken anything already committed to.
- **PATCH** — clarification, wording, typo correction, or formatting change that does not alter
  the substantive obligation of any rule.

Every amendment, regardless of size, is recorded as a new row in the version history (Section 5)
with the version number, date, a one-line description of the change, and the human approver of
record. An amendment does not apply retroactively to work already evidenced as complete under a
prior version — it applies going forward from the date it is ratified. A downstream artifact
citing this constitution should cite the version it was checked against, so drift is detectable.

### 4.5 How Conflicts Between Principles Are Resolved

Where two principles appear to pull in different directions for a specific decision, they are
resolved in this order, applied to the specific facts at hand rather than in the abstract:

1. A non-waivable principle (Section 4.3) prevails over a waivable one.
2. Among non-waivable principles, Human Governance (III) prevails as the tiebreaker of last
   resort — when genuinely unresolved, the decision is escalated to a human rather than settled by
   an agent picking a side.
3. Among waivable principles, the one whose violation would produce irreversible or
   externally-visible harm (data loss, security exposure, published incorrect information)
   prevails over one whose violation is internally recoverable.
4. Any conflict resolved under this section is itself recorded as a decision with rationale
   (Principle X) — silently picking one principle over another without a record is itself a
   violation of Principle X.

### 4.6 How Non-Compliance Blocks Release Readiness

Release readiness (whatever gate marks the project, or a versioned increment of it, as ready to be
presented for final review) is a boolean derived from the state of every applicable principle and
policy check, not a subjective judgment call made at the end:

- Any mandatory policy check (Principle VI) in a `FAIL` state blocks release readiness outright.
- Any `EXCEPTION-REQUESTED` that has not received recorded human approval blocks release readiness
  as if it were a `FAIL`.
- Any non-waivable principle (Section 4.3) found to be violated blocks release readiness and
  cannot be overridden by an exception.
- Any waivable principle violated without a properly recorded, approved, unexpired exception
  (Section 4.2) blocks release readiness.
- Evidence gaps under Principle XI (missing test execution, missing approval record, missing
  traceability link) are treated as non-compliance for the item they concern, not as a
  documentation nice-to-have.

Release readiness is re-evaluated whenever the set of applicable checks changes (new code, new
policy version, expired exception) — it is not a one-time gate that, once passed, is assumed to
still hold indefinitely.

### 4.7 Initial Version

The initial version of this constitution is **1.0.0**, drafted 2026-09-12 and ratified 2026-09-13
per Human Gate 1 (Section 6). The version history begins incrementing from the first change made
*after* ratification; the fallback-behavior addition made prior to ratification did not consume a
version number, per the pre-ratification-revisions rule this section originally established.

## 5. Version History

| Version | Date | Change | Author | Human Approver |
|---|---|---|---|---|
| 1.0.0 (draft) | 2026-09-12 | Initial constitution drafted from the Section 6 governance prompt: Principles I–XI, governance rules (4.1–4.7), version history established. Pending Human Gate 1. | Agent (SpecKit `/speckit.constitution` stage) | *(pending — not yet approved)* |
| 1.0.0 (ratified) | 2026-09-13 | Human Gate 1 completed: all eleven principles and the governance section (4.1–4.7) confirmed as written, no amendments required as a condition of approval. | — | Prudhvidhar Reddy Katta (human candidate) |

Future amendments are appended as new rows, never by editing a prior row's "Change" content —
the version history is itself subject to Principle X (repository integrity): it is a record, not
a mutable summary.

## 6. Ratification (Human Gate 1)

This constitution takes effect for the purposes of Principle I ("no implementation without an
approved specification" and, by the same logic, no downstream SDLC artifact without an approved
constitution) only once a human reviewer completes Human Gate 1:

- [x] Confirms the eleven principles faithfully capture the intended non-negotiables for this
  project (and requests amendment here, before proceeding, if not).
- [x] Confirms the governance section (Section 4) is workable as written.
- [x] Records approval — name, date, and any amendments required as a condition of approval — as
  a new row in Section 5's version history.

**Ratified 2026-09-13 by Prudhvidhar Reddy Katta (human candidate). No amendments required as a
condition of approval.** This constitution is in effect for the purposes of Principle I as of this
date; see Section 5 for the recorded approval row.
