import { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Save } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { displayUser } from '../utils/relationships';
import { PageHeader } from './PageHeader';

export function GroupDetailPage() {
  const { groupId = '' } = useParams();
  const queryClient = useQueryClient();
  const group = useQuery({ queryKey: ['group', groupId], queryFn: () => adminApi.groups.get(groupId), enabled: !!groupId });
  const users = useQuery({
    queryKey: ['users-for-group-detail', group.data?.tenantId],
    queryFn: () => adminApi.users.list({ tenantId: group.data!.tenantId, size: 100 }),
    enabled: !!group.data?.tenantId,
  });
  const roles = useQuery({
    queryKey: ['roles-for-group-detail', group.data?.tenantId],
    queryFn: () => adminApi.roles.list({ tenantId: group.data!.tenantId, size: 100 }),
    enabled: !!group.data?.tenantId,
  });
  const updateGroup = useMutation({
    mutationFn: (body: { name?: string; displayName?: string; description?: string }) => adminApi.groups.update(groupId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const addMember = useMutation({
    mutationFn: (userId: string) => adminApi.groups.addMember(groupId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const removeMember = useMutation({
    mutationFn: (userId: string) => adminApi.groups.removeMember(groupId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const assignRole = useMutation({
    mutationFn: (roleId: string) => adminApi.groups.assignRole(groupId, roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const removeRole = useMutation({
    mutationFn: (roleId: string) => adminApi.groups.removeRole(groupId, roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });

  function update(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    updateGroup.mutate({
      name: String(form.get('name') ?? ''),
      displayName: String(form.get('displayName') ?? ''),
      description: String(form.get('description') ?? ''),
    });
  }

  if (group.isLoading) {
    return <LoadingState label="Loading group" />;
  }
  if (group.isError) {
    return <ErrorState error={group.error} />;
  }
  if (!group.data) {
    return null;
  }

  const members = users.data?.items.filter((user) => group.data.memberIds.includes(user.id)) ?? [];
  const availableUsers = users.data?.items.filter((user) => !group.data.memberIds.includes(user.id)) ?? [];
  const assignedRoles = roles.data?.items.filter((role) => group.data.roleIds.includes(role.id)) ?? [];
  const availableRoles = roles.data?.items.filter((role) => !group.data.roleIds.includes(role.id)) ?? [];

  return (
    <>
      <PageHeader title={group.data.displayName || group.data.name} description="Groups are tenant-scoped containers. Users inherit effective roles and permissions from assigned group roles." />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <Card title="Group metadata">
          <form onSubmit={update} className="grid gap-3">
            <Field label="Name"><Input name="name" defaultValue={group.data.name} required /></Field>
            <Field label="Display name"><Input name="displayName" defaultValue={group.data.displayName} /></Field>
            <Field label="Description"><Input name="description" defaultValue={group.data.description ?? ''} /></Field>
            <Button type="submit" icon={<Save className="h-4 w-4" />}>Save group</Button>
            {updateGroup.isError && <ErrorState error={updateGroup.error} />}
          </form>
        </Card>
        <Card title="Assigned roles">
          <form
            onSubmit={(event) => {
              event.preventDefault();
              assignRole.mutate(String(new FormData(event.currentTarget).get('roleId') ?? ''));
            }}
            className="mb-4 flex gap-2"
          >
            <Select name="roleId" className="flex-1">
              <option value="">Select tenant role</option>
              {availableRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
            </Select>
            <Button type="submit" variant="secondary">Assign role</Button>
          </form>
          {roles.isLoading && <LoadingState label="Loading roles" />}
          {roles.isError && <ErrorState error={roles.error} />}
          <DataTable
            items={assignedRoles}
            emptyTitle="No group roles"
            columns={[
              { header: 'Role', render: (role) => <span className="font-medium">{role.name}</span> },
              { header: 'Permissions', render: (role) => role.permissionIds.length },
              { header: 'Action', render: (role) => <Button variant="danger" onClick={() => removeRole.mutate(role.id)}>Remove</Button> },
            ]}
          />
          {(assignRole.isError || removeRole.isError) && <div className="mt-3"><ErrorState error={assignRole.error ?? removeRole.error} /></div>}
        </Card>
        <Card title="Members">
          <form
            onSubmit={(event) => {
              event.preventDefault();
              addMember.mutate(String(new FormData(event.currentTarget).get('userId') ?? ''));
            }}
            className="mb-4 flex gap-2"
          >
            <Select name="userId" className="flex-1">
              <option value="">Select tenant user</option>
              {availableUsers.map((user) => <option key={user.id} value={user.id}>{displayUser(user)}</option>)}
            </Select>
            <Button type="submit" variant="secondary">Add member</Button>
          </form>
          {users.isLoading && <LoadingState label="Loading members" />}
          {users.isError && <ErrorState error={users.error} />}
          <DataTable
            items={members}
            emptyTitle="No group members"
            columns={[
              { header: 'User', render: (user) => <Link className="font-medium text-brand hover:underline" to={`/users/${user.id}`}>{user.displayName}</Link> },
              { header: 'Username', render: (user) => user.username },
              { header: 'Status', render: (user) => user.accountStatus },
              { header: 'Action', render: (user) => <Button variant="danger" onClick={() => removeMember.mutate(user.id)}>Remove</Button> },
            ]}
          />
          {(addMember.isError || removeMember.isError) && <div className="mt-3"><ErrorState error={addMember.error ?? removeMember.error} /></div>}
        </Card>
      </div>
    </>
  );
}
