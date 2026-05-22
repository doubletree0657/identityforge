import { Link } from 'react-router-dom';
import { EmptyState } from './State';

export function TenantRequired({ label = 'Select a tenant to continue.' }: { label?: string }) {
  return (
    <div className="rounded-lg border border-line bg-white p-6">
      <EmptyState title="No tenant selected" detail={label} />
      <div className="mt-4 text-center">
        <Link className="text-sm font-medium text-brand hover:underline" to="/tenants">
          Open tenant management
        </Link>
      </div>
    </div>
  );
}
