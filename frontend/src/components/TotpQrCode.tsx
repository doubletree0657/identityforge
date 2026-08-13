import { useEffect, useRef, useState } from 'react';
import { drawQrCode } from '../utils/qrCode';

export function TotpQrCode({ uri }: { uri: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    try {
      if (canvas.current) drawQrCode(canvas.current, uri);
      setError('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not render QR code');
    }
  }, [uri]);

  return (
    <div className="rounded-lg border border-line bg-white p-4">
      <div className="font-semibold text-ink">Scan with your authenticator app</div>
      <p className="mt-1 text-sm text-slate-600">The QR code contains the one-time setup URI. The manual setup key is available below.</p>
      <canvas ref={canvas} className="mt-3 h-auto max-w-full" role="img" aria-label="TOTP enrollment QR code" />
      {error && <p className="mt-2 text-sm text-red-700">{error}. Enter the setup key manually instead.</p>}
    </div>
  );
}
