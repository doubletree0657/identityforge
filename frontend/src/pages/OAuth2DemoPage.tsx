import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { getApiBaseUrl } from '../api/storage';
import { PageHeader } from './PageHeader';

export function OAuth2DemoPage() {
  const [authorizationUrl, setAuthorizationUrl] = useState('');
  const [tokenCommand, setTokenCommand] = useState('');

  function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const baseUrl = getApiBaseUrl();
    const clientId = String(form.get('clientId') ?? '');
    const redirectUri = String(form.get('redirectUri') ?? '');
    const scope = String(form.get('scope') ?? 'iam.read');
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
            <Field label="Client ID" hint="Use a persisted OAuth2 client from the selected tenant. Confidential clients need the one-time secret from create or rotation for token exchange.">
              <Input name="clientId" required />
            </Field>
            <Field label="Redirect URI" hint="Must exactly match one of the persisted redirect URIs on the client.">
              <Input name="redirectUri" defaultValue="http://127.0.0.1:8080/oauth2/demo/callback" required />
            </Field>
            <Field label="Scopes" hint="Request scopes granted to the client, such as iam.read or iam.write. Consent is shown when the client requires it.">
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
