import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { TenantRequired } from '../components/TenantRequired';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function RolesPage() {
  const [page, setPage] = useState(0);
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const queryClient = useQueryClient();
  const roles = useQuery({
    queryKey: ['roles', page, selectedTenantId],
    queryFn: () => adminApi.roles.list({ page, size: 20, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const permissions = useQuery({
    queryKey: ['permissions-for-roles', selectedTenantId],
    queryFn: () => adminApi.permissions.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const users = useQuery({
    queryKey: ['users-for-role-assignments', selectedTenantId],
    queryFn: () => adminApi.users.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const groups = useQuery({
    queryKey: ['groups-for-role-assignments', selectedTenantId],
    queryFn: () => adminApi.groups.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const createRole = useMutation({
    mutationFn: adminApi.roles.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['roles'] }),
  });
  const assignPermission = useMutation({
    mutationFn: ({ roleId, permissionId }: { roleId: string; permissionId: string }) => adminApi.roles.assignPermission(roleId, permissionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['roles'] }),
  });
  const removePermission = useMutation({
    mutationFn: ({ roleId, permissionId }: { roleId: string; permissionId: string }) => adminApi.roles.removePermission(roleId, permissionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['roles'] }),
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createRole.mutate({ tenantId: selectedTenantId, name: String(form.get('name') ?? '') });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Roles" description="Create roles and attach tenant-scoped permissions." />
      {!selectedTenantId && <TenantRequired label="Roles are tenant scoped. Select a tenant to create roles and assign permissions." />}
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create role">
          {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant in the header before creating roles.</p>}
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant"><Input value={selectedTenant?.name ?? 'No tenant selected'} disabled /></Field>
            <Field label="Name"><Input name="name" required /></Field>
            <Button type="submit" disabled={!selectedTenantId}>Create</Button>
            {createRole.isError && <ErrorState error={createRole.error} />}
          </form>
        </Card>
        <Card title="Roles">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant to load roles and available permissions.</p>}
          {roles.isLoading && <LoadingState />}
          {roles.isError && <ErrorState error={roles.error} />}
          {roles.data && (
            <>
              <DataTable
                items={roles.data.items}
                columns={[
                  { header: 'Name', render: (role) => <span className="font-medium">{role.name}</span> },
                  {
                    header: 'Permissions',
                    render: (role) => (
                      <div className="flex max-w-[360px] flex-wrap gap-1">
                        {role.permissionIds.length === 0 && <span className="text-sm text-slate-500">No permissions</span>}
                        {role.permissionIds.map((permissionId) => {
                          const permission = permissions.data?.items.find((item) => item.id === permissionId);
                          return (
                            <button
                              key={permissionId}
                              type="button"
                              onClick={() => removePermission.mutate({ roleId: role.id, permissionId })}
                              className="rounded-full border border-line bg-slate-50 px-2 py-1 text-xs text-slate-700"
                            >
                              {permission?.name ?? permissionId} ×
                            </button>
                          );
                        })}
                      </div>
                    ),
                  },
                  {
                    header: 'Assignments',
                    render: (role) => {
                      const userCount = users.data?.items.filter((user) => user.roleIds.includes(role.id)).length ?? role.userAssignmentCount;
                      const groupCount = groups.data?.items.filter((group) => group.roleIds.includes(role.id)).length ?? role.groupAssignmentCount;
                      return <span className="text-sm text-slate-600">{userCount} users / {groupCount} groups</span>;
                    },
                  },
                  {
                    header: 'Assign permission',
                    render: (role) => (
                      <form
                        className="flex min-w-[300px] gap-2"
                        onSubmit={(event) => {
                          event.preventDefault();
                          assignPermission.mutate({ roleId: role.id, permissionId: String(new FormData(event.currentTarget).get('permissionId') ?? '') });
                        }}
                      >
                        <Select name="permissionId" className="flex-1">
                          <option value="">Select permission</option>
                          {permissions.data?.items.map((permission) => <option key={permission.id} value={permission.id}>{permission.name}</option>)}
                        </Select>
                        <Button type="submit" variant="secondary">Assign</Button>
                      </form>
                    ),
                  },
                ]}
              />
              <Pagination page={roles.data} onPageChange={setPage} />
            </>
          )}
          {(assignPermission.isError || removePermission.isError) && <div className="mt-3"><ErrorState error={assignPermission.error ?? removePermission.error} /></div>}
        </Card>
      </div>
    </>
  );
}
