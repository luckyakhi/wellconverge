# ADR-0004: Java 21 + Gradle (Kotlin DSL) + Spring Boot 3

- **Status:** Accepted
- **Date:** 2026-07-09

## Context

The backend needs a build tool and framework that support clean multi-module boundaries, are strong for
enterprise/DDD work, and deploy comfortably to AWS Fargate/EKS.

## Decision

- **Java 21 (LTS)** — records, sealed types, pattern matching suit DDD value objects and events.
- **Gradle with the Kotlin DSL**, multi-module, using a version catalog (`gradle/libs.versions.toml`)
  for centralized dependency versions. Chosen over Maven for better multi-module ergonomics and
  incremental builds.
- **Spring Boot 3.x** in the `bootstrap` module and adapters only (Web, Data JPA, Validation). The
  domain and application layers stay Spring-free.

## Consequences

- Wide ecosystem, easy containerization, good AWS story.
- Gradle Kotlin DSL is less familiar to some than Maven XML — acceptable, and a useful thing to learn.
- We must discipline ourselves to keep Spring out of `-domain`/`-application` (enforced by not putting
  Spring on those modules' classpaths).
