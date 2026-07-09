# CLAUDE.md — working agreement for AI agents on WellConverge

This repo is a hands-on learning ground for **Spec-Driven Development (SDD)**: DDD for modeling, BDD to
make specs verifiable, built as **thin end-to-end vertical slices, one at a time**. The spec is the
source of truth — code follows the spec, not the other way around.

If anything here conflicts with `docs/`, `docs/` wins and this file should be updated.

## Start here (read before changing code)

- `docs/vision.md` — product + learning goals, bounded-context map
- `docs/architecture.md` — modular monolith, hexagonal layout, and the **8-step SDD loop**
- `docs/adr/` — why the system is shaped this way (ADR-0001…0004)
- `docs/contexts/<context>/spec.md` — the **living spec** for each bounded context (start with `membership`)
- `ITERATIONS.md` — what shipped and what's next

## The SDD loop — follow it for every feature

Frame the behavior in the context `spec.md` → refine the ubiquitous language → model the
aggregate/VOs/invariants → **write Gherkin scenarios that fail first** → declare ports → implement the
domain → wire the slice (use case + adapters + UI) → record ADRs / update the spec. Do not jump to code
before the scenario exists.

## Architecture rules (non-negotiable)

- **Modular monolith** (one Spring Boot process); each **bounded context = 3 Gradle modules**.
- **Dependency direction is strict:** `adapters → application → domain`. The **domain depends on
  nothing** — no Spring, no JPA, no web on its classpath. Enforced by the module graph; don't add those
  deps to `*-domain` or `*-application`.
- `*-domain`: aggregates, value objects (Java `record`s, validate in the compact constructor), typed IDs
  (never bare `UUID`/`String`), sealed domain events. Rule violations throw `DomainException` subclasses.
- `*-application`: `port/in` (use-case interfaces + commands + views), `port/out` (repository + event
  publisher), `service/` (use-case impls). Services take an injected `Clock`. **This module owns the
  Cucumber BDD specs**, which drive the ports with **in-memory adapters** (fast, framework-free) — the
  BDD suite is NOT driven over HTTP.
- `*-adapters`: REST controllers (inbound) + JPA (outbound). Spring lives only here and in `bootstrap`.
- `bootstrap`: composition root — `@Configuration` classes wire framework-free services into beans;
  Flyway owns the schema (`ddl-auto: validate`).

## Conventions

- Packages: `com.wellconverge.<context>.<layer>` (e.g. `com.wellconverge.membership.domain`).
- Errors map to HTTP in a `*ExceptionHandler` (`@RestControllerAdvice`, RFC 7807 ProblemDetail):
  not-found → 404, uniqueness/lifecycle conflicts → 409, other domain violations → 400.
- One aggregate per repository port. Uniqueness rules that span the whole set (e.g. unique email) are
  enforced at the application boundary, not inside the aggregate — document this in the spec's invariants.
- New bounded context → copy the `membership` module shape and add it to `backend/settings.gradle.kts`.
- Record non-obvious decisions as a new numbered ADR in `docs/adr/`.

## Build & run (IMPORTANT: no JDK on this machine)

The backend is built/tested through Docker (there is no local `java`/`gradle`):

```bash
# Run the executable specification + domain tests
docker run --rm -v "$PWD/backend":/workspace -w /workspace \
  gradle:8.10-jdk21 gradle --no-daemon test

# Full local stack (Postgres + backend + React)
docker compose -f deploy/docker-compose.yml up --build   # UI :5173, API :8080
```

Frontend: `npm install` then `npm run dev` in `frontend/`. A green `gradle test` means every BDD
scenario passed — a failing scenario fails the build.

## Guardrails

- Don't weaken the domain's purity to make wiring easier — fix the wiring instead.
- Don't add a feature without a failing scenario first.
- Commit/push only when the user asks.
- Keep slices thin: prefer a working end-to-end sliver over a complete-but-inert layer.
