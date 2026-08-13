# MFA Design

IdentityForge supports a TOTP factor plus one-time recovery codes for local
users. This is a portfolio-grade implementation with explicit boundaries; it is
not a substitute for production key management, distributed throttling, or a
formal step-up-authentication policy.

## Lifecycle and API

The Admin Console uses the signed-in user's ID from `/api/me`. Secret-bearing
operations are enforced as self-service in the application layer even when the
caller has `iam.mfa.manage`:

| Operation | Endpoint | Sensitive response |
| --- | --- | --- |
| Read status | `GET /api/users/{id}/mfa/totp` | No; state and counts only |
| Start/restart setup | `POST /api/users/{id}/mfa/totp/enrollment` | TOTP setup key and `otpauth://` URI, once |
| Verify setup | `POST /api/users/{id}/mfa/totp/verification` | Ten recovery codes on first verification, once |
| Replace recovery set | `POST /api/users/{id}/mfa/totp/recovery-codes` | Ten replacement codes, once |
| Disable factor | `DELETE /api/users/{id}/mfa/totp` | No |

Enrollment of a replacement secret does not deactivate the current verified
factor. The old factor and recovery set remain valid until the pending secret is
successfully verified; promotion then atomically replaces the factor and all
recovery codes. Initial verification and replacement advance the user's
credential security version so existing access tokens become stale. The UI
keeps the one-time response visible and tells the user to save it before signing
in again.

## Secret and recovery-code handling

- TOTP secrets contain 160 random bits, are encrypted with AES-GCM, and appear
  only in enrollment responses. The QR is rendered locally from that same URI.
- Each recovery code contains 80 random bits and is formatted as four groups of
  four Base32 characters. Ten codes are issued per set.
- The database stores only a deterministic HMAC-SHA-256 digest. Its key is
  derived from the configured secret-encryption key with a recovery-code domain
  label, separating it from the AES key use.
- Login normalizes formatting, calculates the digest, and performs a conditional
  `unused -> used` update. A concurrent or repeated use can succeed at most once.
- Regeneration replaces the full set. Re-enrollment promotion and MFA disable
  revoke the old set. Used rows remain until replacement/disable so status can
  report total and remaining counts.
- Plaintext codes, hashes, setup keys, provisioning URIs, and submitted MFA
  values are never written to audit events.
- Responses that can contain setup or recovery material send
  `Cache-Control: no-store`.

## Authentication and audit behavior

The backend-owned `/login/mfa` form accepts either a six-digit TOTP or a
recovery code. TOTP time-step replay protection remains active. TOTP and
recovery attempts share the per-user attempt guard, and the browser receives a
generic error.

Successful and denied transitions produce tenant-bound audit evidence:

| Event | Meaning |
| --- | --- |
| `MFA_ENROLLED` / `MFA_REENROLLMENT_STARTED` | A pending setup was created |
| `MFA_VERIFIED` / `MFA_VERIFY_FAILED` / `MFA_VERIFY_THROTTLED` | Setup proof result |
| `MFA_RECOVERY_CODES_GENERATED` / `MFA_RECOVERY_CODES_REGENERATED` | A new set replaced the prior set |
| `MFA_RECOVERY_CODE_USED` | One recovery code completed a login challenge |
| `MFA_RECOVERY_CODES_REVOKED` | Disable removed a recovery set |
| `MFA_CHALLENGE_SUCCEEDED` / `MFA_CHALLENGE_FAILED` / `MFA_CHALLENGE_THROTTLED` | Login challenge result |
| `MFA_DISABLED` | The TOTP credential and recovery set were removed |

Audit records contain actor, tenant, target user, result, request metadata, and
safe reason codes only.

## Production hardening boundary

The current attempt guard is in-process and should become a durable distributed
control before horizontal scaling. Enrollment and recovery-code regeneration
trust the short-lived Admin Console bearer token; because enabling or replacing
MFA invalidates prior tokens, subsequent tokens pass the MFA login challenge. A
production system should still model explicit recent-authentication/step-up
requirements, managed and rotatable digest/encryption keys, session revocation,
security monitoring, and recovery-support policy.
