# URL Shortener Data Model

## Scope

The URL-shortener mapping remains one business table, `links`. V2 adds local
orchestration and analytics tables for the approved T24/T25 capabilities. SQLite
is the durable source of truth for one backend instance. This document defines
the schema contract and does not authorize a hosted or multi-instance design.

## `links` table

| Column | SQLite type | Nullability | Constraints | Purpose |
|---|---|---:|---|---|
| `id` | `INTEGER` | NOT NULL | `PRIMARY KEY` | Internal identity; never exposed as the short code |
| `code` | `TEXT` | NOT NULL | `UNIQUE`, binary/case-sensitive, exactly 7 Base62 characters | Public lookup key |
| `destination_url` | `TEXT` | NOT NULL | length 1–2048 characters | Validated HTTP/HTTPS destination |
| `created_at` | `TEXT` | NOT NULL | UTC instant in ISO-8601/RFC-3339 form | Creation time |

Canonical migration shape:

```sql
CREATE TABLE links (
    id INTEGER PRIMARY KEY,
    code TEXT NOT NULL COLLATE BINARY UNIQUE
        CHECK (length(code) = 7)
        CHECK (code NOT GLOB '*[^A-Za-z0-9]*'),
    destination_url TEXT NOT NULL
        CHECK (length(destination_url) BETWEEN 1 AND 2048),
    created_at TEXT NOT NULL
);
```

The application owns semantic URL validation (scheme, credentials, controls,
and parser behavior). Database checks provide a final size/non-empty guard and
must not be treated as a replacement for domain validation.

## Keys and indexes

- `id` is the internal integer primary key.
- The unique constraint on `code` is the collision authority and creates the
  lookup index. Do not add a second differently collated code index.
- Redirect lookup is an indexed equality lookup on the case-sensitive `code`.
- No index is required on `destination_url` or `created_at` for the MVP.
- No foreign keys are needed while `links` is the only business table.

## Timestamp contract

`created_at` is stored as UTC text in canonical RFC-3339 form with a `Z`
offset, for example `2026-08-31T14:30:00.000Z`. The service should generate
the instant and persist it in the same create operation; reads must not convert
it to local time. Future migrations must preserve this representation or
explicitly document a conversion.

## Code capacity and collision behavior

Codes use the 62-character alphabet `[A-Za-z0-9]`, are case-sensitive, and are
seven characters long. The theoretical space is:

`62^7 = 3,521,614,606,208` codes.

The prototype operating bound is 1,000 persisted links. The generator must use
a cryptographically secure source, attempt insertion, and retry only on a
unique-code conflict. Retries must be bounded; exhaustion is a controlled
server error and must not leave a partial mapping. The database constraint,
not a pre-check query, decides whether a code is available.

## Migration contract

- Flyway is the sole schema-evolution mechanism.
- The first business migration is `V1__create_links.sql` and creates the table
  above in a clean database.
- `V2__orchestration_and_analytics.sql` adds the local orchestration state and
  analytics click-event tables described below.
- Later changes use monotonically versioned migrations and must not edit an
  already-applied migration.
- T06 must test clean installation, startup against the latest schema,
  restart persistence, uniqueness, constraint rejection, and migration
  failure handling.
- Migration application is expected to be transactional where SQLite/Flyway
  support permits; a failed startup must not advertise a healthy database.
- Schema changes must remain compatible with the one-instance SQLite boundary
  until a separately approved public-service database design replaces it.

## Local analytics tables

`analytics_click_events` stores one row per accepted redirect event:

| Column | SQLite type | Nullability | Constraints | Purpose |
|---|---|---:|---|---|
| `id` | `INTEGER` | NOT NULL | `PRIMARY KEY` | Event identity |
| `code` | `TEXT` | NOT NULL | binary/case-sensitive | Short-code dimension |
| `occurred_at` | `TEXT` | NOT NULL | UTC instant in ISO-8601/RFC-3339 form | Event time |

The `(code, occurred_at)` index supports the total and hourly aggregate reads.
The table contains no destination URL, IP address, user agent, or client
identity. There is no retention or deletion job; local rows remain until the
database is reset or otherwise managed through the runbook. The asynchronous
publisher may drop events when its bounded queue or writer cannot accept them.

## Local orchestration tables

`V2__orchestration_and_analytics.sql` also creates durable tables for runs,
tasks, dependencies, attempts, approvals, graph versions, audit events, and
per-run metrics. Their state and relationship constraints are defined in that
migration. They support local worker/API coordination; no table makes the
service an agent executor or an externally authenticated control plane.

## Out of scope

The prototype has no aliases, expiration, deletion/disable state, ownership,
analytics identity dimensions, analytics retention/deletion operation,
destination deduplication, or hosted analytics service. Adding public lifecycle,
identity, retention, or multi-instance behavior requires a new migration and an
updated approved API/domain contract.
