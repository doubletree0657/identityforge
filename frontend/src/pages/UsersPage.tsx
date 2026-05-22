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
import { TenantRequired } from '../components/TenantRequired';
import { useTenantContext } from '../context/TenantContext';
import { AccountStatus } from '../types/api';
import { formatDate } from '../utils/format';
import { roleNames, userGroups } from '../utils/relationships';
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
  const roles = useQuery({
    queryKey: ['roles-for-users', selectedTenantId],
    queryFn: () => adminApi.roles.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const groups = useQuery({
    queryKey: ['groups-for-users', selectedTenantId],
    queryFn: () => adminApi.groups.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const createUser = useMutation({
    mutationFn: async (body: {
      tenantId: string;
      username: string;
      displayName: string;
      email?: string;
      phoneNumber?: string;
      accountStatus?: AccountStatus;
      initialPassword?: string;
      roleIds: string[];
      groupIds: string[];
    }) => {
      let user = await adminApi.users.create({
        tenantId: body.tenantId,
        username: body.username,
        displayName: body.displayName,
      });
      user = await adminApi.users.update(user.id, {
        email: body.email || undefined,
        phoneNumber: body.phoneNumber || undefined,
        accountStatus: body.accountStatus,
      });
      if (body.initialPassword) {
        user = await adminApi.users.setPassword(user.id, { newPassword: body.initialPassword });
      }
      await Promise.all(body.roleIds.map((roleId) => adminApi.users.assignRole(user.id, roleId)));
      await Promise.all(body.groupIds.map((groupId) => adminApi.groups.addMember(groupId, user.id)));
      return user;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createUser.mutate({
      tenantId: selectedTenantId,
      username: String(form.get('username') ?? ''),
      displayName: String(form.get('displayName') ?? ''),
      email: String(form.get('email') ?? ''),
      phoneNumber: String(form.get('phoneNumber') ?? ''),
      accountStatus: String(form.get('accountStatus') ?? 'PENDING') as AccountStatus,
      initialPassword: String(form.get('initialPassword') ?? ''),
      roleIds: form.getAll('roleIds').map(String).filter(Boolean),
      groupIds: form.getAll('groupIds').map(String).filter(Boolean),
    });
    event.currentTarget.reset();
  }

  return (
    <>
      <PageHeader title="Users" description="Create users, browse tenant-scoped identities, and open detail workflows." />
      {!selectedTenantId && <TenantRequired label="Users belong to a tenant. Select a tenant before creating or browsing identities." />}
      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <Card title="Create user">
          {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant in the header before creating users.</p>}
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant"><Input value={selectedTenant?.name ?? 'No tenant selected'} disabled /></Field>
            <Field label="Username"><Input name="username" required /></Field>
            <Field label="Display name"><Input name="displayName" required /></Field>
            <Field label="Email"><Input name="email" type="email" /></Field>
            <Field label="Phone number"><Input name="phoneNumber" /></Field>
            <Field label="Account status">
              <select name="accountStatus" defaultValue="PENDING" className="min-h-10 rounded-md border border-line bg-white px-3 text-sm">
                <option value="PENDING">PENDING</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
                <option value="LOCKED">LOCKED</option>
              </select>
            </Field>
            <Field label="Initial password" hint="Optional. Stored only through the password management API.">
              <Input name="initialPassword" type="password" autoComplete="new-password" />
            </Field>
            <Field label="Roles" hint="Optional direct role assignment. Groups are organizational containers, not required for access.">
              <select name="roleIds" multiple className="min-h-24 rounded-md border border-line bg-white px-3 py-2 text-sm">
                {roles.data?.items.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
              </select>
            </Field>
            <Field label="Groups" hint="Optional. A user can belong to zero, one, or many groups.">
              <select name="groupIds" multiple className="min-h-24 rounded-md border border-line bg-white px-3 py-2 text-sm">
                {groups.data?.items.map((group) => <option key={group.id} value={group.id}>{group.displayName || group.name}</option>)}
              </select>
            </Field>
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
                  { header: 'Roles', render: (user) => roleNames(user, roles.data?.items) || <span className="text-slate-500">No roles</span> },
                  { header: 'Groups', render: (user) => {
                    const memberships = userGroups(user.id, groups.data?.items);
                    return memberships.length ? memberships.map((group) => group.displayName || group.name).join(', ') : <span className="text-slate-500">No groups</span>;
                  } },
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
