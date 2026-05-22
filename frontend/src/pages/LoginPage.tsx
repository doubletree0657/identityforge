import { LogIn } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { Button } from '../components/Button';
import { startOAuthLogin } from '../api/auth';

export function LoginPage() {
  const location = useLocation();
  const expired = Boolean((location.state as { expired?: boolean } | null)?.expired);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f5f7fb] px-4">
      <section className="w-full max-w-md rounded-lg border border-line bg-white p-6 shadow-sm">
        <div className="text-sm font-semibold text-brand">International IAM Platform</div>
        <h1 className="mt-2 text-2xl font-semibold text-ink">Admin Console</h1>
        <p className="mt-2 text-sm text-slate-600">
          {expired ? 'Your session has expired. Sign in again to continue.' : 'Sign in through the backend authorization server.'}
        </p>
        <Button className="mt-6 w-full" icon={<LogIn className="h-4 w-4" />} onClick={() => void startOAuthLogin()}>
          Sign in with International IAM
        </Button>
      </section>
    </main>
  );
}
