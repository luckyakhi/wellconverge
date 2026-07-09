# WellConverge — Architecture & the Spec-Driven Workflow

## 1. Shape of the system

A **modular monolith** (ADR-0002): one deployable Spring Boot process composed of independent
**bounded-context modules**. Each context is internally structured with **hexagonal architecture**
(ports & adapters, ADR-0003) so the domain stays pure and framework-free.

```
                ┌─────────────────────────── bootstrap (Spring Boot) ───────────────────────────┐
                │   wires adapters, config, DataSource, Flyway; the only deployable artifact       │
                └───────────────▲───────────────────────────────────────────────▲─────────────────┘
                                │                                                 │
   ┌──────────── membership (bounded context) ───────────┐        ┌──── habit-tracking (future) ────┐
   │  membership-adapters                                 │        │  ...                             │
   │   ├─ inbound:  REST controllers  ───────┐            │        │                                  │
   │   └─ outbound: JPA repository  ◄──┐      │            │        │                                  │
   │  membership-application            │      ▼            │        │                                  │
   │   ├─ ports.in:  use case ifaces  ◄─┼── drives ── UI/HTTP        │                                  │
   │   ├─ ports.out: repository iface ─┘   (implemented by outbound) │                                  │
   │   └─ services:  use case impls                       │        │                                  │
   │  membership-domain  (pure Java: aggregates, VOs, events, rules)│                                  │
   └──────────────────────────────────────────────────────┘        └──────────────────────────────────┘
```

### Dependency rule (must always hold)

```
adapters  ──▶  application  ──▶  domain
                                   ▲
                                   └── depends on NOTHING (no Spring, no JPA, no web)
```

Adapters may depend on application and domain. Application may depend on domain. **The domain depends
on nothing.** This is what keeps business rules testable in milliseconds and portable across frameworks.

## 2. Anatomy of a bounded context

Each context is three Gradle modules:

| Module                  | Contains                                                            | May import                     |
|-------------------------|--------------------------------------------------------------------|--------------------------------|
| `*-domain`              | Aggregates, value objects, domain events, domain services, rules   | JDK only                       |
| `*-application`         | Use-case interfaces (`ports.in`), repository ports (`ports.out`), use-case implementations, commands. **Owns the BDD acceptance specs.** | its `-domain`                  |
| `*-adapters`            | REST controllers (inbound), JPA entities + repository impls (outbound), mappers | its `-application` + Spring    |

## 3. The Spec-Driven Development loop

Every sub-feature follows the same eight steps. **Do them in order.**

1. **Frame** — state the user-visible behavior in one or two sentences in the context `spec.md`.
2. **Ubiquitous language** — add/clarify the terms in the spec's glossary. Names in code == names here.
3. **Model** — sketch the aggregate(s), value objects, invariants, and domain events in `spec.md`.
4. **Specify (executable)** — write Gherkin `.feature` scenarios for the behavior. These are the
   acceptance criteria and they must fail first (red).
5. **Design ports** — declare the inbound use-case interface and any outbound ports needed.
6. **Implement domain** — write the aggregate/VO logic to satisfy the rules. Unit-test invariants.
7. **Wire the slice** — implement the use case + adapters (REST, JPA) + UI so the scenario is green
   end to end.
8. **Record & reflect** — capture non-obvious decisions as ADRs; update the spec's status.

> **Executable specification:** our Cucumber scenarios drive the **application ports**, not HTTP. They
> run with in-memory adapters, so they are fast and test *behavior*, not plumbing. HTTP/JPA are covered
> by thinner integration tests. This keeps the BDD suite as a true living specification of the domain.

## 4. Testing strategy (the pyramid)

| Level                | Tooling                         | Scope                                            |
|----------------------|---------------------------------|--------------------------------------------------|
| Domain unit tests    | JUnit 5                         | Aggregate invariants, value-object validation    |
| **Acceptance / BDD** | **Cucumber-JVM + JUnit 5**     | Use-case behavior via ports (in-memory adapters)  |
| Integration          | Spring Boot Test + Testcontainers (later) | REST ↔ application, JPA ↔ Postgres      |
| E2E (later)          | Playwright                     | React ↔ API ↔ DB                                 |

## 5. Runtime & delivery evolution

| Stage        | How it runs                                             | Status   |
|--------------|---------------------------------------------------------|----------|
| Iteration 1  | `docker compose` — Spring Boot + Postgres + React        | **now**  |
| Later        | Local Kubernetes (kind/minikube), kustomize/helm         | planned  |
| Later        | AWS Fargate/EKS provisioned with Terraform               | planned  |

## 6. Conventions

- **Packages:** `com.wellconverge.<context>.<layer>` e.g. `com.wellconverge.membership.domain`.
- **Value objects & commands:** Java `record`s; validate in the compact constructor.
- **IDs:** typed value objects wrapping `UUID` (e.g. `MemberId`), never bare strings.
- **Errors:** domain throws context exceptions (e.g. `EmailAlreadyRegisteredException`); adapters map
  them to HTTP status codes.
- **One aggregate per repository port.**
