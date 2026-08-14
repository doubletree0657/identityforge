import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Save, Trash2 } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Field, Input, Select } from '../components/Form';
import { Notice } from '../components/Notice';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useAuth } from '../context/AuthContext';
import { AccountStatus, UserAttributeValueType } from '../types/api';
import { compact, formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function UserDetailPage() {
  const { userId = '' } = useParams();
  const [confirmMfaDisable, setConfirmMfaDisable] = useState(false);
  const queryClient = useQueryClient();
  const { user: currentUser, hasPermission } = useAuth();

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
  const mfaStatus = useQuery({
    queryKey: ['mfa-status', userId],
    queryFn: () => adminApi.mfa.status(userId),
    enabled: !!userId && hasPermission('iam.mfa.manage'),
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
  const addToGroup = useMutation({
    mutationFn: (groupId: string) => adminApi.groups.addMember(groupId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups-for-user-detail'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const removeFromGroup = useMutation({
    mutationFn: (groupId: string) => adminApi.groups.removeMember(groupId, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['groups-for-user-detail'] });
      queryClient.invalidateQueries({ queryKey: ['groups'] });
    },
  });
  const disableTotp = useMutation({
    mutationFn: () => adminApi.mfa.disableTotp(userId),
    onSuccess: () => {
      setConfirmMfaDisable(false);
      queryClient.invalidateQueries({ queryKey: ['mfa-status', userId] });
    },
  });

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
    const formElement = event.currentTarget;
    setPassword.mutate({
      newPassword: String(form.get('newPassword') ?? ''),
      passwordResetRequired: form.get('passwordResetRequired') === 'on',
    }, { onSuccess: () => formElement.reset() });
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
    const formElement = event.currentTarget;
    setAttribute.mutate({
      name: String(form.get('name') ?? ''),
      value: String(form.get('value') ?? ''),
      valueType: String(form.get('valueType') ?? 'STRING') as UserAttributeValueType,
    }, { onSuccess: () => formElement.reset() });
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
  const availableGroups = groups.data?.items.filter((group) => !group.memberIds.includes(user.data.id)) ?? [];

  return (
    <>
      <PageHeader
        title={user.data.displayName}
        description={`${user.data.username} · Users belong to one tenant, may join multiple groups, and can receive roles directly or through group inheritance.`}
        action={
          <div className="flex flex-wrap gap-2">
            <Button variant="ghost" onClick={() => window.history.back()}>← Back</Button>
            {user.data.accountStatus !== 'ACTIVE' && <Button variant="secondary" onClick={() => updateUser.mutate({ accountStatus: 'ACTIVE' })}>Activate / unlock</Button>}
            {user.data.accountStatus !== 'DISABLED' && <Button variant="secondary" onClick={() => updateUser.mutate({ accountStatus: 'DISABLED' })}>Disable</Button>}
            {user.data.accountStatus !== 'LOCKED' && <Button variant="secondary" onClick={() => updateUser.mutate({ accountStatus: 'LOCKED' })}>Lock</Button>}
          </div>
        }
      />
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="Identity">
          <form onSubmit={onUpdateUser} className="grid gap-3 md:grid-cols-2">
            <Field label="Display name" required><Input name="displayName" defaultValue={user.data.displayName} required maxLength={160} /></Field>
            <Field label="Email"><Input name="email" type="email" defaultValue={user.data.email ?? ''} maxLength={254} /></Field>
            <Field label="Phone number"><Input name="phoneNumber" type="tel" defaultValue={user.data.phoneNumber ?? ''} maxLength={40} /></Field>
            <Field label="Status">
              <Select name="accountStatus" defaultValue={user.data.accountStatus}>
                <option value="ACTIVE">ACTIVE</option>
                <option value="PENDING">PENDING</option>
                <option value="DISABLED">DISABLED</option>
                <option value="LOCKED">LOCKED</option>
              </Select>
            </Field>
            <p className="text-sm text-slate-600 md:col-span-2">
              PENDING users are not fully activated, ACTIVE users can authenticate, DISABLED users cannot authenticate, and LOCKED users are blocked until an admin unlocks them.
            </p>
            <div className="md:col-span-2"><Button type="submit" icon={<Save className="h-4 w-4" />}>Save identity</Button></div>
            {updateUser.isError && <div className="md:col-span-2"><ErrorState error={updateUser.error} /></div>}
            {updateUser.isSuccess && <div className="md:col-span-2"><Notice title="Identity updated" tone="success" /></div>}
          </form>
        </Card>
        <Card title="Password">
          <form onSubmit={onPassword} className="grid gap-3">
            <Field label="New password" required hint="Minimum 8 characters. The value is sent only to the credential endpoint."><Input name="newPassword" type="password" autoComplete="new-password" minLength={8} required /></Field>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input name="passwordResetRequired" type="checkbox" className="h-4 w-4" />
              Require password reset
            </label>
            <Button type="submit" isLoading={setPassword.isPending} loadingLabel="Updating credential…">Set password</Button>
            {setPassword.isError && <ErrorState error={setPassword.error} />}
            {setPassword.isSuccess && <Notice title="Password updated" tone="success">Existing access tokens were invalidated by the security-state change.</Notice>}
          </form>
        </Card>
        <Card title="Profile">
          {profile.isLoading && <LoadingState label="Loading profile" />}
          {profile.isError && <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />}
          {profile.data && (
            <form onSubmit={onProfile} className="grid gap-3 md:grid-cols-2">
              {Object.entries(profileFields).map(([field, label]) => (
                <Field key={field} label={label}>
                  <Input name={field} defaultValue={(profile.data as unknown as Record<string, string | undefined>)[field] ?? ''} />
                </Field>
              ))}
              <div className="md:col-span-2"><Button type="submit">Save profile</Button></div>
              {updateProfile.isError && <div className="md:col-span-2"><ErrorState error={updateProfile.error} /></div>}
            </form>
          )}
        </Card>
        <Card title="Direct roles">
          {roles.isLoading && <LoadingState label="Loading tenant roles" />}
          {roles.isError && <ErrorState error={roles.error} onRetry={() => void roles.refetch()} />}
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
            <Select name="roleId" className="flex-1" required>
              <option value="">Select role</option>
              {availableRoles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}
            </Select>
            <Button type="submit">Assign</Button>
          </form>
          {(assignRole.isError || removeRole.isError) && <div className="mt-3"><ErrorState error={assignRole.error ?? removeRole.error} /></div>}
        </Card>
        <Card title="Effective authorization">
          <div className="grid gap-4">
            <AuthList title="Group-derived roles" values={user.data.groupRoles} />
            <AuthList title="Effective roles" values={user.data.effectiveRoles} />
            <AuthList title="Direct permissions" values={user.data.directPermissions} />
            <AuthList title="Group-derived permissions" values={user.data.groupPermissions} />
            <AuthList title="Effective permissions" values={user.data.effectivePermissions} />
          </div>
        </Card>
        <Card title="Group memberships">
          {groups.isLoading && <LoadingState label="Loading groups" />}
          {groups.isError && <ErrorState error={groups.error} onRetry={() => void groups.refetch()} />}
          <p className="mb-3 text-sm text-slate-600">Groups are optional organizational containers. This user can belong to multiple groups or none.</p>
          {!groups.isLoading && memberships.length === 0 && <p className="text-sm text-slate-500">This user is not a member of any groups in this tenant.</p>}
          {memberships.length > 0 && (
            <DataTable
              items={memberships}
              getKey={(group) => group.id}
              emptyTitle="No group memberships"
              emptyDetail="Group membership is optional; direct roles remain independent."
              columns={[
                { header: 'Group', render: (group) => <span className="font-medium">{group.displayName || group.name}</span> },
                { header: 'Description', render: (group) => group.description || '-' },
                { header: 'Action', render: (group) => <Button variant="danger" onClick={() => removeFromGroup.mutate(group.id)}>Remove</Button> },
              ]}
            />
          )}
          <form
            onSubmit={(event) => {
              event.preventDefault();
              addToGroup.mutate(String(new FormData(event.currentTarget).get('groupId') ?? ''));
            }}
            className="mt-4 flex gap-2"
          >
            <Select name="groupId" className="flex-1" required>
              <option value="">Select group</option>
              {availableGroups.map((group) => <option key={group.id} value={group.id}>{group.displayName || group.name}</option>)}
            </Select>
            <Button type="submit" variant="secondary">Add to group</Button>
          </form>
          {(addToGroup.isError || removeFromGroup.isError) && <div className="mt-3"><ErrorState error={addToGroup.error ?? removeFromGroup.error} /></div>}
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
          {attributes.isError && <ErrorState error={attributes.error} onRetry={() => void attributes.refetch()} />}
          {attributes.data && (
            <DataTable
              items={attributes.data}
              getKey={(attribute) => attribute.id}
              emptyTitle="No custom attributes"
              emptyDetail="Add a typed attribute only when the core profile does not model the value."
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
            {mfaStatus.isLoading && <LoadingState label="Loading MFA status" />}
            {mfaStatus.data && (
              <>
                <div className="flex flex-wrap gap-2">
                  <Badge>{mfaStatus.data.enrollmentPending ? 'SETUP PENDING' : mfaStatus.data.totpVerified ? 'MFA ACTIVE' : 'NOT ENROLLED'}</Badge>
                  {mfaStatus.data.totpVerified && <Badge>{`${mfaStatus.data.recoveryCodesRemaining} RECOVERY CODES LEFT`}</Badge>}
                </div>
                <p className="text-sm text-slate-600">
                  Setup secrets and recovery codes are self-service only. Administrators can review status and disable a lost or compromised factor without seeing credential material.
                </p>
                {currentUser?.userId === userId ? (
                  <Link className="text-sm font-medium text-brand hover:underline" to="/mfa">Manage your MFA setup</Link>
                ) : (
                  <Button
                    variant="danger"
                    disabled={!mfaStatus.data.totpEnrolled || disableTotp.isPending}
                    onClick={() => setConfirmMfaDisable(true)}
                  >Disable MFA</Button>
                )}
              </>
            )}
            {(mfaStatus.isError || disableTotp.isError) && <ErrorState error={mfaStatus.error ?? disableTotp.error} />}
          </div>
        </Card>
        <Card
          title="Related audit events"
          action={<Link className="text-sm font-medium text-brand hover:underline" to={`/audit-logs?resourceType=USER&resourceId=${user.data.id}`}>Open audit logs</Link>}
        >
          {auditLogs.isLoading && <LoadingState label="Loading audit events" />}
          {auditLogs.isError && <ErrorState error={auditLogs.error} onRetry={() => void auditLogs.refetch()} />}
          {auditLogs.data && (
            <DataTable
              items={auditLogs.data.items}
              getKey={(log) => log.id}
              emptyTitle="No related audit events"
              emptyDetail="User-specific administration and security events will appear here."
              columns={[
                { header: 'When', render: (log) => formatDate(log.createdAt) },
                { header: 'Action', render: (log) => <span className="font-medium">{log.action}</span> },
                { header: 'Result', render: (log) => <Badge>{log.result}</Badge> },
              ]}
            />
          )}
        </Card>
      </div>
      <ConfirmDialog
        open={confirmMfaDisable}
        title={`Disable MFA for ${user.data.displayName}?`}
        detail="The authenticator credential and every recovery code will be revoked. Existing user access tokens will also be invalidated. Credential material remains hidden from administrators."
        confirmLabel="Disable user MFA"
        isPending={disableTotp.isPending}
        onCancel={() => setConfirmMfaDisable(false)}
        onConfirm={() => disableTotp.mutate()}
      />
    </>
  );
}

const profileFields: Record<string, string> = {
  givenName: 'Given name',
  familyName: 'Family name',
  preferredName: 'Preferred name',
  locale: 'Locale',
  timezone: 'Time zone',
  avatarUrl: 'Avatar URL',
  jobTitle: 'Job title',
  department: 'Department',
  organization: 'Organization',
  employeeNumber: 'Employee number',
};

function AuthList({ title, values }: { title: string; values: string[] }) {
  return (
    <div>
      <div className="mb-2 text-xs font-semibold uppercase text-slate-500">{title}</div>
      <div className="flex flex-wrap gap-2">
        {values.length === 0 && <span className="text-sm text-slate-500">None</span>}
        {values.map((value) => <Badge key={value}>{value}</Badge>)}
      </div>
    </div>
  );
}
