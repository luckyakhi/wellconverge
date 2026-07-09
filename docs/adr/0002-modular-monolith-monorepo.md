# ADR-0002: Modular monolith in a monorepo

- **Status:** Accepted
- **Date:** 2026-07-09

## Context

WellConverge will grow several bounded contexts (Membership, Habit Tracking, Coaching, …). We could
start as microservices or as a single monolith. The primary goal is *learning DDD/BDD/SDD*, not scaling
to millions of users. Premature microservices add network boundaries, distributed-transaction pain, and
operational overhead that obscure the modeling lessons.

## Decision

- **One monorepo** holding backend, frontend, and infrastructure.
- **One deployable modular monolith** for the backend: bounded contexts are enforced as separate Gradle
  modules with a strict dependency direction, but they run in a single Spring Boot process.
- Contexts communicate in-process for now; a context never reaches into another's internals — only
  through published application ports / events.

## Consequences

- Fast to build and reason about; easy local run (one process + Postgres).
- Module boundaries + the dependency rule keep us honest, so a future split to services is mechanical,
  not a rewrite.
- We must resist cross-context coupling; the Gradle module graph makes violations compile errors.
- Single database schema for now (schema-per-context namespacing via table prefixes / Flyway).
