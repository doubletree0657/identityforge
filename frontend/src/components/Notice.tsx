import { AlertTriangle, CheckCircle2, Info } from 'lucide-react';
import { ReactNode } from 'react';

export function Notice({ title, children, tone = 'info' }: { title: string; children?: ReactNode; tone?: 'info' | 'success' | 'warning' }) {
  const styles = tone === 'success'
    ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
    : tone === 'warning'
      ? 'border-amber-200 bg-amber-50 text-amber-900'
      : 'border-sky-200 bg-sky-50 text-sky-900';
  const Icon = tone === 'success' ? CheckCircle2 : tone === 'warning' ? AlertTriangle : Info;
  return (
    <div className={`flex items-start gap-3 rounded-lg border p-4 text-sm ${styles}`} role={tone === 'warning' ? 'alert' : 'status'}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <div><div className="font-semibold">{title}</div>{children && <div className="mt-1 opacity-90">{children}</div>}</div>
    </div>
  );
}
