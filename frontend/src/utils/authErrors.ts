export function isAuthenticationFailure(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const candidate = error as { status?: number; code?: string };
  return candidate.status === 401 || candidate.code === 'unauthorized';
}
