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
| Local run  | Mac dev: plain local processes (no Docker) · Windows: local Kubernetes |
| Cloud      | AWS ECS Fargate via Terraform                                    |

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
├── agents/                   # A2A multi-agent demo (Concierge ↔ Membership Ops), see agents/README.md
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

## Three ways to run this

| Machine     | How                        | Where                |
|-------------|----------------------------|-----------------------|
| Mac (dev)   | Plain local processes, no Docker | Homebrew JDK 21 + Gradle 8.10 + `postgresql@16` |
| Windows     | Local Kubernetes            | `deploy/k8s/` (kustomize) |
| AWS         | ECS Fargate                 | `deploy/terraform/` — runbook: [`deploy/terraform/README.md`](deploy/terraform/README.md) |

## Prerequisites

- **JDK 21** — on Mac: `brew install openjdk@21` (or Corretto/Temurin 21)
- **Gradle 8.10** — on Mac: `brew install gradle`. Gradle is also provided via the wrapper; if
  `backend/gradle/wrapper/gradle-wrapper.jar` is missing, run `gradle wrapper --gradle-version 8.10`
  once from `backend/` to generate it (needs a system Gradle), or let the Docker build fetch it.
- **PostgreSQL 16** — on Mac: `brew install postgresql@16` (see below), or Docker/Postgres.app
- Node (for the frontend), and Docker/kubectl/Terraform only if you're doing container/k8s/cloud work.

## Quick start

```bash
# Backend acceptance specs (the verifiable BDD specs) — fast, no DB
cd backend && ./gradlew :membership:membership-application:test

# Full stack via Docker
docker compose -f deploy/docker-compose.yml up --build
# API:      http://localhost:8080/api/members
# Frontend: http://localhost:5173
```

## Running the servers locally (no Docker)

Requires Postgres running locally with a `wellconverge`/`wellconverge` role+database on `5432` (matches
the defaults in `backend/bootstrap/src/main/resources/application.yml`), JDK 21 on `JAVA_HOME`, and
Gradle 8.10 (this repo has no committed `gradlew` wrapper jar, only `gradle-wrapper.properties` — install
Gradle separately, e.g. from [gradle.org](https://gradle.org/releases/)).

### Starting Postgres locally

Pick one:

**Option A — Homebrew Postgres** (recommended for Mac dev — no Docker):

```bash
brew install postgresql@16
brew services start postgresql@16
createuser -s wellconverge          # if it doesn't already exist
psql -d postgres -c "ALTER ROLE wellconverge WITH PASSWORD 'wellconverge';"
createdb -O wellconverge wellconverge
```

**Option B — just the `db` container from docker-compose** (if Docker is installed; gives you the exact
same Postgres version/config the full stack uses, without starting the backend/frontend containers too):

```bash
docker compose -f deploy/docker-compose.yml up -d db
```

**Option C — Postgres.app** (macOS, no Docker): install from [postgresapp.com](https://postgresapp.com),
start it, then create the role + database once:

```bash
psql -U postgres -h localhost -c "CREATE ROLE wellconverge WITH LOGIN PASSWORD 'wellconverge' SUPERUSER;"
createdb -U postgres -h localhost -O wellconverge wellconverge
```

Flyway (run automatically by `bootRun`) owns the schema — no manual migrations needed once the empty
`wellconverge` database exists.

```bash
# Backend — from backend/, runs on :8080
cd backend && gradle :bootstrap:bootRun

# Frontend — from frontend/, runs on :5173 (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

## Running on Windows (local Kubernetes)

```bash
kubectl apply -k deploy/k8s/
```

Manifests: `namespace.yaml`, `postgres-secret.yaml` + `postgres.yaml`, `backend.yaml`, `frontend.yaml` —
see `deploy/k8s/kustomization.yaml`.

## Running on AWS (ECS Fargate)

Provisioned via Terraform — ECS Fargate, RDS, ALB, GitHub OIDC for CI deploys. See `deploy/terraform/`.
