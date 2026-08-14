import { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes, useId } from 'react';

export function Field({
  label,
  children,
  hint,
  error,
  required,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
  error?: string;
  required?: boolean;
}) {
  const hintId = useId();
  return (
    <label className="grid gap-1 text-sm">
      <span className="font-medium text-slate-700">{label}{required && <span className="ml-1 text-[#b42318]" aria-hidden="true">*</span>}</span>
      <span aria-describedby={hint || error ? hintId : undefined} className="contents">{children}</span>
      {(error || hint) && <span id={hintId} className={`text-xs ${error ? 'font-medium text-[#b42318]' : 'text-slate-500'}`}>{error || hint}</span>}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  const { className = '', ...inputProps } = props;
  return (
    <input
      className={`min-h-10 rounded-md border border-line bg-white px-3 text-sm text-ink shadow-sm outline-none transition placeholder:text-slate-400 invalid:border-[#dc8b84] focus:border-brand focus:ring-2 focus:ring-brand/20 ${className}`}
      {...inputProps}
    />
  );
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const { className = '', ...textareaProps } = props;
  return (
    <textarea
      className={`min-h-24 rounded-md border border-line bg-white px-3 py-2 text-sm text-ink shadow-sm outline-none transition placeholder:text-slate-400 invalid:border-[#dc8b84] focus:border-brand focus:ring-2 focus:ring-brand/20 ${className}`}
      {...textareaProps}
    />
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  const { className = '', ...selectProps } = props;
  return (
    <select
      className={`min-h-10 rounded-md border border-line bg-white px-3 text-sm text-ink shadow-sm outline-none transition invalid:border-[#dc8b84] focus:border-brand focus:ring-2 focus:ring-brand/20 ${className}`}
      {...selectProps}
    />
  );
}
