import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer, transformWithEsbuild } from 'vite';

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(async () => server.close());

const flowSource = await readFile(new URL('../src/utils/demoFlows.ts', import.meta.url), 'utf8');
const flowCompiled = await transformWithEsbuild(flowSource, 'demoFlows.ts', { loader: 'ts', format: 'esm', target: 'es2020' });
const flows = await import(`data:text/javascript;base64,${Buffer.from(flowCompiled.code).toString('base64')}`);

test('shared UI states expose accessible status, recovery, and workflow semantics', async () => {
  const { EmptyState, ErrorState, LoadingState } = await server.ssrLoadModule('/src/components/State.tsx');
  const { Stepper } = await server.ssrLoadModule('/src/components/Stepper.tsx');

  const loading = renderToStaticMarkup(LoadingState({ label: 'Loading tenant users' }));
  const failed = renderToStaticMarkup(ErrorState({ error: { status: 403, message: 'Missing iam.users.read.' }, onRetry: () => undefined }));
  const empty = renderToStaticMarkup(EmptyState({ title: 'No users', detail: 'Create the first identity.' }));
  const steps = renderToStaticMarkup(Stepper({ steps: [
    { label: 'Configure', status: 'complete' },
    { label: 'Authorize', status: 'current' },
    { label: 'Verify', status: 'upcoming' },
  ] }));

  assert.match(loading, /role="status"/);
  assert.match(loading, /Loading tenant users/);
  assert.match(failed, /role="alert"/);
  assert.match(failed, /Permission required/);
  assert.match(failed, /Try again/);
  assert.match(empty, /No users/);
  assert.match(empty, /Create the first identity/);
  assert.match(steps, /aria-current="step"/);
  assert.match(steps, /Workflow progress/);
});

test('OAuth demo validation rejects unregistered redirects, grants, and scopes', () => {
  const client = {
    grantTypes: ['client_credentials'],
    redirectUris: ['https://app.example.test/callback'],
    scopes: ['openid'],
    allowedResourcePermissions: [],
  };
  const errors = flows.oauthDemoErrors({
    client,
    clientId: 'demo',
    redirectUri: 'https://evil.example.test/callback',
    scopes: ['openid', 'payroll.salary.write'],
  });

  assert.ok(errors.some((error) => error.includes('authorization_code')));
  assert.ok(errors.some((error) => error.includes('exactly match')));
  assert.ok(errors.some((error) => error.includes('payroll.salary.write')));
});

test('SCIM demo commands stay tenant-scoped, protocol-shaped, and token-safe', () => {
  const command = flows.buildScimCommand({
    baseUrl: 'http://localhost:8080/',
    tenantId: 'tenant-id',
    operation: 'add-member',
    groupId: 'group-id',
    userId: 'user-id',
  });

  assert.match(command, /\/scim\/v2\/tenant-id\/Groups\/group-id/);
  assert.match(command, /application\/scim\+json/);
  assert.match(command, /PatchOp/);
  assert.match(command, /"path":\s*"members"/);
  assert.match(command, /<ADMIN_ACCESS_TOKEN>/);
  assert.doesNotMatch(command, /eyJ[A-Za-z0-9_-]+\./);

  const hostilePath = flows.buildScimCommand({ baseUrl: 'http://localhost:8080', tenantId: 'tenant-id', operation: 'add-member', groupId: "group'; echo unsafe", userId: 'user-id' });
  assert.doesNotMatch(hostilePath, /group'; echo unsafe/);
  assert.match(hostilePath, /group%27%3B%20echo%20unsafe/);
});
