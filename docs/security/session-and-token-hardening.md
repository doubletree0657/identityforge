# Session and Token Hardening

IdentityForge uses database-local durable authorization state and servlet
sessions. This closes the single-deployment lifecycle gaps without claiming
distributed session, cache, denylist, or high-availability infrastructure.

## Browser session boundary

- Browser authentication uses an `HttpOnly`, `SameSite=Lax` cookie. Production
  defaults require `Secure`; the development profile disables it for local HTTP.
- Sessions have a 15-minute idle timeout and an eight-hour absolute timeout.
  Both are configurable, but production deployments should only shorten them
  without a threat-model review.
- Spring Security changes the session ID after authentication. Every
  authenticated browser request revalidates the persisted account, tenant,
  password-reset, and security-version state.
- Logout is a CSRF-protected `POST`. `GET /logout` only renders a confirmation
  form; it cannot mutate authentication state. Successful Admin Console logout
  invalidates the session, clears the cookie, revokes that user's Admin Console
  authorization grants, and records a non-sensitive audit event.
- Login, MFA, consent, and logout HTML responses are non-cacheable.
- Browser routes send a restrictive content security policy, deny framing and
  referrer disclosure, and retain Spring Security's secure response headers.

The Admin and SCIM APIs are a separate stateless filter chain. They never use a
browser session and accept bearer tokens only, so CSRF is disabled only on that
non-cookie boundary. OAuth token, revocation, introspection, and device
authorization endpoints are also non-cookie protocol endpoints. The
session-authenticated authorization/consent `POST` remains CSRF protected.

## Token lifecycle

Defaults are deliberately bounded:

| Artifact | Lifetime / behavior |
| --- | --- |
| Authorization code | 5 minutes |
| JWT access token | 10 minutes |
| Refresh token | 8 hours, rotated on every use |
| Expired authorization row | purged hourly |

Authorization and consent state use the existing PostgreSQL schema through
Spring Authorization Server's JDBC services. Refresh-token rotation retains
only a SHA-256 digest of a used token until family expiry; replay detection
revokes the successor family. The revocation endpoint removes the complete
authorization family, so its access and refresh tokens immediately fail when presented back to
IdentityForge. Consent revocation does the same for that user/client pair.

IdentityForge validates both persisted authorization state and user security
state for access tokens used on its own APIs. Database triggers remove affected
authorization families after:

- a password, MFA, account status, role, permission, or group membership change
  increments a user's security version;
- a tenant status change or user deletion;
- a client registration, secret, scope, grant, redirect URI, authentication
  method, application permission, or linked resource-server status change.

This is intentionally fail-closed. A security-relevant administration change
requires the affected user or client to authorize again.

Independent resource servers validate self-contained JWTs without an
IdentityForge database dependency. They therefore have a maximum residual
revocation window equal to the access-token lifetime (10 minutes by default).
Immediate cross-service revocation requires introspection, a distributed
denylist, or event propagation and is outside this milestone.

## Audit contract

Lifecycle auditing records identifiers and reason codes, never token, code,
cookie, password, or secret values. Important actions include:

- `USER_LOGGED_OUT` and `USER_SESSION_REJECTED`;
- `OAUTH2_TOKEN_REFRESHED` and `OAUTH2_TOKEN_REFRESH_FAILED`;
- `OAUTH2_REFRESH_TOKEN_REUSE_DETECTED`;
- `OAUTH2_TOKEN_FAMILY_REVOKED` and `OAUTH2_TOKEN_REVOCATION_NOOP`;
- existing consent and security-state administration events.

## Production configuration and key practice

Production must set the existing database, issuer, allowed-origin, Admin
Console, encryption-key, and signing-key environment variables. Relevant
lifecycle overrides are:

```text
IAM_ACCESS_TOKEN_TTL=PT10M
IAM_REFRESH_TOKEN_TTL=PT8H
IAM_AUTHORIZATION_CODE_TTL=PT5M
IAM_AUTHORIZATION_CLEANUP_INTERVAL=PT1H
IAM_SESSION_IDLE_TIMEOUT=15m
IAM_SESSION_ABSOLUTE_TIMEOUT=PT8H
IAM_COOKIE_SECURE=true
```

The default profile never creates a missing signing key. Provision
`IAM_SIGNING_KEY_FILE` before startup, mount it read-only with least-privilege
filesystem access, keep it out of images and source control, back it up through
the deployment secret system, and monitor its expiry/rotation plan. The `dev`
and test profiles alone allow local key generation.

The current provider exposes one signing key. Until managed multi-key rotation
is implemented, rotate with a coordinated deployment that retains the previous
public key for at least the maximum token lifetime, or accept that outstanding
tokens will fail after cutover. Do not rotate `IAM_SECRET_ENCRYPTION_KEY`
in-place: current encrypted MFA material must first be re-encrypted through a
planned migration. PostgreSQL authorization rows contain bearer credentials and
must receive the same encryption-at-rest, backup, access-control, and log
redaction protections as other secrets.

## Explicit non-goals

- distributed servlet-session storage or concurrent-session coordination;
- a distributed token denylist or cross-service revocation event bus;
- managed KMS/HSM signing, overlapping-key publication, or automatic rotation;
- high availability, rate limiting, and distributed MFA throttling.
