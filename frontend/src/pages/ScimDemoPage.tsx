import { FormEvent, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiBaseUrl } from '../api/storage';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { CopyButton } from '../components/CopyButton';
import { Field, Input, Select } from '../components/Form';
import { Notice } from '../components/Notice';
import { Stepper } from '../components/Stepper';
import { TenantRequired } from '../components/TenantRequired';
import { useTenantContext } from '../context/TenantContext';
import { buildScimCommand, ScimDemoOperation, scimInputError } from '../utils/demoFlows';
import { PageHeader } from './PageHeader';

const operationDetails: Record<ScimDemoOperation, { label: string; method: string; detail: string }> = {
  discover: { label: 'Discover capabilities', method: 'GET', detail: 'Read the server-advertised supported subset.' },
  'list-users': { label: 'List or filter users', method: 'GET', detail: 'Exercise one-based pagination or a bounded equality filter.' },
  'create-user': { label: 'Provision a user', method: 'POST', detail: 'Create an identity in the same tenant directory used by the Admin Console.' },
  'patch-user': { label: 'Deactivate a user', method: 'PATCH', detail: 'Use PatchOp to map active=false to the disabled account state.' },
  'create-group': { label: 'Provision a group', method: 'POST', detail: 'Create a direct-membership group.' },
  'add-member': { label: 'Add group member', method: 'PATCH', detail: 'Add a direct user member and invalidate stale authorization state.' },
};

export function ScimDemoPage() {
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const [operation, setOperation] = useState<ScimDemoOperation>('discover');
  const [values, setValues] = useState({ userName: 'scim.demo', displayName: 'SCIM Demo User', email: 'scim.demo@example.test', userId: '', groupId: '', groupName: 'SCIM Demo Group' });
  const [command, setCommand] = useState('');
  const [error, setError] = useState('');
  const input = useMemo(() => ({ baseUrl: getApiBaseUrl(), tenantId: selectedTenantId, operation, ...values }), [operation, selectedTenantId, values]);

  function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = scimInputError(input);
    if (validationError) {
      setError(validationError);
      setCommand('');
      return;
    }
    setError('');
    setCommand(buildScimCommand(input));
  }

  function setValue(name: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setCommand('');
    setError('');
  }

  return (
    <>
      <PageHeader title="SCIM 2.0 Provisioning Demo" description="Generate tenant-bound requests against IdentityForge’s documented SCIM subset and trace the result into the shared directory and audit model." />
      {!selectedTenantId && <TenantRequired label="SCIM base URLs are tenant scoped. Select a tenant to generate a safe demo request." />}
      <Stepper steps={[
        { label: 'Choose operation', detail: 'Pick a supported user, group, or discovery request.', status: command ? 'complete' : 'current' },
        { label: 'Run request', detail: 'Use an Admin API bearer token with the matching permissions.', status: command ? 'current' : 'upcoming' },
        { label: 'Verify outcome', detail: 'Inspect the directory resource and SCIM audit event.', status: 'upcoming' },
      ]} />
      <div className="mt-4 grid gap-4 xl:grid-cols-[440px_1fr]">
        <Card title="Build a supported request" description={selectedTenant ? `Base tenant: ${selectedTenant.name}` : 'A tenant context is required.'}>
          <form className="grid gap-4" onSubmit={generate}>
            <Field label="Operation" hint={operationDetails[operation].detail}>
              <Select value={operation} onChange={(event) => { setOperation(event.target.value as ScimDemoOperation); setCommand(''); setError(''); }} disabled={!selectedTenantId}>
                {Object.entries(operationDetails).map(([value, detail]) => <option key={value} value={value}>{detail.method} · {detail.label}</option>)}
              </Select>
            </Field>
            {(operation === 'list-users' || operation === 'create-user') && <Field label="Username" hint={operation === 'list-users' ? 'Leave blank to list the first 25 users.' : 'Unique after normalization within this tenant.'} required={operation === 'create-user'}><Input value={values.userName} onChange={(event) => setValue('userName', event.target.value)} required={operation === 'create-user'} /></Field>}
            {operation === 'create-user' && <><Field label="Display name"><Input value={values.displayName} onChange={(event) => setValue('displayName', event.target.value)} /></Field><Field label="Primary email"><Input type="email" value={values.email} onChange={(event) => setValue('email', event.target.value)} /></Field></>}
            {operation === 'create-group' && <Field label="Group display name" required><Input value={values.groupName} onChange={(event) => setValue('groupName', event.target.value)} required /></Field>}
            {(operation === 'patch-user' || operation === 'add-member') && <Field label="User ID" hint="Use the id returned by SCIM or shown on the user detail page."><Input value={values.userId} onChange={(event) => setValue('userId', event.target.value)} placeholder="<USER_ID>" /></Field>}
            {operation === 'add-member' && <Field label="Group ID"><Input value={values.groupId} onChange={(event) => setValue('groupId', event.target.value)} placeholder="<GROUP_ID>" /></Field>}
            {error && <p className="text-sm font-medium text-[#b42318]" role="alert">{error}</p>}
            <Button type="submit" disabled={!selectedTenantId}>Generate curl command</Button>
          </form>
        </Card>
        <div className="grid content-start gap-4">
          <Card title="Request preview" action={command ? <CopyButton value={command} label="Copy command" /> : undefined}>
            {command ? <pre className="max-h-[440px] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-[#111c24] p-4 text-xs leading-6 text-slate-100">{command}</pre> : <div className="rounded-lg border border-dashed border-line bg-slate-50 p-8 text-center text-sm text-slate-500">Choose an operation and generate a request. Access tokens stay as placeholders and are never copied from browser storage.</div>}
          </Card>
          <Notice title="Expected authorization boundary" tone="info">The token needs the admin API audience, <code>iam.read</code> or <code>iam.write</code>, the matching directory permission, and access to this tenant. SCIM errors remain protocol-shaped.</Notice>
          <Card title="Verify the vertical slice">
            <ol className="grid list-decimal gap-2 pl-5 text-sm leading-6 text-slate-600">
              <li>Run the generated request with a local admin access token.</li>
              <li>For writes, open <Link className="font-medium text-brand hover:underline" to={operation.includes('group') || operation === 'add-member' ? '/groups' : '/users'}>the shared directory screen</Link> and confirm the same resource changed.</li>
              <li>Open <Link className="font-medium text-brand hover:underline" to="/audit-logs">Audit logs</Link> and enter the exact emitted <code>SCIM_*</code> action shown by the response workflow.</li>
            </ol>
            <div className="mt-4 flex flex-wrap gap-2"><Badge>SCIM 2.0 SUBSET</Badge><Badge>ETAG / IF-MATCH</Badge><Badge>DIRECT MEMBERSHIP</Badge></div>
          </Card>
          <Notice title="Deliberate non-goals" tone="warning">Bulk, nested groups, POST search, extension schemas, attribute projection, and the complete filter grammar are not represented as supported capabilities.</Notice>
        </div>
      </div>
    </>
  );
}
