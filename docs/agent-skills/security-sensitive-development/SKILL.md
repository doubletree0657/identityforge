---
name: security-sensitive-development
description: Apply IAM-focused security reasoning to IdentityForge changes. Use when modifying authentication, authorization, identity lifecycle, tenant isolation, permissions, secrets, tokens, audit behavior, or adjacent code that crosses a security boundary.
---

# Security-Sensitive Development

## Identify the security boundary

- Establish the actors, trust boundaries, tenant context, protected resources, and enforcement points affected by the change.
- Consider authentication boundaries, authorization enforcement, privilege escalation paths, tenant isolation, sensitive data handling, auditability, and applicable standards.
- Trace both allowed and denied paths, including cross-tenant and lower-privilege behavior where relevant.

## Preserve security properties

- Do not weaken security controls, validation, or secure defaults for implementation convenience.
- Keep secrets, credentials, tokens, and sensitive identity data out of inappropriate storage, responses, logs, and audit payloads.
- Preserve useful audit evidence for security-relevant actions without recording sensitive material.
- Follow project security documentation when available; consult authoritative standards when protocol semantics or interoperability are material to the decision.

## Validate risk-sensitive behavior

- Add or update focused tests for enforcement boundaries and failure cases in proportion to the risk.
- Call out unresolved security assumptions, standards gaps, or hardening work instead of implying stronger guarantees than the implementation provides.
