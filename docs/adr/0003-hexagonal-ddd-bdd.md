# ADR-0003: Hexagonal architecture, DDD tactical patterns, and BDD as the executable spec

- **Status:** Accepted
- **Date:** 2026-07-09

## Context

We want business rules that are (a) expressed in the domain language, (b) verifiable, and (c) not
entangled with Spring/JPA/HTTP. We also want "the spec" and "the acceptance tests" to be one artifact.

## Decision

1. **Hexagonal (ports & adapters)** per bounded context: a pure `-domain`, an `-application` layer that
   declares inbound use-case ports and outbound repository ports, and `-adapters` for REST/JPA.
2. **DDD tactical patterns:** aggregates guard invariants; value objects are immutable `record`s that
   validate on construction; typed IDs; domain events for significant state changes.
3. **BDD as executable specification:** behavior is written in Gherkin and executed by Cucumber-JVM.
   Scenarios drive the **application ports** with **in-memory outbound adapters**, so they test domain
   behavior fast and framework-free. HTTP and persistence get separate, thinner integration tests.

## Consequences

- Domain logic is unit-testable in milliseconds and portable.
- The Gherkin suite doubles as living, human-readable documentation of every rule.
- Slightly more indirection (ports/mappers) than a layered Spring app — accepted as the point of the
  exercise.
- Driving BDD at the port (not HTTP) means a passing spec does not by itself prove the wiring; we cover
  wiring with dedicated integration tests.
