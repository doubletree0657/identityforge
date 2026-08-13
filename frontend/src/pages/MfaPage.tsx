import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { RecoveryCodesNotice } from '../components/RecoveryCodesNotice';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState, LoadingState } from '../components/State';
import { TotpQrCode } from '../components/TotpQrCode';
import { useAuth } from '../context/AuthContext';
import type { MfaStatusResponse } from '../types/api';
import { PageHeader } from './PageHeader';

export function MfaPage() {
  const { user, hasPermission } = useAuth();
  const userId = user?.userId ?? '';
  const queryClient = useQueryClient();
  const [secret, setSecret] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);

  const status = useQuery({
    queryKey: ['mfa-status', userId],
    queryFn: () => adminApi.mfa.status(userId),
    enabled: !!userId && hasPermission('iam.mfa.manage'),
  });
  const refreshStatus = () => queryClient.invalidateQueries({ queryKey: ['mfa-status', userId] });
  const enroll = useMutation({
    mutationFn: () => adminApi.mfa.enrollTotp(userId),
    onSuccess: (result) => {
      setSecret(result.secret);
      setOtpauthUri(result.otpauthUri ?? '');
      setRecoveryCodes([]);
      void refreshStatus();
    },
  });
  const verify = useMutation({
    mutationFn: (code: string) => adminApi.mfa.verifyTotp(userId, code),
    onSuccess: (result) => {
      if (result.verified) {
        setSecret('');
        setOtpauthUri('');
        if (result.recoveryCodes.length > 0) setRecoveryCodes(result.recoveryCodes);
        queryClient.setQueryData<MfaStatusResponse>(['mfa-status', userId], (current) => current && ({
          ...current,
          totpEnrolled: true,
          totpVerified: true,
          enrollmentPending: false,
          recoveryCodesRemaining: result.recoveryCodes.length || current.recoveryCodesRemaining,
          recoveryCodesTotal: result.recoveryCodes.length || current.recoveryCodesTotal,
        }));
      }
    },
  });
  const regenerate = useMutation({
    mutationFn: () => adminApi.mfa.regenerateRecoveryCodes(userId),
    onSuccess: (result) => {
      setRecoveryCodes(result.recoveryCodes);
      void refreshStatus();
    },
  });
  const disable = useMutation({
    mutationFn: () => adminApi.mfa.disableTotp(userId),
    onSuccess: () => {
      setSecret('');
      setOtpauthUri('');
      setRecoveryCodes([]);
      void refreshStatus();
    },
  });

  function verifyCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    verify.mutate(String(new FormData(event.currentTarget).get('code') ?? ''));
  }

  const canManage = !!userId && hasPermission('iam.mfa.manage');
  const failure = enroll.error ?? verify.error ?? regenerate.error ?? disable.error ?? status.error;

  return (
    <>
      <PageHeader title="MFA" description="Secure your own administrator account with an authenticator and one-time recovery codes." />
      {!userId && <Card title="MFA unavailable"><p className="text-sm text-slate-600">The current access token does not identify a local user.</p></Card>}
      {userId && !hasPermission('iam.mfa.manage') && (
        <Card title="MFA unavailable"><p className="text-sm text-slate-600">Your role does not include the MFA management permission.</p></Card>
      )}
      {canManage && (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)]">
          <Card title="Authenticator setup">
            {status.isLoading && <LoadingState label="Loading MFA status" />}
            {status.data && (
              <div className="mb-4 flex flex-wrap gap-2">
                <Badge>{status.data.enrollmentPending ? 'SETUP PENDING' : status.data.totpVerified ? 'MFA ACTIVE' : 'NOT ENROLLED'}</Badge>
                {status.data.totpVerified && <Badge>{`${status.data.recoveryCodesRemaining} RECOVERY CODES LEFT`}</Badge>}
              </div>
            )}
            <div className="grid gap-4">
              <div>
                <h3 className="font-semibold text-ink">1. Start enrollment</h3>
                <p className="mt-1 text-sm text-slate-600">Starting again replaces any pending setup. An active factor and its recovery codes stay valid until the replacement is verified.</p>
                <Button className="mt-3" onClick={() => enroll.mutate()} disabled={enroll.isPending}>
                  {status.data?.totpEnrolled ? 'Restart authenticator setup' : 'Enroll authenticator'}
                </Button>
              </div>
              {secret && (
                <div className="grid gap-3">
                  <h3 className="font-semibold text-ink">2. Scan or enter the setup key</h3>
                  {otpauthUri && <TotpQrCode uri={otpauthUri} />}
                  <SecretNotice title="Manual setup key" secret={secret} />
                  <p className="text-sm text-slate-600">Keep this page open until verification succeeds. The setup key is not returned by status APIs.</p>
                </div>
              )}
              {(secret || status.data?.enrollmentPending) && (
                <form onSubmit={verifyCode} className="grid gap-3 sm:max-w-sm">
                  <h3 className="font-semibold text-ink">3. Verify the authenticator</h3>
                  <Field label="Current six-digit code"><Input name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" placeholder="123456" required /></Field>
                  <Button type="submit" variant="secondary" disabled={verify.isPending}>Verify and activate</Button>
                  {verify.data && !verify.data.verified && <Badge>INVALID CODE</Badge>}
                </form>
              )}
              {recoveryCodes.length > 0 && <RecoveryCodesNotice codes={recoveryCodes} />}
            </div>
          </Card>
          <Card title="Recovery and reset">
            <div className="grid gap-4 text-sm text-slate-600">
              <p>At sign-in, use an authenticator code normally. If the device is unavailable, enter one saved recovery code in the same MFA field.</p>
              <div>
                <div className="font-semibold text-ink">Recovery codes</div>
                <p className="mt-1">Regeneration immediately invalidates every old code, including unused ones. The new set is displayed once.</p>
                <Button
                  className="mt-3"
                  variant="secondary"
                  disabled={!status.data?.totpVerified || regenerate.isPending}
                  onClick={() => window.confirm('Replace all existing recovery codes?') && regenerate.mutate()}
                >Regenerate codes</Button>
                {recoveryCodes.length > 0 && <p className="mt-2 text-xs">After saving the codes, sign out and back in so your new access token is protected by the updated MFA state.</p>}
              </div>
              <div className="border-t border-line pt-4">
                <div className="font-semibold text-ink">Disable MFA</div>
                <p className="mt-1">This removes the authenticator credential and every recovery code, and invalidates existing user access tokens.</p>
                <Button
                  className="mt-3"
                  variant="danger"
                  disabled={!status.data?.totpEnrolled || disable.isPending}
                  onClick={() => window.confirm('Disable MFA and revoke all recovery codes?') && disable.mutate()}
                >Disable MFA</Button>
              </div>
            </div>
          </Card>
          {failure && <div className="xl:col-span-2"><ErrorState error={failure} /></div>}
        </div>
      )}
    </>
  );
}
