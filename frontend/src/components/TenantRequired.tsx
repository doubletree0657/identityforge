import { Link } from 'react-router-dom';
import { EmptyState } from './State';

export function TenantRequired({ label = 'Select a tenant to continue.' }: { label?: string }) {
  return (
    <div className="rounded-xl border border-line bg-white p-5 shadow-panel">
      <EmptyState title="Choose a tenant context" detail={label} action={<Link className="inline-flex min-h-9 items-center rounded-md bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand-dark" to="/tenants">Open tenant management</Link>} />
    </div>
  );
}
