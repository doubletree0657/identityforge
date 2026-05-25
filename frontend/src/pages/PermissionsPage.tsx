import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Card } from '../components/Card';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useTenantContext } from '../context/TenantContext';
import { PermissionResponse } from '../types/api';
import { PageHeader } from './PageHeader';

export function PermissionsPage() {
  const { selectedTenantId } = useTenantContext();
  const permissions = useQuery({
    queryKey: ['permissions-catalog'],
    queryFn: () => adminApi.permissions.list({ size: 100 }),
  });
  const roles = useQuery({
    queryKey: ['roles-for-permission-catalog', selectedTenantId],
    queryFn: () => adminApi.roles.list({ size: 100, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });

  const categories = groupByCategory(permissions.data?.items ?? []);

  return (
    <>
      <PageHeader title="Permission Catalog" description="System IAM permissions are seeded by the backend and assigned to roles from this catalog." />
      {!selectedTenantId && (
        <Card title="Catalog">
          <p className="text-sm text-slate-600">The IAM permission catalog is global. Select a tenant to see which tenant roles use each permission.</p>
        </Card>
      )}
      {permissions.isLoading && <LoadingState />}
      {permissions.isError && <ErrorState error={permissions.error} />}
      {permissions.data && (
        <div className="grid gap-4">
          {Object.entries(categories).map(([category, items]) => (
            <Card key={category} title={category}>
              <DataTable
                items={items}
                columns={[
                  {
                    header: 'Permission',
                    render: (permission) => (
                      <div>
                        <div className="font-medium">{permission.displayName || permission.name}</div>
                        <div className="font-mono text-xs text-slate-500">{permission.name}</div>
                      </div>
                    ),
                  },
                  { header: 'Description', render: (permission) => permission.description || '-' },
                  {
                    header: 'Managed',
                    render: (permission) => <Badge>{permission.systemManaged ? 'SYSTEM' : 'CUSTOM'}</Badge>,
                  },
                  {
                    header: 'Used by roles',
                    render: (permission) => {
                      if (!selectedTenantId) {
                        return <span className="text-sm text-slate-500">Select tenant</span>;
                      }
                      const roleNames = roles.data?.items
                        .filter((role) => role.permissionIds.includes(permission.id))
                        .map((role) => role.name) ?? [];
                      return roleNames.length === 0 ? (
                        <span className="text-sm text-slate-500">No roles</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {roleNames.map((roleName) => <Badge key={roleName}>{roleName}</Badge>)}
                        </div>
                      );
                    },
                  },
                ]}
              />
            </Card>
          ))}
        </div>
      )}
    </>
  );
}

function groupByCategory(permissions: PermissionResponse[]) {
  return permissions.reduce<Record<string, PermissionResponse[]>>((groups, permission) => {
    const category = permission.category || 'Custom';
    groups[category] = [...(groups[category] ?? []), permission];
    return groups;
  }, {});
}
