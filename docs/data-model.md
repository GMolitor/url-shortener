# URL Shortener Data Model

## Scope

The prototype has one business table, `links`. SQLite is the durable source of
truth for one backend instance. This document defines the schema contract for
T05/T06; it does not authorize repository or migration implementation outside
those tasks.

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
- Later changes use monotonically versioned migrations and must not edit an
  already-applied migration.
- T06 must test clean installation, startup against the latest schema,
  restart persistence, uniqueness, constraint rejection, and migration
  failure handling.
- Migration application is expected to be transactional where SQLite/Flyway
  support permits; a failed startup must not advertise a healthy database.
- Schema changes must remain compatible with the one-instance SQLite boundary
  until a separately approved public-service database design replaces it.

## Out of scope

The MVP has no aliases, expiration, deletion/disable state, ownership,
analytics, click counters, or destination deduplication. Adding any of these
requires a new migration and an updated approved API/domain contract.
