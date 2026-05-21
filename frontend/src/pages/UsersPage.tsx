import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Save } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useTenantContext } from '../context/TenantContext';
import { formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function UsersPage() {
  const [page, setPage] = useState(0);
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const queryClient = useQueryClient();
  const users = useQuery({
    queryKey: ['users', page, selectedTenantId],
    queryFn: () => adminApi.users.list({ page, size: 20, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const createUser = useMutation({
    mutationFn: adminApi.users.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createUser.mutate({
      tenantId: selectedTenantId,
      username: String(form.get('username') ?? ''),
      displayName: String(form.get('displayName') ?? ''),
    });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Users" description="Create users, browse tenant-scoped identities, and open detail workflows." />
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create user">
          {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant in the header before creating users.</p>}
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant"><Input value={selectedTenant?.name ?? 'No tenant selected'} disabled /></Field>
            <Field label="Username"><Input name="username" required /></Field>
            <Field label="Display name"><Input name="displayName" required /></Field>
            <Button type="submit" icon={<Save className="h-4 w-4" />} disabled={createUser.isPending || !selectedTenantId}>Create</Button>
            {createUser.isError && <ErrorState error={createUser.error} />}
          </form>
        </Card>
        <Card title="User directory">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant to load its users without copying a tenant UUID.</p>}
          {users.isLoading && <LoadingState />}
          {users.isError && <ErrorState error={users.error} />}
          {users.data && (
            <>
              <DataTable
                items={users.data.items}
                columns={[
                  { header: 'User', render: (user) => <Link className="font-medium text-brand hover:underline" to={`/users/${user.id}`}>{user.displayName}</Link> },
                  { header: 'Username', render: (user) => user.username },
                  { header: 'Email', render: (user) => user.email || '-' },
                  { header: 'Phone', render: (user) => user.phoneNumber || '-' },
                  { header: 'Status', render: (user) => <Badge>{user.accountStatus}</Badge> },
                  { header: 'Updated', render: (user) => formatDate(user.updatedAt) },
                ]}
              />
              <Pagination page={users.data} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </>
  );
}
