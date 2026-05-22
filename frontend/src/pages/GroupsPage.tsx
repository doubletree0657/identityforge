import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { TenantRequired } from '../components/TenantRequired';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function GroupsPage() {
  const [page, setPage] = useState(0);
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const queryClient = useQueryClient();
  const groups = useQuery({
    queryKey: ['groups', page, selectedTenantId],
    queryFn: () => adminApi.groups.list({ page, size: 20, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const users = useQuery({
    queryKey: ['users-for-groups', selectedTenantId],
    queryFn: () => adminApi.users.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const createGroup = useMutation({
    mutationFn: adminApi.groups.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups'] });
      queryClient.invalidateQueries({ queryKey: ['users-for-groups'] });
    },
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createGroup.mutate({
      tenantId: selectedTenantId,
      name: String(form.get('name') ?? ''),
      displayName: String(form.get('displayName') ?? ''),
      description: String(form.get('description') ?? ''),
    });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Groups" description="Manage tenant-scoped identity collections and memberships." />
      {!selectedTenantId && <TenantRequired label="Groups are optional tenant containers. Select a tenant before creating or managing memberships." />}
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create group">
          {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant in the header before creating groups.</p>}
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant"><Input value={selectedTenant?.name ?? 'No tenant selected'} disabled /></Field>
            <Field label="Name"><Input name="name" required /></Field>
            <Field label="Display name"><Input name="displayName" /></Field>
            <Field label="Description"><Input name="description" /></Field>
            <Button type="submit" disabled={!selectedTenantId}>Create</Button>
            {createGroup.isError && <ErrorState error={createGroup.error} />}
          </form>
        </Card>
        <Card title="Groups">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant to load groups and tenant users for membership changes.</p>}
          {groups.isLoading && <LoadingState />}
          {groups.isError && <ErrorState error={groups.error} />}
          {groups.data && (
            <>
              <DataTable
                items={groups.data.items}
                columns={[
                  { header: 'Group', render: (group) => <Link className="font-medium text-brand hover:underline" to={`/groups/${group.id}`}>{group.displayName || group.name}</Link> },
                  { header: 'Name', render: (group) => group.name },
                  { header: 'Description', render: (group) => group.description || '-' },
                  { header: 'Members', render: (group) => `${group.memberIds.length} member${group.memberIds.length === 1 ? '' : 's'}` },
                  {
                    header: 'Preview',
                    render: (group) => (
                      <div className="flex max-w-[360px] flex-wrap gap-1">
                        {group.memberIds.length === 0 && <span className="text-sm text-slate-500">No members</span>}
                        {group.memberIds.map((memberId) => {
                          const member = users.data?.items.find((user) => user.id === memberId);
                          return (
                            <span key={memberId} className="rounded-full bg-slate-100 px-2 py-1 text-xs text-slate-700">
                              {member?.displayName ?? memberId}
                            </span>
                          );
                        })}
                      </div>
                    ),
                  },
                  { header: 'Action', render: (group) => <Link className="text-sm font-medium text-brand hover:underline" to={`/groups/${group.id}`}>Manage members</Link> },
                ]}
              />
              <Pagination page={groups.data} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </>
  );
}
