import { ShieldX } from 'lucide-react';
import { Button } from '../components/Button';
import { logout } from '../api/auth';

export function AccessDeniedPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f5f7fb] px-4">
      <section className="w-full max-w-md rounded-lg border border-line bg-white p-6 shadow-sm">
        <ShieldX className="h-8 w-8 text-[#b42318]" />
        <h1 className="mt-4 text-2xl font-semibold text-ink">Access denied</h1>
        <p className="mt-2 text-sm text-slate-600">This account does not have an Admin Console role.</p>
        <Button className="mt-6" variant="secondary" onClick={logout}>
          Logout
        </Button>
      </section>
    </main>
  );
}
