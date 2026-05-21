import { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';

export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium text-slate-700">{label}</span>
      {children}
      {hint && <span className="text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className="min-h-10 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
      {...props}
    />
  );
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      className="min-h-24 rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
      {...props}
    />
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className="min-h-10 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
      {...props}
    />
  );
}
