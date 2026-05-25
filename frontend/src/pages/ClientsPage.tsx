import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select, Textarea } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { SecretNotice } from '../components/SecretNotice';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { ClientStatus, ClientType } from '../types/api';
import { arrayToCsv, compact, csvToArray } from '../utils/format';
import { PageHeader } from './PageHeader';

export function ClientsPage() {
  const [page, setPage] = useState(0);
  const [oneTimeSecret, setOneTimeSecret] = useState('');
  const [template, setTemplate] = useState('web-app');
  const [clientType, setClientType] = useState<ClientType>('CONFIDENTIAL');
  const [grantType, setGrantType] = useState('authorization_code');
  const { hasPermission } = useAuth();
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const queryClient = useQueryClient();
  const clients = useQuery({
    queryKey: ['clients', page, selectedTenantId],
    queryFn: () => adminApi.clients.list({ page, size: 20, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const createClient = useMutation({
    mutationFn: adminApi.clients.create,
    onSuccess: (result) => {
      setOneTimeSecret(result.clientSecret ?? '');
      queryClient.invalidateQueries({ queryKey: ['clients'] });
    },
  });
  const updateClient = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof adminApi.clients.update>[1] }) => adminApi.clients.update(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clients'] }),
  });
  const rotateSecret = useMutation({
    mutationFn: adminApi.clients.rotateSecret,
    onSuccess: (result) => setOneTimeSecret(result.clientSecret ?? ''),
  });

  const templates = {
    spa: {
      label: 'Admin Console SPA / Public PKCE Client',
      clientType: 'PUBLIC' as ClientType,
      grantType: 'authorization_code',
      requirePkce: true,
      requireConsent: false,
      redirectUris: 'http://localhost:5173/oauth2/callback',
      scopes: 'openid,profile,iam.read,iam.write',
    },
    service: {
      label: 'Backend Service / Confidential Client Credentials Client',
      clientType: 'CONFIDENTIAL' as ClientType,
      grantType: 'client_credentials',
      requirePkce: false,
      requireConsent: false,
      redirectUris: '',
      scopes: 'iam.read,iam.write',
    },
    'web-app': {
      label: 'Web App / Confidential Authorization Code Client',
      clientType: 'CONFIDENTIAL' as ClientType,
      grantType: 'authorization_code',
      requirePkce: true,
      requireConsent: true,
      redirectUris: 'http://localhost:8080/oauth2/demo/callback',
      scopes: 'openid,profile,iam.read',
    },
  };
  const selectedTemplate = templates[template as keyof typeof templates];

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const selectedType = String(form.get('clientType') ?? 'CONFIDENTIAL') as ClientType;
    const selectedGrant = String(form.get('grantType') ?? 'authorization_code');
    createClient.mutate({
      tenantId: selectedTenantId,
      clientId: String(form.get('clientId') ?? ''),
      name: String(form.get('name') ?? ''),
      clientType: selectedType,
      requirePkce: selectedType === 'PUBLIC' || form.get('requirePkce') === 'on',
      requireConsent: form.get('requireConsent') === 'on',
      redirectUris: csvToArray(String(form.get('redirectUris') ?? '')),
      grantTypes: [selectedGrant],
      scopes: csvToArray(String(form.get('scopes') ?? '')),
      authenticationMethods: [selectedType === 'PUBLIC' ? 'none' : 'client_secret_basic'],
    });
  }

  return (
    <>
      <PageHeader title="OAuth2 Clients" description="Manage persisted OAuth2 client registrations. Raw secrets are only displayed on create or rotation." />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <Card title="Create client">
          {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant in the header before creating OAuth2 clients.</p>}
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant"><Input value={selectedTenant?.name ?? 'No tenant selected'} disabled /></Field>
            <Field label="Template" hint="Templates set safe defaults. Review values before creating the client.">
              <Select
                value={template}
                onChange={(event) => {
                  const next = event.target.value as keyof typeof templates;
                  setTemplate(next);
                  setClientType(templates[next].clientType);
                  setGrantType(templates[next].grantType);
                }}
              >
                {Object.entries(templates).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}
              </Select>
            </Field>
            <Field label="Client ID"><Input name="clientId" required /></Field>
            <Field label="Client name"><Input name="name" required /></Field>
            <Field label="Client type" hint="Confidential clients can hold a backend secret. Public clients cannot keep a secret and use PKCE with authentication method none.">
              <Select name="clientType" value={clientType} onChange={(event) => setClientType(event.target.value as ClientType)}>
                <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                <option value="PUBLIC">PUBLIC</option>
              </Select>
            </Field>
            <label className="flex items-center gap-2 text-sm"><input key={`${template}-pkce`} name="requirePkce" type="checkbox" defaultChecked={selectedTemplate.requirePkce || clientType === 'PUBLIC'} disabled={clientType === 'PUBLIC'} /> Require PKCE</label>
            <label className="flex items-center gap-2 text-sm"><input key={`${template}-consent`} name="requireConsent" type="checkbox" defaultChecked={selectedTemplate.requireConsent} /> Require consent</label>
            <Field label="Grant type" hint="authorization_code is for browser sign-in. client_credentials is for service-to-service access.">
              <Select name="grantType" value={grantType} onChange={(event) => setGrantType(event.target.value)}>
                <option value="authorization_code">authorization_code</option>
                <option value="client_credentials">client_credentials</option>
              </Select>
            </Field>
            <Field label="Redirect URIs" hint="Required for authorization_code because the authorization server sends the browser back to this URI.">
              <Textarea key={`${template}-redirects`} name="redirectUris" defaultValue={selectedTemplate.redirectUris} placeholder="https://app.example.test/callback" required={grantType === 'authorization_code'} />
            </Field>
            <Field label="Scopes" hint="Scopes are delegated access names such as iam.read, openid, or profile.">
              <Input key={`${template}-scopes`} name="scopes" defaultValue={selectedTemplate.scopes} />
            </Field>
            <Field label="Authentication method" hint="Set automatically from client type: confidential uses client_secret_basic; public uses none.">
              <Input value={clientType === 'PUBLIC' ? 'none' : 'client_secret_basic'} disabled />
            </Field>
            <Button type="submit" disabled={createClient.isPending || !selectedTenantId || !hasPermission('iam.clients.write')}>Create</Button>
            {createClient.isError && <ErrorState error={createClient.error} />}
            {oneTimeSecret && <SecretNotice title="One-time client secret" secret={oneTimeSecret} />}
          </form>
        </Card>
        <Card title="Clients">
          {!selectedTenantId && <p className="text-sm text-slate-600">Select a tenant to load OAuth2 clients.</p>}
          {clients.isLoading && <LoadingState />}
          {clients.isError && <ErrorState error={clients.error} />}
          {clients.data && (
            <>
              <DataTable
                items={clients.data.items}
                columns={[
                  { header: 'Client', render: (client) => <span className="font-medium">{client.name}</span> },
                  { header: 'Client ID', render: (client) => client.clientId },
                  { header: 'Type', render: (client) => <Badge>{client.clientType}</Badge> },
                  { header: 'Status', render: (client) => <Badge>{client.status}</Badge> },
                  { header: 'Scopes', render: (client) => client.scopes.join(', ') || '-' },
                  {
                    header: 'Update',
                    render: (client) => (
                      <form
                        className="grid min-w-[340px] gap-2"
                        onSubmit={(event) => {
                          event.preventDefault();
                          const form = new FormData(event.currentTarget);
                          updateClient.mutate({
                            id: client.id,
                            body: compact({
                              clientName: String(form.get('clientName') ?? ''),
                              status: String(form.get('status') ?? client.status) as ClientStatus,
                              requirePkce: form.get('requirePkce') === 'on',
                              requireConsent: form.get('requireConsent') === 'on',
                              redirectUris: csvToArray(String(form.get('redirectUris') ?? '')),
                              grantTypes: csvToArray(String(form.get('grantTypes') ?? '')),
                              scopes: csvToArray(String(form.get('scopes') ?? '')),
                              authenticationMethods: csvToArray(String(form.get('authenticationMethods') ?? '')),
                            }),
                          });
                        }}
                      >
                        <Input name="clientName" defaultValue={client.name} />
                        <Select name="status" defaultValue={client.status}><option value="ACTIVE">ACTIVE</option><option value="DISABLED">DISABLED</option></Select>
                        <Input name="redirectUris" defaultValue={arrayToCsv(client.redirectUris)} />
                        <Input name="grantTypes" defaultValue={arrayToCsv(client.grantTypes)} />
                        <Input name="scopes" defaultValue={arrayToCsv(client.scopes)} />
                        <Input name="authenticationMethods" defaultValue={arrayToCsv(client.authenticationMethods)} />
                        <label className="flex items-center gap-2 text-xs"><input name="requirePkce" type="checkbox" defaultChecked={client.requirePkce} /> Require PKCE</label>
                        <label className="flex items-center gap-2 text-xs"><input name="requireConsent" type="checkbox" defaultChecked={client.requireConsent} /> Require consent</label>
                        <div className="flex gap-2">
                          <Button type="submit" variant="secondary" disabled={!hasPermission('iam.clients.write')}>Save</Button>
                          <Button type="button" variant="danger" onClick={() => rotateSecret.mutate(client.id)} disabled={client.clientType === 'PUBLIC' || !hasPermission('iam.clients.write')}>Rotate secret</Button>
                        </div>
                      </form>
                    ),
                  },
                ]}
              />
              <Pagination page={clients.data} onPageChange={setPage} />
            </>
          )}
          {(updateClient.isError || rotateSecret.isError) && <div className="mt-3"><ErrorState error={updateClient.error ?? rotateSecret.error} /></div>}
        </Card>
      </div>
    </>
  );
}
