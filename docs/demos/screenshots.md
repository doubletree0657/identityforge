# Screenshot Capture Guide

This guide defines a reproducible, security-safe visual set for portfolio
reviews. Screenshots must come from the running application at the commit being
presented. Do not fabricate UI images or retain screenshots after the interface
has materially changed.

The repository keeps this capture contract in source control so screenshots
can be refreshed deliberately. Generated images belong in
`docs/assets/screenshots/` using the filenames below.

## Capture Environment

1. Start PostgreSQL, IdentityForge with the `dev` profile, the Admin Console,
   and the Payroll resource service using the
   [Portfolio Review Guide](portfolio-review.md#local-demo-setup).
2. Reset to clean bootstrap data when prior mutations would distract from the
   story: `./scripts/reset-local-db.sh`, then restart the backend.
3. Use a desktop viewport of `1440 × 900` at 100% browser zoom. Capture a second
   `390 × 844` image only when demonstrating responsive navigation.
4. Use the default light theme, close developer tools, hide bookmarks and
   unrelated tabs, and capture the application viewport rather than the whole
   desktop.
5. Select **Development Tenant** before tenant-scoped captures.

## Canonical Visual Sequence

| Order | Filename | Screen | Story proved |
| --- | --- | --- | --- |
| 01 | `01-sign-in.png` | Admin Console sign-in | Branded Authorization Code + PKCE entry point; no password is collected by React. |
| 02 | `02-dashboard.png` | Dashboard with Development Tenant selected | Reviewer orientation, tenant context, inventory, and guided journeys. |
| 03 | `03-effective-authorization.png` | Synthetic `review-user` detail after assigning the seeded auditor role | Direct/group/effective RBAC visibility and account/MFA status without credentials. |
| 04 | `04-application-scopes.png` | Payroll API application | Separation of system IAM permissions from tenant-owned application scopes. |
| 05 | `05-oauth2-request.png` | OAuth2 & OIDC request ready | Registered redirect/scope validation and the real authorization journey. |
| 06 | `06-consent.png` | Backend-owned consent page using a consent-required demo client | Human-readable delegated scopes and explicit allow/deny choice. |
| 07 | `07-payroll-200-403.png` | Sanitized terminal or split visual | Same Payroll token yields employee `200` and salary `403`. Never show the token or Authorization header value. |
| 08 | `08-mfa-status.png` | MFA active status after recovery codes are safely stored | Verified factor and remaining-code count, with no setup key, QR, or plaintext code visible. |
| 09 | `09-scim-demo.png` | SCIM request preview | Supported subset, tenant-qualified URL, placeholder bearer token, and verification path. |
| 10 | `10-audit-events.png` | Audit Logs filtered to an exact demo action | Tenant-bound, non-sensitive security evidence. |
| 11 | `11-mobile-navigation.png` | Optional mobile Admin Console navigation | Responsive information architecture; supporting image, not the lead screenshot. |

The recommended README gallery is 02, 03, 05, 07, 09, and 10. The remaining
images support a longer case study or interview deck.

## Sensitive-Data Exclusion Rules

Never capture or commit:

- access tokens, refresh tokens, authorization codes, cookies, or session IDs;
- client secrets, password fields containing values, signing/encryption keys,
  or environment secrets;
- TOTP setup keys, `otpauth://` URIs, QR codes tied to a live factor, submitted
  TOTP values, or plaintext recovery codes;
- browser local storage, network panels, request headers, database consoles, or
  logs that may contain bearer material;
- real personal, organization, or payroll data.

UUIDs and local-only demo identifiers are acceptable, but crop them when they
do not help the story. Use only bootstrap or clearly synthetic identities.

For the Payroll proof, either redact the bearer value before capture or show
only sanitized response summaries:

```text
GET /api/payroll/employees  -> 200 OK
GET /api/payroll/salaries   -> 403 Forbidden (missing payroll.salary.read)
```

Do not use a blur that could be reversed or leave a token partially visible;
exclude the sensitive value before capture.

## Caption Contract

Every published screenshot should include a one-sentence caption with:

1. the workflow action;
2. the backend-enforced boundary it demonstrates; and
3. any relevant limitation.

Example:

> A user-authorized Payroll token containing `payroll.employee.read` succeeds
> on employee data and receives `403` on salary data; the service uses static
> demo records and is not a payroll product.

Avoid captions such as “enterprise-ready,” “production secure,” “SCIM
compliant,” or “zero trust.” Prefer precise language: “backend-enforced,”
“supported subset,” “separate process,” “short-lived JWT,” and “portfolio
demonstration.”

## Acceptance Checklist

- The image matches the current commit and the current navigation labels.
- The selected tenant and synthetic actor are understandable.
- No secret or bearer material is present, including in the address bar.
- The image proves one claim rather than showing an unexplained dense screen.
- The caption names both the enforcement boundary and the relevant limitation.
- Text remains legible at the README’s rendered width.
- Filenames and ordering match the canonical sequence.
- All README image links resolve on both GitHub and GitLab.

If a safe, current screenshot cannot be produced, omit it rather than checking
in a mockup. The diagrams and reproducible walkthrough remain the source of
truth until a live capture is available.
