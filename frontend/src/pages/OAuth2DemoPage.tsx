import { FormEvent, useEffect, useMemo, useState } from 'react';
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
  const [resourceCommands, setResourceCommands] = useState('');
  const [selectedClientId, setSelectedClientId] = useState('');
  const [selectedApplicationScopes, setSelectedApplicationScopes] = useState<string[]>([]);
  const { selectedTenantId } = useTenantContext();
  const clients = useQuery({
    queryKey: ['clients', 'oauth2-demo', selectedTenantId],
    queryFn: () => adminApi.clients.list({ page: 0, size: 100, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId,
  });
  const clientItems = clients.data?.items ?? [];
  const selectedClient = clientItems.find((client) => client.id === selectedClientId);
  const allowedApplicationScopeNames = useMemo(
    () => selectedClient?.allowedResourcePermissions.map((permission) => permission.name) ?? [],
    [selectedClient],
  );
  const selectedScopesNotAllowed = selectedApplicationScopes.filter(
    (scope) => !allowedApplicationScopeNames.includes(scope),
  );

  useEffect(() => {
    if (selectedClientId && !clientItems.some((client) => client.id === selectedClientId)) {
      setSelectedClientId('');
      setSelectedApplicationScopes([]);
      setAuthorizationUrl('');
      setTokenCommand('');
      setResourceCommands('');
    }
  }, [clientItems, selectedClientId]);

  useEffect(() => {
    setSelectedApplicationScopes((scopes) =>
      scopes.filter((scope) => allowedApplicationScopeNames.includes(scope)),
    );
  }, [allowedApplicationScopeNames]);

  function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const baseUrl = getApiBaseUrl();
    const clientId = selectedClient?.clientId ?? String(form.get('clientId') ?? '');
    const redirectUri = String(form.get('redirectUri') ?? '');
    const baseScopes = String(form.get('scope') ?? 'iam.read').split(/[,\s]+/).filter(Boolean);
    const applicationScopes = selectedApplicationScopes;
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
    setResourceCommands(resourceApiCommands(baseUrl, applicationScopes));
  }

  function resourceApiCommands(baseUrl: string, applicationScopes: string[]) {
    const employeeStatus = applicationScopes.includes('payroll.employee.read') ? 'expected: 200' : 'expected: 403';
    const salaryReadStatus = applicationScopes.includes('payroll.salary.read') ? 'expected: 200' : 'expected: 403';
    const salaryWriteStatus = applicationScopes.includes('payroll.salary.write') ? 'expected: 200' : 'expected: 403';
    const commands: string[] = [
      `# Employees (${employeeStatus})
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  ${baseUrl}/demo-resource-api/payroll/employees`,
      `# Salaries read (${salaryReadStatus})
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  ${baseUrl}/demo-resource-api/payroll/salaries`,
      `# Salaries write (${salaryWriteStatus})
curl -X POST -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  -H "Content-Type: application/json" \\
  -d '{}' \\
  ${baseUrl}/demo-resource-api/payroll/salaries`,
    ];
    return commands.join('\n\n');
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
              <Select
                value={selectedClientId}
                disabled={clients.isLoading || clients.isError || !selectedTenantId}
                onChange={(event) => {
                  setSelectedClientId(event.target.value);
                  setSelectedApplicationScopes([]);
                  setAuthorizationUrl('');
                  setTokenCommand('');
                  setResourceCommands('');
                }}
              >
                <option value="">Manual client ID</option>
                {clientItems.map((client) => (
                  <option key={client.id} value={client.id}>{client.name} ({client.clientId})</option>
                ))}
              </Select>
            </Field>
            {!selectedTenantId && <p className="text-sm text-slate-500">Select a tenant before choosing persisted OAuth2 clients.</p>}
            {clients.isLoading && <p className="text-sm text-slate-500">Loading OAuth2 clients...</p>}
            {clients.isError && <p className="text-sm text-red-700">Unable to load OAuth2 clients for the selected tenant.</p>}
            {selectedTenantId && !clients.isLoading && !clients.isError && clientItems.length === 0 && (
              <p className="text-sm text-slate-500">No OAuth2 clients exist for this tenant yet.</p>
            )}
            <Field label="Client ID" hint="Use a persisted OAuth2 client from the selected tenant. Confidential clients need the one-time secret from create or rotation for token exchange.">
              <Input name="clientId" value={selectedClient?.clientId ?? undefined} disabled={!!selectedClient} required={!selectedClient} />
            </Field>
            {selectedClient && (
              <div className="grid gap-2 rounded-md border border-line p-3 text-sm">
                <div><span className="font-semibold">Selected client:</span> {selectedClient.name} ({selectedClient.clientId})</div>
                <div><span className="font-semibold">Linked application:</span> {selectedClient.resourceServerName ?? 'None linked'}</div>
                <div><span className="font-semibold">Client scopes:</span> {selectedClient.scopes.length > 0 ? selectedClient.scopes.join(', ') : 'None configured'}</div>
                <div className="grid gap-1">
                  <span className="font-semibold">Allowed application scopes</span>
                  {selectedClient.allowedResourcePermissions.length === 0 && <span className="text-slate-500">No application scopes assigned.</span>}
                  {selectedClient.allowedResourcePermissions.map((permission) => (
                    <label key={permission.id} className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        name="applicationScope"
                        value={permission.name}
                        checked={selectedApplicationScopes.includes(permission.name)}
                        onChange={(event) => {
                          setSelectedApplicationScopes((current) =>
                            event.target.checked
                              ? [...current, permission.name]
                              : current.filter((scope) => scope !== permission.name),
                          );
                        }}
                      />
                      <span>{permission.name}</span>
                    </label>
                  ))}
                </div>
                {selectedScopesNotAllowed.length > 0 && (
                  <p className="rounded-md border border-amber-200 bg-amber-50 p-2 text-amber-800">
                    Remove unallowed scopes before generating: {selectedScopesNotAllowed.join(', ')}
                  </p>
                )}
              </div>
            )}
            <Field label="Redirect URI" hint="Must exactly match one of the persisted redirect URIs on the client.">
              <Input
                key={selectedClient?.id ?? 'manual-redirect'}
                name="redirectUri"
                defaultValue={selectedClient?.redirectUris[0] ?? 'http://127.0.0.1:8080/oauth2/demo/callback'}
                required
              />
            </Field>
            <Field label="Base scopes" hint="Use scopes explicitly configured on the client. Application scopes are selected separately above.">
              <Input key={selectedClient?.id ?? 'manual-scope'} name="scope" defaultValue={selectedClient?.scopes[0] ?? 'iam.read'} />
            </Field>
            <Button type="submit" disabled={selectedScopesNotAllowed.length > 0}>Generate</Button>
          </form>
        </Card>
        <Card title="Local demo steps">
          <ol className="grid list-decimal gap-3 pl-5 text-sm text-slate-700">
            <li>Select the International IAM Dev Client or another persisted client linked to Payroll API.</li>
            <li>Select allowed application scopes such as payroll.employee.read or payroll.salary.read.</li>
            <li>Generate the authorization URL and open it in a browser.</li>
            <li>Log in as admin or a test user, then approve consent if the client requires it.</li>
            <li>Copy the authorization code from the redirect URL.</li>
            <li>Exchange the code for a token with the generated curl command.</li>
            <li>Call the demo Payroll resource API with the returned access token.</li>
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
              {resourceCommands && (
                <div>
                  <div className="text-sm font-semibold">Resource API curl commands</div>
                  <pre className="mt-2 overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">{resourceCommands}</pre>
                </div>
              )}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
