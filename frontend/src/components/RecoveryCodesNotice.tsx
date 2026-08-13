import { useState } from 'react';
import { Button } from './Button';

export function RecoveryCodesNotice({ codes }: { codes: string[] }) {
  const [copied, setCopied] = useState(false);
  const text = codes.join('\n');

  async function copyCodes() {
    await navigator.clipboard.writeText(text);
    setCopied(true);
  }

  function downloadCodes() {
    const blob = new Blob([
      'IdentityForge MFA recovery codes\n',
      'Each code works once. Store these securely.\n\n',
      text,
      '\n',
    ], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'identityforge-recovery-codes.txt';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="rounded-lg border border-[#f6d58a] bg-[#fff8e6] p-4 text-[#6b4e00]">
      <div className="font-semibold">Save your recovery codes now</div>
      <p className="mt-1 text-sm">
        Each code signs you in once if your authenticator is unavailable. They will not be shown again; regenerating replaces every previous code.
      </p>
      <div className="mt-3 grid grid-cols-1 gap-2 rounded-md bg-white p-3 font-mono text-sm text-slate-900 sm:grid-cols-2">
        {codes.map((code) => <div key={code}>{code}</div>)}
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button type="button" variant="secondary" onClick={() => void copyCodes()}>{copied ? 'Copied' : 'Copy codes'}</Button>
        <Button type="button" variant="secondary" onClick={downloadCodes}>Download .txt</Button>
      </div>
      <p className="mt-3 text-xs">Prefer an encrypted password manager or an offline copy. Do not store these beside your password.</p>
    </div>
  );
}
