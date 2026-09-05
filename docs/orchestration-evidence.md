# Orchestration Evidence (T19)

**Date:** 2026-09-04  
**Scope:** T24 repository service and the external agent runtime used for this
development session  
**Status:** Evidence package complete; no T20 scenario demonstration is claimed

## Evidence boundary

This document separates two orchestration layers:

1. The repository-contained Spring Boot service added by T24. Its behavior is
   executable locally, durable in SQLite, and covered by the linked tests.
2. The external Orchestration Agent runtime that coordinated this development
   session. Its behavior is summarized only where the approved
   [assignment-reconciliation record](assignment-reconciliation.md) states it.
   No raw external event export, run ID, worker transcript, prompt history, or
   agent-runtime log is present in this workspace.

The words **implemented/tested** below refer to the repository service unless
the external runtime is named explicitly. A test-derived record is not a
historical production run record.

## Responsibilities and boundary

| Concern | Repository service (T24) | External agent runtime (session evidence) |
|---|---|---|
| Plan representation | Accepts a named task graph with dependencies, high-impact markers, retry limits, and fallback links. | Decomposed development work and assigned work to separate worker contexts during the brownfield effort, according to the reconciliation record. |
| State authority | Owns durable run, task, attempt, approval, graph-version, audit, and metric rows. | Owned the parent-thread conversation and worker coordination; the repository service has no adapter proving that it was the runtime's source of truth. |
| Work execution | Does not execute an action. A worker claims a ready task and submits a result through the service API. | Workers performed development, testing, security, and review work in the session, as summarized by the reconciliation record. |
| Gates and safety | Enforces entry, high-impact task, and exit approval transitions; supports cancel, rollback, and policy validation. | Applied session-level completion gates, human approval, and safe-stop decisions as described in the reconciliation record. |
| Evidence | Exposes durable state, attempts, audit events, and per-run metrics through controller endpoints. | Produced worker reports with changed paths, checks, findings, and residual risks, but those reports are not imported into the T24 schema. |

The HTTP surface is defined in
[OrchestrationController.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationController.java)
and specified alongside the analytics contract in
[openapi.yaml](openapi.yaml). The local endpoints are not an adapter for the
external agent runtime.
There is no queue, worker process, scheduler, lease/heartbeat handler, or
external-runtime client in the orchestration package.

## Lifecycle and state model

The durable run states declared by
[V2__orchestration_and_analytics.sql](../backend/src/main/resources/db/migration/V2__orchestration_and_analytics.sql)
are `PLANNED`, `READY`, `RUNNING`, `WAITING_EXIT_APPROVAL`, `COMPLETE`,
`FAILED`, `CANCELLED`, and `ROLLED_BACK`. The transitions exercised by the
implementation are:

```text
create
  -> PLANNED
       -- ENTRY APPROVED + start --> RUNNING
                                      |\
                                      | \-- terminal task failure without usable fallback --> FAILED
                                      |
                                      +-- all task obligations satisfied --> WAITING_EXIT_APPROVAL
                                      |                                      |\
                                      |                                      | \-- EXIT REJECTED --> CANCELLED
                                      |                                      +-- EXIT APPROVED --> COMPLETE
                                      |
                                      +-- cancel or rejected entry/task approval --> CANCELLED
                                      +-- rollback from a non-terminal-rollback state --> ROLLED_BACK
```

Task states are `PENDING`, `WAITING_APPROVAL`, `READY`, `RUNNING`,
`RETRY_WAITING`, `SUCCEEDED`, `FAILED`, and `CANCELLED`. In the current code,
independent pending tasks become `READY` (or `WAITING_APPROVAL` when
high-impact), claimed tasks become `RUNNING`, successful results become
`SUCCEEDED`, and terminal failures become `FAILED`. A retryable failure is
returned directly to `READY`; the schema's `RETRY_WAITING` state is not
currently selected by the result path. This is an implementation detail and
limitation, not evidence of timed retry waiting.

Run creation and transitions are transactional in
[JdbcOrchestrationRepository.java](../backend/src/main/java/com/example/urlshortener/orchestration/JdbcOrchestrationRepository.java).
The restart test demonstrates that a run and a claimed `RUNNING` task/attempt
remain present after application restart; it does not demonstrate automatic
worker recovery or reassignment.

## Dependency graph validation and synchronization

`OrchestrationService` validates before initial persistence:

- one to 100 tasks per run, and no more than 100 after a replan;
- task keys matching `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`;
- existing, unique dependencies, with no more than 32 per task;
- no dependency cycles, including self-reference;
- existing fallback targets and an acyclic fallback graph; and
- descriptions/actions outside the prohibited local-policy terms are rejected.

The service revalidates persisted dependency and fallback rows during the
transaction that starts a run. That second check protects the start gate from
malformed persisted rows and is covered by the malformed-cycle integration
tests. The dependency and fallback checks are in
[OrchestrationService.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationService.java)
and the transactional start path is in
[JdbcOrchestrationRepository.java](../backend/src/main/java/com/example/urlshortener/orchestration/JdbcOrchestrationRepository.java).

Dependencies provide synchronization rather than a separate barrier service:

- independent tasks can all be ready and claimed concurrently by different
  workers;
- a dependent task is made ready only after its dependencies succeed;
- a failed dependency can satisfy a dependent task only when its configured
  fallback succeeds;
- a fallback task is dormant until its source fails and is activated; and
- the run enters `WAITING_EXIT_APPROVAL` only when every task obligation is
  satisfied, including an activated fallback path.

The integration test
[schedulesIndependentTasksAndWaitsAtBarrierUntilExitApproval](../backend/src/test/java/com/example/urlshortener/integration/OrchestrationAndAnalyticsIntegrationTest.java)
exercises a sequential path (`a` then `barrier`), a high-impact approval path
(`b`), and concurrent-ready work (`b` and `c`). The fallback tests in that
same file exercise dormant fallbacks, required barriers, primary success, and
fallback activation. These are worker/API coordination tests; they do not
prove that the service launches parallel operating-system processes.

## Entry, task, and exit gates

| Gate | Implemented behavior | Evidence |
|---|---|---|
| Entry | A run starts as `PLANNED`. `start` requires an `ENTRY` approval with decision `APPROVED`; graph validation also runs before `RUNNING`. A rejected entry invokes safe-stop. | `JdbcOrchestrationRepository.start/approve`; cycle and approval tests. |
| High-impact task | A task marked `highImpact` moves to `WAITING_APPROVAL` when dependencies are satisfied. A `TASK` approval moves it to `READY`; rejection cancels the run. | `schedulesIndependentTasks...`, `highImpactFallbackWaitsForTaskApprovalBeforeItCanBeClaimed`. |
| Exit | After all obligations are satisfied, the run waits in `WAITING_EXIT_APPROVAL`. `EXIT` approval is required for `COMPLETE`; rejection cancels the run. | `schedulesIndependentTasks...`, approval-rejection tests. |

Approval requests validate checkpoint, decision, actor length/non-blank, and
task key where applicable. The service records an actor string, but it does
not authenticate or authorize that actor; see [limitations](#limitations-and-capabilities-not-evidenced).

## Sequential and parallel paths

The service's scheduling unit is a task claim. A worker calls
`GET .../ready-tasks`, then claims a task using
`POST /api/orchestration/runs/{runId}/tasks/{taskKey}/claim`. Different ready
tasks can therefore follow parallel paths, while dependency rows enforce
sequential paths. A result recomputes newly eligible tasks and checks the exit
barrier in the same repository transaction.

The service does not provide a thread pool, dispatch policy, fairness policy,
worker timeout, or a process-level join. The caller is responsible for polling
and performing the work. Synchronization is durable state plus dependency
queries, not an assertion that the external agent runtime used a particular
parallelism level.

## Context and decision lineage

The repository records the following lineage fields:

- run ID, name, current graph version, creation/start/completion times, outcome,
  last error, and rollback metadata;
- task key, description, action, dependency rows, high-impact flag, worker ID,
  attempt count, output/error summaries, and rollback metadata;
- attempt number, worker ID, start/end times, state, error code, and outcome
  summary;
- approval checkpoint, decision, approving actor, reason, and timestamp; and
- graph version, parent version, trigger task key, replanning decision, and
  timestamp.

Audit rows record the task (when applicable), event type, state transition,
actor, bounded detail, and timestamp. Available event types include run
creation/start, task readiness/claim/success/failure/retry scheduling,
approvals, fallback activation, exit-gate waiting, graph replanning, safe-stop,
and rollback. The read endpoints for audit events and attempts are exposed by
[OrchestrationController.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationController.java).

This lineage is sufficient to reconstruct the service-level state transitions.
It is not a context store: prompts, model/provider identity, full worker
messages, artifacts, diffs, evidence attachments, and hashes are not stored by
T24. External session lineage is therefore limited to the summary in
[assignment-reconciliation.md](assignment-reconciliation.md) and the existing
task/handoff documents.

## Human approvals and session decisions

The repository has explicit `ENTRY`, `TASK`, and `EXIT` approval records. Tests
prove both approval and rejection paths, including cancellation metrics and
safe-stop audit events. The actor is recorded as supplied by the caller.

For the external runtime, the approved reconciliation record states that this
session had a human approval before resolving a discovered production defect,
and that the runtime safe-stopped when tests exposed an unapproved production
issue. It also records human approval of the T18 scope decision on 2026-09-04
and T24/T25 completion. Those are session-level decisions, not rows in a
repository run, because no external runtime export or imported run record is
available.

## Bounded retries and fallback

Each task declares `maxAttempts` from 1 through 3. A claim increments the
attempt number and creates a durable attempt row. Only the claiming worker may
submit the result. A retryable failure with attempts remaining returns the task
to `READY`; otherwise the task becomes `FAILED`. A non-retryable terminal
failure with no fallback fails the run and writes failure metrics.

A configured fallback must exist and the fallback graph must be acyclic. When
the source task fails, a pending fallback is activated as `READY` or
`WAITING_APPROVAL` according to its high-impact flag. A successful fallback
satisfies the source obligation for downstream dependency and exit-gate
purposes. A successful primary leaves an unused fallback dormant. These paths
are covered by the integration tests named in the dependency section.

The implementation has no retry delay/backoff, jitter, timeout, circuit
breaker, or automatic re-run worker. `retryable` is a worker-submitted result
classification; it is not independently verified by the service.

## Rollback and safe-stop

`POST .../cancel` is the explicit safe-stop path. It is also invoked for
rejected entry, task, or exit approval. It closes active attempts as failed
attempt records with cancellation detail, cancels non-terminal tasks, marks
the run `CANCELLED`, and updates metrics atomically. Late worker results are
rejected because the task is no longer `RUNNING`.

`POST .../rollback` marks the run `ROLLED_BACK`, records bounded reason and
metadata, closes active attempts with rollback evidence, preserves already
`SUCCEEDED`/`FAILED` task states, and cancels other tasks. Rollback is state
and evidence management; the repository does not call a compensation action
or undo an external deployment. The rollback and cancellation tests verify
active-attempt closure, late-result rejection, metadata, audit events, and
metrics.

## Policy guardrails

The service enforces a local/trusted-demo boundary in the task input and
request surface:

- actions/descriptions containing `public hosting`, `cloud`, `authentication`,
  `rate limiting`, `moderation`, or `link lifecycle` are rejected;
- task, dependency, fallback, name, actor, reason, decision, output, error,
  and metadata inputs have bounded syntax or lengths;
- dependency and fallback graphs are checked before start and before initial
  persistence, with a transactional persisted-graph check at start;
- database constraints restrict states, approval decisions, attempt counts,
  and relationships; and
- orchestration request bodies are subject to the existing request-size filter.

The policy test verifies rejection before repository interaction for a
prohibited task, and the controller test verifies the body and dependency
bounds:
[OrchestrationServiceTest.java](../backend/src/test/java/com/example/urlshortener/orchestration/OrchestrationServiceTest.java)
and [OrchestrationControllerIntegrationTest.java](../backend/src/test/java/com/example/urlshortener/integration/OrchestrationControllerIntegrationTest.java).

These are input guardrails, not a security boundary for untrusted public use.
There is no authentication, authorization, quota, secret scanner, destination
scanner, or approval identity provider in T24.

## Audit observability and traceability

The SQLite migration creates separate durable tables for runs, tasks,
dependencies, attempts, approvals, graph versions, audit events, and metrics,
with indexes for ready tasks, task dependencies, audit lookup, and analytics
(the latter is T25). Audit and attempt APIs return bounded summaries rather
than full worker context. Audit ordering is by occurrence time and row ID;
attempt ordering is by task key and attempt number.

This gives an evaluator a trace path of:

```text
run ID -> graph version/dependencies -> task -> attempt/worker result
       -> approval decision -> audit state transition -> terminal metrics
```

The service-level trace is queryable through the `GET` endpoints in the
controller. The general application runbook still describes the prototype's
local observability boundary; it does not claim hosted tracing, log shipping,
or alerting ([runbook.md](runbook.md)).

## Reliability metrics

For terminal outcomes, `JdbcOrchestrationRepository` writes one metrics row
with:

| Metric | Current calculation |
|---|---|
| `successRate` | succeeded task count divided by total task count |
| `retryFrequency` | tasks with attempts greater than one divided by total task count |
| `rollbackFrequency` | `1.0` when this run outcome is `ROLLED_BACK`, otherwise `0.0` |
| `mttrMs` | average duration of ended failed attempts for the run |
| `endToEndLatencyMs` | completion time minus creation time |
| `outcome` | terminal outcome such as `SUCCESS`, `FAILURE`, `CANCELLED`, or `ROLLED_BACK` |

The tests assert success and retry frequencies for recovery, failure, and
fallback paths, rollback frequency and metadata, cancellation behavior, and
positive MTTR when in-flight attempts are closed. These are per-run SQLite
metrics, not an operational SLO, cross-run aggregate, percentile series,
alert, or dashboard. Active-run metric availability is not established by the
current evidence.

## Dynamic replanning

`POST /api/orchestration/runs/{runId}/replans` adds tasks to an active run when:

1. the caller supplies the current graph version;
2. the run is `RUNNING` or `READY`;
3. the trigger task exists and is `SUCCEEDED`;
4. the additions pass the task, dependency, fallback, policy, and total-count
   validation; and
5. the repository transaction increments the graph version and stores its
   parent version, trigger task, decision, and timestamp.

The new tasks are then considered by the normal readiness computation. A stale
version is rejected with a conflict, and the integration test verifies that
version 1 becomes version 2 and that a second version-1 replan is rejected.
Replanning does not remove or edit existing tasks, is unavailable for terminal
runs, and has no separate human-approval checkpoint beyond the normal
high-impact task gate.

## Representative records and templates

### Normalized repository record derived from a passing test

This is a compact, normalized representation of the scenario in
`schedulesIndependentTasksAndWaitsAtBarrierUntilExitApproval`. It intentionally
omits generated UUIDs and test timestamps; it is not an exported run.

```yaml
record_kind: test-derived-repository-run
source: OrchestrationAndAnalyticsIntegrationTest.schedulesIndependentTasksAndWaitsAtBarrierUntilExitApproval
run:
  name: parallel
  graph_version: 1
  final_state: COMPLETE
  approvals: [ENTRY/APPROVED, TASK:b/APPROVED, EXIT/APPROVED]
tasks:
  - {key: a, state: SUCCEEDED, attempts: 1}
  - {key: b, state: SUCCEEDED, high_impact: true, attempts: 1}
  - {key: c, state: SUCCEEDED, attempts: 1}
  - {key: barrier, state: SUCCEEDED, depends_on: [b, c], attempts: 1}
observed:
  ready_order: [a, c, barrier]
  attempt_count: 4
  success_rate: 1.0
  audit_includes: [RUN_CREATED, RUN_STARTED, TASK_READY, TASK_CLAIMED, TASK_SUCCEEDED, EXIT_GATE_WAITING]
```

The test also verifies that `b` is approval-gated before it can be claimed and
that `c` can be claimed alongside it after its dependencies are satisfied.

### External-runtime record template

The following fields are the minimum useful record for a future exported
agent-runtime run. Bracketed values are intentionally unpopulated in this
workspace.

```yaml
record_kind: external-agent-runtime-run
run_id: "[not available]"
date: "[not available beyond 2026-09-04 session summary]"
input_or_requirement: "[attach approved input]"
parent_context: "[thread/session reference]"
workers: "[worker IDs, roles, and assigned tasks]"
dependency_graph: "[serialized graph and versions]"
gates: "[entry/task/exit decisions, actors, timestamps]"
decisions: "[decision log with rationale and evidence links]"
retries_or_fallbacks: "[attempts and outcomes]"
safe_stop_or_rollback: "[trigger, actor, scope, recovery evidence]"
validation: "[commands, results, and artifact references]"
changed_paths: "[worker-reported paths]"
residual_risks: "[open findings and owner]"
human_approval: "[approval record or explicit absence]"
```

## Limitations and capabilities not evidenced

The following should not be inferred from T24 or this package:

- The repository service does not launch, supervise, or dynamically assign
  agent processes. External worker execution and the service API are separate
  concerns.
- No authentication or authorization protects approval, claim, result, cancel,
  rollback, or replan endpoints. A supplied actor/worker ID is not proof of
  identity.
- No timeout, lease expiry, heartbeat, abandoned-worker recovery, durable
  message queue, back-pressure, fairness, or cross-instance coordination is
  implemented. Restart persistence retains state but does not recover work.
- Retry timing, backoff, and worker execution success are not independently
  validated; fallback and rollback metadata do not execute compensating work.
- Prompts, full context, model identity, tool calls, artifacts, diffs, hashes,
  and external logs are not part of repository lineage.
- Metrics are per-run terminal summaries. There is no evidence of aggregate
  reliability reporting, SLO evaluation, alerting, hosted tracing, or log
  shipping.
- No raw external agent-runtime record is available, so exact external run
  sequencing, worker identities, timestamps, retry counts, and gate records
  cannot be independently reconstructed here.
- T20's greenfield, brownfield, and ambiguous-requirement case studies are not
  claimed by this T19 package; they remain a separate task in
  [tasks.md](tasks.md).

The approved boundary remains local/trusted-demo and single-instance. T24 is
not evidence of a public orchestration platform or public URL-shortener
deployment.

## Evidence index

- [T18 assignment reconciliation](assignment-reconciliation.md): approved
  boundary and external-runtime summary.
- [T24 task status and dependency graph](tasks.md): task scope and completion
  basis.
- [OrchestrationService.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationService.java):
  validation, policy, approval input, and replan validation.
- [JdbcOrchestrationRepository.java](../backend/src/main/java/com/example/urlshortener/orchestration/JdbcOrchestrationRepository.java):
  durable transitions, dependencies, fallback, rollback, audit, and metrics.
- [OrchestrationController.java](../backend/src/main/java/com/example/urlshortener/orchestration/OrchestrationController.java):
  local HTTP operations and read endpoints.
- [OpenAPI contract](openapi.yaml): local analytics and orchestration HTTP
  payloads and response/status documentation.
- [V2__orchestration_and_analytics.sql](../backend/src/main/resources/db/migration/V2__orchestration_and_analytics.sql):
  durable schema and state constraints.
- [OrchestrationServiceTest.java](../backend/src/test/java/com/example/urlshortener/orchestration/OrchestrationServiceTest.java):
  pre-persistence graph and policy validation.
- [OrchestrationAndAnalyticsIntegrationTest.java](../backend/src/test/java/com/example/urlshortener/integration/OrchestrationAndAnalyticsIntegrationTest.java):
  paths, gates, synchronization, retries, fallback, stop, rollback, metrics,
  and replanning.
- [OrchestrationControllerIntegrationTest.java](../backend/src/test/java/com/example/urlshortener/integration/OrchestrationControllerIntegrationTest.java):
  request/body bounds.
- [RestartPersistenceIntegrationTest.java](../backend/src/test/java/com/example/urlshortener/integration/RestartPersistenceIntegrationTest.java):
  durable run and claimed-worker state across restart.
