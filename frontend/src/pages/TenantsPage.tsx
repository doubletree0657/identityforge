import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { Notice } from '../components/Notice';
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
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenant-context-tenants'] });
    },
  });
  const updateTenant = useMutation({
    mutationFn: ({ id, body }: { id: string; body: { name?: string; slug?: string; status?: TenantStatus } }) =>
      adminApi.tenants.update(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenant-context-tenants'] });
    },
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const formElement = event.currentTarget;
    createTenant.mutate({
      name: String(form.get('name') ?? ''),
      slug: String(form.get('slug') ?? ''),
    }, { onSuccess: () => formElement.reset() });
  }

  function update(event: FormEvent<HTMLFormElement>, tenant: TenantResponse) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    updateTenant.mutate({
      id: tenant.id,
      body: compact({
        name: String(form.get('name') ?? ''),
        status: String(form.get('status') ?? tenant.status) as TenantStatus,
      }),
    });
  }

  return (
    <>
      <PageHeader title="Tenants" description="Manage tenant boundaries and lifecycle status. This console uses status-based lifecycle actions instead of risky cascade deletion." />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create tenant">
          <form onSubmit={create} className="grid gap-3">
            <Field label="Name" required hint="Human-readable organization name, up to 120 characters.">
              <Input name="name" required maxLength={120} />
            </Field>
            <Field label="Realm slug" required hint="Lowercase letters, numbers, and internal hyphens. Used in realm-qualified sign-in.">
              <Input name="slug" placeholder="acme-corp" required maxLength={63} pattern="[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" title="Use lowercase letters, numbers, and internal hyphens." />
            </Field>
            <Button type="submit" icon={<Save className="h-4 w-4" />} isLoading={createTenant.isPending} loadingLabel="Creating tenant…">
              Create tenant
            </Button>
            {createTenant.isError && <ErrorState error={createTenant.error} />}
            {createTenant.isSuccess && <Notice title="Tenant created" tone="success">It is now available in the global tenant context selector.</Notice>}
          </form>
        </Card>
        <Card title="Tenant directory">
          {tenants.isLoading && <LoadingState />}
          {tenants.isError && <ErrorState error={tenants.error} onRetry={() => void tenants.refetch()} />}
          {tenants.data && (
            <>
              <DataTable
                items={tenants.data.items}
                getKey={(tenant) => tenant.id}
                emptyTitle="No tenants available"
                emptyDetail="Create the first tenant to establish an isolated realm for identities and applications."
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
                        <Input value={tenant.slug} aria-label="Tenant realm slug" readOnly />
                        <Select name="status" defaultValue={tenant.status} aria-label="Tenant status">
                          <option value="ACTIVE">ACTIVE</option>
                          <option value="SUSPENDED">SUSPENDED</option>
                          <option value="ARCHIVED">ARCHIVED</option>
                        </Select>
                        <div className="flex flex-wrap gap-2">
                          <Button type="submit" variant="secondary" disabled={updateTenant.isPending}>Save</Button>
                          <Button type="button" variant="secondary" onClick={() => updateTenant.mutate({ id: tenant.id, body: { status: 'ACTIVE' } })}>Reactivate</Button>
                          <Button type="button" variant="danger" onClick={() => updateTenant.mutate({ id: tenant.id, body: { status: 'SUSPENDED' } })}>Suspend</Button>
                        </div>
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
