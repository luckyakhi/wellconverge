# WellConverge

A wellness platform, built as a **hands-on learning ground for Spec-Driven Development (SDD)** in the
agentic era. Every feature travels the same path:

> **Vision → Bounded-context spec → Executable specification (BDD) → Domain model (DDD) → Vertical slice (API + UI + data) → Deploy**

Specs are the source of truth. BDD (Gherkin + Cucumber) makes them *verifiable*. DDD keeps the model
honest. We build **one thin end-to-end sub-feature at a time**.

## Tech at a glance

| Layer      | Choice                                                            |
|------------|------------------------------------------------------------------|
| Backend    | Java 21, Gradle (Kotlin DSL) multi-module, Spring Boot 3         |
| Modeling   | Domain-Driven Design, hexagonal (ports & adapters) per context   |
| Specs      | BDD with Gherkin + Cucumber-JVM (executable acceptance tests)    |
| Frontend   | React + TypeScript (Vite)                                        |
| Data       | PostgreSQL, Flyway migrations                                    |
| Local run  | Docker Compose (now) → local Kubernetes (later)                  |
| Cloud      | AWS Fargate/EKS via Terraform (later)                            |

## Repository layout

```
wellconverge/
├── docs/                     # The "spec" half of spec-driven development
│   ├── vision.md             # What & why of the whole platform
│   ├── architecture.md       # How the pieces fit; the SDD workflow
│   ├── adr/                  # Architecture Decision Records
│   └── contexts/             # One folder per DDD bounded context
│       └── membership/       # ← first context (spec.md)
├── backend/                  # Gradle multi-module Java backend
│   ├── membership/           # First bounded context (hexagonal)
│   │   ├── membership-domain/        # Pure domain — no framework
│   │   ├── membership-application/   # Use cases + ports + BDD specs
│   │   └── membership-adapters/      # REST in, JPA out
│   └── bootstrap/            # Spring Boot deployable that wires it all
├── frontend/                 # React + TS app
└── deploy/                   # docker-compose (k8s + terraform later)
```

## Current status — Iteration 1

**Membership context: register a member + complete onboarding.**

- [x] Vision, architecture, ADRs, and the Membership context spec
- [x] Executable BDD acceptance specs (Cucumber) at the application boundary
- [x] `Member` aggregate + value objects (hexagonal domain)
- [x] Application use cases (`RegisterMember`, `CompleteOnboarding`) + ports
- [x] REST + JPA adapters, Spring Boot bootstrap, Flyway schema
- [x] React signup + profile UI
- [x] docker-compose (app + Postgres)

See [`docs/contexts/membership/spec.md`](docs/contexts/membership/spec.md) for the living spec and
[`ITERATIONS.md`](ITERATIONS.md) for the roadmap.

## Prerequisites

This environment has Node, Docker, kubectl, and Terraform, but **not a JDK**. To build/run the backend
locally you need:

- **JDK 21** (e.g. `sdk install java 21-tem` via [SDKMAN](https://sdkman.io), or Temurin 21)
- Gradle is provided via the wrapper. If `backend/gradle/wrapper/gradle-wrapper.jar` is missing, run
  `gradle wrapper --gradle-version 8.10` once from `backend/` to generate it (needs a system Gradle),
  or let the Docker build fetch it.

## Quick start

```bash
# Backend acceptance specs (the verifiable BDD specs) — fast, no DB
cd backend && ./gradlew :membership:membership-application:test

# Full stack locally
docker compose -f deploy/docker-compose.yml up --build
# API:      http://localhost:8080/api/members
# Frontend: http://localhost:5173
```
