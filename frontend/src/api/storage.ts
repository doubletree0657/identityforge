const TOKEN_KEY = 'iam.adminConsole.token';
const BASE_URL_KEY = 'iam.adminConsole.baseUrl';

export function getAccessToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? '';
}

export function setAccessToken(token: string) {
  if (token.trim()) {
    localStorage.setItem(TOKEN_KEY, token.trim());
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
  window.dispatchEvent(new Event('admin-console-settings-changed'));
}

export function clearAccessToken() {
  localStorage.removeItem(TOKEN_KEY);
  window.dispatchEvent(new Event('admin-console-settings-changed'));
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
