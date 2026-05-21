import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { PageHeader } from './PageHeader';

export function PermissionsPage() {
  const [page, setPage] = useState(0);
  const [tenantId, setTenantId] = useState('');
  const queryClient = useQueryClient();
  const permissions = useQuery({ queryKey: ['permissions', page, tenantId], queryFn: () => adminApi.permissions.list({ page, size: 20, tenantId }) });
  const createPermission = useMutation({
    mutationFn: adminApi.permissions.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['permissions'] }),
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createPermission.mutate({ tenantId: String(form.get('tenantId') ?? ''), name: String(form.get('name') ?? '') });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Permissions" description="Create named capabilities that can be attached to roles." />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create permission">
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant ID"><Input name="tenantId" required /></Field>
            <Field label="Name"><Input name="name" placeholder="users:read" required /></Field>
            <Button type="submit">Create</Button>
            {createPermission.isError && <ErrorState error={createPermission.error} />}
          </form>
        </Card>
        <Card title="Permissions">
          <div className="mb-4"><Field label="Tenant filter"><Input value={tenantId} onChange={(event) => { setTenantId(event.target.value); setPage(0); }} /></Field></div>
          {permissions.isLoading && <LoadingState />}
          {permissions.isError && <ErrorState error={permissions.error} />}
          {permissions.data && (
            <>
              <DataTable
                items={permissions.data.items}
                columns={[
                  { header: 'Name', render: (permission) => <span className="font-medium">{permission.name}</span> },
                  { header: 'Tenant', render: (permission) => <span className="font-mono text-xs">{permission.tenantId}</span> },
                  { header: 'ID', render: (permission) => <span className="font-mono text-xs">{permission.id}</span> },
                ]}
              />
              <Pagination page={permissions.data} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </>
  );
}
