import axios from 'axios';
import { apiRequest } from './client';
import { clearAccessToken, getApiBaseUrl, setAuthToken } from './storage';

const CLIENT_ID = 'iam-admin-console';
const REDIRECT_PATH = '/oauth2/callback';
const SCOPE = 'openid profile iam.read iam.write';
const CODE_VERIFIER_KEY = 'iam.adminConsole.pkce.codeVerifier';
const STATE_KEY = 'iam.adminConsole.pkce.state';

export interface CurrentUser {
  subject: string;
  username: string;
  userId?: string;
  tenantId?: string;
  displayName?: string;
  roles: string[];
  scopes: string[];
}

export async function startOAuthLogin() {
  const codeVerifier = randomUrlSafe(64);
  const state = randomUrlSafe(32);
  const codeChallenge = await sha256Base64Url(codeVerifier);
  sessionStorage.setItem(CODE_VERIFIER_KEY, codeVerifier);
  sessionStorage.setItem(STATE_KEY, state);

  const url = new URL('/oauth2/authorize', getApiBaseUrl());
  url.searchParams.set('response_type', 'code');
  url.searchParams.set('client_id', CLIENT_ID);
  url.searchParams.set('redirect_uri', redirectUri());
  url.searchParams.set('scope', SCOPE);
  url.searchParams.set('state', state);
  url.searchParams.set('code_challenge', codeChallenge);
  url.searchParams.set('code_challenge_method', 'S256');
  window.location.assign(url.toString());
}

export async function completeOAuthLogin(code: string, state: string) {
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const codeVerifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(CODE_VERIFIER_KEY);
  if (!expectedState || !codeVerifier || expectedState !== state) {
    throw new Error('The login response could not be verified. Start sign-in again.');
  }

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    code,
    code_verifier: codeVerifier,
  });
  const response = await axios.post<{ access_token: string; expires_in: number }>(
    new URL('/oauth2/token', getApiBaseUrl()).toString(),
    body,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
  setAuthToken(response.data.access_token, response.data.expires_in);
}

export function getCurrentUser() {
  return apiRequest<CurrentUser>('GET', '/api/me');
}

export function logout() {
  clearAccessToken();
  window.location.assign(new URL('/logout', getApiBaseUrl()).toString());
}

function redirectUri() {
  return `${window.location.origin}${REDIRECT_PATH}`;
}

function randomUrlSafe(length: number) {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

async function sha256Base64Url(value: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return base64Url(new Uint8Array(digest));
}

function base64Url(bytes: Uint8Array) {
  let value = '';
  bytes.forEach((byte) => {
    value += String.fromCharCode(byte);
  });
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
