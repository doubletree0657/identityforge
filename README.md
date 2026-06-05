# International IAM Platform

International IAM Platform is a **portfolio-grade IAM and OAuth2 Authorization
Server prototype**. It demonstrates tenant-aware identity administration,
role-based access control (RBAC), OAuth2 Authorization Code + PKCE, application
scopes, consent management, token lifecycle foundations, audit logging, and
scope-protected resource API access. It also demonstrates OIDC ID Tokens and a
scope-aware UserInfo endpoint for tenant-owned user identity claims.

This is a public portfolio project created to demonstrate modern Java backend
engineering, identity security, system design, React integration, Docker,
CI/CD, testing, and English technical documentation. It is an independent
project and is not copied from, or presented as, any company product.

The repository is intended for engineers, reviewers, and interviewers who want
to inspect a practical IAM/OAuth2 implementation and run a complete local demo.
It is **not production-ready**, is not intended to replace a production IAM
platform, and requires production hardening before use with real users or
business data.

## Why This Project Exists

IAM systems connect authentication, authorization, identity data, security
boundaries, API design, and operational concerns. This project provides a
concrete workspace for demonstrating those concerns as integrated product
slices rather than isolated framework examples.

The main local demo proves a complete application-scope flow:

1. A tenant owns a Payroll API application.
2. The application defines permissions such as `payroll.employee.read`.
3. An OAuth2 client is allowed to request selected application permissions as
   scopes.
4. Spring Authorization Server issues an access token after user authorization.
5. A demo resource API allows or rejects requests based on the issued scopes.

## Feature Overview

### Identity Administration

- Tenant-aware users, profiles, custom attributes, password credentials, and
  account status.
- Group membership and user lifecycle administration.
- SCIM-style user and group provisioning foundation.

### Tenant and Admin Authorization

- Tenant-scoped administration with `platform-admin`, `tenant-admin`, and
  `auditor` role templates.
- Backend-enforced Admin RBAC for `/api/**`.
- Fine-grained Admin API permission checks in addition to OAuth2 scope checks.

### RBAC and Effective Permissions

- Tenant-scoped roles backed by a global system IAM permission catalog.
- Direct user roles and group-derived roles.
- De-duplicated effective roles and effective permissions exposed to the
  authenticated user and Admin Console.

### OAuth2 / Authorization Server

- Spring Authorization Server integration with persisted OAuth2 clients.
- OAuth2 Authorization Code flow, including PKCE for the public Admin Console
  client.
- Backend-owned login, optional TOTP challenge, and consent pages.
- JWT access tokens, JWK support, and scope validation.
- OIDC ID Tokens with stable user-ID subjects and safe basic identity claims.
- OIDC UserInfo endpoint at `/userinfo` with claims filtered by granted scopes.

### OIDC Identity Claims

OIDC identity scopes are separate from application authorization scopes and
Admin API permissions:

| Scope | UserInfo claims |
| --- | --- |
| `openid` | `sub` |
| `profile` | `preferred_username`, `name`, `display_name`, `tenant_id`, `tenant_name`, `account_status` |
| `email` | `email`, `email_verified` |
| `groups` | `groups` |
| `roles` | `roles`, `effective_roles` |

`groups` and `roles` are explicit custom scopes. ID Tokens contain `sub` plus
small basic identity context, add account status for `profile`, and add email
claims for `email`. They do not contain groups, roles, effective permissions,
credential data, secrets, or tokens. `sub` is the stable user ID as a string;
pairwise subject identifiers and advanced claim mapping remain future work.

### Applications and Resource Servers

- Tenant-owned Applications / Resource Servers.
- Application permissions modeled separately from system IAM permissions.
- OAuth2 clients linked to applications with selected application permissions
  allowed as requestable scopes.

### Demo Resource API

- Static Payroll API endpoints under `/demo-resource-api/payroll/**`.
- Scope enforcement for `payroll.employee.read`, `payroll.salary.read`, and
  `payroll.salary.write`.
- A complete local demonstration of allowed and denied resource access.

### Token Lifecycle and Consents

- Refresh token foundation for supported confidential authorization-code
  clients.
- OAuth2 token revocation endpoint at `/oauth2/revoke`.
- Safe consent listing and revocation for current users and administrators.
- JDBC-backed consent storage.

### Security and Audit

- TOTP MFA enrollment and verification foundation with encrypted secret
  storage.
- Audit events for identity administration, authentication, consent, token
  lifecycle, and other security-sensitive actions.
- Safe DTOs and one-time secret display rules that avoid exposing password
  hashes, TOTP ciphertext, client secret hashes, authorization codes, or tokens.

### Admin Console

- React TypeScript Admin Console using real backend Admin APIs.
- OAuth2 Authorization Code + PKCE login.
- Pages for tenants, users, groups, roles, permissions, applications, OAuth2
  clients, consents, MFA actions, audit logs, and guided demo workflows.

### Engineering Practices

- Java 21, Spring Boot 3.5.x, PostgreSQL, Redis, Flyway, and Docker Compose.
- Clean pre-release Flyway V1 schema.
- Testcontainers-backed integration tests.
- Maven Wrapper, frontend build verification, Docker image build, and GitLab
  CI/CD pipeline.
- OpenAPI documentation and centralized API validation/error handling.

## Architecture Overview

```mermaid
flowchart LR
    Browser[Browser]
    Console[React Admin Console]
    Backend[IAM Backend<br/>Spring Boot]
    Auth[OAuth2 Authorization Server]
    Admin[Admin APIs]
    Model[Application / Resource Server Model]
    Payroll[Demo Payroll Resource API]
    DB[(PostgreSQL)]
    Redis[(Redis)]

    Browser --> Console
    Console -->|Authorization Code + PKCE and Admin API calls| Backend
    Backend --> Auth
    Backend --> Admin
    Backend --> Model
    Backend --> Payroll
    Backend --> DB
    Backend --> Redis
    Auth -->|JWT access token with application scopes| Console
    Console -->|Scoped API call| Payroll
```

```mermaid
flowchart TB
    Tenant[Tenant] --> User[User]
    User --> Membership[Group Membership]
    Membership --> Group[Group]
    User --> DirectRole[Direct Role Assignment]
    Group --> GroupRole[Group Role Assignment]
    DirectRole --> Role[Role]
    GroupRole --> Role
    Role --> SystemPermission[System IAM Permission]

    Tenant --> Application[Application / Resource Server]
    Application --> AppPermission[Application Permission]
    Tenant --> Client[OAuth2 Client]
    Client --> Application
    Client --> AllowedScope[Allowed Application Scope]
    AppPermission --> AllowedScope
    AllowedScope --> Token[Access Token Scope]
    Token --> DemoApi[Demo Payroll Resource API]
```

System IAM permissions protect this platform's Admin APIs. Application
permissions describe capabilities exposed by tenant-owned applications and can
be issued as OAuth2 scopes. They are intentionally separate authorization
models. OIDC scopes control identity claims and do not grant application or
Admin API access.

## Quick Start

### Prerequisites

- JDK 21
- Docker or a Docker-compatible container runtime
- Node.js 20+ and npm
- Git

### Start Local Dependencies

From the repository root:

```bash
docker compose up -d
```

This starts PostgreSQL on `localhost:5432` and Redis on `localhost:6379`.
Compose credentials are local development values only.

### Start the Backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend starts at `http://localhost:8080`.

```bash
curl http://localhost:8080/api/health
```

### Start the Admin Console

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, select **Sign in with International IAM**, and
log in with:

```text
username: admin
password: admin123456
```

These credentials are created only by the `dev` profile and must never be used
in a production environment.

The frontend defaults to `http://localhost:8080` for backend calls. Set
`VITE_API_BASE_URL` when a different local backend URL is required.

## Dev Bootstrap Data

The `dev` profile creates local-only data for the main portfolio demo:

| Type | Value |
| --- | --- |
| Tenant | `Development Tenant` |
| Admin user | `admin` / `admin123456` |
| Public Admin Console client | `iam-admin-console` |
| Demo OAuth2 client | `International IAM Dev Client` |
| Demo OAuth2 client ID / secret | `international-iam-dev` / `secret` |
| Application / Resource Server | `Payroll API` |
| Application permissions | `payroll.employee.read`, `payroll.salary.read`, `payroll.salary.write` |

## End-to-End OAuth2 Application Scope Demo

This walkthrough demonstrates that an access token containing
`payroll.employee.read` can read employee data but cannot read salary data.

1. Start Docker dependencies:

   ```bash
   docker compose up -d
   ```

2. Start the backend with the dev profile:

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Start the frontend in a second terminal:

   ```bash
   cd frontend
   npm run dev
   ```

4. Open `http://localhost:5173` and log in as `admin` / `admin123456`.
5. Open **Applications** and verify that **Payroll API** exists.
6. Open **OAuth2 Clients** and verify that **International IAM Dev Client** is
   linked to **Payroll API**.
7. Open **OAuth2 Demo**.
8. Select **International IAM Dev Client**.
9. Select OIDC scopes such as `openid profile email groups roles`, then select
   `payroll.employee.read` separately.
10. Generate the authorization URL.
11. Open the authorization URL.
12. Log in and approve consent if required.
13. Copy the returned authorization code.
14. Exchange the code for an access token using the generated token command.
15. Inspect the returned ID Token and call UserInfo:

    ```bash
    curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
      http://localhost:8080/userinfo
    ```

16. Call the employee endpoint:

    ```bash
    curl -i \
      -H "Authorization: Bearer <ACCESS_TOKEN>" \
      http://localhost:8080/demo-resource-api/payroll/employees
    ```

    Expected result: HTTP `200`.

17. Call the salary endpoint with the same token:

    ```bash
    curl -i \
      -H "Authorization: Bearer <ACCESS_TOKEN>" \
      http://localhost:8080/demo-resource-api/payroll/salaries
    ```

    Expected result: HTTP `403` because `payroll.salary.read` was not
    requested.

The Payroll API is intentionally an in-server static demo resource. A
production architecture would normally protect separate resource services.

## Admin Console Workflows

The Admin Console supports the core IAM relationships without requiring normal
UUID copy/paste:

- Select a tenant once and use it across tenant-scoped screens.
- Create and manage users, profiles, attributes, groups, roles, applications,
  and OAuth2 clients.
- Assign roles directly to users or indirectly through groups.
- Attach global system IAM permissions to tenant roles.
- Review direct, group-derived, and effective roles and permissions.
- Define application permissions and allow selected permissions as OAuth2
  client scopes.
- Manage TOTP actions, OAuth2 consents, and audit log review.
- Use **IAM Workflow** and **OAuth2 Demo** as guided portfolio demonstrations.

## What This Project Demonstrates

For code review and interview discussion, the repository demonstrates:

- Spring Authorization Server integration with persisted client registration.
- OAuth2 Authorization Code + PKCE for a public React client.
- Token scopes generated from tenant-owned application permissions.
- Scope-aware OIDC ID Token and UserInfo identity claims.
- Scope-protected resource API behavior, including expected `200` and `403`
  outcomes.
- Tenant-aware RBAC and effective authorization from direct and group-derived
  roles.
- Fine-grained backend authorization for Admin API operations.
- Safe DTO boundaries, client secret handling, and avoidance of credential
  material in normal API responses.
- Audit trail design for identity, authentication, consent, and token events.
- Testcontainers integration tests, Docker-based local development, and CI/CD
  practice.

## Security Notes

- `/api/health` is public.
- Admin APIs under `/api/**` require OAuth2 JWT scopes and backend Admin RBAC.
  Scopes are necessary but not sufficient for administrative access.
- SCIM-style APIs under `/scim/v2/**` are scope-protected.
- The React Admin Console uses the persisted public `iam-admin-console` client
  with Authorization Code + PKCE.
- Browser login, MFA challenge, and consent are backend-owned at `/login`,
  `/login/mfa`, and `/oauth2/consent`.
- Confidential client secrets are shown only on creation or rotation.
- TOTP setup secrets and `otpauth://` URIs are shown only during enrollment.
- The default admin credentials, demo client secret, signing configuration,
  Compose credentials, and browser token storage model are for local
  development only.

After startup:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Current Limitations

This project is a portfolio-grade prototype, not a production-ready IAM
platform.

- Authorization and token storage is not fully distributed production storage.
- The Demo Payroll API is an in-server static resource API, not a real external
  service or real payroll system.
- MFA UX needs QR code and recovery code polish.
- Pairwise OIDC subject identifiers and advanced claim transformation remain
  future work.
- SCIM provisioning needs broader protocol and workflow polish.
- The frontend is functional but is not a fully polished enterprise console.
- Production secrets, signing keys, session management, rate limiting,
  observability, high availability, and operational hardening remain future
  work.

## Development Commands

Run backend tests. Docker is required for Testcontainers:

```bash
./mvnw test
```

Build the frontend:

```bash
cd frontend
npm run build
```

Stop local dependencies:

```bash
docker compose down
```

Reset local database volumes when a clean development environment is required:

```bash
./scripts/reset-local-db.sh
```

## Roadmap

Planning and production-hardening priorities are tracked in
[docs/ROADMAP.md](docs/ROADMAP.md).

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Spring Security and Spring Authorization Server
- Spring Data JPA and Flyway
- PostgreSQL and Redis
- React, TypeScript, and Vite
- Docker Compose
- Testcontainers
- OpenAPI / Swagger UI
- Maven Wrapper
- GitLab CI/CD
