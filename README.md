# URL Shortener

Greenfield foundation for the URL shortener modular monolith.

## Engineering documentation

- [Requirements](docs/requirements.md)
- [Architecture and ADRs](docs/architecture.md)
- [Data model](docs/data-model.md)
- [OpenAPI contract](docs/openapi.yaml)
- [Adversarial architecture review](docs/adversarial-review.md)
- [Task plan and dependency graph](docs/tasks.md)
- [Agent handoff guide](docs/agent-handoff.md)

## Prerequisites

- Java 25
- Node.js 24 and npm

## Run locally

From `backend`, start Spring Boot:

```powershell
.\\gradlew.bat bootRun
```

From `frontend`, install dependencies and start Vite:

```powershell
npm ci
npm run dev
```

The backend listens on `http://localhost:8080` and the frontend on
`http://localhost:5173`.

## Configuration

- `SERVER_PORT`: backend port, default `8080`
- `DATABASE_PATH`: SQLite file path, default `./data/url-shortener.sqlite`
- `PUBLIC_ORIGIN`: configured public URL origin, default `http://localhost:8080`

Flyway migration infrastructure is configured and runs automatically at backend
startup. T06 will add the first business migration, `V1__create_links.sql`,
defined by the approved data-model contract.

## Validation

Backend:

```powershell
cd backend
.\\gradlew.bat test
.\\gradlew.bat spotlessCheck
```

Frontend:

```powershell
cd frontend
npm ci
npm test
npm run lint
npm run format
npm run build
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

This foundation contains no URL creation, short-code generation, redirects,
analytics, authentication, rate limiting, or business-specific database tables.
