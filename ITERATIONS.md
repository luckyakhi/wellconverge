# Iterations roadmap

We ship one thin end-to-end vertical slice per iteration. Each follows the SDD loop in
[`docs/architecture.md`](docs/architecture.md#3-the-spec-driven-development-loop).

## Iteration 1 — Membership: register + onboard  ← current

**Goal:** a person can register and complete a wellness-goal onboarding, end to end.

- [x] Vision, architecture, ADRs 0001–0004
- [x] Membership context spec + ubiquitous language
- [x] Executable BDD specs (Cucumber) for registration & onboarding
- [x] `Member` aggregate, value objects, domain events (pure domain)
- [x] Application use cases + ports + in-memory adapter for BDD
- [x] REST controller + JPA adapter + Flyway schema + Spring Boot bootstrap
- [x] React signup + profile UI
- [x] docker-compose (app + Postgres)
- [ ] **Your practice loop:** run the BDD suite, extend a scenario, watch it drive the code

## Iteration 2 — candidates (pick one when ready)

- **Auth for Membership** — password/credential + login; decide the Identity context boundary.
- **Habit Tracking slice** — second bounded context: log a habit, compute a streak (rich domain rules).
- **Local Kubernetes** — kind cluster + kustomize manifests for the current stack.
- **Domain events published** — in-process event bus so contexts react to `MemberOnboarded`.

## Backlog / later

- Testcontainers integration tests (REST↔app, JPA↔Postgres) and Playwright E2E.
- Coaching context (appointments, no double-booking).
- Content context; Notifications context.
- Terraform → AWS Fargate/EKS; CI pipeline.
