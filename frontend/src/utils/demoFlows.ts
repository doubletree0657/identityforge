import type { ClientResponse } from '../types/api';

export interface OAuthDemoInput {
  client?: ClientResponse;
  clientId: string;
  redirectUri: string;
  scopes: string[];
}

export function oauthDemoErrors(input: OAuthDemoInput): string[] {
  const errors: string[] = [];
  if (!input.clientId.trim()) errors.push('Choose a persisted client or enter a client ID.');
  try {
    const url = new URL(input.redirectUri);
    if (!['http:', 'https:'].includes(url.protocol)) errors.push('Redirect URI must use HTTP or HTTPS.');
  } catch {
    errors.push('Enter a valid absolute redirect URI.');
  }
  if (input.client && !input.client.grantTypes.includes('authorization_code')) {
    errors.push('The selected client does not allow the authorization_code grant.');
  }
  if (input.client && !input.client.redirectUris.includes(input.redirectUri)) {
    errors.push('Redirect URI must exactly match one registered on the selected client.');
  }
  const configuredScopes = new Set([
    ...(input.client?.scopes ?? []),
    ...(input.client?.allowedResourcePermissions.map((permission) => permission.name) ?? []),
  ]);
  const unsupported = input.client ? input.scopes.filter((scope) => !configuredScopes.has(scope)) : [];
  if (unsupported.length) errors.push(`The client is not configured for: ${unsupported.join(', ')}.`);
  if (!input.scopes.length) errors.push('Select at least one scope.');
  return errors;
}

export type ScimDemoOperation = 'discover' | 'list-users' | 'create-user' | 'patch-user' | 'create-group' | 'add-member';

export interface ScimDemoInput {
  baseUrl: string;
  tenantId: string;
  operation: ScimDemoOperation;
  userName?: string;
  displayName?: string;
  email?: string;
  userId?: string;
  groupId?: string;
  groupName?: string;
}

export function buildScimCommand(input: ScimDemoInput): string {
  const base = `${input.baseUrl.replace(/\/$/, '')}/scim/v2/${encodeURIComponent(input.tenantId)}`;
  const headers = `-H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>' \\
  -H 'Accept: application/scim+json'`;
  const writeHeaders = `${headers} \\
  -H 'Content-Type: application/scim+json'`;
  switch (input.operation) {
    case 'discover':
      return `curl --fail-with-body ${headers} \\
  ${shellQuote(`${base}/ServiceProviderConfig`)}`;
    case 'list-users': {
      const filter = input.userName?.trim() ? `?filter=${encodeURIComponent(`userName eq ${JSON.stringify(input.userName.trim())}`)}` : '?startIndex=1&count=25';
      return `curl --fail-with-body ${headers} \\
  ${shellQuote(`${base}/Users${filter}`)}`;
    }
    case 'create-user': {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: input.userName?.trim(),
        displayName: input.displayName?.trim(),
        active: true,
        ...(input.email?.trim() ? { emails: [{ value: input.email.trim(), primary: true }] } : {}),
      };
      return writeCommand('POST', `${base}/Users`, body, writeHeaders);
    }
    case 'patch-user': {
      const body = { schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'], Operations: [{ op: 'replace', path: 'active', value: false }] };
      return writeCommand('PATCH', `${base}/Users/${pathValue(input.userId, '<USER_ID>')}`, body, writeHeaders);
    }
    case 'create-group': {
      const body = { schemas: ['urn:ietf:params:scim:schemas:core:2.0:Group'], displayName: input.groupName?.trim(), members: [] };
      return writeCommand('POST', `${base}/Groups`, body, writeHeaders);
    }
    case 'add-member': {
      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'add', path: 'members', value: [{ value: input.userId?.trim() || '<USER_ID>', type: 'User' }] }],
      };
      return writeCommand('PATCH', `${base}/Groups/${pathValue(input.groupId, '<GROUP_ID>')}`, body, writeHeaders);
    }
  }
}

export function scimInputError(input: ScimDemoInput): string | undefined {
  if (!input.tenantId) return 'Select a tenant before generating a tenant-scoped SCIM request.';
  if (input.operation === 'create-user' && !input.userName?.trim()) return 'Username is required to create a SCIM user.';
  if (input.operation === 'create-group' && !input.groupName?.trim()) return 'Display name is required to create a SCIM group.';
  return undefined;
}

function writeCommand(method: string, url: string, body: object, headers: string) {
  return `curl --fail-with-body -X ${method} ${headers} \\
  --data ${shellQuote(JSON.stringify(body, null, 2))} \\
  ${shellQuote(url)}`;
}

function pathValue(value: string | undefined, placeholder: string) {
  return value?.trim()
    ? encodeURIComponent(value.trim()).replace(/[!'()*]/g, (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`)
    : placeholder;
}

function shellQuote(value: string) {
  return `'${value.replace(/'/g, "'\\''")}'`;
}
