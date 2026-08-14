import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { Select } from '../components/Form';
import { EmptyState, ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { OAuth2ConsentResponse } from '../types/api';
import { PageHeader } from './PageHeader';

export function OAuth2ConsentsPage() {
  const [selectedUserId, setSelectedUserId] = useState('');
  const [revokeTarget, setRevokeTarget] = useState<{ clientId: string; clientName: string; userId?: string } | null>(null);
  const { hasPermission } = useAuth();
  const { selectedTenantId } = useTenantContext();
  const queryClient = useQueryClient();

  const currentUserConsents = useQuery({
    queryKey: ['oauth2-consents', 'me'],
    queryFn: adminApi.oauth2Consents.me,
  });
  const users = useQuery({
    queryKey: ['users', 'oauth2-consents', selectedTenantId],
    queryFn: () => adminApi.users.list({ page: 0, size: 100, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId && hasPermission('iam.users.read'),
  });
  const selectedUserConsents = useQuery({
    queryKey: ['oauth2-consents', 'user', selectedUserId],
    queryFn: () => adminApi.oauth2Consents.list({ userId: selectedUserId }),
    enabled: !!selectedUserId && hasPermission('iam.clients.read'),
  });
  const revokeCurrentUserConsent = useMutation({
    mutationFn: adminApi.oauth2Consents.revokeMe,
    onSuccess: () => {
      setRevokeTarget(null);
      queryClient.invalidateQueries({ queryKey: ['oauth2-consents'] });
    },
  });
  const revokeUserConsent = useMutation({
    mutationFn: ({ clientId, userId }: { clientId: string; userId: string }) =>
      adminApi.oauth2Consents.revoke(clientId, userId),
    onSuccess: () => {
      setRevokeTarget(null);
      queryClient.invalidateQueries({ queryKey: ['oauth2-consents'] });
    },
  });

  function consentTable(
    consents: OAuth2ConsentResponse[],
    onRevoke: (consent: OAuth2ConsentResponse) => void,
    canRevoke: boolean,
  ) {
    if (consents.length === 0) {
      return <EmptyState title="No OAuth2 consents" detail="Consent records appear after a user approves access for a client that requires consent." />;
    }
    return (
      <DataTable
        items={consents}
        columns={[
          { header: 'Client', render: (consent) => <span className="font-medium">{consent.clientName}</span> },
          { header: 'Client ID', render: (consent) => consent.clientId },
          { header: 'Application', render: (consent) => consent.resourceServerName || '-' },
          {
            header: 'Scopes',
            render: (consent) => (
              <div className="flex max-w-[360px] flex-wrap gap-1">
                {consent.scopes.map((scope) => <Badge key={scope}>{scope}</Badge>)}
              </div>
            ),
          },
          {
            header: 'Actions',
            render: (consent) => (
              <Button type="button" variant="danger" disabled={!canRevoke} onClick={() => onRevoke(consent)}>
                Revoke
              </Button>
            ),
          },
        ]}
      />
    );
  }

  return (
    <>
      <PageHeader
        title="OAuth2 Consents"
        description="Review and revoke stored OAuth2 consent grants. Tokens and client secrets are never shown."
      />
      <div className="grid gap-4 xl:grid-cols-2">
        <Card title="My consents">
          {currentUserConsents.isLoading && <LoadingState />}
          {currentUserConsents.isError && <ErrorState error={currentUserConsents.error} onRetry={() => void currentUserConsents.refetch()} />}
          {currentUserConsents.data && consentTable(
            currentUserConsents.data,
            (consent) => setRevokeTarget({ clientId: consent.clientId, clientName: consent.clientName }),
            true,
          )}
          {revokeCurrentUserConsent.isError && <div className="mt-3"><ErrorState error={revokeCurrentUserConsent.error} /></div>}
        </Card>
        <Card title="User consents">
          <div className="mb-3 grid gap-2">
            <Select
              value={selectedUserId}
              onChange={(event) => setSelectedUserId(event.target.value)}
              disabled={!selectedTenantId || users.isLoading || !hasPermission('iam.users.read')}
            >
              <option value="">Select user</option>
              {(users.data?.items ?? []).map((user) => (
                <option key={user.id} value={user.id}>{user.displayName} ({user.username})</option>
              ))}
            </Select>
            {!selectedTenantId && <p className="text-sm text-slate-500">Select a tenant to load users.</p>}
            {users.isError && <ErrorState error={users.error} onRetry={() => void users.refetch()} />}
          </div>
          {selectedUserConsents.isLoading && <LoadingState />}
          {selectedUserConsents.isError && <ErrorState error={selectedUserConsents.error} onRetry={() => void selectedUserConsents.refetch()} />}
          {selectedUserConsents.data && consentTable(
            selectedUserConsents.data,
            (consent) => setRevokeTarget({ clientId: consent.clientId, clientName: consent.clientName, userId: selectedUserId }),
            hasPermission('iam.clients.write'),
          )}
          {revokeUserConsent.isError && <div className="mt-3"><ErrorState error={revokeUserConsent.error} /></div>}
        </Card>
      </div>
      <ConfirmDialog
        open={revokeTarget !== null}
        title={`Revoke ${revokeTarget?.clientName ?? 'application'} consent?`}
        detail="The stored approval and associated authorization family will be removed. The user must approve the requested scopes again on a future authorization."
        confirmLabel="Revoke consent"
        isPending={revokeCurrentUserConsent.isPending || revokeUserConsent.isPending}
        onCancel={() => setRevokeTarget(null)}
        onConfirm={() => revokeTarget?.userId
          ? revokeUserConsent.mutate({ clientId: revokeTarget.clientId, userId: revokeTarget.userId })
          : revokeTarget && revokeCurrentUserConsent.mutate(revokeTarget.clientId)}
      />
    </>
  );
}
