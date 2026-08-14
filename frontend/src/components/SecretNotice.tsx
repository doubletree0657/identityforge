import { CopyButton } from './CopyButton';

export function SecretNotice({ title, secret }: { title: string; secret: string }) {
  return (
    <div className="rounded-lg border border-[#f6d58a] bg-[#fff8e6] p-4 text-sm text-[#6b4e00]">
      <div className="font-semibold">{title}</div>
      <div className="mt-2 flex items-start gap-2 rounded-md bg-white p-2">
        <div className="min-w-0 flex-1 break-all px-1 py-2 font-mono text-slate-900">{secret}</div>
        <CopyButton value={secret} label="Copy" />
      </div>
      <div className="mt-2">This value is shown once. Store it somewhere appropriate before leaving this page.</div>
    </div>
  );
}
