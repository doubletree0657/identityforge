import { Link } from 'react-router-dom';
import { Card } from '../components/Card';
import { Badge } from '../components/Badge';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

const steps = [
  { label: 'Select tenant', path: '/tenants', detail: 'Pick a tenant from the global header selector.' },
  { label: 'Create user', path: '/users', detail: 'Create a tenant user, then open the user detail page.' },
  { label: 'Set password', path: '/users', detail: 'Use the Password card on the user detail page.' },
  { label: 'Create permission', path: '/permissions', detail: 'Create a tenant permission such as users:read.' },
  { label: 'Create role', path: '/roles', detail: 'Create a role in the same tenant.' },
  { label: 'Assign permission to role', path: '/roles', detail: 'Use the permission selector on the Roles page.' },
  { label: 'Assign role to user', path: '/users', detail: 'Use the role selector on the user detail page.' },
  { label: 'Create group', path: '/groups', detail: 'Create a tenant group for the same user population.' },
  { label: 'Add user to group', path: '/groups', detail: 'Use the tenant user selector in the group membership workflow.' },
  { label: 'Create OAuth2 client', path: '/clients', detail: 'Choose confidential or public and follow the guided defaults.' },
  { label: 'Rotate client secret', path: '/clients', detail: 'Rotate only confidential client secrets and copy the one-time value from the console.' },
  { label: 'Review audit logs', path: '/audit-logs', detail: 'Filter by tenant, action, resource type, or resource id.' },
];

export function IamWorkflowPage() {
  const { selectedTenant, selectedTenantId } = useTenantContext();

  return (
    <>
      <PageHeader title="IAM Workflow" description="A guided chain showing how tenant-scoped IAM resources relate to each other." />
      <div className="grid gap-4 lg:grid-cols-[320px_1fr]">
        <Card title="Current tenant">
          {selectedTenant ? (
            <div className="grid gap-2 text-sm">
              <div className="text-lg font-semibold text-ink">{selectedTenant.name}</div>
              <div className="text-slate-600">{selectedTenant.slug}</div>
              <Badge>{selectedTenant.status}</Badge>
              <div className="break-all font-mono text-xs text-slate-500">{selectedTenant.id}</div>
            </div>
          ) : (
            <p className="text-sm text-slate-600">Select a tenant in the header before walking through tenant-scoped user, role, group, client, and audit workflows.</p>
          )}
        </Card>
        <Card title="Relationship model">
          <div className="grid gap-2 text-sm text-slate-700">
            <p>A user always belongs to exactly one tenant.</p>
            <p>Groups are optional organizational containers; a user can be in zero, one, or many groups.</p>
            <p>Roles are assigned directly to users in this version. Role-to-group assignment remains future work.</p>
            <p>Permissions attach to roles, and OAuth2 clients belong to the selected tenant.</p>
          </div>
        </Card>
      </div>
      <div className="mt-4">
        <Card title="Demo walkthrough">
          <div className="grid gap-3">
            {steps.map((step, index) => (
              <Link
                key={step.label}
                to={step.path}
                className="grid gap-1 rounded-md border border-line bg-white px-3 py-3 text-sm hover:border-brand hover:bg-slate-50 md:grid-cols-[42px_220px_1fr]"
              >
                <span className="font-mono text-xs text-slate-500">{String(index + 1).padStart(2, '0')}</span>
                <span className="font-semibold text-ink">{step.label}</span>
                <span className="text-slate-600">{step.detail}</span>
              </Link>
            ))}
          </div>
          {!selectedTenantId && <p className="mt-4 text-sm text-slate-600">Most links will ask for a tenant context until one is selected.</p>}
        </Card>
      </div>
    </>
  );
}
