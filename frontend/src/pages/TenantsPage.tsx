import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { DataTable } from '../components/Table';
import { ErrorState, LoadingState } from '../components/State';
import { TenantResponse, TenantStatus } from '../types/api';
import { compact, formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function TenantsPage() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const tenants = useQuery({
    queryKey: ['tenants', page],
    queryFn: () => adminApi.tenants.list({ page, size: 20 }),
  });
  const createTenant = useMutation({
    mutationFn: adminApi.tenants.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tenants'] }),
  });
  const updateTenant = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { name?: string; slug?: string; status?: TenantStatus } }) =>
      adminApi.tenants.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tenants'] }),
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createTenant.mutate({ name: String(form.get('name') ?? '') });
    event.currentTarget.reset();
  }

  function update(event: FormEvent<HTMLFormElement>, tenant: TenantResponse) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    updateTenant.mutate({
      id: tenant.id,
      body: compact({
        name: String(form.get('name') ?? ''),
        slug: String(form.get('slug') ?? ''),
        status: String(form.get('status') ?? tenant.status) as TenantStatus,
      }),
    });
  }

  return (
    <>
      <PageHeader title="Tenants" description="Manage tenant boundaries and lifecycle status." />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create tenant">
          <form onSubmit={create} className="grid gap-3">
            <Field label="Name">
              <Input name="name" required maxLength={120} />
            </Field>
            <Button type="submit" icon={<Save className="h-4 w-4" />} disabled={createTenant.isPending}>
              Create
            </Button>
            {createTenant.isError && <ErrorState error={createTenant.error} />}
          </form>
        </Card>
        <Card title="Tenant directory">
          {tenants.isLoading && <LoadingState />}
          {tenants.isError && <ErrorState error={tenants.error} />}
          {tenants.data && (
            <>
              <DataTable
                items={tenants.data.items}
                columns={[
                  { header: 'Name', render: (tenant) => <span className="font-medium">{tenant.name}</span> },
                  { header: 'Slug', render: (tenant) => tenant.slug },
                  { header: 'Status', render: (tenant) => <Badge>{tenant.status}</Badge> },
                  { header: 'Updated', render: (tenant) => formatDate(tenant.updatedAt) },
                  {
                    header: 'Edit',
                    render: (tenant) => (
                      <form onSubmit={(event) => update(event, tenant)} className="grid min-w-[320px] gap-2">
                        <Input name="name" defaultValue={tenant.name} aria-label="Tenant name" />
                        <Input name="slug" defaultValue={tenant.slug} aria-label="Tenant slug" />
                        <Select name="status" defaultValue={tenant.status} aria-label="Tenant status">
                          <option value="ACTIVE">ACTIVE</option>
                          <option value="SUSPENDED">SUSPENDED</option>
                          <option value="ARCHIVED">ARCHIVED</option>
                        </Select>
                        <Button type="submit" variant="secondary" disabled={updateTenant.isPending}>Save</Button>
                      </form>
                    ),
                  },
                ]}
              />
              <Pagination page={tenants.data} onPageChange={setPage} />
            </>
          )}
          {updateTenant.isError && <div className="mt-3"><ErrorState error={updateTenant.error} /></div>}
        </Card>
      </div>
    </>
  );
}
