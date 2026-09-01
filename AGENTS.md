# Repository Guidelines

## Project Structure & Module Organization

- `backend/` contains the Spring Boot application, Gradle build, SQLite configuration, Flyway migrations, and Java tests.
- `backend/src/main/java/com/example/urlshortener/` contains application code organized by API, domain, service, repository, configuration, and observability responsibilities as features are added.
- `frontend/` contains the React/Vite application, TypeScript source, component tests, linting, and formatting configuration.
- `docs/` is the source of truth for requirements, architecture, ADRs, reviews, task dependencies, and agent handoffs.
- Keep local databases, build output, dependencies, IDE files, and environment secrets untracked; `.gitignore` already covers them.

## Build, Test, and Development Commands

Backend commands run from `backend/`:

```powershell
.\gradlew.bat bootRun       # Start Spring Boot
.\gradlew.bat test          # Run Java/Spring tests
.\gradlew.bat spotlessCheck # Verify Java formatting
```

Frontend commands run from `frontend/`:

```powershell
npm ci             # Install locked dependencies
npm run dev        # Start Vite
npm test           # Run Vitest
npm run lint       # Run ESLint
npm run format     # Check Prettier formatting
npm run build      # Type-check and build production assets
```

## Coding Style & Naming Conventions

Use four-space indentation for Java and two spaces for TypeScript/JSON/YAML. Use Java `PascalCase` classes, `camelCase` methods/variables, and descriptive TypeScript component names in `PascalCase`. Keep SQL migrations versioned as `V<number>__description.sql`. Run formatting and lint checks before submitting changes.

## Testing Guidelines

Backend tests use JUnit/Spring Boot; frontend tests use Vitest. Name tests after observable behavior, such as `applicationStarts` or `submitsValidUrl`. Add unit tests for isolated logic and integration tests for HTTP, SQLite, migration, concurrency, security, and restart behavior. Every behavior change should include relevant tests.

## Code Comments and Documentation

Add concise comments or Javadocs to core classes and non-obvious methods when
they explain behavior that is not apparent from the name or signature. Prioritize
security boundaries, validation rationale, persistence/concurrency invariants,
retry limits, externally visible HTTP behavior, and non-obvious algorithms.
Comments should explain why the behavior exists and identify important
constraints; do not restate the code line by line. Update comments when the
behavior changes, remove comments that become inaccurate, and remember that
comments do not replace tests or task/architecture documentation.

## Commits & Pull Requests

No Git history is available in this greenfield workspace. Use imperative, focused commit subjects such as `Add link persistence migration` or `Test redirect lookup`. Pull requests should explain the change, link the task, identify tests run, document configuration changes, and include UI screenshots when frontend behavior changes.

## Security & Configuration

Never commit secrets, `.env` files, SQLite databases, or full sensitive destination URLs in logs. Use the documented `SERVER_PORT`, `DATABASE_PATH`, and `PUBLIC_ORIGIN` variables. Read the requirements and architecture documents before changing API, persistence, redirect, or security behavior.
