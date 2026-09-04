# Operations and Testing Runbook

This runbook describes the local/trusted-demo production-ready prototype. It is the
operational companion to the [requirements](requirements.md),
[architecture](architecture.md), and [OpenAPI contract](openapi.yaml).

## Local setup

### Prerequisites

- Java 25
- Node.js 24 and npm
- No external services; SQLite is file-backed and Flyway runs at backend startup

### Start the services

From `backend`, start Spring Boot:

```powershell
.\gradlew.bat bootRun
```

From `frontend`, install the locked dependencies and start Vite:

```powershell
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` and `/actuator` to the
backend at `http://localhost:8080` during development. The backend binds to
`127.0.0.1` by default.

### Configuration

Set these environment variables before starting the backend when overrides are
needed:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Backend listening port |
| `DATABASE_PATH` | `./data/url-shortener.sqlite` | SQLite database file |
| `PUBLIC_ORIGIN` | `http://localhost:8080` | Trusted origin used in returned short URLs |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | Sole allowed CORS origin |

`PUBLIC_ORIGIN` is configuration, not derived from `Host` or forwarded headers.
Keep it aligned with the address users should receive in API responses.
Both origin settings are validated at startup and must be absolute HTTP(S)
origins without credentials, paths, queries, fragments, or wildcards. An
invalid value fails startup with the setting name in the diagnostic.

## API quick reference

The authoritative contract is [openapi.yaml](openapi.yaml). A smoke request
against the default backend:

```powershell
$response = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/links `
  -ContentType 'application/json' `
  -Body '{"url":"https://example.com/docs"}'
$response
```

The response contains `code`, `shortUrl`, `destinationUrl`, and UTC
`createdAt`. Visit the returned `shortUrl` or request it with redirects
disabled to inspect the `302 Location` response:

```powershell
curl.exe -i $response.shortUrl
```

Expected API behavior:

- `POST /api/links`: `201` after durable persistence.
- `GET /{code}`: `302` with `Location` and `Cache-Control: no-store` for a
  known seven-character Base62 code.
- Unknown codes: `404` with `CODE_NOT_FOUND`.
- Invalid JSON, fields, URLs, or code paths: `400` with the appropriate stable
  error code.
- Non-JSON create requests: `415` with `UNSUPPORTED_MEDIA_TYPE`.
- Requests over 4,096 bytes: `413` with `REQUEST_TOO_LARGE`, regardless of
  whether the request uses a declared length or chunked transfer encoding.
- Persistence failures: `500` with `STORAGE_FAILURE`.
- Code-allocation exhaustion: `503` with `SERVICE_UNAVAILABLE`.

Destination URLs are validated syntactically and stored as supplied; the
backend never resolves, fetches, previews, or proxies them. Loopback and
private-network destinations are intentionally accepted within this
local/trusted-demo boundary. They must not be treated as safe for a future
public deployment without a separately approved abuse and destination-safety
design.

Error responses contain `status`, `errorCode`, `message`, `requestId`, and
UTC `timestamp`. Messages and logs do not expose stack traces, SQL details,
secrets, or full destination URLs.

## Health and observability

Check application and SQLite health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Healthy startup returns HTTP `200` with status `UP`; an unavailable database or
application component returns HTTP `503` with status `DOWN`. Every request
receives an `X-Request-Id` response header and an access log with the event,
method, path, status, duration, and request ID. Storage and unexpected failures
log the exception type and correlation fields without sensitive exception
messages.

## Database and migration operations

The database is created at `DATABASE_PATH` and migrations are applied by
Flyway. The current business migration is `V1__create_links.sql`, which owns
the `links` table and its constraints. Never edit an already-applied migration.

For a checksum mismatch or intentionally disposable local database:

1. Stop the backend.
2. Copy the SQLite file to a safe backup location if its data matters.
3. Remove only the configured local database file (and its SQLite `-shm` or
   `-wal` sidecars if present).
4. Restart the backend and allow Flyway to recreate the schema.

For a useful local backup, stop the backend first so no write is in progress,
then copy the configured SQLite file. Restore only while the backend is
stopped. Production backup, integrity checking, retention, and corruption
recovery are not prototype requirements; they would be designed separately
before any public deployment.

## Shutdown and failure handling

Stop `bootRun` with `Ctrl+C`; Spring Boot uses graceful shutdown with a
ten-second shutdown phase. If startup fails, inspect the first migration or
datasource error, correct configuration or file permissions, and restart.

The prototype is single-instance. Do not point multiple backend processes at
the same SQLite file or expose the service to untrusted public traffic.
Anonymous creation has no rate limiting, quota, moderation, or link lifecycle
controls. These are limitations of the local prototype and would be release
blockers only for a future public service.

## Testing and quality checks

Run backend checks from `backend`:

```powershell
.\gradlew.bat test
.\gradlew.bat spotlessCheck
```

Run frontend checks from `frontend`:

```powershell
npm ci
npm test
npm run lint
npm run format
npm run build
```

The backend suite covers URL policy, secure code generation, reserved paths,
collision retries, API/error behavior, CORS and origin handling, SQLite
persistence, migration constraints, restart persistence, concurrency, health,
failure handling, and request/log redaction. The frontend suite covers the
accessible form, client validation, loading and error states, successful result
display, network fallback, and clipboard success/failure.

Before handing off a change:

1. Run the relevant focused tests while iterating.
2. Run the complete backend or frontend checks for the affected area.
3. Review the complete diff and confirm no database, build output, dependency
   cache, environment file, or secret is staged.
4. Update the relevant requirements, architecture, API, data-model, or
   operational documentation when behavior changes.

## Current limitations

- Prototype capacity is 10 concurrent clients, 100 creates per minute, and
  1,000 persisted links.
- Request-size rejection reads at most one byte beyond the 4,096-byte limit,
  so chunked requests cannot bypass the body cap.
- Anonymous creation is abuse-prone because rate limiting and quotas are not
  implemented.
- SQLite is not suitable for high write concurrency or multiple instances.
- Links cannot be deleted, disabled, expired, or assigned to an owner.
- Destination safety is syntactic; the service does not assess reputation.
- Backups, metrics, tracing, distributed controls, and public deployment
  operations are outside the prototype.
