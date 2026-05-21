import { AlertCircle, Loader2 } from 'lucide-react';
import { ApiError } from '../api/client';

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-line bg-white p-4 text-sm text-slate-600">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

export function ErrorState({ error }: { error: unknown }) {
  const apiError = error as ApiError;
  return (
    <div className="flex items-start gap-2 rounded-lg border border-[#f4c7c3] bg-[#fff5f5] p-4 text-sm text-[#8f1c13]">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <div>
        <div className="font-semibold">{apiError.code || 'Request failed'}</div>
        <div>{apiError.message || 'Something went wrong.'}</div>
      </div>
    </div>
  );
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="rounded-lg border border-dashed border-line bg-slate-50 p-6 text-center">
      <div className="font-medium text-ink">{title}</div>
      {detail && <div className="mt-1 text-sm text-slate-500">{detail}</div>}
    </div>
  );
}
