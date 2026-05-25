UPDATE permissions
SET system_managed = TRUE
WHERE name IN (
    'iam.admin',
    'iam.tenants.read',
    'iam.tenants.write',
    'iam.users.read',
    'iam.users.write',
    'iam.groups.read',
    'iam.groups.write',
    'iam.roles.read',
    'iam.roles.write',
    'iam.permissions.read',
    'iam.permissions.write',
    'iam.clients.read',
    'iam.clients.write',
    'iam.audit.read',
    'iam.mfa.manage'
);

UPDATE role_permissions
SET permission_id = canonical.id
FROM permissions duplicate
JOIN (
    SELECT name, MIN(id) AS id
    FROM permissions
    WHERE system_managed = TRUE
    GROUP BY name
) canonical ON canonical.name = duplicate.name
WHERE role_permissions.permission_id = duplicate.id
  AND duplicate.system_managed = TRUE
  AND duplicate.id <> canonical.id;

DELETE FROM permissions duplicate
USING (
    SELECT name, MIN(id) AS id
    FROM permissions
    WHERE system_managed = TRUE
    GROUP BY name
) canonical
WHERE duplicate.system_managed = TRUE
  AND duplicate.name = canonical.name
  AND duplicate.id <> canonical.id;

ALTER TABLE permissions
    ALTER COLUMN tenant_id DROP NOT NULL;

UPDATE permissions
SET tenant_id = NULL
WHERE system_managed = TRUE;

CREATE UNIQUE INDEX uq_permissions_system_name
    ON permissions (name)
    WHERE system_managed = TRUE;
