import { AlertTriangle, X } from 'lucide-react';
import { useEffect } from 'react';
import { Button } from './Button';

export function ConfirmDialog({
  open,
  title,
  detail,
  confirmLabel,
  isPending = false,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  detail: string;
  confirmLabel: string;
  isPending?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape' && !isPending) onCancel(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isPending, onCancel, open]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/55 p-4 backdrop-blur-sm" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !isPending) onCancel(); }}>
      <section className="w-full max-w-md rounded-xl border border-line bg-white p-6 shadow-elevated" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-detail">
        <div className="flex items-start justify-between gap-4">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-red-50 text-[#b42318]"><AlertTriangle className="h-5 w-5" /></div>
          <button type="button" className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100" onClick={onCancel} disabled={isPending} aria-label="Close confirmation"><X className="h-5 w-5" /></button>
        </div>
        <h2 id="confirm-title" className="mt-4 text-lg font-semibold text-ink">{title}</h2>
        <p id="confirm-detail" className="mt-2 text-sm leading-6 text-slate-600">{detail}</p>
        <div className="mt-6 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onCancel} disabled={isPending}>Cancel</Button>
          <Button type="button" variant="danger" onClick={onConfirm} isLoading={isPending} loadingLabel="Applying…">{confirmLabel}</Button>
        </div>
      </section>
    </div>
  );
}
