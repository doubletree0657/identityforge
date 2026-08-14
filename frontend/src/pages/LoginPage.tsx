import { ArrowRight, CheckCircle2, LockKeyhole, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { startOAuthLogin } from '../api/auth';
import { Button } from '../components/Button';
import { Notice } from '../components/Notice';

export function LoginPage() {
  const location = useLocation();
  const [params] = useSearchParams();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const expired = Boolean((location.state as { expired?: boolean } | null)?.expired);
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
    <main className="grid min-h-screen bg-[#edf3f2] lg:grid-cols-[minmax(380px,0.9fr)_minmax(520px,1.1fr)]">
      <section className="hidden overflow-hidden bg-[#16242d] p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="text-lg font-semibold tracking-tight">IdentityForge</div>
        <div className="max-w-xl">
          <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-400/15 text-emerald-300"><ShieldCheck className="h-7 w-7" /></div>
          <h1 className="text-4xl font-semibold leading-tight tracking-tight">Identity administration, made visible.</h1>
          <p className="mt-4 text-base leading-7 text-slate-300">Explore tenant isolation, effective RBAC, OAuth2 and OIDC, TOTP recovery, SCIM provisioning, and security audit trails in one coherent demonstration.</p>
          <div className="mt-8 grid gap-3 text-sm text-slate-300">
            {['Authorization Code with PKCE', 'Backend-enforced tenant RBAC', 'One-time credential boundaries'].map((item) => <div key={item} className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-emerald-300" />{item}</div>)}
          </div>
        </div>
        <p className="text-xs text-slate-500">Portfolio demonstration · not a production IAM replacement</p>
      </section>
      <section className="flex items-center justify-center px-5 py-12">
        <div className="w-full max-w-md">
          <div className="mb-7 flex items-center gap-3 lg:hidden"><div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand text-white"><LockKeyhole className="h-5 w-5" /></div><div className="font-semibold text-ink">IdentityForge</div></div>
          <div className="rounded-2xl border border-white/80 bg-white p-7 shadow-elevated sm:p-9">
            <div className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">Admin Console</div>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-ink">Welcome back</h2>
            <p className="mt-3 text-sm leading-6 text-slate-600">Continue through the IdentityForge authorization server. Password and MFA credentials never enter this console.</p>
            {(loggedOut || expired) && <div className="mt-5"><Notice title={loggedOut ? 'Signed out securely' : 'Session expired'} tone={loggedOut ? 'success' : 'warning'}>{loggedOut ? 'Your browser session and Admin Console grant have ended.' : 'Sign in again to continue without losing server-side data.'}</Notice></div>}
            {error && <p className="mt-5 text-sm font-medium text-[#b42318]" role="alert">{error}</p>}
            <Button className="mt-7 w-full py-3" icon={<ArrowRight className="h-4 w-4" />} isLoading={pending} loadingLabel="Redirecting securely…" onClick={() => void signIn()}>Continue to sign in</Button>
            <div className="mt-5 flex items-start gap-2 border-t border-line pt-5 text-xs leading-5 text-slate-500"><LockKeyhole className="mt-0.5 h-3.5 w-3.5 shrink-0" />Uses OAuth2 Authorization Code with PKCE and a short-lived access token stored only for this browser session.</div>
          </div>
        </div>
      </section>
    </main>
  );
}
