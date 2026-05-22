import { useEffect, useState } from 'react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { completeOAuthLogin } from '../api/auth';
import { ErrorState, LoadingState } from '../components/State';

export function OAuth2CallbackPage() {
  const [params] = useSearchParams();
  const [done, setDone] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      setError(new Error(params.get('error_description') || params.get('error') || 'The login response was incomplete.'));
      return;
    }
    completeOAuthLogin(code, state)
      .then(() => setDone(true))
      .catch((caught) => setError(caught instanceof Error ? caught : new Error('Login failed.')));
  }, [params]);

  if (done) {
    return <Navigate to="/" replace />;
  }
  return (
    <main className="mx-auto max-w-xl p-6">
      {error ? <ErrorState error={error} /> : <LoadingState label="Completing sign-in" />}
    </main>
  );
}
