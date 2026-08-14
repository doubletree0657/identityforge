import { AlertCircle, Inbox, Loader2, RefreshCw } from 'lucide-react';
import { ApiError } from '../api/client';
import { ReactNode } from 'react';
import { Button } from './Button';

export function LoadingState({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-24 items-center justify-center gap-3 rounded-lg border border-line bg-slate-50/70 p-5 text-sm text-slate-600" role="status" aria-live="polite">
      <Loader2 className="h-4 w-4 animate-spin" />
      {label}
    </div>
  );
}

export function ErrorState({ error, onRetry, title }: { error: unknown; onRetry?: () => void; title?: string }) {
  const apiError = error as ApiError;
  const message = apiError?.message || 'Something went wrong. Try again, or check that the backend is available.';
  return (
    <div className="flex items-start gap-3 rounded-lg border border-[#f4c7c3] bg-[#fff7f6] p-4 text-sm text-[#8f1c13]" role="alert">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="min-w-0 flex-1">
        <div className="font-semibold">{title || friendlyErrorTitle(apiError)}</div>
        <div className="mt-0.5 text-[#9f2d24]">{message}</div>
        {onRetry && <Button className="mt-3" type="button" variant="secondary" icon={<RefreshCw className="h-4 w-4" />} onClick={onRetry}>Try again</Button>}
      </div>
    </div>
  );
}

export function EmptyState({ title, detail, action, icon }: { title: string; detail?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="rounded-lg border border-dashed border-line bg-slate-50/80 p-8 text-center">
      <div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-white text-slate-400 shadow-sm">{icon ?? <Inbox className="h-5 w-5" />}</div>
      <div className="font-medium text-ink">{title}</div>
      {detail && <div className="mt-1 text-sm text-slate-500">{detail}</div>}
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}

function friendlyErrorTitle(error: ApiError | undefined) {
  if (error?.status === 403) return 'Permission required';
  if (error?.status === 401) return 'Session authorization failed';
  if (error?.status === 404) return 'Resource not found';
  if (error?.status === 409) return 'Change conflicts with existing data';
  if (error?.status === 422 || error?.status === 400) return 'Review the highlighted input';
  return error?.code && error.code !== 'api_error' ? error.code.replace(/_/g, ' ') : 'Request failed';
}
