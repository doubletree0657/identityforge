import { FormEvent, useEffect, useState } from 'react';
import { Save, Trash2 } from 'lucide-react';
import { Button } from '../components/Button';
import { Field, Input } from '../components/Form';
import { clearAccessToken, getAccessToken, getApiBaseUrl, setAccessToken, setApiBaseUrl } from '../api/storage';

export function TokenPanel() {
  const [token, setToken] = useState(getAccessToken());
  const [baseUrl, setBaseUrl] = useState(getApiBaseUrl());

  useEffect(() => {
    const listener = () => {
      setToken(getAccessToken());
      setBaseUrl(getApiBaseUrl());
    };
    window.addEventListener('admin-console-settings-changed', listener);
    return () => window.removeEventListener('admin-console-settings-changed', listener);
  }, []);

  function submit(event: FormEvent) {
    event.preventDefault();
    setAccessToken(token);
    setApiBaseUrl(baseUrl);
  }

  return (
    <form onSubmit={submit} className="grid gap-2 rounded-lg border border-line bg-slate-50 p-3 xl:grid-cols-[220px_minmax(280px,520px)_auto]">
      <Field label="API base URL">
        <Input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} placeholder="http://localhost:8080" />
      </Field>
      <Field label="Development bearer token">
        <Input
          type="password"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder="Paste an iam.read / iam.write access token"
          autoComplete="off"
        />
      </Field>
      <div className="flex items-end gap-2">
        <Button type="submit" icon={<Save className="h-4 w-4" />}>Save</Button>
        <Button
          type="button"
          variant="secondary"
          icon={<Trash2 className="h-4 w-4" />}
          onClick={() => {
            setToken('');
            clearAccessToken();
          }}
        >
          Clear
        </Button>
      </div>
    </form>
  );
}
