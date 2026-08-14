export type OAuthCallbackFailureKind =
  | 'browser_session_expired'
  | 'authorization_denied'
  | 'authentication_failure'
  | 'api_failure';

export interface OAuthCallbackFailure {
  kind: OAuthCallbackFailureKind;
  title: string;
  message: string;
}

export function callbackProtocolFailure(errorCode: string | null): OAuthCallbackFailure {
  if (errorCode === 'access_denied') {
    return { kind: 'authorization_denied', title: 'Authorization denied', message: 'Access was not approved. You can safely return and start sign-in again.' };
  }
  if (errorCode === 'login_required' || errorCode === 'interaction_required') {
    return { kind: 'browser_session_expired', title: 'Authorization session ended', message: 'The authorization-server browser session is no longer valid. Start a fresh sign-in.' };
  }
  return { kind: 'authentication_failure', title: 'Sign-in could not be completed', message: 'The authorization response was missing, invalid, or could not be verified.' };
}

export function callbackExchangeFailure(error: unknown): OAuthCallbackFailure {
  const status = error && typeof error === 'object' && 'response' in error
    ? (error as { response?: { status?: number } }).response?.status
    : undefined;
  if (status && status >= 500) {
    return { kind: 'api_failure', title: 'Authorization server unavailable', message: 'The server could not complete the token exchange. Retry the sign-in flow; if it persists, review the backend logs.' };
  }
  return { kind: 'authentication_failure', title: 'Sign-in could not be completed', message: error instanceof Error ? error.message : 'The token exchange failed.' };
}
