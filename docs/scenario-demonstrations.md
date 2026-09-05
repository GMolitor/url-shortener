# End-to-End Scenario Demonstrations (T20)

**Date:** 2026-09-04  
**Scope:** Greenfield prototype, brownfield hardening, and ambiguous-requirement
case studies only

## Evidence convention

These are evidence-based case studies, not claims that three separately
exported orchestration runs exist. The workspace contains no raw external
agent-runtime run ID, worker transcript, prompt history, or event export
([orchestration evidence](orchestration-evidence.md#evidence-boundary)).

- **Repository evidence** means an implementation, test, migration, or
  documented command is present in this checkout.
- **Working-session fact** means it is recorded by the approved reconciliation
  or handoff documents.
- **Reconstructed** means the sequence or role assignment is derived from the
  task graph and handoff material; exact external worker identities and
  timestamps are not claimed.
- **Template** means a useful demonstration shape where the repository does
  not preserve a historical run record.

The external runtime and the T24 repository service must remain distinct. T24
stores and coordinates task state through an API; it does not launch or
supervise agent processes ([T19 boundary](orchestration-evidence.md#responsibilities-and-boundary)).

## 1. Greenfield prototype scenario

**Evidence class:** Reconstructed from the approved task graph, requirements,
architecture, task handoffs, and release evidence. This is not a historical
external-runtime run record.

### Input and decomposition

**Input:** Build a local-first URL-shortener prototype that accepts an HTTP or
HTTPS destination, creates a seven-character case-sensitive Base62 code,
persists it in SQLite, redirects known codes with HTTP 302, and provides the
React workflow. The approved boundary is one local backend instance, up to 10
concurrent clients, 100 creates per minute, and 1,000 persisted links. Public
hosting is out of scope ([requirements](requirements.md#capacity-and-prototype-boundary),
[architecture](architecture.md#context)).

**Decomposition and role map:** The role labels below are reconstructed from
task ownership and module boundaries; no external worker IDs are preserved.

| Work | Assigned role (reconstructed) | Depends on |
|---|---|---|
| Requirements and assumptions | requirements/product reviewer | none |
| Architecture and adversarial dispositions | architecture/security reviewer | requirements |
| Schema and migration contract | data-model/persistence worker | architecture |
| OpenAPI and error contract | API contract worker | architecture |
| Build, configuration, Flyway, health, tooling | foundation/build worker | architecture and API contract |
| URL policy and secure code generation | domain/security worker | requirements, architecture, data model |
| SQLite repository | persistence worker | data model and foundation |
| Spring API and redirects | backend/API worker | API contract, persistence, domain |
| React workflow | frontend worker | API contract and foundation |
| Unit, frontend, integration, security, and reliability checks | test/security/reliability workers | respective implementation stages |
| Review, documentation, and release readiness | review/documentation/release roles | implementation and validation evidence |

### Dependencies and execution paths

The documented critical path is `T01 -> T02 -> T03 -> T04 -> T05 -> T06 ->
T08 -> T12 -> T13 -> T15 -> T17` ([task plan](tasks.md#critical-path)).

The plan also records these parallel paths:

- After architecture, data-model work and initial API analysis can proceed in
  parallel.
- After the data model/API contracts, foundation and domain/code-generation
  work can proceed in parallel.
- After foundation, persistence and frontend work can proceed where contracts
  permit.
- After implementation, backend tests, frontend tests, and portions of
  reliability validation can proceed in parallel.
- After validation, code review and documentation can proceed in parallel.

The paths join at the integration/security/review/release gates. This is the
repository's documented dependency plan, not proof of a particular external
worker scheduling order.

### Decisions and decision lineage

1. **2026-08-31, T01:** human approval accepted the local/trusted-demo
   boundary, capacity planning bounds, and prototype-versus-production
   distinction ([T01 handoff](tasks.md#t01-handoff-and-approval-gate)).
2. **2026-08-31, T02:** human approval accepted configured-origin/CORS trust,
   Flyway ownership, SQLite limits, bounded collision behavior, centralized
   validation/errors, and observability ([T02 handoff](tasks.md#t02-handoff-and-approval-gate)).
3. **2026-08-31, T04:** the OpenAPI contract was approved for the create,
   redirect, health, stable-error, limit, origin, CORS, and caching behavior
   ([T04 handoff](tasks.md#t04-handoff-and-approval-gate)).
4. **2026-09-01, T15:** independent requirements, architecture, security,
   scope, and test-evidence review found no additional local-prototype defects.
5. **2026-09-03, T17:** the human reviewer approved the local prototype after
   clean-checkout verification, dependency review, acceptance checks, and the
   go/no-go review.

These decisions preserve a small local modular monolith: React/Vite, Spring
Boot, and SQLite. They do not authorize authentication, aliases, expiration,
hosted analytics, public hosting, or other deferred product work. The later
approved local analytics exception is covered by scenario 3.

### Issue handling, approvals, and validation

The working rules require a worker to stop on a contradiction with an approved
requirement/ADR, avoid silently resolving a blocking ambiguity, add tests for
behavior changes, and report changed files, checks, decisions, warnings, and
risks ([handoff guide](agent-handoff.md#working-rules)). Issues were therefore
expected to return to the relevant contract or review gate rather than be
silently broadened.

Repository evidence for the completed path includes:

- backend `test` and `spotlessCheck` passed;
- frontend `npm ci`, `npm test`, `npm run lint`, `npm run format`, and
  `npm run build` passed;
- API, URL policy, persistence, migration, restart, collision, concurrency,
  health, security, and reliability behavior is covered by the documented
  backend suites; and
- the runbook documents local startup, configuration, database care,
  troubleshooting, and testing ([runbook](runbook.md)).

### Outputs and limitations

**Outputs:** the runnable application and its contracts in
[requirements.md](requirements.md), [architecture.md](architecture.md),
[data-model.md](data-model.md), [openapi.yaml](openapi.yaml), the React and
Spring source trees, migrations, tests, and the approved release handoff in
[tasks.md](tasks.md#t17-handoff-and-approval).

**Limitations:** no raw greenfield agent run is available, so worker identity,
exact scheduling, retry history, and external approval events cannot be
claimed. The application remains local/trusted-demo and single-instance;
anonymous abuse controls, lifecycle management, backups, hosted operations,
and public capacity are outside scope ([runbook limitations](runbook.md#current-limitations)).

## 2. Brownfield hardening scenario

**Evidence class:** Working-session reconstruction. The final brownfield review
and handoff record the inputs, role owners, outcomes, approvals, commands, and
remaining evidence gaps. The exact external runtime sequence is not available.

### Input and decomposition

**Input:** Start from the approved T17 local prototype and audit it for
prototype-quality correctness, security, resource control, reproducibility,
and documentation consistency. Findings F-01 through F-14 and follow-up tasks
A01-A07 are recorded in the [brownfield readiness plan](brownfield-readiness-plan.md).

**Assigned roles and work:** These owners are explicitly documented in the
plan for A01-A07.

| Work | Assigned role | Dependency |
|---|---|---|
| A01 request-body enforcement | backend/security | T17; preserve the 4,096-byte API limit |
| A02 atomic persistence | backend/persistence | T03/T06 contracts |
| A03 API/configuration boundaries | backend/API | T04/T08; coordinate with A01 |
| A04 redirect/destination security | backend/security/documentation | T07/T08 |
| A05 local frontend/backend integration | frontend | T09/T11; coordinate with A03 if errors change |
| A06 reproducible checks and prototype-quality validation | build/CI | T17; coordinate with A01-A05 |
| A07 consistency and handoff | documentation/release reviewer | A01-A06 as applicable |

### Dependencies and execution paths

The plan makes T17 the prerequisite, allows A01-A06 to proceed in parallel
where contracts do not conflict, and joins them at A07. A02 was to be reviewed
before repository abstraction changes; A03 was to update OpenAPI/tests if
framework error behavior changed. This gives the following path:

```text
T17 approved
  -> A01  A02  A03  A04  A05  A06   (parallel where compatible)
  -> A07 documentation/release handoff
```

The actual evidence says A01-A05 are complete. A06 and A07 remain partial
because no hosted CI job or clean checkout of the uncommitted state was
available and the separate dependency-review step was absent. Thus the
parallel plan is evidenced, but not every planned lane has a complete
independent execution record.

### Decisions and decision lineage

1. T17 retained the local/trusted-demo, one-instance, SQLite boundary and
   approved the prototype handoff.
2. The brownfield audit converted concrete findings into narrowly scoped A01-A07
   tasks. It explicitly rejected speculative public hosting, permanent database
   infrastructure, public rate limiting, moderation, lifecycle management, and
   disaster-recovery work.
3. The final review on 2026-09-03 gave a **GO** verdict for the local prototype,
   accepted A01-A05 against the approved contracts, and left the A06/A07
    evidence caveats visible ([final review status](brownfield-readiness-plan.md#final-brownfield-review-status-2026-09-03)).

The approved reconciliation also records that the external runtime used a
parent coordinator with separate coder, tester, security, and review contexts;
it records a tester-discovered empty-port validation defect, replanning after
that finding, a human approval before resolving a discovered production issue,
and a safe-stop when tests exposed an unapproved production issue
([reconciliation](assignment-reconciliation.md#decision-1-orchestration-layer-boundary)).
Those facts establish the control pattern, not exact worker IDs or a complete
event timeline.

### Issue handling, approvals, and validation

The hardening issues were handled within the existing contracts:

- A01 changed the request guard to enforce actual bytes for fixed-length and
  chunked requests, with bounded reading and request-boundary tests.
- A02 made generated-key persistence atomic with SQLite `INSERT ... RETURNING`
  and rollback coverage for simulated key failure.
- A03 added origin startup validation and stable in-scope framework error
  handling, with origin, CORS, method/path/media, and redaction tests.
- A04 preserved the no-fetch redirect guarantee and documented intentional
  private/loopback acceptance rather than adding reputation infrastructure.
- A05 verified the separate Vite/Spring local topology and frontend failure
  states.

The human-approved T17 handoff and the 2026-09-03 final GO review are the
documented approval gates. Validation recorded in the handoff includes:

```text
backend/gradlew.bat test                         PASS
backend/gradlew.bat spotlessCheck                PASS
frontend/npm.cmd ci                              PASS
frontend/npm.cmd test                            PASS (11 tests)
frontend/npm.cmd run lint                        PASS
frontend/npm.cmd run format                      PASS
frontend/npm.cmd run build                       PASS
focused 1,000-link persistence check             PASS
```

The focused Gradle invocation took 21.885 seconds and the test case reported
2.785 seconds. The full suite also included the 10-client concurrent-create
check. These are local correctness and capacity-bound checks, not public load
or hosted-CI evidence ([handoff record](agent-handoff.md#final-brownfield-review-handoff-2026-09-03)).

### Outputs and limitations

**Outputs:** A01-A05 implementation/tests and synchronized documentation; the
current brownfield review and handoff; and a documented local-prototype GO
verdict. The plan's evidence points to request filtering, atomic repository
writes, origin validation, API handlers, redirect security, frontend
integration, and the relevant tests.

**Limitations:** A06/A07 are not claimed complete. There is no hosted CI run,
clean checkout of the uncommitted state, separate dependency-review result, or
raw external worker transcript. Public deployment remains blocked by the
documented abuse, destination-safety, lifecycle, backup/operations, and
multi-instance gaps. The current working tree also contains unrelated-to-T20
pre-existing implementation changes; this T20 document does not alter or
reclassify them.

## 3. Ambiguous-requirement scenario

**Evidence class:** Actual approved scope decision plus reconstructed execution
shape. The decision lineage is documented; no raw external run export exists.

### Input and ambiguity

The assignment required an orchestration layer and analytics demonstrations,
while the existing MVP requirements and architecture explicitly excluded
analytics and described only an external agent runtime. The key ambiguity was
whether to treat the assignment concerns as documentation-only, expand the
public product boundary, or add local repository capability.

The documented working rule was to stop rather than silently resolve a
blocking ambiguity. T18 therefore became the decision point.

### Decomposition, roles, and dependencies

**Documented runtime roles:** a parent external coordinator and separate coder,
tester, security, and review contexts are recorded for the session. Specific
worker assignment for T24 versus T25 is not recorded, so the following
implementation-role split is a **reconstructed/template** decomposition:

| Work | Role (documented or reconstructed) | Depends on |
|---|---|---|
| Reconcile assignment against MVP boundary | parent coordinator + human reviewer | T17/T18 input |
| Durable orchestration control plane | orchestration backend worker | approved T18 decision |
| Non-blocking redirect analytics | analytics/backend worker | approved T18 decision |
| Cross-cutting tests and security checks | tester/security workers | T24/T25 implementations |
| Evidence packaging and scenarios | documentation/release role | T24/T25 evidence |

The task graph records `T18 -> T24,T25 -> T19,T20`. T24 and T25 were therefore
eligible as parallel implementation lanes after approval; T19 and T20 consume
their evidence in parallel. The repository does not preserve the external
worker schedule, so this is a dependency-path demonstration, not a historical
parallelism claim.

### Decisions and decision lineage

1. **T18, human-approved 2026-09-04:** the external runtime remains the
   development-session coordinator, but it is not sufficient as the assignment
   deliverable by itself.
2. **T24 scope:** add a locally runnable, SQLite-backed orchestration control
   plane with durable runs/tasks, dependency graphs, gates, approvals, retries,
   fallback, rollback, safe-stop, policy checks, audit events, metrics, and
   replanning. It must not launch external workers or add public infrastructure.
3. **T25 scope:** override the original analytics exclusion with a bounded local
   capability: redirect events are published through a failure-isolated bounded
   publisher, and aggregate reads are provided without delaying or breaking
   redirects.
4. T19 must distinguish service behavior from external-runtime behavior; T20
   must package the three scenario demonstrations. T21-T23 remain downstream
   tasks and are not started by this document.

The later reconciliation decision is the authority for the T24/T25 exception,
while the original requirements row still said analytics were deferred. T22
reconciles that historical inconsistency in the current requirements,
architecture, data model, runbook, README, and OpenAPI set; it does not expand
the public-service boundary.

### Issue handling, approvals, and validation

The repository-service demonstration covers the ambiguous case's control
behaviors. The normalized record in T19 is explicitly derived from a passing
test, not an exported run:

```yaml
record_kind: test-derived-repository-run
source: OrchestrationAndAnalyticsIntegrationTest.schedulesIndependentTasksAndWaitsAtBarrierUntilExitApproval
final_state: COMPLETE
approvals: [ENTRY/APPROVED, TASK:b/APPROVED, EXIT/APPROVED]
tasks: [a:SUCCEEDED, b:SUCCEEDED, c:SUCCEEDED, barrier:SUCCEEDED]
```

It demonstrates `a` then the `b`/`c` branches, a high-impact task approval,
the `barrier` join, and exit approval. The same integration suite covers
fallback activation, bounded retries and worker ownership, terminal failure,
approval rejection/safe-stop, cancellation, rollback, metrics, malformed
graphs, and graph-versioned replanning. T19 records the limitation that these
are worker/API coordination tests and do not prove operating-system process
parallelism.

For T25, repository tests cover time-bounded click aggregates, analytics API
validation and empty windows, while the implementation's bounded publisher
isolates queue-full and persistence failures from redirect handling. The
reconciliation explicitly requires analytics not to delay or break redirects.

The human approval for T18 and T24/T25 completion is documented in
[assignment-reconciliation.md](assignment-reconciliation.md#approval-gate).
Repository approval rows and transitions are separately test-covered; they are
not evidence that the external human decision was imported into a T24 run.

### Outputs and limitations

**Outputs:** T24's orchestration implementation and V2 migration,
T25's analytics implementation and V2 tables, their unit/integration tests,
the T19 evidence package, and this T20 case-study document. The T19 evidence
index links the principal source files, including
[OrchestrationService.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationService.java),
[JdbcOrchestrationRepository.java](../backend/src/main/java/com/example/urlshortener/orchestration/JdbcOrchestrationRepository.java),
[OrchestrationAndAnalyticsIntegrationTest.java](../backend/src/test/java/com/example/urlshortener/integration/OrchestrationAndAnalyticsIntegrationTest.java),
and [V2 migration](../backend/src/main/resources/db/migration/V2__orchestration_and_analytics.sql).

**Limitations:** T24 has no authentication/authorization, queue, scheduler,
worker lease/heartbeat, timeout, backoff, automatic worker recovery, or
compensating rollback action. Its metrics are per-run terminal summaries, not
operational SLOs or dashboards. T25 is local, bounded, and privacy-limited;
it does not imply hosted analytics, identity tracking, retention operations, or
public deployment. Exact external worker identities, run IDs, timestamps,
retries, approvals, and sequencing remain unavailable
([T19 limitations](orchestration-evidence.md#limitations-and-capabilities-not-evidenced)).

## Evidence index and scope guard

Primary sources for all three demonstrations are:

- [task plan and dependency graph](tasks.md);
- [assignment reconciliation](assignment-reconciliation.md);
- [orchestration evidence](orchestration-evidence.md);
- [brownfield readiness plan](brownfield-readiness-plan.md);
- [agent handoff guide](agent-handoff.md);
- [README](../README.md), [requirements](requirements.md), and
  [runbook](runbook.md); and
- the linked implementation and test files in the scenario sections.

This document changes no application behavior and does not claim that T20 itself
was executed by an external worker or that any reconstructed role/path is a
missing historical run record. At the time of T20 authoring, T21-T23 were
downstream consumers; current completion and approval are recorded in
[`tasks.md`](tasks.md) and [`final-release-review.md`](final-release-review.md).
