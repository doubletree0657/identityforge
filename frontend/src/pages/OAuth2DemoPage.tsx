import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { CopyButton } from '../components/CopyButton';
import { Field, Input, Select } from '../components/Form';
import { Notice } from '../components/Notice';
import { Stepper } from '../components/Stepper';
import { getApiBaseUrl } from '../api/storage';
import { useTenantContext } from '../context/TenantContext';
import { oauthDemoErrors } from '../utils/demoFlows';
import { PageHeader } from './PageHeader';

const OIDC_SCOPES = ['openid', 'profile', 'email', 'groups', 'roles'];
const PAYROLL_API_BASE_URL = (import.meta.env.VITE_PAYROLL_API_BASE_URL ?? 'http://localhost:8090').replace(/\/$/, '');

export function OAuth2DemoPage() {
  const [authorizationUrl, setAuthorizationUrl] = useState('');
  const [tokenCommand, setTokenCommand] = useState('');
  const [resourceCommands, setResourceCommands] = useState('');
  const [selectedClientId, setSelectedClientId] = useState('');
  const [selectedOidcScopes, setSelectedOidcScopes] = useState<string[]>(['openid', 'profile', 'email']);
  const [selectedApplicationScopes, setSelectedApplicationScopes] = useState<string[]>([]);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);
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
      setSelectedOidcScopes(['openid', 'profile', 'email']);
      setSelectedApplicationScopes([]);
      setAuthorizationUrl('');
      setTokenCommand('');
      setResourceCommands('');
    }
  }, [clientItems, selectedClientId]);

  useEffect(() => {
    if (selectedClient) {
      setSelectedOidcScopes((scopes) => scopes.filter((scope) => selectedClient.scopes.includes(scope)));
    }
    setSelectedApplicationScopes((scopes) =>
      scopes.filter((scope) => allowedApplicationScopeNames.includes(scope)),
    );
  }, [allowedApplicationScopeNames, selectedClient]);

  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsGenerating(true);
    const form = new FormData(event.currentTarget);
    const baseUrl = getApiBaseUrl();
    const clientId = selectedClient?.clientId ?? String(form.get('clientId') ?? '');
    const redirectUri = String(form.get('redirectUri') ?? '');
    const baseScopes = String(form.get('scope') ?? '').split(/[,\s]+/).filter(Boolean);
    const applicationScopes = selectedApplicationScopes;
    const scopes = [...new Set([...selectedOidcScopes, ...baseScopes, ...applicationScopes])];
    const errors = oauthDemoErrors({ client: selectedClient, clientId, redirectUri, scopes });
    if (errors.length) {
      setValidationErrors(errors);
      setAuthorizationUrl('');
      setTokenCommand('');
      setResourceCommands('');
      setIsGenerating(false);
      return;
    }
    setValidationErrors([]);
    const state = crypto.randomUUID();
    const url = new URL('/oauth2/authorize', baseUrl);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('scope', scopes.join(' '));
    url.searchParams.set('state', state);
    let codeVerifier = '';
    if (selectedClient?.requirePkce || selectedClient?.clientType === 'PUBLIC') {
      codeVerifier = randomBase64Url(48);
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(codeVerifier));
      url.searchParams.set('code_challenge', base64Url(new Uint8Array(digest)));
      url.searchParams.set('code_challenge_method', 'S256');
    }
    setAuthorizationUrl(url.toString());
    const authentication = selectedClient?.clientType === 'PUBLIC' ? '' : `-u ${shellQuote(`${clientId}:<client-secret>`)} `;
    const publicClientId = selectedClient?.clientType === 'PUBLIC' ? ` \\
  -d ${shellQuote(`client_id=${clientId}`)}` : '';
    const verifier = codeVerifier ? ` \\
  -d ${shellQuote(`code_verifier=${codeVerifier}`)}` : '';
    setTokenCommand(`curl ${authentication}-X POST ${shellQuote(`${baseUrl}/oauth2/token`)} \\
  -H 'Content-Type: application/x-www-form-urlencoded' \\
  -d 'grant_type=authorization_code' \\
  -d 'code=<authorization-code>' \\
  -d ${shellQuote(`redirect_uri=${redirectUri}`)}${publicClientId}${verifier}`);
    setResourceCommands(resourceApiCommands(PAYROLL_API_BASE_URL, applicationScopes));
    setIsGenerating(false);
  }

  function resourceApiCommands(baseUrl: string, applicationScopes: string[]) {
    const employeeStatus = applicationScopes.includes('payroll.employee.read') ? 'expected: 200' : 'expected: 403';
    const salaryReadStatus = applicationScopes.includes('payroll.salary.read') ? 'expected: 200' : 'expected: 403';
    const salaryWriteStatus = applicationScopes.includes('payroll.salary.write') ? 'expected: 200' : 'expected: 403';
    const commands: string[] = [
      `# Employees (${employeeStatus})
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  ${baseUrl}/api/payroll/employees`,
      `# Salaries read (${salaryReadStatus})
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  ${baseUrl}/api/payroll/salaries`,
      `# Salaries write (${salaryWriteStatus})
curl -X POST -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  -H "Content-Type: application/json" \\
  -d '{}' \\
  ${baseUrl}/api/payroll/salaries`,
    ];
    return commands.join('\n\n');
  }

  return (
    <>
      <PageHeader
        title="OAuth2 & OIDC Authorization Demo"
        description="Build a validated Authorization Code request, complete consent and MFA, exchange the code, and test scope enforcement without storing client secrets or tokens."
      />
      <Stepper steps={[
        { label: 'Configure request', detail: 'Choose a registered client, redirect URI, and allowed scopes.', status: authorizationUrl ? 'complete' : 'current' },
        { label: 'Authorize user', detail: 'Complete sign-in, MFA, and consent in the authorization server.', status: authorizationUrl ? 'current' : 'upcoming' },
        { label: 'Verify tokens', detail: 'Exchange the code, inspect UserInfo, and call the resource API.', status: 'upcoming' },
      ]} />
      <div className="mt-4 grid gap-4 xl:grid-cols-[440px_1fr]">
        <Card title="1. Configure authorization" description="Persisted clients are validated against their registered grant, redirects, and scopes.">
          <form onSubmit={(event) => void generate(event)} className="grid gap-3">
            <Field label="Persisted client" hint="Select a saved client to use its client ID and inspect assigned application scopes.">
              <Select
                value={selectedClientId}
                disabled={clients.isLoading || clients.isError || !selectedTenantId}
                onChange={(event) => {
                  setSelectedClientId(event.target.value);
                  setSelectedOidcScopes(['openid', 'profile', 'email']);
                  setSelectedApplicationScopes([]);
                  setAuthorizationUrl('');
                  setTokenCommand('');
                  setResourceCommands('');
                  setValidationErrors([]);
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
              <Notice title="No OAuth2 clients in this tenant" tone="warning"><Link className="font-medium underline" to="/clients">Create a client</Link> with an authorization-code template, then return to this demo.</Notice>
            )}
            <Field label="Client ID" hint="Use a persisted OAuth2 client from the selected tenant. Confidential clients need the one-time secret from create or rotation for token exchange.">
              <Input name="clientId" value={selectedClient?.clientId ?? undefined} disabled={!!selectedClient} required={!selectedClient} />
            </Field>
            {selectedClient && (
              <div className="grid gap-3 rounded-lg border border-line bg-slate-50/60 p-4 text-sm">
                <div><span className="font-semibold">Selected client:</span> {selectedClient.name} ({selectedClient.clientId})</div>
                <div><span className="font-semibold">Linked application:</span> {selectedClient.resourceServerName ?? 'None linked'}</div>
                <div><span className="font-semibold">Client scopes:</span> {selectedClient.scopes.length > 0 ? selectedClient.scopes.join(', ') : 'None configured'}</div>
                <div className="grid gap-1">
                  <span className="font-semibold">OIDC identity scopes</span>
                  {OIDC_SCOPES.map((scope) => (
                    <label key={scope} className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        value={scope}
                        checked={selectedOidcScopes.includes(scope)}
                        disabled={!selectedClient.scopes.includes(scope)}
                        onChange={(event) => {
                          setSelectedOidcScopes((current) =>
                            event.target.checked
                              ? [...current, scope]
                              : current.filter((value) => value !== scope),
                          );
                        }}
                      />
                      <span>{scope}</span>
                    </label>
                  ))}
                </div>
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
            {!selectedClient && (
              <div className="grid gap-1 rounded-md border border-line p-3 text-sm">
                <span className="font-semibold">OIDC identity scopes</span>
                {OIDC_SCOPES.map((scope) => (
                  <label key={scope} className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      value={scope}
                      checked={selectedOidcScopes.includes(scope)}
                      onChange={(event) => {
                        setSelectedOidcScopes((current) =>
                          event.target.checked
                            ? [...current, scope]
                            : current.filter((value) => value !== scope),
                        );
                      }}
                    />
                    <span>{scope}</span>
                  </label>
                ))}
              </div>
            )}
            <Field label="Other client scopes" hint="Use explicitly configured non-OIDC scopes such as iam.read. Application scopes are selected separately above.">
              <Input key={selectedClient?.id ?? 'manual-scope'} name="scope" defaultValue={selectedClient?.scopes.filter((scope) => !OIDC_SCOPES.includes(scope)).join(' ') ?? 'iam.read'} />
            </Field>
            {validationErrors.length > 0 && <Notice title="Fix the request before continuing" tone="warning"><ul className="list-disc pl-4">{validationErrors.map((error) => <li key={error}>{error}</li>)}</ul></Notice>}
            <Button type="submit" isLoading={isGenerating} loadingLabel="Generating secure request…" disabled={selectedScopesNotAllowed.length > 0}>Generate authorization request</Button>
          </form>
        </Card>
        <Card title="2. Run and verify the flow" description="Generated values remain in this page only. Replace placeholders locally when exchanging the code.">
          <ol className="grid list-decimal gap-3 pl-5 text-sm text-slate-700">
            <li>Select the IdentityForge Dev Client or another persisted client linked to Payroll API.</li>
            <li>Select OIDC identity scopes and, separately, allowed application scopes such as payroll.employee.read.</li>
            <li>Generate the authorization URL and open it in a browser.</li>
            <li>Log in as admin or a test user, then approve consent if the client requires it.</li>
            <li>Copy the authorization code from the redirect URL.</li>
            <li>Exchange the code for a token with the generated curl command.</li>
            <li>Inspect the ID Token, call UserInfo, and call the independently running Payroll resource service with the returned access token.</li>
          </ol>
          <div className="mt-4 flex flex-wrap gap-3 text-sm">
            <Link className="font-medium text-brand hover:underline" to="/clients">Manage OAuth2 clients</Link>
            <Link className="font-medium text-brand hover:underline" to="/users">Manage users</Link>
            <Link className="font-medium text-brand hover:underline" to="/audit-logs">Review audit logs</Link>
          </div>
          {authorizationUrl && (
            <div className="mt-5 grid gap-5 border-t border-line pt-5">
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4">
                <div className="text-sm font-semibold text-emerald-900">Authorization request ready</div>
                <p className="mt-1 text-xs leading-5 text-emerald-800">Opening this URL starts the real backend login, MFA, and consent journey.</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <a className="inline-flex min-h-9 items-center rounded-md bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand-dark" href={authorizationUrl} target="_blank" rel="noreferrer">Open authorization flow</a>
                  <CopyButton value={authorizationUrl} label="Copy URL" />
                </div>
              </div>
              <div>
                <div className="flex items-center justify-between gap-2"><div className="text-sm font-semibold">Token exchange command</div><CopyButton value={tokenCommand} /></div>
                <pre className="mt-2 overflow-x-auto whitespace-pre-wrap rounded-lg bg-[#111c24] p-4 text-xs leading-6 text-slate-100">{tokenCommand}</pre>
              </div>
              <div>
                <div className="text-sm font-semibold">OIDC response inspection</div>
                <p className="mt-1 text-sm text-slate-600">Decode the returned ID Token locally with a trusted JWT inspection tool. Do not log tokens or paste production tokens into third-party sites.</p>
                <pre className="mt-2 overflow-x-auto rounded-lg bg-[#111c24] p-4 text-xs text-slate-100">{`curl -H "Authorization: Bearer <ACCESS_TOKEN>" \\
  ${getApiBaseUrl()}/userinfo`}</pre>
              </div>
              {resourceCommands && (
                <div>
                  <div className="text-sm font-semibold">External Payroll service curl commands</div>
                  <pre className="mt-2 overflow-x-auto whitespace-pre-wrap rounded-lg bg-[#111c24] p-4 text-xs leading-6 text-slate-100">{resourceCommands}</pre>
                </div>
              )}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

function randomBase64Url(length: number) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array) {
  let value = '';
  bytes.forEach((byte) => { value += String.fromCharCode(byte); });
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function shellQuote(value: string) {
  return `'${value.replace(/'/g, "'\\''")}'`;
}
