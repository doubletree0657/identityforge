import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
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
  const addMember = useMutation({
    mutationFn: ({ groupId, userId }: { groupId: string; userId: string }) => adminApi.groups.addMember(groupId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
  });
  const removeMember = useMutation({
    mutationFn: ({ groupId, userId }: { groupId: string; userId: string }) => adminApi.groups.removeMember(groupId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
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
                  { header: 'Name', render: (group) => <span className="font-medium">{group.displayName || group.name}</span> },
                  { header: 'Tenant', render: (group) => <span className="font-mono text-xs">{group.tenantId}</span> },
                  {
                    header: 'Members',
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
                  {
                    header: 'Membership',
                    render: (group) => (
                      <form
                        className="grid min-w-[260px] gap-2"
                        onSubmit={(event) => {
                          event.preventDefault();
                          addMember.mutate({ groupId: group.id, userId: String(new FormData(event.currentTarget).get('userId') ?? '') });
                        }}
                      >
                        <Select name="userId">
                          <option value="">Select tenant user</option>
                          {users.data?.items.map((user) => (
                            <option key={user.id} value={user.id}>
                              {user.displayName} ({user.username})
                            </option>
                          ))}
                        </Select>
                        <div className="flex gap-2">
                          <Button type="submit" variant="secondary">Add</Button>
                          <Button
                            type="button"
                            variant="danger"
                            onClick={(event) => {
                              const form = event.currentTarget.closest('form');
                              const userId = String(new FormData(form!).get('userId') ?? '');
                              removeMember.mutate({ groupId: group.id, userId });
                            }}
                          >
                            Remove
                          </Button>
                        </div>
                      </form>
                    ),
                  },
                ]}
              />
              <Pagination page={groups.data} onPageChange={setPage} />
            </>
          )}
          {(addMember.isError || removeMember.isError) && <div className="mt-3"><ErrorState error={addMember.error ?? removeMember.error} /></div>}
        </Card>
      </div>
    </>
  );
}
