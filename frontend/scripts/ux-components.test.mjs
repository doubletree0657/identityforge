import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { createServer, transformWithEsbuild } from 'vite';

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'silent' });
after(async () => server.close());

const flowSource = await readFile(new URL('../src/utils/demoFlows.ts', import.meta.url), 'utf8');
const flowCompiled = await transformWithEsbuild(flowSource, 'demoFlows.ts', { loader: 'ts', format: 'esm', target: 'es2020' });
const flows = await import(`data:text/javascript;base64,${Buffer.from(flowCompiled.code).toString('base64')}`);
const authErrorsSource = await readFile(new URL('../src/utils/authErrors.ts', import.meta.url), 'utf8');
const authErrorsCompiled = await transformWithEsbuild(authErrorsSource, 'authErrors.ts', { loader: 'ts', format: 'esm', target: 'es2020' });
const authErrors = await import(`data:text/javascript;base64,${Buffer.from(authErrorsCompiled.code).toString('base64')}`);
const authState = await server.ssrLoadModule('/src/utils/authState.ts');

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

test('only 401 responses are classified as invalidated authentication', () => {
  assert.equal(authErrors.isAuthenticationFailure({ status: 401, code: 'unauthorized' }), true);
  assert.equal(authErrors.isAuthenticationFailure({ status: 500, code: 'server_error' }), false);
  assert.equal(authErrors.isAuthenticationFailure({ status: 403, code: 'forbidden' }), false);
  assert.equal(authErrors.isAuthenticationFailure(new Error('Network unavailable')), false);
});

test('authentication lifecycle keeps anonymous, expiry, denial, and API failure distinct', () => {
  const resolve = (overrides = {}) => authState.resolveAuthenticationStatus({
    hasAccessToken: false,
    accessTokenExpired: false,
    loading: false,
    hasUser: false,
    error: null,
    ...overrides,
  });
  assert.equal(resolve(), 'anonymous');
  assert.equal(resolve({ hasAccessToken: true, accessTokenExpired: true }), 'access_token_expired');
  assert.equal(resolve({ hasAccessToken: true, error: { status: 401 } }), 'authorization_invalidated');
  assert.equal(resolve({ hasAccessToken: true, hasUser: true, error: { status: 401 } }), 'authorization_invalidated');
  assert.equal(resolve({ hasAccessToken: true, error: { status: 403 } }), 'authorization_denied');
  assert.equal(resolve({ hasAccessToken: true, error: { status: 500 } }), 'api_failure');
  assert.equal(resolve({ hasAccessToken: true, hasUser: true }), 'authenticated');
});

test('console access tokens are bounded to the browser session and legacy persistent tokens are discarded', async () => {
  const memoryStorage = () => {
    const values = new Map();
    return {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, String(value)),
      removeItem: (key) => values.delete(key),
    };
  };
  globalThis.localStorage = memoryStorage();
  globalThis.sessionStorage = memoryStorage();
  globalThis.window = { dispatchEvent: () => true };
  localStorage.setItem('iam.adminConsole.token', 'legacy-token');
  const storage = await server.ssrLoadModule('/src/api/storage.ts');

  storage.setAuthToken('session-token', 600);

  assert.equal(sessionStorage.getItem('iam.adminConsole.token'), 'session-token');
  assert.equal(localStorage.getItem('iam.adminConsole.token'), null);
  assert.equal(storage.getAccessToken(), 'session-token');
});

test('Admin Console bootstrap renders server failures instead of reporting session expiry', async () => {
  const { AuthGateView } = await server.ssrLoadModule('/src/layout/AuthGate.tsx');
  const markup = renderToStaticMarkup(AuthGateView({
    children: 'Dashboard content',
    auth: {
      status: 'api_failure',
      isLoading: false,
      isAuthenticated: false,
      isAdmin: false,
      hasPermission: () => false,
      error: { status: 500, code: 'server_error', message: 'The server could not complete this request.' },
      retry: () => undefined,
    },
  }));

  assert.match(markup, /server could not complete this request/i);
  assert.match(markup, /Try again/);
  assert.doesNotMatch(markup, /Session expired/);
});

test('authenticated administrators pass the gate and reach dashboard content', async () => {
  const { AuthGateView } = await server.ssrLoadModule('/src/layout/AuthGate.tsx');
  const markup = renderToStaticMarkup(AuthGateView({
    children: 'Admin Console dashboard',
    auth: {
      status: 'authenticated',
      isLoading: false,
      isAuthenticated: true,
      isAdmin: true,
      hasPermission: () => true,
      error: null,
      retry: () => undefined,
    },
  }));

  assert.match(markup, /Admin Console dashboard/);
});

test('fresh anonymous visits route to sign in without an expiry reason', async () => {
  const { LoginPage } = await server.ssrLoadModule('/src/pages/LoginPage.tsx');
  const markup = renderToStaticMarkup(createElement(
    MemoryRouter,
    { initialEntries: ['/login'] },
    createElement(LoginPage),
  ));

  assert.doesNotMatch(markup, /Session expired|Access token expired|Authorization ended/);
  assert.match(markup, /Sign in to the Admin Console/);
});
