# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-07-09

## Context

This is a learning project practicing spec-driven development. Decisions and *their rationale* are part
of what we want to learn from later. Undocumented decisions get re-litigated and their context is lost.

## Decision

We record every architecturally significant decision as a short Architecture Decision Record (ADR) in
`docs/adr/`, numbered sequentially, using the format: Context / Decision / Consequences. ADRs are
immutable once accepted; we supersede rather than edit.

## Consequences

- A newcomer (or an AI agent) can reconstruct *why* the system is shaped as it is.
- Small overhead per decision; we only record *significant* ones, not every choice.
