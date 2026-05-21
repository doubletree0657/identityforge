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
authorization-code login flow, a Dockerfile, local PostgreSQL/Redis Compose
services, a React TypeScript Admin Console under `frontend/`, and a GitLab CI
pipeline.

The Admin API surface is completed and hardened for frontend consumption.
Current management APIs cover tenants, users, profiles, custom user attributes,
groups, roles, permissions, OAuth2 clients, MFA enrollment operations, and audit
log queries with paginated list responses, practical validation, safer default
profile handling, consistent validation errors, and safe response DTOs that
avoid exposing password hashes, TOTP secret material, and client secret hashes.
The Admin Console demonstrates those APIs through real backend calls and a
development bearer-token panel.

The project has performed a pre-release Flyway schema reset toward a stronger
identity model. The current baseline includes tenant status, richer user
identity fields, separate user profiles, separate password and TOTP
credentials, custom attributes, explicit group memberships, tenant-scoped
RBAC, OAuth2 client registration concepts, and security event metadata.

Login support currently uses Spring Security's default server-side form login as
an early integration step for browser-based authorization work. It is not a
product-grade login experience, does not include a frontend, and does not by
itself grant access to management or SCIM APIs. Those APIs remain protected by
OAuth2 JWT scope checks.

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
./mvnw spring-boot:run
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
`VITE_API_BASE_URL` or with the API base URL field in the console header.

Paste a development OAuth2 access token with `iam.read` and/or `iam.write`
scopes into the token panel. The token is stored in local storage for local
development only. Do not paste passwords, client secrets, refresh tokens, or
other long-lived production credentials.

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

The health endpoint is public. Management APIs under `/api/**` and SCIM APIs
under `/scim/v2/**` require OAuth2 JWT scopes:

- Read operations require `iam.read`.
- Write operations require `iam.write`.

## OAuth2 Authorization Code Demo

The backend now supports a development authorization-code flow using persisted
OAuth2 clients and local username/password login.

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

4. The browser is redirected to `/login`; after local form login, the
   authorization server redirects back to the client redirect URI with a code.
5. Exchange the code at `/oauth2/token` with the confidential client's
   authentication and use the returned bearer token on `/api/**` requests that
   require `iam.read`.

The executable integration test
`OAuth2AuthorizationCodeLoginFlowTests` seeds this setup and proves the full
flow end to end.

## Roadmap

Planning is tracked in [docs/ROADMAP.md](docs/ROADMAP.md). The roadmap is the
single planning document for current direction, future product tracks, and
documentation policy.
