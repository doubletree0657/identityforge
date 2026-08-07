# ADR 0003: Single Issuer and Audience-Bound Tokens

## Status

Accepted

## Decision

Use one externally configured issuer, so OAuth `client_id` is globally unique.
Admin access tokens target `identityforge-admin-api`; application access tokens
target the linked resource-server identifier. Resource endpoints require both
the expected audience and scope.

User access-token subjects are immutable user IDs. Application tokens omit the
complete IAM role, group, and permission graph. Admin tokens contain only the
effective authorization data required by the administration API. A credentials
security version is validated on token use so account, tenant, password, MFA,
and authorization changes can invalidate stale tokens.

Signing material is accessed through `SigningKeyProvider`. The development
implementation persists an RSA key to a configured, ignored local file. Managed
key storage and overlapping previous-key verification remain deployment
hardening work.
