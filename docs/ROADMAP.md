# Roadmap

This roadmap is the planning source for `international-iam-platform`. It is
organized around complete, demonstrable product slices rather than tiny
framework configuration tasks.

The project is under active development and is not production-ready. Planning
should keep the long-term target in view: a portfolio-grade IAM platform for
modern Java backend engineering, identity security, OAuth2, MFA, SCIM-style
provisioning, Docker, CI/CD, and international job interview preparation.

## Current State

The codebase currently includes:

- Java 21 and Spring Boot backend structure.
- IAM entities for tenants, users, user profiles, password credentials, custom
  attributes, clients, roles, permissions, groups, group membership, and audit
  events.
- A pre-release Flyway schema reset with a single baseline migration before the
  first stable release.
- JPA repositories and Testcontainers-backed persistence tests.
- Service-layer workflows and selected tenant boundary checks.
- REST APIs with DTO validation, centralized error handling, and OpenAPI output.
- Spring Authorization Server integration, JWT/JWK support, and scope-protected
  APIs using `iam.read` and `iam.write`.
- OAuth2 client management APIs for safe creation, update, and confidential
  client secret rotation.
- Backend Admin APIs for a future React Admin Console, covering tenants, users,
  profiles, custom attributes, groups, roles, permissions, OAuth2 clients, TOTP
  operations, and audit log queries.
- Hardened Admin API contracts with paginated responses, bounded page sizes,
  stronger request validation, safer default user profile reads, tenant slug
  uniqueness checks, and cleaner validation errors.
- Repository-backed Spring Authorization Server `RegisteredClientRepository`
  integration for persisted OAuth2 client registrations.
- Dev-profile bootstrap data for local Admin Console testing with a persisted
  development tenant and OAuth2 client.
- End-to-end OAuth2 authorization-code login flow using local users,
  persisted confidential clients, JWT token issuance, and scope-protected API
  access.
- Spring Security default form login for early browser-based authorization
  integration.
- TOTP enrollment and verification, including encrypted MFA secret storage.
- SCIM-style user and group provisioning APIs.
- Dockerfile, local PostgreSQL/Redis Compose services, and GitLab CI stages for
  test, package, and Docker image build.
- React TypeScript Admin Console under `frontend/` with real Admin API
  integration, development bearer-token configuration, resource management
  screens, MFA operations, audit log queries, and an OAuth2 authorization-code
  demo helper.

Current login support is an integration step, not a product-grade authentication
experience. It should be expanded into a designed authentication flow before the
project presents it as a user-facing IAM capability.

## Project Realignment

Goal: make the repository read as a serious IAM platform project with clear
scope, honest status, and product-oriented development slices.

Planned work:

- Keep documentation concise and current.
- Use the roadmap as the planning hub.
- Prefer end-to-end deliverables that can be demonstrated locally or in CI.
- Avoid treating small framework wiring as completed product functionality.
- Align commit and issue planning around visible IAM outcomes.

## Identity Domain Model Upgrade

Status: active track.

Goal: evolve the existing IAM model into a stronger product domain that can
support real authentication, client management, provisioning, audit trails, and
tenant-aware administration.

Completed in the current pre-release reset:

- Replaced the old Flyway migration chain with one baseline schema.
- Moved password credential persistence out of the core user record.
- Continued credential boundary cleanup by moving TOTP secret persistence out
  of the core user record.
- Added user profile and custom user attribute concepts.
- Made group membership explicit.
- Kept roles and permissions tenant-scoped.
- Expanded OAuth2 client registration fields for future persistent
  `RegisteredClient` integration.
- Expanded audit records into security event-style metadata.

Remaining candidate slices:

- Account lifecycle states and transitions.
- Credential ownership, password metadata, and policy hooks.
- Tenant-aware user, group, role, permission, and client relationships.
- Clear distinction between platform administration concepts and OAuth2 client
  registration concepts.
- Tests for tenant boundaries and security-sensitive invariants.

## End-to-End OAuth2 Login Flow

Goal: provide a demonstrable OAuth2 authorization code flow that connects local
users, registered clients, authorization requests, login, redirects, and token
issuance.

Completed in the current slice:

- Persistent confidential client authorization-code scenario.
- Browser-style redirect to login and resumed authorization request.
- Authorization code exchange for a self-contained JWT access token.
- Protected `/api/**` access with `iam.read` and rejection without the required
  scope.

Candidate slices:

- PKCE support for public clients.
- Productized consent and login pages.
- Broader manual seed/bootstrap workflow for local demos.

## Productized Authentication

Goal: replace the current default form login dependency with a deliberate
authentication experience and backend behavior suitable for an IAM product
portfolio project.

Candidate slices:

- Designed login route and response behavior.
- Account status handling for disabled, locked, expired, and incomplete users.
- Password policy and safe password change flows.
- Generic failure behavior that avoids account enumeration.
- Session handling and logout behavior.
- Clear boundaries between browser login sessions and OAuth2-protected APIs.

## OAuth2 Client Management

Goal: make OAuth2 client registration and management a first-class product area
rather than local development configuration.

Completed slices:

- Persistent client registration model.
- Repository-backed Spring Authorization Server integration.
- Client secret hashing, rotation, and display rules.
- Redirect URI, scope, grant type, and authentication method management.
- Public versus confidential client behavior.
- Administrative APIs with validation and audit events.
- Safe list and detail APIs for frontend administration without exposing raw
  secrets or client secret hashes.

## Backend Admin API Surface

Status: implemented baseline.

Goal: provide a coherent backend API layer that can support a future React Admin
Console without exposing credential internals.

Completed slices:

- Tenant list, detail, create, and update APIs.
- User list, detail, create, update, role assignment, role removal, and password
  update APIs.
- User profile and custom user attribute APIs.
- Group list, detail, create, update, member add, member remove, and member
  listing APIs.
- Role and permission list/detail/create/update and assignment APIs.
- OAuth2 client list/detail APIs using safe DTOs.
- TOTP enrollment, verification, and disable endpoints.
- Audit log query API with simple filters and pagination.
- Frontend-ready paginated response metadata for primary list APIs.
- Bounded pagination and practical DTO validation.
- Empty/default profile response when a user exists but has no stored profile.
- Tenant slug duplicate rejection and generic validation errors for non-password
  admin validation failures.

Candidate slices:

- Richer search filters.
- More complete lifecycle workflows for account state, group ownership, and
  delegated administration.

## React Admin Console

Status: implemented baseline.

Goal: provide a portfolio-grade full-stack console that demonstrates the IAM
platform through real backend Admin APIs without adding production frontend
authentication.

Completed slices:

- Vite React TypeScript frontend under `frontend/`.
- Development API base URL and bearer-token panel.
- Admin layout with navigation, loading states, error states, empty states, and
  reusable tables/pagination.
- Dashboard overview backed by paginated Admin APIs.
- Tenant, user, user detail, profile, attribute, group, role, permission,
  OAuth2 client, MFA, and audit log management screens.
- OAuth2 authorization-code demo helper with generated authorization URL and
  token exchange guidance.
- Local development token workflow backed by dev-only OAuth2 client bootstrap.
- Frontend build verification through `npm run build`.

Candidate slices:

- Production authentication and session model.
- Designed login, consent, and MFA challenge screens.
- UI-level automated tests and richer interaction coverage.

## MFA and Strong Authentication

Goal: develop MFA as a coherent authentication capability with enrollment,
challenge, verification, recovery, and audit behavior.

Candidate slices:

- TOTP enrollment lifecycle.
- MFA challenge during login.
- Recovery code generation and verification.
- Step-up authentication for sensitive actions.
- Secret encryption, rotation strategy, and exposure tests.
- Audit events for enrollment, verification, recovery, and failure cases.

## SCIM and Provisioning

Goal: grow the SCIM-style APIs into a practical provisioning surface for users,
groups, membership, lifecycle changes, and tenant-specific identity data.

Candidate slices:

- User create, update, deactivate, and lookup behavior.
- Group create, update, lookup, and membership behavior.
- Consistent identifiers, filtering, pagination, and error responses.
- Tenant boundary enforcement.
- Provisioning audit events.
- Compatibility notes for SCIM-inspired versus fully SCIM-compliant behavior.

## Audit Logging and Security Events

Goal: turn audit logging into a dependable security event trail for identity,
administration, OAuth2, MFA, and provisioning workflows.

Candidate slices:

- Event taxonomy for authentication, authorization, administration, MFA, and
  provisioning.
- Request metadata capture.
- Tenant-aware audit queries.
- Protection against logging secrets or sensitive credential material.
- Tests for required events in critical workflows.
- Operational examples for review and incident analysis.

## Docker, CI/CD, and Deployment

Goal: make the project easy to run locally, verify in CI, package as a
container image, and later deploy as a realistic backend service.

Candidate slices:

- Reliable local Compose workflow for PostgreSQL and Redis.
- Reproducible Maven test and package stages.
- Docker image build and tagging strategy.
- Container registry publishing.
- Environment-specific configuration.
- CI secret handling.
- Deployment practice, smoke checks, and rollback notes.

## Documentation Policy

- `README.md` should explain the project, current state, local workflow, and
  link to this roadmap.
- `docs/ROADMAP.md` is the single source of truth for planning until the
  product behavior is larger and more stable.
- Architecture and security design documents should be recreated later, after
  the core domain model and end-to-end OAuth2 login flow become more
  substantial.
- Documentation should describe implemented behavior honestly.
- Documentation should not make small framework configuration look like a
  completed IAM product feature.
