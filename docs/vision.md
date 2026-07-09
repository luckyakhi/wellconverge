# WellConverge — Vision

## Problem

People juggle their wellness across disconnected apps: one for habits, another for booking a coach,
another for content. Nothing *converges*. WellConverge is a single platform where a member's identity,
habits, coaching, and content live together and reinforce each other.

## Product goal

Help a member build sustainable wellness by making the next healthy action obvious, low-friction, and
connected to their goals and their coach.

## Learning goal (why this repo exists)

This is a deliberate practice ground for **Spec-Driven Development** with:

- **DDD** — model the business in a ubiquitous language, split into bounded contexts, protect
  invariants inside aggregates.
- **BDD** — express each behavior as a Gherkin scenario that runs as an automated acceptance test, so
  "the spec" and "the tests" are the same artifact.
- **Iterative vertical slices** — every increment ships a thin but complete path from UI to database.
- **Cloud-native delivery** — the same artifact runs on Docker Compose, local Kubernetes, and AWS.

## Target bounded contexts (candidate map)

These are the business capabilities we expect to model. We build them one vertical slice at a time.

| Context               | Responsibility                                                        | Status        |
|-----------------------|-----------------------------------------------------------------------|---------------|
| **Membership**        | Who the member is: registration, profile, onboarding, wellness goals  | **Iteration 1** |
| Habit Tracking        | Logging habits, streaks, reminders                                    | Planned       |
| Coaching              | Coaches, availability, appointments                                   | Planned       |
| Content               | Articles, programs, media library                                     | Planned       |
| Notifications         | Cross-context nudges and reminders                                    | Planned       |
| Insights              | Aggregated progress, reporting                                        | Later         |

`Membership` is intentionally first: nearly every other context references a member, and it is a clean
first aggregate to practice the full SDD loop on.

## Principles

1. **The spec is the contract.** Code changes start from a spec/scenario change.
2. **Make it verifiable.** If a rule can't be written as a Gherkin scenario, it isn't specified yet.
3. **Thin vertical slices.** Prefer a working end-to-end sliver over a complete-but-inert layer.
4. **Keep the domain pure.** Business rules live in framework-free code (`*-domain`).
5. **Decisions are recorded.** Non-obvious choices become ADRs.

## Non-goals (for now)

- Real payments, HIPAA/PHI-grade compliance, and multi-tenant billing.
- Native mobile apps.
- Microservice-per-context deployment — we start as a **modular monolith** and split only if a real
  force demands it (see ADR-0002).
