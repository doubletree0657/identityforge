export function isAuthenticationFailure(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const candidate = error as { status?: number; code?: string };
  return candidate.status === 401 || candidate.code === 'unauthorized';
}

export function isAuthorizationFailure(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && 'status' in error && error.status === 403);
}
