# IdentityForge Documentation Map

Use this page when reviewing the repository without running it. The root
[README](../README.md) is the product overview and quick start; the documents
below provide implementation evidence and explicit boundaries.

## Recommended Review Order

1. [Portfolio Review Guide](demos/portfolio-review.md) — a five-minute static
   review path and a narrated local demonstration.
2. [Architecture Foundation](architecture/README.md) — module ownership,
   trust boundaries, and intentional modular-monolith trade-offs.
3. [SCIM 2.0 Supported Subset](protocols/scim.md) — endpoint matrix,
   concurrency behavior, protocol errors, and non-goals.
4. [Session and Token Hardening](security/session-and-token-hardening.md) —
   session limits, rotation, replay handling, revocation, and remaining
   distributed-systems gaps.
5. [MFA Design](security/mfa.md) — one-time credential boundaries, recovery
   codes, audit behavior, and production hardening needs.
6. [External Payroll Resource Service](demos/external-payroll-resource-service.md)
   — issuer, audience, and scope enforcement across a separate process.

## Durable Design Decisions

- [ADR 0001: Modular Monolith](architecture/decisions/0001-modular-monolith.md)
- [ADR 0002: Realm Identity and Platform Authority](architecture/decisions/0002-realm-identity-and-platform-authority.md)
- [ADR 0003: Single-Issuer Token Trust](architecture/decisions/0003-single-issuer-token-trust.md)
- [ADR 0004: Clean Schema Baseline](architecture/decisions/0004-clean-schema-baseline.md)

## Reviewer Assets

- [Screenshot Capture Guide](demos/screenshots.md) defines the canonical visual
  sequence, safe capture rules, filenames, and acceptance criteria.
- [Roadmap](ROADMAP.md) distinguishes completed portfolio slices from later
  protocol and production-hardening work.

Documentation describes the repository as implemented. “Portfolio-grade” means
the system demonstrates coherent engineering and security trade-offs; it does
not mean production-ready, certified, highly available, or suitable for real
identity or payroll data.
