export function SecretNotice({ title, secret }: { title: string; secret: string }) {
  return (
    <div className="rounded-lg border border-[#f6d58a] bg-[#fff8e6] p-4 text-sm text-[#6b4e00]">
      <div className="font-semibold">{title}</div>
      <div className="mt-2 break-all rounded-md bg-white px-3 py-2 font-mono text-slate-900">{secret}</div>
      <div className="mt-2">This value is shown once. Store it somewhere appropriate before leaving this page.</div>
    </div>
  );
}
