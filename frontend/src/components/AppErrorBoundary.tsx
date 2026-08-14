import { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from './Button';

interface State { error?: Error }

export class AppErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = {};

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Admin Console rendering failed', error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#edf3f2] p-5">
        <section className="w-full max-w-lg rounded-2xl border border-line bg-white p-7 shadow-elevated" role="alert">
          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-amber-50 text-amber-700"><AlertTriangle className="h-6 w-6" /></div>
          <h1 className="mt-4 text-2xl font-semibold text-ink">The console could not render this view</h1>
          <p className="mt-2 text-sm leading-6 text-slate-600">Server-side data is unchanged. Reload the console to recover; if this repeats, capture the current route and browser console error.</p>
          <Button className="mt-6" onClick={() => window.location.reload()}>Reload console</Button>
        </section>
      </main>
    );
  }
}
