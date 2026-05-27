import { FormEvent, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { getApiBaseUrl } from '../api/storage';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function OAuth2DemoPage() {
  const [authorizationUrl, setAuthorizationUrl] = useState('');
  const [tokenCommand, setTokenCommand] = useState('');
  const [selectedClientId, setSelectedClientId] = useState('');
  const { selectedTenantId } = useTenantContext();
  const clients = useQuery({
    queryKey: ['clients', 'oauth2-demo', selectedTenantId],
    queryFn: () => adminApi.clients.list({ page: 0, size: 100, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const selectedClient = (clients.data?.items ?? []).find((client) => client.id === selectedClientId);

  function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const baseUrl = getApiBaseUrl();
    const clientId = selectedClient?.clientId ?? String(form.get('clientId') ?? '');
    const redirectUri = String(form.get('redirectUri') ?? '');
    const baseScopes = String(form.get('scope') ?? 'iam.read').split(/[,\s]+/).filter(Boolean);
    const applicationScopes = form.getAll('applicationScope').map(String);
    const scope = [...baseScopes, ...applicationScopes].join(' ');
    const state = crypto.randomUUID();
    const url = new URL('/oauth2/authorize', baseUrl);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('scope', scope);
    url.searchParams.set('state', state);
    setAuthorizationUrl(url.toString());
    setTokenCommand(`curl -u '${clientId}:<client-secret>' -X POST '${baseUrl}/oauth2/token' \\
  -H 'Content-Type: application/x-www-form-urlencoded' \\
  -d 'grant_type=authorization_code' \\
  -d 'code=<authorization-code>' \\
  -d 'redirect_uri=${redirectUri}'`);
  }

  return (
    <>
      <PageHeader
        title="OAuth2 Authorization Code Demo"
        description="Generate a development authorization URL and review the token exchange path. This page does not fake the flow or store client secrets."
      />
      <div className="grid gap-4 lg:grid-cols-[420px_1fr]">
        <Card title="Generate authorization URL">
          <form onSubmit={generate} className="grid gap-3">
            <Field label="Persisted client" hint="Select a saved client to use its client ID and inspect assigned application scopes.">
              <Select value={selectedClientId} onChange={(event) => setSelectedClientId(event.target.value)}>
                <option value="">Manual client ID</option>
                {(clients.data?.items ?? []).map((client) => (
                  <option key={client.id} value={client.id}>{client.name} ({client.clientId})</option>
                ))}
              </Select>
            </Field>
            <Field label="Client ID" hint="Use a persisted OAuth2 client from the selected tenant. Confidential clients need the one-time secret from create or rotation for token exchange.">
              <Input name="clientId" value={selectedClient?.clientId ?? undefined} disabled={!!selectedClient} required={!selectedClient} />
            </Field>
            {selectedClient && (
              <div className="grid gap-2 rounded-md border border-line p-3 text-sm">
                <div><span className="font-semibold">Linked application:</span> {selectedClient.resourceServerName ?? 'None'}</div>
                <div className="grid gap-1">
                  <span className="font-semibold">Allowed application scopes</span>
                  {selectedClient.allowedResourcePermissions.length === 0 && <span className="text-slate-500">No application scopes assigned.</span>}
                  {selectedClient.allowedResourcePermissions.map((permission) => (
                    <label key={permission.id} className="flex items-center gap-2">
                      <input type="checkbox" name="applicationScope" value={permission.name} />
                      <span>{permission.name}</span>
                    </label>
                  ))}
                </div>
              </div>
            )}
            <Field label="Redirect URI" hint="Must exactly match one of the persisted redirect URIs on the client.">
              <Input name="redirectUri" defaultValue={selectedClient?.redirectUris[0] ?? 'http://127.0.0.1:8080/oauth2/demo/callback'} required />
            </Field>
            <Field label="Base scopes" hint="Use scopes explicitly configured on the client. Application scopes are selected separately above.">
              <Input name="scope" defaultValue="iam.read" />
            </Field>
            <Button type="submit">Generate</Button>
          </form>
        </Card>
        <Card title="Local demo steps">
          <ol className="grid list-decimal gap-3 pl-5 text-sm text-slate-700">
            <li>Create or select a tenant, then create an active local user and set a password from the Users workflow.</li>
            <li>Create a persisted OAuth2 client on the OAuth2 Clients page with authorization_code and a matching redirect URI.</li>
            <li>Enable TOTP for the user if you want to demonstrate the MFA challenge during browser login.</li>
            <li>Open the authorization URL in a browser and sign in through the backend-owned login page.</li>
            <li>Complete the TOTP challenge when the signed-in user has a verified TOTP credential.</li>
            <li>Approve or deny the backend-owned consent page when the client requires consent.</li>
            <li>Copy the returned authorization code from the redirect URL and exchange it at `/oauth2/token`.</li>
            <li>Paste the resulting access token into the Admin Console token panel, then call a protected Admin API.</li>
          </ol>
          <div className="mt-4 flex flex-wrap gap-3 text-sm">
            <Link className="font-medium text-brand hover:underline" to="/clients">Manage OAuth2 clients</Link>
            <Link className="font-medium text-brand hover:underline" to="/users">Manage users</Link>
            <Link className="font-medium text-brand hover:underline" to="/audit-logs">Review audit logs</Link>
          </div>
          {authorizationUrl && (
            <div className="mt-5 grid gap-3">
              <div>
                <div className="text-sm font-semibold">Authorization URL</div>
                <a className="break-all text-sm text-brand hover:underline" href={authorizationUrl} target="_blank" rel="noreferrer">{authorizationUrl}</a>
              </div>
              <div>
                <div className="text-sm font-semibold">Token exchange command</div>
                <pre className="mt-2 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">{tokenCommand}</pre>
              </div>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
