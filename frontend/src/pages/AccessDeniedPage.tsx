import { ShieldX } from 'lucide-react';
import { Button } from '../components/Button';
import { logout } from '../api/auth';
import { AuthShell } from '../components/AuthShell';

export function AccessDeniedPage() {
  return (
    <AuthShell step="Access denied">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-red-50"><ShieldX className="h-6 w-6 text-[#b42318]" /></div>
        <h1 className="mt-4 text-2xl font-semibold text-ink">Access denied</h1>
        <p className="mt-2 text-sm text-slate-600">
          You signed in successfully, but this account has no recognized IAM Admin API permission. Ask a tenant or platform administrator to review your role assignment.
        </p>
        <Button className="mt-6" variant="secondary" onClick={logout}>
          Sign out and use another account
        </Button>
    </AuthShell>
  );
}
