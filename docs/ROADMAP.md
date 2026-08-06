# Roadmap

## Current Milestone Summary

IdentityForge is a portfolio-grade Identity and Access Management platform.
The current milestone demonstrates a working Admin Console, tenant-aware
identity administration, backend-enforced RBAC, OAuth2 Authorization Code +
PKCE, tenant-owned application scopes, token lifecycle foundations, consent
management, audit logging, and a complete local scope-protected resource API
demo. OIDC ID Token and UserInfo identity claims are also implemented with
explicit privacy boundaries.

The next phase is not to claim production readiness. It is to improve portfolio
presentation, complete selected identity protocol foundations, and harden the
security and operational model where a production deployment would require
more depth.

## Completed Major Slices

### Core Domain Model and Persistence

- Tenant, user, profile, custom attribute, password credential, TOTP
  credential, group, role, permission, OAuth2 client, application/resource
  server, application permission, consent, and audit event models.
- PostgreSQL persistence with a clean pre-release Flyway V1 schema.
- JPA repositories and Testcontainers-backed persistence/integration tests.

### Admin API and Admin Console

- Admin APIs for core tenant-aware IAM workflows.
- React TypeScript Admin Console using real backend APIs.
- Tenant selection, relationship-aware screens, safe DTOs, validation,
  pagination, and guided IAM/OAuth2 demo pages.

### Authentication and Password Management

- Local username/password authentication with account status enforcement.
- Password credential management separated from the core user record.
- Backend-owned login and authentication flow pages.

### OAuth2 Authorization Code + PKCE

- Spring Authorization Server integration with persisted OAuth2 clients.
- Authorization Code flow for confidential clients.
- Authorization Code + PKCE login for the public Admin Console client.
- JWT access tokens, JWK support, and OAuth2 scope validation.

### TOTP MFA Foundation

- TOTP enrollment, verification, disable, and login challenge behavior.
- Encrypted TOTP secret storage and one-time setup secret exposure.

### Tenant-Aware RBAC and Effective Permissions

- Tenant-scoped roles and group membership.
- Direct user roles and group-derived roles.
- De-duplicated effective roles and effective permissions.
- Tenant-boundary checks for user, group, and role relationships.

### Global System IAM Permission Catalog

- Backend-seeded system IAM permissions shared across tenants.
- Tenant role templates for `platform-admin`, `tenant-admin`, and `auditor`.
- Reserved IAM permission handling and metadata for Admin Console display.

### Fine-Grained Admin API Authorization

- Backend-enforced Admin RBAC for `/api/**`.
- Required `iam.read` / `iam.write` scopes plus concrete built-in permission
  checks.
- Tenant-bound administration and read-only auditor behavior.

### Applications / Resource Servers

- Tenant-owned Applications / Resource Servers.
- Application permissions modeled separately from system IAM permissions.
- OAuth2 clients linked to applications with selected permissions allowed as
  requestable application scopes.

### Demo Payroll Resource API

- Static demo endpoints protected by `payroll.employee.read`,
  `payroll.salary.read`, and `payroll.salary.write`.
- Complete local demonstration of successful and denied application-scope
  access.

### Refresh Token and Consent Management Foundation

- Refresh token support for supported confidential authorization-code clients.
- OAuth2 token revocation endpoint.
- JDBC-backed consent storage, safe consent listing, and consent revocation.
- Token lifecycle and consent audit events without token or secret values.

### OIDC UserInfo and ID Token Claims

- Stable `sub` values based on immutable user IDs.
- Safe basic ID Token identity claims without roles, permissions, credentials,
  secrets, or token values.
- Scope-aware `/userinfo` responses for `openid`, `profile`, `email`, and the
  custom `groups` and `roles` scopes.
- Explicit separation between OIDC identity claims, application scopes, and
  Admin API permissions.

### Audit Logging

- Audit events across identity administration, authentication, MFA, OAuth2
  consent, and token lifecycle workflows.
- Tenant-aware audit queries and Admin Console review.

### CI/CD and Local Docker Environment

- Docker Compose services for PostgreSQL and Redis.
- Dockerfile and GitLab CI stages for testing, packaging, and image build.
- Maven Wrapper, frontend build verification, and Testcontainers integration.

## Current Limitations

- The project is not production-ready and is not intended to replace a
  production IAM platform.
- Authorization and token storage is not fully distributed production storage.
- The Demo Payroll API is an in-server static resource API, not a real external
  business service.
- MFA UX needs QR code and recovery code polish.
- Pairwise subject identifiers and advanced claim transformation remain future
  work.
- SCIM-style provisioning needs broader protocol and workflow polish.
- The frontend is functional but is not a fully polished enterprise console.
- Production secrets, signing keys, session management, rate limiting,
  observability, high availability, and operational hardening remain future
  work.

## Next Recommended Milestones

### Portfolio Demo Polish and Screenshots

- Add current screenshots and a concise visual demo narrative.
- Keep README walkthroughs aligned with the implemented Admin Console.
- Improve reviewer orientation without overstating product maturity.

### MFA QR Code and Recovery Codes

- Add QR code presentation for TOTP enrollment.
- Design recovery code generation, storage, one-time use, and audit behavior.
- Improve administrator and end-user MFA guidance.

### SCIM Provisioning Polish

- Expand user, group, membership, filtering, pagination, and error behavior.
- Document SCIM-inspired behavior versus full protocol compliance.
- Strengthen provisioning audit events and tenant-boundary tests.

### Production Session and Token Hardening

- Evaluate durable distributed authorization and token storage.
- Harden browser session, logout, CSRF, revocation, and token lifecycle
  behavior.
- Define production signing key, secret, and rotation practices.

### Separate External Resource Service Demo

- Move or duplicate the Payroll resource demo into a separate service.
- Demonstrate resource-server JWT validation outside the IAM backend.
- Keep the service limited to demo data and scope enforcement.

### Frontend UX Polish

- Improve visual consistency, form guidance, empty states, and error recovery.
- Add targeted UI-level automated tests for critical workflows.
- Refine login, consent, and MFA challenge presentation.

## Later Roadmap

### Advanced OIDC Claim Mapping

- Add pairwise subject identifiers where client privacy requirements justify
  them.
- Design an explicit claim transformation and release policy model.

### Policy Engine / PDP Foundation

- Define a policy decision point model beyond additive RBAC.
- Keep policy concepts separate from OAuth2 scope issuance and Admin API
  permission checks.

### Externalized Authorization Decisions

- Explore authorization decision APIs for external resource services.
- Define decision inputs, outputs, caching, and tenant boundaries.

### Audit Export and Integrity

- Add safe audit export and retention controls.
- Evaluate integrity protection and operational review workflows.

### Production Key Management

- Integrate managed signing keys, rotation, and secure secret storage.
- Define environment-specific key and credential practices.

### Multi-Environment Deployment

- Add development, test, staging, and production deployment patterns.
- Document configuration, migration, rollback, and secret handling.

### Observability

- Add metrics, structured logs, tracing, health detail, and alerting guidance.
- Define security-focused operational dashboards and failure signals.

## Out of Scope for Now

- A full production IAM replacement.
- A complete policy engine.
- Real payroll or other business data.
- Enterprise-grade high-availability token/session storage.
- Full SaaS billing, organization management, or commercial product features.
