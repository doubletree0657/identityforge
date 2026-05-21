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

export function GroupsPage() {
  const [page, setPage] = useState(0);
  const [tenantId, setTenantId] = useState('');
  const queryClient = useQueryClient();
  const groups = useQuery({ queryKey: ['groups', page, tenantId], queryFn: () => adminApi.groups.list({ page, size: 20, tenantId }) });
  const createGroup = useMutation({
    mutationFn: adminApi.groups.create,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['groups'] }),
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
      tenantId: String(form.get('tenantId') ?? ''),
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
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant ID"><Input name="tenantId" required /></Field>
            <Field label="Name"><Input name="name" required /></Field>
            <Field label="Display name"><Input name="displayName" /></Field>
            <Field label="Description"><Input name="description" /></Field>
            <Button type="submit">Create</Button>
            {createGroup.isError && <ErrorState error={createGroup.error} />}
          </form>
        </Card>
        <Card title="Groups">
          <div className="mb-4">
            <Field label="Tenant filter"><Input value={tenantId} onChange={(event) => { setTenantId(event.target.value); setPage(0); }} /></Field>
          </div>
          {groups.isLoading && <LoadingState />}
          {groups.isError && <ErrorState error={groups.error} />}
          {groups.data && (
            <>
              <DataTable
                items={groups.data.items}
                columns={[
                  { header: 'Name', render: (group) => <span className="font-medium">{group.displayName || group.name}</span> },
                  { header: 'Tenant', render: (group) => <span className="font-mono text-xs">{group.tenantId}</span> },
                  { header: 'Members', render: (group) => group.memberIds.length },
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
                        <Input name="userId" placeholder="User UUID" />
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
