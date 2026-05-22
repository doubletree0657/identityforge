import { useEffect, useRef, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { clearOAuthLoginAttempt, completeOAuthLogin, startOAuthLogin } from '../api/auth';
import { Button } from '../components/Button';
import { ErrorState, LoadingState } from '../components/State';

export function OAuth2CallbackPage() {
  const [params] = useSearchParams();
  const hasStartedExchange = useRef(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (hasStartedExchange.current) {
      return;
    }
    hasStartedExchange.current = true;
    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      setError(new Error(params.get('error_description') || params.get('error') || 'The login response was incomplete.'));
      clearOAuthLoginAttempt();
      return;
    }
    completeOAuthLogin(code, state)
      .then(() => setDone(true))
      .catch((caught) => {
        clearOAuthLoginAttempt();
        setError(caught instanceof Error ? caught : new Error('Login failed.'));
      });
  }, [params]);

  if (done) {
    return <Navigate to="/" replace />;
  }
  return (
    <main className="mx-auto max-w-xl p-6">
      {error ? (
        <div className="grid gap-4">
          <ErrorState error={error} />
          <Button className="w-fit" onClick={() => void startOAuthLogin()}>
            Restart sign-in
          </Button>
        </div>
      ) : <LoadingState label="Completing sign-in" />}
    </main>
  );
}
