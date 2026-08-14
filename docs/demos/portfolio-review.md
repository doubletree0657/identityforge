# Portfolio Review Guide

This guide helps an external reviewer understand IdentityForge quickly and
then verify its strongest end-to-end behavior locally. It is a demonstration
script, not a production deployment guide.

## What IdentityForge Is

IdentityForge is a portfolio-grade IAM platform implemented as a Java modular
monolith with a React Admin Console and a separate OAuth2 resource-service
demo. It connects tenant-aware identity administration, additive RBAC, local
authentication, TOTP MFA, OAuth2/OIDC, a bounded SCIM 2.0 adapter, token
lifecycle controls, and audit evidence.

The project is deliberately not presented as a production IAM replacement. It
does not claim high availability, managed key rotation, distributed session or
throttling infrastructure, complete SCIM conformance, a general policy engine,
or real payroll behavior.

## Five-Minute Review Without Running the Project

| Question | Evidence |
| --- | --- |
| Are tenant and platform privileges distinct? | [Realm identity/platform authority ADR](../architecture/decisions/0002-realm-identity-and-platform-authority.md) and backend-enforced Admin API checks in `directory/access`. |
| Is OAuth authorization separate from application authorization? | The root README architecture diagrams and the [Payroll resource-service guide](external-payroll-resource-service.md). |
| Are protocol and credential boundaries explicit? | [SCIM support matrix](../protocols/scim.md), [MFA design](../security/mfa.md), and [session/token hardening](../security/session-and-token-hardening.md). |
| Is the resource-service boundary real? | `payroll-resource-service/` has its own Spring Boot application and security integration tests; it shares no domain model or database with the IAM application. |
| Is architecture enforced rather than only diagrammed? | `ArchitectureTests` verifies Spring Modulith dependencies and cycles; integration suites cover persistence, HTTP security, OAuth2, SCIM, and JWT enforcement. |
| Are limitations stated honestly? | [Current Limitations](../../README.md#current-limitations) and the [Roadmap](../ROADMAP.md). |

The best code-review starting points are:

- `src/main/java/io/github/doubletree/iam/directory/access` for concrete Admin
  API permissions and delegation rules;
- `src/main/java/io/github/doubletree/iam/oauth` for token profiles, browser
  flows, consent, and authorization lifecycle;
- `src/main/java/io/github/doubletree/iam/provisioning` for the SCIM protocol
  adapter over directory use cases;
- `frontend/src/pages` for operator workflows and protocol demonstrations;
- `payroll-resource-service/src/main` for independent audience/scope
  enforcement.

## Local Demo Setup

Prerequisites: JDK 21, Node.js 20+, npm, and Docker.

Run each long-lived process in its own terminal from the repository root:

```bash
# Terminal 1: PostgreSQL
docker compose up -d postgres

# Terminal 2: IdentityForge
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 3: Admin Console
cd frontend
npm install
npm run dev

# Terminal 4: independent Payroll resource service
./mvnw -f payroll-resource-service/pom.xml spring-boot:run
```

Confirm the public health endpoints before the walkthrough:

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8090/api/health
```

Open `http://localhost:5173` and use the local-only development account:

```text
realm / username: development/admin
password: admin123456
```

These credentials, the development client secret, HTTP URLs, and generated
signing key are demo conveniences. They are not deployment recommendations.

## Fifteen-Minute Narrated Walkthrough

### 1. Establish the Tenant Boundary

On **Dashboard**, select **Development Tenant**. Point out that the selected
tenant persists across directory, access, application, protocol, and audit
screens. Open **Tenants** and explain that suspension/archival is modeled as a
lifecycle transition rather than destructive cascade deletion.

Evidence to call out:

- tenant-qualified `realm/username` login;
- tenant-local normalized usernames;
- application and database checks on same-tenant relationships;
- platform operator authority stored separately from tenant-managed roles.

### 2. Trace Effective Authorization

Open **Roles** and the global **Permission Catalog** to show that tenant roles
select known system IAM permissions. The development administrator’s platform
operator authority is intentionally stored outside that tenant role graph; do
not describe it as a tenant-admin role assignment.

For visible effective-RBAC evidence, create a synthetic `review-user`, assign
the seeded `auditor` role, and open the user detail screen. In **Effective
authorization**, distinguish direct roles, group-derived roles, direct/group
permissions, and the de-duplicated effective result. The optional mutation
story below shows how to move the same authorization through a group.

The important boundary is that OAuth2 `iam.read`/`iam.write` scopes are
necessary but not sufficient: the backend also evaluates a concrete permission
such as `iam.users.read`. A tenant role cannot create platform authority merely
by using a privileged-looking name.

### 3. Separate IAM Permissions from Application Scopes

Open **Applications** and select **Payroll API**. Its
`payroll.employee.read`, `payroll.salary.read`, and `payroll.salary.write`
permissions describe capabilities in the external service; they do not grant
Admin API access.

Open **OAuth2 Clients** and select **IdentityForge Dev Client**. It is a
development confidential client linked to Payroll API and allowed to request
the three application permissions as scopes. The public
`identityforge-console` client is separate, requires PKCE, and targets the Admin
API audience.

### 4. Prove OAuth2/OIDC and Least Privilege

Open **OAuth2 & OIDC** and select **IdentityForge Dev Client**. Use the
registered redirect URI
`http://127.0.0.1:8080/oauth2/demo/callback`, select the identity scopes you
want to inspect, and select only `payroll.employee.read` from the application
scopes. Generate and open the authorization request.

The development client does not require consent by default. To demonstrate the
consent screen, first enable **Require consent** for that client or register a
new confidential authorization-code client using the Web App template. Do not
describe consent as mandatory for the bootstrap client.

After authorization, copy the `code` query parameter from the redirect address
and run the generated exchange command. Replace placeholders only in your local
terminal; do not paste tokens into documentation, screenshots, issue reports,
or third-party JWT sites.

Use the returned access token:

```bash
curl -i -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8090/api/payroll/employees

curl -i -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8090/api/payroll/salaries
```

Expected result: employees returns `200`; salaries returns `403`. The token is
valid, correctly signed, issued by IdentityForge, and intended for
`payroll-api`, but it lacks `payroll.salary.read`. This is the clearest
end-to-end proof in the repository.

Optionally call `/userinfo` with the same user-authorized access token and
compare released claims to the requested OIDC scopes. ID Tokens and UserInfo do
not expose effective permissions, credentials, secrets, or token values.

### 5. Show MFA Without Exposing Credentials

Open **MFA**. The flow is start enrollment, scan the locally rendered QR,
verify a six-digit TOTP, and save ten one-time recovery codes. Enrollment keys
and recovery codes are displayed once and must never appear in screenshots.

If you complete enrollment, sign out and repeat an authorization flow to show
the backend-owned MFA challenge. A recovery code can be entered in the same
challenge field and is consumed atomically. The attempt guard is process-local;
distributed throttling and a separate recent-authentication ceremony remain
production work.

### 6. Demonstrate the SCIM Adapter

Open **SCIM Provisioning**. Generate capability, user, group, or direct
membership requests and explain that the page leaves the bearer token as a
placeholder by design.

Executing a SCIM write requires a **user-authorized Admin API audience token**
with `iam.write` and the corresponding effective directory permission. The
bootstrap Payroll client’s client-credentials token is audience-bound to
`payroll-api` and intentionally cannot administer SCIM. For an execution demo,
register an unlinked confidential authorization-code client, authorize the
development platform operator for `iam.read iam.write`, exchange the code, and
use that short-lived token locally.

After a SCIM write, verify that the user or group appears in the normal
directory screen and that a `SCIM_*` action appears in **Audit Logs**. This
proves SCIM is a protocol adapter over the same directory and audit model, not
a second identity store.

### 7. Close on Audit and Failure Boundaries

Open **Audit Logs** and filter exact actions such as `SCIM_USER_CREATED`,
`MFA_VERIFIED`, or an OAuth2 lifecycle event produced during the walkthrough.
Explain that events record actor, tenant, target, outcome, request metadata,
and safe reason codes—not passwords, TOTP values, recovery codes, client
secrets, authorization codes, or tokens.

Finish with the project’s explicit limitations rather than a production claim:
single-deployment session/authorization state, process-local MFA throttling,
bounded SCIM support, static Payroll data, and missing managed key rotation,
observability, high availability, and generalized policy evaluation.

## Optional IAM Mutation Story

For a longer interview demonstration:

1. Create a user and set an initial password.
2. Create a role and attach one permission from the seeded catalog.
3. Assign the role directly and inspect effective authorization.
4. Create a group, move the assignment to the group, and add the user.
5. Show the same effective permission now arriving through group inheritance.
6. Review the resulting user, group, role, and audit events.

Use disposable names such as `review-user`, `review-operators`, and
`review-reader`. Reset the local database afterward with
`./scripts/reset-local-db.sh` if a clean bootstrap state is needed.

## What a Reviewer Should Conclude

- Identity, access, OAuth2 scopes, OIDC claims, and application permissions are
  related but intentionally separate concepts.
- Security decisions are enforced in backend policy and protocol layers; the
  React console is an operator experience, not the authorization boundary.
- One-time credentials and token values have explicit non-disclosure rules.
- The modular monolith keeps transactional IAM relationships coherent while
  making capability ownership and future extraction boundaries visible.
- Production gaps are documented as engineering work, not hidden behind the
  phrase “portfolio-grade.”

For consistent reviewer images, follow the [Screenshot Capture Guide](screenshots.md).
