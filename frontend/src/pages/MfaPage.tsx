import { FormEvent, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState } from '../components/State';
import { TenantRequired } from '../components/TenantRequired';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function MfaPage() {
  const [userId, setUserId] = useState('');
  const [secret, setSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const { hasPermission } = useAuth();
  const { selectedTenantId } = useTenantContext();
  const users = useQuery({
    queryKey: ['users-for-mfa', selectedTenantId],
    queryFn: () => adminApi.users.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const enroll = useMutation({
    mutationFn: () => adminApi.mfa.enrollTotp(userId),
    onSuccess: (result) => {
      setSecret(result.secret);
      setOtpauthUri(result.otpauthUri ?? '');
    },
  });
  const verify = useMutation({ mutationFn: (code: string) => adminApi.mfa.verifyTotp(userId, code) });
  const disable = useMutation({ mutationFn: () => adminApi.mfa.disableTotp(userId), onSuccess: () => {
    setSecret('');
    setOtpauthUri('');
  } });

  function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    verify.mutate(String(new FormData(event.currentTarget).get('code') ?? ''));
  }

  return (
    <>
      <PageHeader title="MFA" description="Enroll and verify TOTP credentials used during local-user login." />
      {!selectedTenantId && <TenantRequired label="Select a tenant to choose users for MFA operations." />}
      <Card title="TOTP operations">
        <div className="grid max-w-2xl gap-4">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant in the header to choose a user for MFA actions.</p>}
          <Field label="User">
            <Select value={userId} onChange={(event) => setUserId(event.target.value)} disabled={!selectedTenantId}>
              <option value="">Select tenant user</option>
              {users.data?.items.map((user) => (
                <option key={user.id} value={user.id}>{user.displayName} ({user.username})</option>
              ))}
            </Select>
          </Field>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => enroll.mutate()} disabled={!userId || !hasPermission('iam.mfa.manage')}>Enroll TOTP</Button>
            <Button variant="danger" onClick={() => disable.mutate()} disabled={!userId || !hasPermission('iam.mfa.manage')}>Disable TOTP</Button>
          </div>
          {secret && (
            <div className="grid gap-3">
              <SecretNotice title="TOTP setup secret" secret={secret} />
              {otpauthUri && <SecretNotice title="otpauth URI" secret={otpauthUri} />}
              <p className="text-sm text-slate-600">
                Add the setup secret or otpauth URI to Google Authenticator, Microsoft Authenticator, or 1Password, then enter the current six-digit code. The placeholder 123456 usually fails unless it is the current authenticator code.
              </p>
            </div>
          )}
          <form onSubmit={verifyCode} className="flex gap-2">
            <Input name="code" placeholder="123456" />
            <Button type="submit" variant="secondary" disabled={!userId || !hasPermission('iam.mfa.manage')}>Verify</Button>
          </form>
          {verify.data && <Badge>{verify.data.verified ? 'VERIFIED' : 'INVALID'}</Badge>}
          {(enroll.isError || verify.isError || disable.isError) && <ErrorState error={enroll.error ?? verify.error ?? disable.error} />}
        </div>
      </Card>
    </>
  );
}
