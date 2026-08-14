import { useEffect, useRef, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { clearOAuthLoginAttempt, completeOAuthLogin, startOAuthLogin } from '../api/auth';
import { Button } from '../components/Button';
import { AuthShell } from '../components/AuthShell';
import { LoadingState } from '../components/State';
import { callbackExchangeFailure, callbackProtocolFailure, OAuthCallbackFailure } from '../utils/oauthCallback';

export function OAuth2CallbackPage() {
  const [params] = useSearchParams();
  const hasStartedExchange = useRef(false);
  const [done, setDone] = useState(false);
  const [failure, setFailure] = useState<OAuthCallbackFailure | null>(null);

  useEffect(() => {
    if (hasStartedExchange.current) {
      return;
    }
    hasStartedExchange.current = true;
    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      setFailure(callbackProtocolFailure(params.get('error')));
      clearOAuthLoginAttempt();
      return;
    }
    completeOAuthLogin(code, state)
      .then(() => setDone(true))
      .catch((caught) => {
        clearOAuthLoginAttempt();
        setFailure(callbackExchangeFailure(caught));
      });
  }, [params]);

  if (done) {
    return <Navigate to="/" replace />;
  }
  return (
    <AuthShell step="Completing">
        <div className="mb-5 text-xs font-semibold uppercase tracking-[0.16em] text-brand">Secure sign-in</div>
      {failure ? (
        <div className="grid gap-4">
          <div className="rounded-xl border border-red-200 bg-red-50 p-4" role="alert">
            <h1 className="text-base font-semibold text-[#8f1c13]">{failure.title}</h1>
            <p className="mt-1 text-sm leading-6 text-[#9f2419]">{failure.message}</p>
          </div>
          <Button className="w-fit" onClick={() => void startOAuthLogin()}>
            Restart sign-in
          </Button>
        </div>
      ) : <LoadingState label="Verifying authorization response and completing sign-in" />}
    </AuthShell>
  );
}
