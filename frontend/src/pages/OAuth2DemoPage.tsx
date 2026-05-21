import { FormEvent, useState } from 'react';
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
            <Field label="Client ID"><Input name="clientId" required /></Field>
            <Field label="Redirect URI"><Input name="redirectUri" defaultValue="http://127.0.0.1:8080/oauth2/demo/callback" required /></Field>
            <Field label="Scopes"><Input name="scope" defaultValue="iam.read" /></Field>
            <Button type="submit">Generate</Button>
          </form>
        </Card>
        <Card title="Local demo steps">
          <ol className="grid list-decimal gap-3 pl-5 text-sm text-slate-700">
            <li>Create a tenant, active local user, and OAuth2 client with an authorization-code grant and matching redirect URI.</li>
            <li>Set the user password from the Users page or backend service path.</li>
            <li>Open the authorization URL in a browser and sign in through the backend form login.</li>
            <li>Copy the returned code from the redirect URL and exchange it at `/oauth2/token`.</li>
            <li>Paste the resulting access token into the Admin Console token panel.</li>
          </ol>
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
