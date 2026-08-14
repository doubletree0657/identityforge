import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Field, Input } from '../components/Form';
import { RecoveryCodesNotice } from '../components/RecoveryCodesNotice';
import { Notice } from '../components/Notice';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState, LoadingState } from '../components/State';
import { TotpQrCode } from '../components/TotpQrCode';
import { Stepper } from '../components/Stepper';
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
  const [confirmation, setConfirmation] = useState<'regenerate' | 'disable' | null>(null);

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
      setConfirmation(null);
      void refreshStatus();
    },
  });
  const disable = useMutation({
    mutationFn: () => adminApi.mfa.disableTotp(userId),
    onSuccess: () => {
      setSecret('');
      setOtpauthUri('');
      setRecoveryCodes([]);
      setConfirmation(null);
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
      <PageHeader title="Multi-factor Authentication" description="Secure your administrator account with a TOTP authenticator and one-time recovery codes. Credential material is self-service and displayed once." />
      {!userId && <Card title="MFA unavailable"><p className="text-sm text-slate-600">The current access token does not identify a local user.</p></Card>}
      {userId && !hasPermission('iam.mfa.manage') && (
        <Card title="MFA unavailable"><p className="text-sm text-slate-600">Your role does not include the MFA management permission.</p></Card>
      )}
      {canManage && (
        <>
        <Stepper steps={[
          { label: 'Start enrollment', detail: 'Create a pending authenticator credential.', status: status.data?.totpEnrolled ? 'complete' : 'current' },
          { label: 'Verify authenticator', detail: 'Prove the authenticator can generate a valid code.', status: status.data?.totpVerified ? 'complete' : status.data?.enrollmentPending ? 'current' : 'upcoming' },
          { label: 'Store recovery codes', detail: 'Save the one-time fallback set securely.', status: recoveryCodes.length ? 'current' : status.data?.totpVerified ? 'complete' : 'upcoming' },
        ]} />
        <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(300px,1fr)]">
          <Card title="Authenticator setup" description="Complete all three steps before leaving this page.">
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
                <Button className="mt-3" onClick={() => enroll.mutate()} isLoading={enroll.isPending} loadingLabel="Creating setup…">
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
                  <Button type="submit" variant="secondary" isLoading={verify.isPending} loadingLabel="Verifying…">Verify and activate</Button>
                  {verify.data && !verify.data.verified && <Notice title="That code was not accepted" tone="warning">Wait for a fresh authenticator code and try again, or verify the device clock is accurate.</Notice>}
                </form>
              )}
              {recoveryCodes.length > 0 && <RecoveryCodesNotice codes={recoveryCodes} />}
              {status.data?.totpVerified && recoveryCodes.length === 0 && <Notice title="MFA is active" tone="success">{status.data.recoveryCodesRemaining} of {status.data.recoveryCodesTotal} recovery codes remain.</Notice>}
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
                  onClick={() => setConfirmation('regenerate')}
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
                  onClick={() => setConfirmation('disable')}
                >Disable MFA</Button>
              </div>
            </div>
          </Card>
          {failure && <div className="xl:col-span-2"><ErrorState error={failure} onRetry={status.isError ? () => void status.refetch() : undefined} /></div>}
        </div>
        <ConfirmDialog
          open={confirmation !== null}
          title={confirmation === 'regenerate' ? 'Replace every recovery code?' : 'Disable multi-factor authentication?'}
          detail={confirmation === 'regenerate' ? 'All existing recovery codes become invalid immediately. Save the new set before navigating away.' : 'The authenticator credential and all recovery codes will be removed, and existing user access tokens will be invalidated.'}
          confirmLabel={confirmation === 'regenerate' ? 'Replace codes' : 'Disable MFA'}
          isPending={regenerate.isPending || disable.isPending}
          onCancel={() => setConfirmation(null)}
          onConfirm={() => confirmation === 'regenerate' ? regenerate.mutate() : disable.mutate()}
        />
        </>
      )}
    </>
  );
}
