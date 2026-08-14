const TOKEN_KEY = 'iam.adminConsole.token';
const TOKEN_EXPIRES_AT_KEY = 'iam.adminConsole.tokenExpiresAt';
const BASE_URL_KEY = 'iam.adminConsole.baseUrl';

export function getAccessToken(): string {
  discardLegacyPersistentToken();
  return sessionStorage.getItem(TOKEN_KEY) ?? '';
}

export function setAccessToken(token: string) {
  discardLegacyPersistentToken();
  if (token.trim()) {
    const normalized = token.trim();
    sessionStorage.setItem(TOKEN_KEY, normalized);
    const expiresAt = jwtExpiresAt(normalized);
    if (expiresAt) {
      sessionStorage.setItem(TOKEN_EXPIRES_AT_KEY, String(expiresAt));
    } else {
      sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
    }
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
  }
  window.dispatchEvent(new Event('admin-console-settings-changed'));
}

export function setAuthToken(token: string, expiresInSeconds: number) {
  discardLegacyPersistentToken();
  sessionStorage.setItem(TOKEN_KEY, token.trim());
  sessionStorage.setItem(TOKEN_EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000));
  window.dispatchEvent(new Event('admin-console-settings-changed'));
}

export function clearAccessToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
  window.dispatchEvent(new Event('admin-console-settings-changed'));
}

export function getAccessTokenExpiresAt(): number {
  discardLegacyPersistentToken();
  return Number(sessionStorage.getItem(TOKEN_EXPIRES_AT_KEY) ?? '0');
}

export function isAccessTokenExpired(): boolean {
  const token = getAccessToken();
  const expiresAt = getAccessTokenExpiresAt();
  return !token || !expiresAt || Date.now() >= expiresAt - 30_000;
}

export function getApiBaseUrl(): string {
  return localStorage.getItem(BASE_URL_KEY) ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
}

export function setApiBaseUrl(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/$/, '');
  if (normalized) {
    localStorage.setItem(BASE_URL_KEY, normalized);
  } else {
    localStorage.removeItem(BASE_URL_KEY);
  }
  window.dispatchEvent(new Event('admin-console-settings-changed'));
}

function discardLegacyPersistentToken() {
  // Access tokens used to live in localStorage and survived a browser session.
  // Never migrate them into the shorter-lived session boundary.
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
}

function jwtExpiresAt(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const claims = JSON.parse(atob(padded)) as { exp?: number };
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}
