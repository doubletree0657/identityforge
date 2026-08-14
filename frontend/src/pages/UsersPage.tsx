import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Save } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { Notice } from '../components/Notice';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { TenantRequired } from '../components/TenantRequired';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { AccountStatus } from '../types/api';
import { formatDate } from '../utils/format';
import { roleNames, userGroups } from '../utils/relationships';
import { PageHeader } from './PageHeader';

export function UsersPage() {
  const [page, setPage] = useState(0);
  const [creationStage, setCreationStage] = useState<'identity' | 'profile' | 'password' | 'relationships'>('identity');
  const { hasPermission } = useAuth();
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
      setCreationStage('identity');
      let user = await adminApi.users.create({
        tenantId: body.tenantId,
        username: body.username,
        displayName: body.displayName,
      });
      setCreationStage('profile');
      user = await adminApi.users.update(user.id, {
        email: body.email || undefined,
        phoneNumber: body.phoneNumber || undefined,
        accountStatus: body.accountStatus,
      });
      if (body.initialPassword) {
        setCreationStage('password');
        user = await adminApi.users.setPassword(user.id, { newPassword: body.initialPassword });
      }
      setCreationStage('relationships');
      await Promise.all(body.roleIds.map((roleId) => adminApi.users.assignRole(user.id, roleId)));
      await Promise.all(body.groupIds.map((groupId) => adminApi.groups.addMember(groupId, user.id)));
      return user;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
    onError: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const formElement = event.currentTarget;
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
    }, { onSuccess: () => formElement.reset() });
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
            <Field label="Username" required hint="Tenant-local sign-in name; stored in normalized form."><Input name="username" required maxLength={120} /></Field>
            <Field label="Display name" required><Input name="displayName" required maxLength={160} /></Field>
            <Field label="Email"><Input name="email" type="email" maxLength={254} /></Field>
            <Field label="Phone number"><Input name="phoneNumber" type="tel" maxLength={40} /></Field>
            <Field label="Account status">
              <Select name="accountStatus" defaultValue="PENDING">
                <option value="PENDING">PENDING</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
                <option value="LOCKED">LOCKED</option>
              </Select>
            </Field>
            <Field label="Initial password" hint="Optional. Stored only through the password management API.">
              <Input name="initialPassword" type="password" autoComplete="new-password" minLength={8} />
            </Field>
            <Field label="Roles" hint="Optional direct role assignment. Groups are organizational containers, not required for access.">
              <Select name="roleIds" multiple className="min-h-24 py-2">
                {roles.data?.items.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
              </Select>
            </Field>
            <Field label="Groups" hint="Optional. A user can belong to zero, one, or many groups.">
              <Select name="groupIds" multiple className="min-h-24 py-2">
                {groups.data?.items.map((group) => <option key={group.id} value={group.id}>{group.displayName || group.name}</option>)}
              </Select>
            </Field>
            <Button type="submit" icon={<Save className="h-4 w-4" />} isLoading={createUser.isPending} loadingLabel={creationStage === 'identity' ? 'Creating identity…' : creationStage === 'profile' ? 'Saving contact details…' : creationStage === 'password' ? 'Setting credential…' : 'Assigning access…'} disabled={!selectedTenantId || !hasPermission('iam.users.write')}>Create user</Button>
            {createUser.isError && <><ErrorState error={createUser.error} title={`Could not complete the ${creationStage} step`} />{creationStage !== 'identity' && <Notice title="The core identity may already exist" tone="warning">Review the refreshed directory before retrying. Open the user detail page to finish password or relationship changes without creating a duplicate.</Notice>}</>}
            {createUser.isSuccess && <Notice title="User created" tone="success">The identity and selected relationships are now visible in the directory.</Notice>}
          </form>
        </Card>
        <Card title="User directory">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant to load its users without copying a tenant UUID.</p>}
          {users.isLoading && <LoadingState />}
          {users.isError && <ErrorState error={users.error} onRetry={() => void users.refetch()} />}
          {users.data && (
            <>
              <DataTable
                items={users.data.items}
                getKey={(user) => user.id}
                emptyTitle="No users in this tenant"
                emptyDetail="Create the first identity to demonstrate authentication, RBAC, MFA, or SCIM provisioning."
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
