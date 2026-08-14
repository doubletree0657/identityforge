const tones: Record<string, string> = {
  ACTIVE: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  SUCCESS: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  VERIFIED: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  PENDING: 'border-amber-200 bg-amber-50 text-amber-800',
  'SETUP PENDING': 'border-amber-200 bg-amber-50 text-amber-800',
  SUSPENDED: 'border-amber-200 bg-amber-50 text-amber-800',
  LOCKED: 'border-red-200 bg-red-50 text-red-800',
  DISABLED: 'border-slate-300 bg-slate-100 text-slate-700',
  ARCHIVED: 'border-slate-300 bg-slate-100 text-slate-700',
  FAILURE: 'border-red-200 bg-red-50 text-red-800',
  'INVALID CODE': 'border-red-200 bg-red-50 text-red-800',
  'MFA ACTIVE': 'border-emerald-200 bg-emerald-50 text-emerald-800',
};

export function Badge({ children, tone }: { children: string; tone?: 'neutral' | 'success' | 'warning' | 'danger' }) {
  const explicit = tone === 'success'
    ? tones.ACTIVE
    : tone === 'warning'
      ? tones.PENDING
      : tone === 'danger'
        ? tones.FAILURE
        : undefined;
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${explicit ?? tones[children] ?? 'border-line bg-slate-50 text-slate-700'}`}>
      {children}
    </span>
  );
}
