import { isAuthenticationFailure, isAuthorizationFailure } from './authErrors';

export type AuthenticationStatus =
  | 'anonymous'
  | 'checking'
  | 'authenticated'
  | 'access_token_expired'
  | 'authorization_invalidated'
  | 'authorization_denied'
  | 'api_failure';

export interface AuthenticationSnapshot {
  hasAccessToken: boolean;
  accessTokenExpired: boolean;
  loading: boolean;
  hasUser: boolean;
  error: unknown;
}

export function resolveAuthenticationStatus(snapshot: AuthenticationSnapshot): AuthenticationStatus {
  if (!snapshot.hasAccessToken) return 'anonymous';
  if (snapshot.accessTokenExpired) return 'access_token_expired';
  if (isAuthenticationFailure(snapshot.error)) return 'authorization_invalidated';
  if (isAuthorizationFailure(snapshot.error)) return 'authorization_denied';
  if (snapshot.error) return 'api_failure';
  if (snapshot.hasUser) return 'authenticated';
  if (snapshot.loading) return 'checking';
  return 'checking';
}
