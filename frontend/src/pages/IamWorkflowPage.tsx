import { Link } from 'react-router-dom';
import { Card } from '../components/Card';
import { Badge } from '../components/Badge';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

const steps = [
  { label: 'Select tenant', path: '/tenants', detail: 'Pick a tenant from the global header selector.' },
  { label: 'Create user', path: '/users', detail: 'Create a tenant user, then open the user detail page.' },
  { label: 'Set password', path: '/users', detail: 'Use the Password card on the user detail page.' },
  { label: 'Review permission catalog', path: '/permissions', detail: 'Use seeded IAM permissions instead of creating arbitrary strings.' },
  { label: 'Create role', path: '/roles', detail: 'Create a role in the same tenant.' },
  { label: 'Assign permission to role', path: '/roles', detail: 'Use the permission selector on the Roles page.' },
  { label: 'Assign role to user', path: '/users', detail: 'Use the role selector on the user detail page.' },
  { label: 'Create group', path: '/groups', detail: 'Create a tenant group for the same user population.' },
  { label: 'Add user to group', path: '/groups', detail: 'Use the tenant user selector in the group membership workflow.' },
  { label: 'Create OAuth2 client', path: '/clients', detail: 'Choose confidential or public and follow the guided defaults.' },
  { label: 'Rotate client secret', path: '/clients', detail: 'Rotate only confidential client secrets and copy the one-time value from the console.' },
  { label: 'Enroll MFA', path: '/mfa', detail: 'Complete authenticator verification and save the one-time recovery-code set.' },
  { label: 'Run OAuth2 / OIDC flow', path: '/oauth2-demo', detail: 'Validate the client request, complete login and consent, then verify scoped resource access.' },
  { label: 'Provision through SCIM', path: '/scim-demo', detail: 'Generate a supported SCIM request and verify the same directory and audit records.' },
  { label: 'Review audit logs', path: '/audit-logs', detail: 'Filter by tenant, action, resource type, or resource id.' },
];

export function IamWorkflowPage() {
  const { selectedTenant, selectedTenantId } = useTenantContext();

  return (
    <>
      <PageHeader title="IAM Lifecycle Workflow" description="A guided vertical slice from tenant and identity creation through effective authorization, protocol access, MFA, provisioning, and audit evidence." />
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
            <p>Roles can be assigned directly to users or to groups.</p>
            <p>System IAM permissions attach to roles, and OAuth2 clients belong to the selected tenant.</p>
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
