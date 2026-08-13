# SCIM 2.0 Supported Subset

IdentityForge implements a deliberately bounded SCIM 2.0 provisioning subset
based on the [SCIM core schema (RFC 7643)](https://www.rfc-editor.org/rfc/rfc7643)
and [SCIM protocol (RFC 7644)](https://www.rfc-editor.org/rfc/rfc7644/).
It is suitable for demonstrating interoperable user and direct-group
provisioning behavior; it is not a claim of complete SCIM conformance.

The tenant-specific base URL is:

```text
/scim/v2/{tenantId}
```

Requests use an OAuth 2.0 bearer token whose audience contains
`identityforge-admin-api`. Read operations require `iam.read`; writes require
`iam.write`. The token also needs the corresponding `iam.users.*` or
`iam.groups.*` permission, and non-platform actors may access only their token's
`tenant_id`. `ServiceProviderConfig` requires `iam.users.read`.

## Resource and Endpoint Matrix

All resource and error responses use `application/scim+json`. JSON request
bodies accept that media type or `application/json`.

| Endpoint | GET | POST | PUT | PATCH | DELETE |
| --- | --- | --- | --- | --- | --- |
| `/ServiceProviderConfig` | Supported capabilities | — | — | — | — |
| `/Users` | List/filter | Create | — | — | — |
| `/Users/{id}` | Read | — | Replace | Supported attributes | Delete |
| `/Groups` | List/filter | Create | — | — | — |
| `/Groups/{id}` | Read | — | Replace with direct members | Attributes/membership | Delete |

User resources support:

- `id`, `userName`, `displayName`, `active`, zero or one `emails` value,
  read-only direct `groups`, and `meta`.
- `userName` is unique after normalization within a tenant.
- `active: true` maps to `ACTIVE`; `false` maps to `DISABLED`. Other internal
  states are returned as inactive.
- Passwords, MFA credentials, roles, permissions, and custom attributes are
  never accepted or returned by SCIM.

Group resources support `id`, `displayName`, direct user `members`, and `meta`.
Nested group members are rejected. Membership changes update the same directory
relationship used by RBAC and invalidate the security version of affected users
so stale access tokens cannot retain group-derived authorization.

## Filtering and Pagination

Filtering is a bounded, index-backed `eq` subset. Attribute names and `eq` are
case-insensitive; string values must be quoted.

| Resource | Supported filters |
| --- | --- |
| User | `userName`, `displayName`, `emails.value`, `active` |
| Group | `displayName`, `members.value` |

`members.value` must be a user UUID. Boolean `active` values are unquoted.
Logical operators, value-path filters outside the documented membership removal
form, and operators such as `co`, `sw`, and `pr` return `invalidFilter`.

Pagination uses SCIM's one-based `startIndex` and `count`. Defaults are
`startIndex=1` and `count=50`; the server caps `count` at 100. `count=0` returns
only `totalResults`. Results use a stable `createdAt`, then `id`, order and are
wrapped in the standard `ListResponse` schema with `Resources`, `startIndex`,
and `itemsPerPage`.

```http
GET /scim/v2/{tenantId}/Users?filter=userName%20eq%20%22alice%22&startIndex=1&count=25
Authorization: Bearer <admin-access-token>
Accept: application/scim+json
```

## PATCH and Replacement Semantics

PATCH bodies must contain only the PatchOp schema and the capitalized
`Operations` field. Every operation needs an explicit `path`.

| Resource | Paths | Behavior |
| --- | --- | --- |
| User | `userName`, `displayName`, `active` | `add`/`replace`; removal rejected |
| User | `emails` | `add`/`replace` one email object; `remove` clears it |
| Group | `displayName` | `add`/`replace`; removal rejected |
| Group | `members` | `add` unions direct users; `replace` replaces all; `remove` clears all |
| Group | `members[value eq "{userId}"]` | `remove` deletes one direct member |

Pathless PATCH, user `groups` mutation, nested groups, extension attributes,
and unsupported paths return a SCIM error. PUT is replacement for this supported
attribute set: omitted optional values return to their documented defaults, and
omitted group members remove all direct memberships.

Resource responses include a strong `ETag` and the same `meta.version` value.
PUT, PATCH, and DELETE accept optional `If-Match`; a stale version returns HTTP
`412` before mutation. `If-Match: *` requests mutation only if the resource
exists. Database optimistic locking remains the final concurrent-write guard.

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations": [
    {
      "op": "add",
      "path": "members",
      "value": [{"value": "00000000-0000-0000-0000-000000000002", "type": "User"}]
    }
  ]
}
```

## Errors, Tenant Isolation, and Audit

Protocol errors use the SCIM Error schema with string `status`, safe `detail`,
and `scimType` where applicable. The implemented mappings include:

| Condition | HTTP | `scimType` |
| --- | --- | --- |
| Malformed JSON/media type | 400/415 | `invalidSyntax` |
| Bad filter | 400 | `invalidFilter` |
| Unsupported PATCH path | 400 | `invalidPath` |
| Invalid schema/value/pagination | 400 | `invalidValue` |
| Duplicate tenant-local value | 409 | `uniqueness` |
| Stale `If-Match` | 412 | omitted |
| Missing resource | 404 | omitted |
| Tenant boundary or permission failure | 403 | omitted, generic detail |

Authentication failures generated before controller dispatch also use the SCIM
Error schema. Application-level tenant lookup and relationship checks are backed
by tenant-qualified repository queries and database same-tenant constraints.
Cross-tenant membership is validated before the group is mutated.

Successful protocol operations record `SCIM_USER_*`, `SCIM_GROUP_*`, and
`SCIM_GROUP_MEMBERSHIP_CHANGED` events in addition to lower-level directory
events. Authenticated validation and tenant failures record
`SCIM_REQUEST_REJECTED` with a non-sensitive reason code. A rejected
cross-tenant request is written to the caller's audit stream, not the tenant ID
chosen in the URL. Request bodies, email values, filters, credentials, and token
contents are not copied into audit details. These events are visible in the
Admin Console audit log; provisioned users and groups appear in the existing
directory screens without a separate SCIM-only data model.

## Explicit Non-Goals

The current subset does not implement `/Schemas`, `/ResourceTypes`, bulk,
sorting, password changes, enterprise/custom schema extensions, attribute
projection, POST search, nested groups, or the full SCIM filter grammar.
`ServiceProviderConfig` reports the supported capability flags and limits so a
client can discover these boundaries. Production deployment would additionally
need dedicated provisioning clients/scopes, distributed rate limiting,
operational conformance testing against target identity providers, and a formal
deprovisioning/retention policy.
