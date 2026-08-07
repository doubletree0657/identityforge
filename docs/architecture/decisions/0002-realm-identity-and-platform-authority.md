# ADR 0002: Realm Identity and Platform Authority

## Status

Accepted

## Decision

Identify users by `(tenant_id, normalized_username)`. Interactive login requires
`realm/username`, where realm is the immutable tenant slug. Usernames use NFKC,
trim, and locale-independent lowercase normalization for lookup while retaining
the entered display value.

Store platform operator authority outside tenant RBAC. Tenant-owned role names,
including `platform-admin`, never confer platform trust. Only a platform actor
may grant or revoke platform authority, and authority changes increment the
target user's security version.

## Consequences

Different tenants may use the same username without ambiguous authentication.
Realm slugs are explicit API input and cannot be silently changed. Tenant
administrators cannot manufacture platform authority through role or permission
assignment.
