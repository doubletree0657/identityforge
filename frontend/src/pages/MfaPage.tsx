import { FormEvent, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState } from '../components/State';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function MfaPage() {
  const [userId, setUserId] = useState('');
  const [secret, setSecret] = useState('');
  const { selectedTenantId } = useTenantContext();
  const users = useQuery({
    queryKey: ['users-for-mfa', selectedTenantId],
    queryFn: () => adminApi.users.list({ tenantId: selectedTenantId, size: 100 }),
    enabled: !!selectedTenantId,
  });
  const enroll = useMutation({ mutationFn: () => adminApi.mfa.enrollTotp(userId), onSuccess: (result) => setSecret(result.secret) });
  const verify = useMutation({ mutationFn: (code: string) => adminApi.mfa.verifyTotp(userId, code) });
  const disable = useMutation({ mutationFn: () => adminApi.mfa.disableTotp(userId), onSuccess: () => setSecret('') });

  function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    verify.mutate(String(new FormData(event.currentTarget).get('code') ?? ''));
  }

  return (
    <>
      <PageHeader title="MFA" description="Admin TOTP enrollment operations. This does not add MFA to login." />
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
            <Button onClick={() => enroll.mutate()} disabled={!userId}>Enroll TOTP</Button>
            <Button variant="danger" onClick={() => disable.mutate()} disabled={!userId}>Disable TOTP</Button>
          </div>
          {secret && <SecretNotice title="TOTP setup secret" secret={secret} />}
          <form onSubmit={verifyCode} className="flex gap-2">
            <Input name="code" placeholder="123456" />
            <Button type="submit" variant="secondary" disabled={!userId}>Verify</Button>
          </form>
          {verify.data && <Badge>{verify.data.verified ? 'VERIFIED' : 'INVALID'}</Badge>}
          {(enroll.isError || verify.isError || disable.isError) && <ErrorState error={enroll.error ?? verify.error ?? disable.error} />}
        </div>
      </Card>
    </>
  );
}
