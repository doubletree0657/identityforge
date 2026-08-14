import { LockKeyhole, ShieldCheck } from 'lucide-react';
import { ReactNode } from 'react';

export function AuthShell({ children, step }: { children: ReactNode; step?: string }) {
  return (
    <main className="min-h-screen bg-[#edf3f2] px-5 py-8 sm:py-12">
      <div className="mx-auto w-full max-w-[520px]">
        <header className="mb-6 flex items-center justify-between px-1">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand text-white shadow-sm"><ShieldCheck className="h-5 w-5" /></div>
            <div><div className="text-sm font-semibold tracking-tight text-ink">IdentityForge</div><div className="text-xs text-slate-500">Admin Console</div></div>
          </div>
          {step && <span className="rounded-full border border-line bg-white px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500">{step}</span>}
        </header>
        <section className="rounded-2xl border border-line bg-white p-7 shadow-panel sm:p-9">
          {children}
        </section>
        <footer className="mt-5 flex items-start justify-center gap-2 px-4 text-center text-xs leading-5 text-slate-500">
          <LockKeyhole className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          <span>Credentials stay on the IdentityForge authorization server. The console uses Authorization Code with PKCE.</span>
        </footer>
      </div>
    </main>
  );
}
