# Architecture Foundation

IdentityForge is a single-deployable modular monolith. Capability ownership is
visible under `io.github.doubletree.iam`, and `ArchitectureTests` uses Spring
Modulith to verify declared dependencies and reject cycles.

| Module | Ownership |
| --- | --- |
| `shared` | Small cross-cutting contracts, errors, actor context, and web primitives |
| `directory` | Tenant lifecycle, identities, profiles, groups, roles, permissions, and delegation policy |
| `authentication` | Realm login, passwords, MFA orchestration, and authenticated principals |
| `applications` | OAuth clients, resource servers, application permissions, and secrets |
| `oauth` | Spring Authorization Server integration, token profiles, consent, issuer, and signing keys |
| `provisioning` | SCIM 2.0 subset parsing, protocol DTOs/errors, and orchestration over directory and audit use cases |
| `audit` | Security event persistence, request metadata, and tenant-scoped queries |
| `bootstrap` | Development-only assembly and seed data |

`directory` is deliberately one module at this stage because its existing JPA
aggregates share transactional identity/access relationships. Its internal
packages distinguish access policy from identity behavior. Splitting tenancy,
identity, and access into closed modules requires replacing cross-aggregate JPA
references with IDs and narrow APIs; that is postponed until it reduces real
complexity instead of creating adapter ceremony.

The module declarations are currently open while legacy entity-based module
contracts are narrowed. New cross-module behavior should prefer immutable API
results or explicit application services over reaching into another module's
persistence package.

## Security Boundaries

- A user identity is `(tenant_id, normalized_username)` and interactive login
  uses `realm/username`.
- Platform authority is separate from tenant roles. A role named
  `platform-admin` has no special trust.
- Tenant-owned associations are checked in application policy and by PostgreSQL
  triggers in the clean baseline.
- Admin tokens use the `identityforge-admin-api` audience. Application tokens
  use their resource-server identifier and omit the IAM authorization graph.
- User access tokens use immutable user IDs as subjects and include a security
  version validated against current account, tenant, password, and credential
  state.
- TOTP secrets are encrypted at rest. Recovery codes are separate credential
  rows containing only domain-separated keyed digests; a conditional update
  makes successful use atomic and preserves an auditable used/remaining count.
- Secret-bearing MFA setup is self-service. User administrators receive only
  factor status and recovery-code counts, plus a destructive disable operation.
- Signing keys are supplied through `SigningKeyProvider`; the development
  profile persists a local key outside source control.
- SCIM is a protocol adapter, not a second identity store. Tenant-qualified
  directory services own users, groups, membership, concurrency versions, and
  security-version invalidation; provisioning owns SCIM schemas, filtering,
  pagination, PATCH semantics, protocol errors, and protocol-level audit events.

The decision records in [decisions](decisions/) explain the constraints and
trade-offs. Product capabilities and limitations remain in the repository
README and roadmap.
