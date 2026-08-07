# ADR 0004: Clean Pre-Release Schema Baseline

## Status

Accepted

## Decision

IdentityForge has no retained production data. Rewrite and squash `V1__init.sql`
as the architectural baseline rather than carrying compatibility migrations for
disposable development databases.

The baseline adds normalized usernames, separate platform authority, global
client IDs, optimistic versions, lifecycle and audit fields, and database
checks for tenant-owned relationships. Local databases created by older
revisions must be reset with `./scripts/reset-local-db.sh`.

## Consequences

The schema clearly represents the intended model without transitional nullable
columns or backfill code. This decision must change before any environment has
data that must survive upgrades; from that point onward, applied migrations are
immutable and changes use additive `V2+` migrations.
