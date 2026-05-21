import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Save, Trash2 } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { AccountStatus, UserAttributeValueType } from '../types/api';
import { compact, formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function UserDetailPage() {
  const { userId = '' } = useParams();
  const queryClient = useQueryClient();
  const [totpSecret, setTotpSecret] = useState('');

  const user = useQuery({ queryKey: ['user', userId], queryFn: () => adminApi.users.get(userId), enabled: !!userId });
  const profile = useQuery({ queryKey: ['user-profile', userId], queryFn: () => adminApi.users.profile(userId), enabled: !!userId });
  const attributes = useQuery({ queryKey: ['user-attributes', userId], queryFn: () => adminApi.users.attributes(userId), enabled: !!userId });
  const roles = useQuery({
    queryKey: ['roles-for-user-detail', user.data?.tenantId],
    queryFn: () => adminApi.roles.list({ tenantId: user.data!.tenantId, size: 100 }),
    enabled: !!user.data?.tenantId,
  });
  const groups = useQuery({
    queryKey: ['groups-for-user-detail', user.data?.tenantId],
    queryFn: () => adminApi.groups.list({ tenantId: user.data!.tenantId, size: 100 }),
    enabled: !!user.data?.tenantId,
  });
  const auditLogs = useQuery({
    queryKey: ['audit-logs-for-user-detail', userId, user.data?.tenantId],
    queryFn: () => adminApi.auditLogs.list({ tenantId: user.data!.tenantId, resourceType: 'USER', resourceId: userId, size: 10 }),
    enabled: !!userId && !!user.data?.tenantId,
  });

  const updateUser = useMutation({
    mutationFn: (body: { displayName?: string; email?: string; phoneNumber?: string; accountStatus?: AccountStatus }) =>
      adminApi.users.update(userId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', userId] }),
  });
  const setPassword = useMutation({ mutationFn: (body: { newPassword: string; passwordResetRequired: boolean }) => adminApi.users.setPassword(userId, body) });
  const updateProfile = useMutation({
    mutationFn: (body: Record<string, string>) => adminApi.users.updateProfile(userId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-profile', userId] }),
  });
  const setAttribute = useMutation({
    mutationFn: ({ name, value, valueType }: { name: string; value: string; valueType: UserAttributeValueType }) =>
      adminApi.users.setAttribute(userId, name, { value, valueType }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-attributes', userId] }),
  });
  const deleteAttribute = useMutation({
    mutationFn: (name: string) => adminApi.users.deleteAttribute(userId, name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user-attributes', userId] }),
  });
  const assignRole = useMutation({
    mutationFn: (roleId: string) => adminApi.users.assignRole(userId, roleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', userId] }),
  });
  const removeRole = useMutation({
    mutationFn: (roleId: string) => adminApi.users.removeRole(userId, roleId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', userId] }),
  });
  const enrollTotp = useMutation({
    mutationFn: () => adminApi.mfa.enrollTotp(userId),
    onSuccess: (result) => setTotpSecret(result.secret),
  });
  const verifyTotp = useMutation({ mutationFn: (code: string) => adminApi.mfa.verifyTotp(userId, code) });
  const disableTotp = useMutation({ mutationFn: () => adminApi.mfa.disableTotp(userId) });

  function onUpdateUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    updateUser.mutate(compact({
      displayName: String(form.get('displayName') ?? ''),
      email: String(form.get('email') ?? ''),
      phoneNumber: String(form.get('phoneNumber') ?? ''),
      accountStatus: String(form.get('accountStatus') ?? '') as AccountStatus,
    }));
  }

  function onPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPassword.mutate({
      newPassword: String(form.get('newPassword') ?? ''),
      passwordResetRequired: form.get('passwordResetRequired') === 'on',
    });
    event.currentTarget.reset();
  }

  function onProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    updateProfile.mutate({
      givenName: String(form.get('givenName') ?? ''),
      familyName: String(form.get('familyName') ?? ''),
      preferredName: String(form.get('preferredName') ?? ''),
      locale: String(form.get('locale') ?? ''),
      timezone: String(form.get('timezone') ?? ''),
      avatarUrl: String(form.get('avatarUrl') ?? ''),
      jobTitle: String(form.get('jobTitle') ?? ''),
      department: String(form.get('department') ?? ''),
      organization: String(form.get('organization') ?? ''),
      employeeNumber: String(form.get('employeeNumber') ?? ''),
    });
  }

  function onAttribute(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setAttribute.mutate({
      name: String(form.get('name') ?? ''),
      value: String(form.get('value') ?? ''),
      valueType: String(form.get('valueType') ?? 'STRING') as UserAttributeValueType,
    });
    event.currentTarget.reset();
  }

  if (user.isLoading) {
    return <LoadingState label="Loading user" />;
  }
  if (user.isError) {
    return <ErrorState error={user.error} />;
  }
  if (!user.data) {
    return null;
  }

  const assignedRoles = roles.data?.items.filter((role) => user.data.roleIds.includes(role.id)) ?? [];
  const availableRoles = roles.data?.items.filter((role) => !user.data.roleIds.includes(role.id)) ?? [];
  const memberships = groups.data?.items.filter((group) => group.memberIds.includes(user.data.id)) ?? [];

  return (
    <>
      <PageHeader title={user.data.displayName} description={`User ${user.data.username} in tenant ${user.data.tenantId}`} />
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Identity">
          <form onSubmit={onUpdateUser} className="grid gap-3 md:grid-cols-2">
            <Field label="Display name"><Input name="displayName" defaultValue={user.data.displayName} /></Field>
            <Field label="Email"><Input name="email" defaultValue={user.data.email ?? ''} /></Field>
            <Field label="Phone number"><Input name="phoneNumber" defaultValue={user.data.phoneNumber ?? ''} /></Field>
            <Field label="Status">
              <Select name="accountStatus" defaultValue={user.data.accountStatus}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="PENDING">PENDING</option>
                <option value="DISABLED">DISABLED</option>
                <option value="LOCKED">LOCKED</option>
              </Select>
            </Field>
            <div className="md:col-span-2"><Button type="submit" icon={<Save className="h-4 w-4" />}>Save identity</Button></div>
            {updateUser.isError && <div className="md:col-span-2"><ErrorState error={updateUser.error} /></div>}
          </form>
        </Card>
        <Card title="Password">
          <form onSubmit={onPassword} className="grid gap-3">
            <Field label="New password"><Input name="newPassword" type="password" autoComplete="new-password" required /></Field>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input name="passwordResetRequired" type="checkbox" className="h-4 w-4" />
              Require password reset
            </label>
            <Button type="submit">Set password</Button>
            {setPassword.isError && <ErrorState error={setPassword.error} />}
          </form>
        </Card>
        <Card title="Profile">
          {profile.isLoading && <LoadingState label="Loading profile" />}
          {profile.isError && <ErrorState error={profile.error} />}
          {profile.data && (
            <form onSubmit={onProfile} className="grid gap-3 md:grid-cols-2">
              {['givenName', 'familyName', 'preferredName', 'locale', 'timezone', 'avatarUrl', 'jobTitle', 'department', 'organization', 'employeeNumber'].map((field) => (
                <Field key={field} label={field}>
                  <Input name={field} defaultValue={(profile.data as unknown as Record<string, string | undefined>)[field] ?? ''} />
                </Field>
              ))}
              <div className="md:col-span-2"><Button type="submit">Save profile</Button></div>
              {updateProfile.isError && <div className="md:col-span-2"><ErrorState error={updateProfile.error} /></div>}
            </form>
          )}
        </Card>
        <Card title="Roles">
          <div className="mb-3 flex flex-wrap gap-2">
            {assignedRoles.length === 0 && user.data.roleIds.length === 0 && <span className="text-sm text-slate-500">No roles assigned.</span>}
            {assignedRoles.map((role) => (
              <button key={role.id} onClick={() => removeRole.mutate(role.id)} className="rounded-full border border-line bg-slate-50 px-2 py-1 text-xs text-slate-700">
                {role.name} ×
              </button>
            ))}
            {user.data.roleIds.filter((roleId) => !assignedRoles.some((role) => role.id === roleId)).map((roleId) => (
              <button key={roleId} onClick={() => removeRole.mutate(roleId)} className="rounded-full border border-line bg-slate-50 px-2 py-1 text-xs text-slate-700">
                {roleId} ×
              </button>
            ))}
          </div>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              assignRole.mutate(String(new FormData(event.currentTarget).get('roleId') ?? ''));
            }}
            className="flex gap-2"
          >
            <Select name="roleId" className="flex-1">
              <option value="">Select role</option>
              {availableRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
            </Select>
            <Button type="submit">Assign</Button>
          </form>
          {(assignRole.isError || removeRole.isError) && <div className="mt-3"><ErrorState error={assignRole.error ?? removeRole.error} /></div>}
        </Card>
        <Card title="Group memberships">
          {groups.isLoading && <LoadingState label="Loading groups" />}
          {groups.isError && <ErrorState error={groups.error} />}
          {!groups.isLoading && memberships.length === 0 && <p className="text-sm text-slate-500">This user is not a member of any groups in this tenant.</p>}
          {memberships.length > 0 && (
            <DataTable
              items={memberships}
              columns={[
                { header: 'Group', render: (group) => <span className="font-medium">{group.displayName || group.name}</span> },
                { header: 'Description', render: (group) => group.description || '-' },
              ]}
            />
          )}
        </Card>
        <Card title="Attributes">
          <form onSubmit={onAttribute} className="mb-4 grid gap-2 md:grid-cols-[1fr_1fr_140px_auto]">
            <Input name="name" placeholder="costCenter" required />
            <Input name="value" placeholder="PLATFORM" required />
            <Select name="valueType" defaultValue="STRING">
              <option value="STRING">STRING</option>
              <option value="NUMBER">NUMBER</option>
              <option value="BOOLEAN">BOOLEAN</option>
              <option value="JSON">JSON</option>
            </Select>
            <Button type="submit">Set</Button>
          </form>
          {setAttribute.isError && <div className="mb-3"><ErrorState error={setAttribute.error} /></div>}
          {attributes.isLoading && <LoadingState label="Loading attributes" />}
          {attributes.data && (
            <DataTable
              items={attributes.data}
              columns={[
                { header: 'Name', render: (attribute) => attribute.name },
                { header: 'Value', render: (attribute) => attribute.value },
                { header: 'Type', render: (attribute) => <Badge>{attribute.valueType}</Badge> },
                { header: 'Remove', render: (attribute) => <Button variant="danger" icon={<Trash2 className="h-4 w-4" />} onClick={() => deleteAttribute.mutate(attribute.name)}>Delete</Button> },
              ]}
            />
          )}
        </Card>
        <Card title="TOTP MFA">
          <div className="grid gap-3">
            <Button onClick={() => enrollTotp.mutate()}>Enroll TOTP</Button>
            {totpSecret && <SecretNotice title="TOTP setup secret" secret={totpSecret} />}
            <form
              onSubmit={(event) => {
                event.preventDefault();
                verifyTotp.mutate(String(new FormData(event.currentTarget).get('code') ?? ''));
              }}
              className="flex gap-2"
            >
              <Input name="code" placeholder="123456" />
              <Button type="submit" variant="secondary">Verify</Button>
            </form>
            {verifyTotp.data && <Badge>{verifyTotp.data.verified ? 'VERIFIED' : 'INVALID'}</Badge>}
            <Button variant="danger" onClick={() => disableTotp.mutate()}>Disable TOTP</Button>
            {(enrollTotp.isError || verifyTotp.isError || disableTotp.isError) && (
              <ErrorState error={enrollTotp.error ?? verifyTotp.error ?? disableTotp.error} />
            )}
          </div>
        </Card>
        <Card
          title="Related audit events"
          action={<Link className="text-sm font-medium text-brand hover:underline" to={`/audit-logs?resourceType=USER&resourceId=${user.data.id}`}>Open audit logs</Link>}
        >
          {auditLogs.isLoading && <LoadingState label="Loading audit events" />}
          {auditLogs.isError && <ErrorState error={auditLogs.error} />}
          {auditLogs.data && (
            <DataTable
              items={auditLogs.data.items}
              columns={[
                { header: 'When', render: (log) => formatDate(log.createdAt) },
                { header: 'Action', render: (log) => <span className="font-medium">{log.action}</span> },
                { header: 'Result', render: (log) => <Badge>{log.result}</Badge> },
              ]}
            />
          )}
        </Card>
      </div>
    </>
  );
}
