# ADR 0001: Capability-Oriented Modular Monolith

## Status

Accepted

## Decision

Keep one Spring Boot deployable and organize backend behavior by capability.
Declare top-level modules with Spring Modulith and verify dependency cycles in
the test suite. Keep the directory identity/access aggregate together for now,
while making its internal policy ownership explicit.

Modules remain open during the transition because several application APIs
still expose owned JPA entities. New cross-module contracts should be narrow and
immutable; closing modules is a later consolidation step after those entity
contracts have been removed.

## Consequences

Ownership and dependency direction are reviewable without introducing network
or distributed-transaction complexity. The remaining open-module surface is a
known maintainability constraint, not permission to create new repository-level
coupling.
