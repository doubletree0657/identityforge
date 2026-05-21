import { ReactNode } from 'react';

export function Card({ title, action, children }: { title?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-line bg-white p-4 shadow-panel">
      {(title || action) && (
        <div className="mb-4 flex items-center justify-between gap-3">
          {title && <h2 className="text-base font-semibold text-ink">{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}
