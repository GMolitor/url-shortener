# Assignment Reconciliation (T18)

**Date:** 2026-09-04  
**Status:** Approved by the human reviewer on 2026-09-04; T24/T25 complete

## Purpose

This record reconciles the external assignment findings with the implemented
local URL-shortener prototype. It records the scope decision that follows the
assignment review. The existing local deployment boundary remains in place,
but T24/T25 were authorized to add the repository-contained orchestration
service and analytics capabilities described below, and both implementations
are now complete.

This is the T18 scope record. Later task completion and release-review status
are maintained in [`tasks.md`](tasks.md) and
[`final-release-review.md`](final-release-review.md); this record preserves the
approval decision and the state of the decision gate when it was made.

## Decision 1: orchestration-layer boundary

The external Orchestration Agent remains the execution coordinator for this
development session, but it is not sufficient as the assignment deliverable by
itself. T24 adds a repository-contained orchestration control plane that an
evaluator can run locally.

The agentic execution used for the brownfield work demonstrates these control
behaviors at the agent-runtime boundary:

- a stateful parent thread coordinating separate worker contexts;
- dependency-aware sequencing of coder, tester, security, and review stages;
- explicit completion gates and human approval before resolving a discovered
  production defect;
- bounded wait/retry behavior and progress requests for long-running workers;
- safe-stop behavior when tests exposed an unapproved production issue;
- worker result reports containing changed paths, checks, findings, and
  residual risks; and
- replanning after the tester found the empty-port validation defect.

The repository now contains the executable service as well as the task graph
and handoff records. T19 must distinguish behavior demonstrated by T24 from
behavior provided by the external agent runtime.

## Decision 2: analytics boundary

Analytics are required by the assignment and were implemented under T25.

The existing MVP requirements and architecture originally excluded analytics.
That scope exception was superseded by the human approval for T25. Redirects
now publish durable analytics events through a bounded, failure-isolated local
publisher, with aggregate read APIs and documented local retention/privacy
boundaries, without undermining the single-instance boundary or breaking the
redirect contract.

T25 defines the event model, persistence impact, failure isolation, API contract,
privacy boundary, and validation. Analytics must not delay or break redirects.
The API is `GET /api/analytics/{code}` with optional `from` and `to` RFC-3339
parameters; it returns totals and hourly UTC buckets for at most 366 days.
Events contain only the short code and UTC time. There is no client identity,
IP address, user agent, automated retention/deletion operation, or analytics UI.
The bounded writer may lose events during queue saturation, persistence failure,
or forced shutdown.

## Reconciliation matrix

| Assignment concern | Current disposition | Evidence or next task |
|---|---|---|
| Runnable URL shortener | Implemented for local/trusted-demo use | T01–T17 and A01–A05 evidence |
| External agentic orchestration | Demonstrated at runtime and supplemented by the repository service | T24 and T19 |
| Dependency graphs and gates | Implemented in the repository service and used during agent execution | T24, `docs/tasks.md`, T19 |
| Human approvals and safe stops | Demonstrated in the agent run | T19 run records |
| Retries, fallback, rollback, metrics, policy runtime | Implemented and tested in the repository service; runtime evidence still needs packaging | T24 and T19 |
| Greenfield/brownfield/ambiguous demonstrations | Packaged as evidence-based case studies; no raw external run export is claimed | T20 |
| Analytics | Implemented as a non-blocking local capability with aggregate reads | T25 and T22 |
| Final engineering summary | Consolidated with assignment-to-artifact traceability | T21 |

## Approval gate

T24 and T25 are complete under these approved scope decisions. T19, T20, and T21
are complete using the implementation and validation evidence. T22 is the
remaining documentation consistency pass:

1. orchestration evidence describes both the repository service and the external
   agent runtime, including their separate responsibilities;
2. analytics is implemented as a non-blocking local capability under T25, with
   remaining assignment limitations explicitly documented; and
3. T22 synchronizes the original MVP documents and OpenAPI with those approved
   local additions without expanding the public-service boundary.

This decision does not approve public deployment and does not reopen completed
greenfield or brownfield application work.
