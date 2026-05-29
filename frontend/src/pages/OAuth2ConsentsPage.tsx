import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Select } from '../components/Form';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { OAuth2ConsentResponse } from '../types/api';
import { PageHeader } from './PageHeader';

export function OAuth2ConsentsPage() {
  const [selectedUserId, setSelectedUserId] = useState('');
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['oauth2-consents'] }),
  });
  const revokeUserConsent = useMutation({
    mutationFn: ({ clientId, userId }: { clientId: string; userId: string }) =>
      adminApi.oauth2Consents.revoke(clientId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['oauth2-consents'] }),
  });

  function consentTable(
    consents: OAuth2ConsentResponse[],
    onRevoke: (clientId: string) => void,
    canRevoke: boolean,
  ) {
    if (consents.length === 0) {
      return <p className="text-sm text-slate-500">No OAuth2 consents found.</p>;
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
              <Button type="button" variant="danger" disabled={!canRevoke} onClick={() => onRevoke(consent.clientId)}>
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
          {currentUserConsents.isError && <ErrorState error={currentUserConsents.error} />}
          {currentUserConsents.data && consentTable(
            currentUserConsents.data,
            (clientId) => revokeCurrentUserConsent.mutate(clientId),
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
            {users.isError && <ErrorState error={users.error} />}
          </div>
          {selectedUserConsents.isLoading && <LoadingState />}
          {selectedUserConsents.isError && <ErrorState error={selectedUserConsents.error} />}
          {selectedUserConsents.data && consentTable(
            selectedUserConsents.data,
            (clientId) => revokeUserConsent.mutate({ clientId, userId: selectedUserId }),
            hasPermission('iam.clients.write'),
          )}
          {revokeUserConsent.isError && <div className="mt-3"><ErrorState error={revokeUserConsent.error} /></div>}
        </Card>
      </div>
    </>
  );
}
