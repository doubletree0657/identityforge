import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { PageHeader } from './PageHeader';

export function RolesPage() {
  const [page, setPage] = useState(0);
  const [tenantId, setTenantId] = useState('');
  const queryClient = useQueryClient();
  const roles = useQuery({ queryKey: ['roles', page, tenantId], queryFn: () => adminApi.roles.list({ page, size: 20, tenantId }) });
  const permissions = useQuery({ queryKey: ['permissions-for-roles', tenantId], queryFn: () => adminApi.permissions.list({ tenantId, size: 100 }) });
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
    createRole.mutate({ tenantId: String(form.get('tenantId') ?? ''), name: String(form.get('name') ?? '') });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Roles" description="Create roles and attach tenant-scoped permissions." />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create role">
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant ID"><Input name="tenantId" required /></Field>
            <Field label="Name"><Input name="name" required /></Field>
            <Button type="submit">Create</Button>
            {createRole.isError && <ErrorState error={createRole.error} />}
          </form>
        </Card>
        <Card title="Roles">
          <div className="mb-4"><Field label="Tenant filter"><Input value={tenantId} onChange={(event) => { setTenantId(event.target.value); setPage(0); }} /></Field></div>
          {roles.isLoading && <LoadingState />}
          {roles.isError && <ErrorState error={roles.error} />}
          {roles.data && (
            <>
              <DataTable
                items={roles.data.items}
                columns={[
                  { header: 'Name', render: (role) => <span className="font-medium">{role.name}</span> },
                  { header: 'Permissions', render: (role) => role.permissionIds.length },
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
                  {
                    header: 'Remove',
                    render: (role) => (
                      <form
                        className="flex min-w-[260px] gap-2"
                        onSubmit={(event) => {
                          event.preventDefault();
                          removePermission.mutate({ roleId: role.id, permissionId: String(new FormData(event.currentTarget).get('permissionId') ?? '') });
                        }}
                      >
                        <Input name="permissionId" placeholder="Permission UUID" />
                        <Button type="submit" variant="danger">Remove</Button>
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
