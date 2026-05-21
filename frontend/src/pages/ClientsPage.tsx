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
import { ClientStatus, ClientType } from '../types/api';
import { arrayToCsv, compact, csvToArray } from '../utils/format';
import { PageHeader } from './PageHeader';

export function ClientsPage() {
  const [page, setPage] = useState(0);
  const [tenantId, setTenantId] = useState('');
  const [oneTimeSecret, setOneTimeSecret] = useState('');
  const queryClient = useQueryClient();
  const clients = useQuery({ queryKey: ['clients', page, tenantId], queryFn: () => adminApi.clients.list({ page, size: 20, tenantId }) });
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

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createClient.mutate({
      tenantId: String(form.get('tenantId') ?? ''),
      clientId: String(form.get('clientId') ?? ''),
      name: String(form.get('name') ?? ''),
      clientType: String(form.get('clientType') ?? 'CONFIDENTIAL') as ClientType,
      requirePkce: form.get('requirePkce') === 'on',
      requireConsent: form.get('requireConsent') === 'on',
      redirectUris: csvToArray(String(form.get('redirectUris') ?? '')),
      grantTypes: csvToArray(String(form.get('grantTypes') ?? '')),
      scopes: csvToArray(String(form.get('scopes') ?? '')),
      authenticationMethods: csvToArray(String(form.get('authenticationMethods') ?? '')),
    });
  }

  return (
    <>
      <PageHeader title="OAuth2 Clients" description="Manage persisted OAuth2 client registrations. Raw secrets are only displayed on create or rotation." />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <Card title="Create client">
          <form onSubmit={create} className="grid gap-3">
            <Field label="Tenant ID"><Input name="tenantId" required /></Field>
            <Field label="Client ID"><Input name="clientId" required /></Field>
            <Field label="Client name"><Input name="name" required /></Field>
            <Field label="Client type">
              <Select name="clientType" defaultValue="CONFIDENTIAL">
                <option value="CONFIDENTIAL">CONFIDENTIAL</option>
                <option value="PUBLIC">PUBLIC</option>
              </Select>
            </Field>
            <label className="flex items-center gap-2 text-sm"><input name="requirePkce" type="checkbox" defaultChecked /> Require PKCE</label>
            <label className="flex items-center gap-2 text-sm"><input name="requireConsent" type="checkbox" defaultChecked /> Require consent</label>
            <Field label="Redirect URIs"><Textarea name="redirectUris" placeholder="https://app.example.test/callback" /></Field>
            <Field label="Grant types"><Input name="grantTypes" defaultValue="authorization_code" /></Field>
            <Field label="Scopes"><Input name="scopes" defaultValue="iam.read" /></Field>
            <Field label="Authentication methods"><Input name="authenticationMethods" defaultValue="client_secret_basic" /></Field>
            <Button type="submit" disabled={createClient.isPending}>Create</Button>
            {createClient.isError && <ErrorState error={createClient.error} />}
            {oneTimeSecret && <SecretNotice title="One-time client secret" secret={oneTimeSecret} />}
          </form>
        </Card>
        <Card title="Clients">
          <div className="mb-4"><Field label="Tenant filter"><Input value={tenantId} onChange={(event) => { setTenantId(event.target.value); setPage(0); }} /></Field></div>
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
                          <Button type="submit" variant="secondary">Save</Button>
                          <Button type="button" variant="danger" onClick={() => rotateSecret.mutate(client.id)} disabled={client.clientType === 'PUBLIC'}>Rotate secret</Button>
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
