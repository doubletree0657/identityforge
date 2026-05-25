# international-iam-platform

`international-iam-platform` is an identity and access management platform
project built with Java 21, Spring Boot, Spring Security, Spring Authorization
Server, PostgreSQL, Docker, CI/CD practice, and a React TypeScript Admin
Console.

The project exists as a portfolio-grade engineering workspace for modern Java
backend development, identity security, OAuth2, MFA, SCIM-style provisioning,
containerized local development, continuous delivery practice, and technical
interview preparation for international backend roles.

It is under active development and is not production-ready. The current code is
useful for demonstrating implementation direction, design judgment, testing
practice, full-stack integration, and incremental delivery, but it should not
be treated as a complete IAM product or deployed for real users.

## Technical Focus

The project targets these areas:

- Java 21 and Spring Boot backend architecture.
- Identity domain modeling for tenants, users, clients, roles, permissions,
  groups, and audit events.
- OAuth2 and authorization server behavior with Spring Authorization Server.
- Spring Security integration, JWTs, JWKs, and scope-based API protection.
- MFA workflows and secure handling of authentication secrets.
- SCIM-style user and group provisioning APIs.
- PostgreSQL persistence with Flyway migrations and JPA.
- Docker-based local services and CI/CD workflows.
- React TypeScript Admin Console integration against real Admin APIs.
- Testcontainers-backed integration testing.
- Clear documentation for code review and interview discussion.

## Current State

The codebase currently includes a Spring Boot backend with Flyway-managed
PostgreSQL schema migrations, a stronger IAM domain model, JPA repositories,
service-layer orchestration, REST controllers, DTO validation, centralized error
handling, OpenAPI output, JWT/JWK support, scope-protected APIs, audit logging,
TOTP enrollment and verification, encrypted MFA secret storage, SCIM-style user
and group APIs, OAuth2 client management APIs with secret rotation, repository-
backed Spring Authorization Server client lookup, an end-to-end tested OAuth2
authorization-code login flow, backend-owned login, TOTP challenge, and OAuth2
consent pages, a Dockerfile, local PostgreSQL/Redis Compose services, a React
TypeScript Admin Console under `frontend/`, and a GitLab CI pipeline.

The Admin API surface is completed and hardened for frontend consumption.
Current management APIs cover tenants, users, profiles, custom user attributes,
groups, roles, permissions, OAuth2 clients, MFA enrollment operations, and audit
log queries with paginated list responses, practical validation, safer default
profile handling, consistent validation errors, and safe response DTOs that
avoid exposing password hashes, TOTP secret material, and client secret hashes.
The Admin Console demonstrates those APIs through real backend calls and a
development bearer-token panel. It now has a global tenant selector that is
used by tenant-scoped screens for users, groups, roles, OAuth2 clients, MFA
operations, and audit logs, while permissions remain a global catalog.

The project has performed a pre-release Flyway schema reset toward a stronger
identity model. The current baseline includes tenant status, richer user
identity fields, separate user profiles, separate password and TOTP
credentials, custom attributes, explicit group memberships, tenant-scoped
RBAC, OAuth2 client registration concepts, and security event metadata.

Browser authentication is backend-owned rather than part of the React Admin
Console. The project provides `/login`, `/login/mfa`, and `/oauth2/consent`
pages for local user login, account status enforcement, optional TOTP challenge
for verified credentials, and consent for OAuth2 clients that require it. These
pages support the authorization-code journey; management and SCIM APIs remain
protected by OAuth2 JWT scope checks.

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Security and Spring Authorization Server
- Spring Data JPA
- PostgreSQL
- Redis
- Flyway
- Testcontainers
- Maven Wrapper
- Docker Compose
- OpenAPI / Swagger UI
- GitLab CI/CD

## Running Tests

Tests use Maven and Testcontainers. Docker or a compatible container runtime
must be available.

```bash
./mvnw test
```

## Local Development

Required tools:

- JDK 21
- Docker or a Docker-compatible runtime
- Git
- Node.js 20+ and npm for the Admin Console

Start local dependencies:

```bash
docker compose up -d
```

Run the application:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Health check:

```bash
curl http://localhost:8080/api/health
```

Run the Admin Console:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The frontend defaults to
`http://localhost:8080` for backend calls. Override it with
`VITE_API_BASE_URL` for local development.

With the `dev` Spring profile active, the backend creates local-only bootstrap
data for browser-based Admin Console login:

1. Start the backend with the dev profile.
2. Start the frontend.
3. Open `http://localhost:5173`.
4. Click `Sign in with International IAM`.
5. Sign in with username `admin` and password `admin123456`.
6. Use the Admin Console.

The default admin credentials are dev-only and are configured only in
`application-dev.yml`. Production must not use fixed bootstrap credentials. The
React Admin Console uses OAuth2 Authorization Code + PKCE with the persisted
public client `iam-admin-console`; browser authentication stays on the backend
owned `/login`, `/login/mfa`, and `/oauth2/consent` pages. The frontend stores
the local development access token and expiry in browser storage and attaches
the bearer token automatically to Admin API calls. The local Admin Console
logout clears frontend token state, calls backend `GET /logout` to invalidate
the browser session, and returns to `http://localhost:5173/login?loggedOut=true`.
Production deployments should prefer a CSRF-protected POST logout and a hardened
browser session model.

The Admin Console is organized around relationship-aware IAM workflows:

- Select a current tenant once in the console header; if only one tenant exists,
  the console selects it automatically.
- Create users, groups, roles, and OAuth2 clients in the selected tenant
  without copying tenant UUIDs.
- A user belongs to one tenant, does not have to belong to a group, and can
  belong to many groups. Groups are optional organizational containers.
- Assign roles directly to users, assign roles to groups, and attach global
  system IAM permissions to tenant roles from the permission catalog.
- Review direct roles, group-derived roles, effective roles, and effective
  permissions on the user detail page.
- Add and remove group members from the group detail page using tenant user
  selectors.
- Manage user identity, profile, custom attributes, password, TOTP actions, and
  related audit events from the user detail page.
- Use OAuth2 client templates for public PKCE SPAs, confidential backend
  service clients, and confidential web apps. Client secrets are shown only once
  on create or rotation; `clientSecretHash` is never exposed.
- Filter audit logs by tenant, action, resource type, resource id, and result.
- Open the `IAM Workflow` page for a guided demo chain from tenant selection
  through audit log review.

Build the frontend:

```bash
cd frontend
npm run build
```

Stop local dependencies:

```bash
docker compose down
```

Reset local database volumes after the pre-release schema reset:

```bash
./scripts/reset-local-db.sh
```

Local services:

- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

The Docker Compose credentials are local development values only.

## API Documentation

After the application starts:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The health endpoint is public. `/api/me` returns safe current-principal
information for authenticated users with `iam.read`; it does not expose
password hashes, client secret hashes, token material, or TOTP ciphertext.
Management APIs under `/api/**` require both OAuth2 JWT scopes and Admin RBAC:

- Read operations require `iam.read`.
- Write operations require `iam.write`.
- The token must also contain an admin role or admin permission.
- `platform-admin` can manage all tenants.
- `tenant-admin` can manage resources only in its own tenant.
- Normal users are rejected from Admin APIs even if a token contains
  `iam.read` or `iam.write`.

Local user access tokens include stable authorization claims: `user_id`,
`tenant_id`, `display_name`, `direct_roles`, `group_roles`, `effective_roles`,
`direct_permissions`, `group_permissions`, and `effective_permissions`.
`roles` and `permissions` remain compatibility aliases for effective roles and
effective permissions. Service-client SCIM access remains scope-based under
`/scim/v2/**`; service-client Admin API access should use an explicit future
machine-admin model rather than React client secrets.

Admin API authorization uses a global system permission catalog seeded by the
backend, not arbitrary strings typed in the Admin Console. Permissions are
atomic capabilities. Roles are tenant-scoped bundles of permissions, and
role-permission assignment references the same global system permission rows
for every tenant. Built-in IAM permissions include:

- `iam.tenants.read`, `iam.tenants.write`
- `iam.users.read`, `iam.users.write`
- `iam.groups.read`, `iam.groups.write`
- `iam.roles.read`, `iam.roles.write`
- `iam.permissions.read`, `iam.permissions.write`
- `iam.clients.read`, `iam.clients.write`
- `iam.audit.read`
- `iam.mfa.manage`
- `iam.admin`

Each seeded permission records display name, description, category, and
`systemManaged`, with no tenant owner. New tenants get role templates that
reference the global catalog immediately: `platform-admin` receives all
built-in permissions, `tenant-admin` receives tenant-scoped management
permissions, and `auditor` receives read-only permissions. Reserved `iam.*`
permissions cannot be created manually through the API. Future custom
application permissions should be modeled separately as tenant plus
application/resource-server capabilities and remain separate from IAM Admin API
permissions. Random database
permission strings do not grant Admin API access unless they are part of the
recognized built-in catalog.

Role and permission resolution is additive and de-duplicated:

- Direct permissions come from `User -> Role -> Permission`.
- Group-derived permissions come from
  `User -> GroupMembership -> Group -> Role -> Permission`.
- Group, role, and user assignments are tenant-boundary checked.

User lifecycle status has login impact:

- `PENDING`: created but not fully activated or password not set.
- `ACTIVE`: can authenticate.
- `DISABLED`: cannot authenticate.
- `LOCKED`: cannot authenticate until an administrator unlocks the user.

TOTP enrollment returns the setup secret and `otpauth://` URI only during
enrollment. The UI tells administrators to add the secret or URI to Google
Authenticator, Microsoft Authenticator, or 1Password and verify with the
current six-digit authenticator code.

## OAuth2 Authorization Code Demo

The backend now supports a productized development authorization-code flow using
persisted OAuth2 clients, local username/password login, optional TOTP challenge,
and project-owned consent.

At a high level:

1. Create a tenant, local user, and confidential OAuth2 client through the
   existing service/API paths. The client should use `authorization_code`,
   `client_secret_basic`, a development redirect URI such as
   `http://127.0.0.1:8080/oauth2/demo/callback`, and the `iam.read` scope.
2. Set the user's initial password through the password management service.
3. Start a browser authorization request:

```text
/oauth2/authorize?response_type=code&client_id=<client-id>&redirect_uri=<redirect-uri>&scope=iam.read&state=<state>
```

4. The browser is redirected to `/login`; disabled and locked users are blocked
   with generic failure messages.
5. If the user has an enabled verified TOTP credential, `/login/mfa` must be
   completed before the authorization request continues.
6. If the client requires consent, `/oauth2/consent` shows the client name,
   requested scopes, scope descriptions, and the authenticated user.
7. After approval, the authorization server redirects back to the client
   redirect URI with a code. Denial returns the OAuth2 `access_denied` response.
8. Exchange the code at `/oauth2/token` with the confidential client's
   authentication and use the returned bearer token on `/api/**` requests that
   require `iam.read`.

The executable integration test
`OAuth2AuthorizationCodeLoginFlowTests` seeds this setup and proves the full
flow end to end. Authentication and consent audit events are recorded without
raw passwords, password hashes, TOTP codes, TOTP secrets, client secrets,
authorization codes, tokens, or signing keys.

Future authentication work remains intentionally out of scope here: a production
frontend session model, OIDC UserInfo, recovery codes, passkeys/WebAuthn,
enterprise risk-based authentication, and full production hardening.

## Roadmap

Planning is tracked in [docs/ROADMAP.md](docs/ROADMAP.md). The roadmap is the
single planning document for current direction, future product tracks, and
documentation policy.
