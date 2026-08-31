# Adversarial Architecture Review

## Verdict

`ARCHITECTURE_APPROVED_WITH_CHANGES`

The modular-monolith design is appropriate for the local prototype, subject to the constraints below.

## Required changes

1. Define capacity and deployment targets before public use.
2. Treat anonymous rate limiting as blocking for public deployment.
3. Use an explicit configured public origin, never an untrusted host header.
4. Use one versioned migration strategy.
5. Publish the complete API contract and error mappings.
6. Test URL-security cases, concurrency, database failures, and collision exhaustion.
7. Document SQLite’s single-instance boundary and database operations.

## T01 traceability

- **AR-001 (capacity/latency):** addressed by the explicit local prototype
  bounds and warm-instance planning targets in `requirements.md`.
- **AR-002 (abuse policy):** addressed at the requirements boundary by
  prohibiting public deployment until rate limiting, quotas, and additional
  abuse controls are implemented. The control itself remains deferred from
  the local MVP.
- **AR-003 (host trust), AR-004 (SQLite boundary), AR-005 (migrations), and
  AR-010 (public redirect abuse):** addressed in the T02 architecture, subject
  to implementation and validation evidence.
- **AR-006 (complete API contract):** assigned to T04.
- **AR-007 (concurrency), AR-008 (URL security), and implementation evidence
  for AR-003/AR-005:** assigned to T06/T07/T08/T10/T12/T13/T14 as applicable.
- **AR-009 (capacity), AR-011 (backups), and AR-012 (link lifecycle):** remain
  production-readiness work beyond the prototype.

T02 must review these dispositions against the human-approved T01 baseline.
The approved baseline covers the initial greenfield scenario prototype; the
eventual production-ready service remains subject to the additional controls
and validation identified in this review.

T02 was approved on 2026-08-31. Architectural resolution does not waive the
implementation, test, or production-readiness gates assigned above.

## Key findings

| ID | Severity | Finding | Resolution |
|---|---|---|---|
| AR-001 | High | No capacity or latency target | Define prototype throughput, volume, and latency bounds |
| AR-002 | High | No abuse policy | Add quotas/rate limiting for public deployment or prohibit public use |
| AR-003 | High | Host-header poisoning risk | Require configured public origin |
| AR-004 | High | SQLite is a single-instance bottleneck | Document boundary and preserve repository abstraction |
| AR-005 | High | Migration strategy was ambiguous | Select versioned migrations and test clean/latest upgrades |
| AR-006 | High | API contract was incomplete | Publish OpenAPI and exact status/error behavior |
| AR-007 | High | No concurrency testing | Add concurrent creation and collision tests |
| AR-008 | High | URL-security coverage was incomplete | Test dangerous schemes, credentials, controls, ports, and encoding |
| AR-009 | Medium | Seven-character capacity may become collision-prone | Bound prototype volume or make length configurable/increase it |
| AR-010 | High | Arbitrary redirects enable phishing | Keep local-only restriction; add moderation/reputation controls before public use |
| AR-011 | Medium | No backup/corruption procedure | Document backup/integrity requirements before production |
| AR-012 | Medium | No link lifecycle state | Add disable/removal capability before public deployment |

## Unnecessary complexity to avoid

- Separate backend services
- Docker or external infrastructure
- Distributed tracing
- Analytics pipelines
- Caching before measurement
- Authentication before the product requires ownership
