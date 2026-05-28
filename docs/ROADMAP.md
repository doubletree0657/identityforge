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
  attributes, clients, tenant-scoped roles, global system IAM permissions,
  resource servers, application permissions, groups, group membership, and
  audit events.
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
  profiles, custom attributes, groups, roles, system IAM permissions,
  applications/resource servers, OAuth2 clients, TOTP operations, and audit log
  queries.
- Hardened Admin API contracts with paginated responses, bounded page sizes,
  stronger request validation, safer default user profile reads, tenant slug
  uniqueness checks, and cleaner validation errors.
- Repository-backed Spring Authorization Server `RegisteredClientRepository`
  integration for persisted OAuth2 client registrations.
- Dev-profile bootstrap data for local Admin Console testing with a persisted
  development tenant, dev-only super admin, admin role/permissions, and OAuth2
  clients.
- Productized OAuth2 authorization-code flow using local users, account status
  checks, optional TOTP challenge, persisted confidential clients, project-owned
  consent, JWT token issuance, and scope-protected API access.
- Backend-owned login, MFA challenge, and OAuth2 consent pages for browser-based
  authorization demos.
- TOTP enrollment and verification, including encrypted MFA secret storage.
- SCIM-style user and group provisioning APIs.
- Dockerfile, local PostgreSQL/Redis Compose services, and GitLab CI stages for
  test, package, and Docker image build.
- React TypeScript Admin Console under `frontend/` with real Admin API
  integration, OAuth2 Authorization Code + PKCE login through the backend,
  productized resource management screens, global tenant context with
  auto-selection for single-tenant local development, relationship-aware
  user-role, group-member, role-permission, application permission, OAuth2
  client, MFA, audit log workflows, an IAM workflow demo page, and an OAuth2
  authorization-code demo helper.

The browser login and consent experience is now demonstrable for local IAM
flows. It is still not a full production session product and should be hardened
further before real deployment.

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
- Kept roles tenant-scoped while moving IAM system permissions into a global
  catalog that tenant roles reference.
- Expanded OAuth2 client registration fields for future persistent
  `RegisteredClient` integration.
- Expanded audit records into security event-style metadata.

Remaining candidate slices:

- Account lifecycle states and transitions.
- Credential ownership, password metadata, and policy hooks.
- Tenant-aware user, group, role, and client relationships, with IAM
  permissions kept as a global system catalog.
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
- Project-owned consent page for clients that require consent, with scope
  descriptions and approve/deny behavior.
- Authorization code exchange for a self-contained JWT access token.
- Protected `/api/**` access with `iam.read` and rejection without the required
  scope.
- Authentication and consent audit events that avoid passwords, TOTP material,
  client secrets, authorization codes, tokens, and signing keys.

Candidate slices:

- PKCE support for public clients.
- Broader manual seed/bootstrap workflow for local demos.

## Productized Authentication

Goal: replace the current default form login dependency with a deliberate
authentication experience and backend behavior suitable for an IAM product
portfolio project.

Completed slices:

- Project-owned `/login` page with generic login failure behavior.
- Account status handling for disabled and locked users.
- TOTP login challenge for users with enabled verified credentials.
- Audit events for login success, login failure, blocked login, MFA challenge
  success/failure, OAuth2 consent approval/denial, and logout where practical.
- Clear boundaries between browser login sessions and OAuth2-protected APIs.

Candidate slices:

- Production frontend session model.
- OIDC UserInfo.
- Recovery codes.
- Passkeys/WebAuthn.
- Enterprise-grade risk-based authentication.
- Full production hardening.
- Password policy and safe password reset/change flows.

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
  application/resource server, OAuth2 client, MFA, and audit log management
  screens.
- Global tenant selector stored in local storage and used as the default context
  for tenant-scoped users, groups, roles, OAuth2 clients, MFA actions, and audit
  logs. The permission catalog is global.
- Relationship-aware IAM workflows: user role assignment, user group membership
  visibility, group member add/remove with tenant user selectors, role
  permission assignment/removal from the global permission catalog, and related
  user audit events.
- OAuth2 client workflow guidance for confidential versus public clients,
  authorization code versus client credentials grants, redirect URIs, scopes,
  PKCE, one-time secret display, confidential-client secret rotation, and
  reusable client templates for SPAs, backend services, and web apps.
- Productized user, group, role, permission, client, MFA, and audit workflows
  that avoid normal UUID copy/paste by using the selected tenant and
  tenant-scoped selectors.
- Documented IAM relationship model in the Admin Console: users belong to one
  tenant, groups are optional and many-to-many with users, roles are
  tenant-scoped permission bundles, and permissions are global atomic
  capabilities attached to roles.
- Guided IAM Workflow page that links the local demo chain from tenant
  selection through user, password, permission, role, group, OAuth2 client, and
  audit log review.
- OAuth2 authorization-code demo helper with generated authorization URL and
  token exchange guidance.
- First-class local Admin Console login backed by dev-only bootstrap admin
  credentials, the public `iam-admin-console` PKCE client, and `/api/me`.
- Backend-enforced Admin RBAC for `/api/**`: `platform-admin` can manage all
  tenants, `tenant-admin` is tenant-bound, normal users are denied even with
  IAM scopes, and each Admin API path also requires its concrete built-in
  system permission.
- Effective authorization calculation across direct user roles and
  group-derived roles, with de-duplicated effective permissions exposed in JWT
  claims, `/api/me`, and the Admin Console.
- Group-to-role assignment APIs and UI, with tenant-boundary checks for
  user/group, user/role, and group/role relationships.
- Global system IAM permission catalog seeded by the backend, including display
  metadata, reserved `iam.*` protection, and Admin API checks aligned to the
  same built-in permission names.
- Fine-grained Admin API authorization: `iam.read`/`iam.write` scopes are still
  required, `iam.admin` implies full administration, auditor remains read-only,
  and the backend remains the source of truth for authorization decisions.
- Tenant-scoped role templates for `platform-admin`, `tenant-admin`, and
  `auditor` that reference global permissions instead of duplicating
  permissions per tenant.
- Resource Server / Application foundation: tenant-owned applications have
  separate application permissions for future business-resource authorization,
  while system IAM permissions remain global Admin API capabilities.
- OAuth2 clients can optionally link to a tenant resource server and allow
  selected application permissions as OAuth2 application scopes. Assigned
  application permission scopes can be requested by that client, while
  unassigned application scopes are rejected by token issuance.
- Demo Payroll resource API under `/demo-resource-api/**` verifies issued
  application scopes with concrete Spring Security authorities:
  `payroll.employee.read` protects employee reads, `payroll.salary.read`
  protects salary reads, and `payroll.salary.write` protects salary writes.
  This is an in-server portfolio slice for the OAuth2 resource-server flow, not
  a full external policy engine or production business application.
- Clearer user lifecycle controls for `PENDING`, `ACTIVE`, `DISABLED`, and
  `LOCKED`, plus status-change audit events.
- TOTP enrollment UX with one-time setup secret display, `otpauth://` URI, and
  authenticator-app guidance.
- Frontend build verification through `npm run build`.

Candidate slices:

- Production authentication and session model.
- Machine-admin authorization for service clients that need non-SCIM Admin API
  access.
- Full policy engine design and separate production-grade resource service
  enforcement.
- Safe delete/archive workflows where domain ownership and cascade behavior are
  explicitly designed.
- Designed login, consent, and MFA challenge screens.
- UI-level automated tests and richer interaction coverage.

## MFA and Strong Authentication

Goal: develop MFA as a coherent authentication capability with enrollment,
challenge, verification, recovery, and audit behavior.

Candidate slices:

- TOTP enrollment lifecycle.
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
