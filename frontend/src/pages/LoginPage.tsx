import { ArrowRight, KeyRound } from 'lucide-react';
import { useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { startOAuthLogin } from '../api/auth';
import { Button } from '../components/Button';
import { AuthShell } from '../components/AuthShell';
import { Notice } from '../components/Notice';

export function LoginPage() {
  const location = useLocation();
  const [params] = useSearchParams();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const reason = (location.state as { reason?: 'access_token_expired' | 'authorization_invalidated' } | null)?.reason;
  const loggedOut = params.get('loggedOut') === 'true';

  async function signIn() {
    setPending(true);
    setError('');
    try {
      await startOAuthLogin();
    } catch {
      setPending(false);
      setError('Sign-in could not be started. Check the configured API URL and try again.');
    }
  }

  return (
    <AuthShell step="Sign in">
      <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand/10 text-brand"><KeyRound className="h-5 w-5" /></div>
      <div className="mt-5 text-xs font-semibold uppercase tracking-[0.16em] text-brand">Secure administration</div>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight text-ink">Sign in to the Admin Console</h1>
      <p className="mt-3 text-sm leading-6 text-slate-600">Continue to the IdentityForge authorization server to enter your realm-qualified account and complete MFA when required.</p>
      {(loggedOut || reason) && <div className="mt-5"><Notice title={loggedOut ? 'Signed out securely' : reason === 'access_token_expired' ? 'Access token expired' : 'Authorization ended'} tone={loggedOut ? 'success' : 'warning'}>{loggedOut ? 'Your browser session and Admin Console grant have ended.' : reason === 'access_token_expired' ? 'Your short-lived console access token reached its expiry. Sign in to obtain a new grant.' : 'The server rejected or invalidated the saved console authorization. Sign in again to continue.'}</Notice></div>}
      {error && <p className="mt-5 rounded-lg border border-red-200 bg-red-50 p-3 text-sm font-medium text-[#b42318]" role="alert">{error}</p>}
      <Button className="mt-7 w-full py-3" icon={<ArrowRight className="h-4 w-4" />} isLoading={pending} loadingLabel="Opening secure sign-in…" onClick={() => void signIn()}>Continue to sign in</Button>
      <p className="mt-5 border-t border-line pt-5 text-xs leading-5 text-slate-500">For the local demo, use the development realm credentials documented in the project walkthrough. This portfolio system is not presented as a production IAM replacement.</p>
    </AuthShell>
  );
}
