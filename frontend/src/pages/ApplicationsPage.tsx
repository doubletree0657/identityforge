import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select, Textarea } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { TenantRequired } from '../components/TenantRequired';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';
import { ResourceServerResponse, ResourceServerStatus } from '../types/api';
import { compact, formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function ApplicationsPage() {
  const [page, setPage] = useState(0);
  const [selectedResourceServerId, setSelectedResourceServerId] = useState('');
  const { hasPermission } = useAuth();
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const queryClient = useQueryClient();
  const canRead = hasPermission('iam.resource-servers.read');
  const canWrite = hasPermission('iam.resource-servers.write');

  const resourceServers = useQuery({
    queryKey: ['resource-servers', page, selectedTenantId],
    queryFn: () => adminApi.resourceServers.list({ page, size: 20, tenantId: selectedTenantId }),
    enabled: !!selectedTenantId && canRead,
  });
  const selectedResourceServer = resourceServers.data?.items.find((item) => item.id === selectedResourceServerId)
    ?? resourceServers.data?.items[0];
  const permissions = useQuery({
    queryKey: ['resource-server-permissions', selectedResourceServer?.id],
    queryFn: () => adminApi.resourceServers.permissions(selectedResourceServer!.id),
    enabled: !!selectedResourceServer?.id && canRead,
  });

  useEffect(() => {
    if (!selectedResourceServerId && resourceServers.data?.items[0]) {
      setSelectedResourceServerId(resourceServers.data.items[0].id);
    }
    if (selectedResourceServerId && resourceServers.data && !resourceServers.data.items.some((item) => item.id === selectedResourceServerId)) {
      setSelectedResourceServerId(resourceServers.data.items[0]?.id ?? '');
    }
  }, [resourceServers.data, selectedResourceServerId]);

  const invalidateResourceServers = () => queryClient.invalidateQueries({ queryKey: ['resource-servers'] });
  const invalidatePermissions = () => queryClient.invalidateQueries({ queryKey: ['resource-server-permissions'] });

  const createResourceServer = useMutation({
    mutationFn: adminApi.resourceServers.create,
    onSuccess: (result) => {
      setSelectedResourceServerId(result.id);
      invalidateResourceServers();
    },
  });
  const updateResourceServer = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof adminApi.resourceServers.update>[1] }) =>
      adminApi.resourceServers.update(id, body),
    onSuccess: invalidateResourceServers,
  });
  const disableResourceServer = useMutation({
    mutationFn: adminApi.resourceServers.disable,
    onSuccess: invalidateResourceServers,
  });
  const reactivateResourceServer = useMutation({
    mutationFn: adminApi.resourceServers.reactivate,
    onSuccess: invalidateResourceServers,
  });
  const createPermission = useMutation({
    mutationFn: ({ id, body }: { id: string; body: Parameters<typeof adminApi.resourceServers.createPermission>[1] }) =>
      adminApi.resourceServers.createPermission(id, body),
    onSuccess: invalidatePermissions,
  });
  const updatePermission = useMutation({
    mutationFn: ({ id, permissionId, body }: {
      id: string;
      permissionId: string;
      body: Parameters<typeof adminApi.resourceServers.updatePermission>[2];
    }) => adminApi.resourceServers.updatePermission(id, permissionId, body),
    onSuccess: invalidatePermissions,
  });

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    createResourceServer.mutate({
      tenantId: selectedTenantId,
      identifier: String(form.get('identifier') ?? ''),
      name: String(form.get('name') ?? ''),
      description: String(form.get('description') ?? ''),
    });
    event.currentTarget.reset();
  }

  function createApplicationPermission(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedResourceServer) {
      return;
    }
    const form = new FormData(event.currentTarget);
    createPermission.mutate({
      id: selectedResourceServer.id,
      body: {
        name: String(form.get('name') ?? ''),
        displayName: String(form.get('displayName') ?? ''),
        description: String(form.get('description') ?? ''),
      },
    });
    event.currentTarget.reset();
  }

  if (!selectedTenantId) {
    return (
      <>
        <PageHeader
          title="Applications"
          description="Tenant-owned applications define future-facing resource permissions outside the IAM Admin API permission catalog."
        />
        <TenantRequired label="Select a tenant to manage its applications and application permissions." />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Applications"
        description="System IAM permissions protect this Admin API. Application permissions describe capabilities exposed by tenant-owned applications and are not a policy engine yet."
      />
      <div className="grid gap-4 xl:grid-cols-[420px_1fr]">
        <div className="grid content-start gap-4">
          <Card title="Create application">
            <form onSubmit={create} className="grid gap-3">
              <Field label="Tenant"><Input value={selectedTenant?.name ?? 'Selected tenant'} disabled /></Field>
              <Field label="Identifier" hint="Unique within the selected tenant.">
                <Input name="identifier" placeholder="payroll-api" required disabled={!canWrite} />
              </Field>
              <Field label="Name"><Input name="name" placeholder="Payroll API" required disabled={!canWrite} /></Field>
              <Field label="Description"><Textarea name="description" placeholder="Payroll application capabilities" disabled={!canWrite} /></Field>
              <Button type="submit" disabled={createResourceServer.isPending || !canWrite}>Create</Button>
              {createResourceServer.isError && <ErrorState error={createResourceServer.error} />}
            </form>
          </Card>
          <Card title="Permission model">
            <div className="grid gap-2 text-sm text-slate-600">
              <p>System IAM permissions such as iam.users.read authorize the IAM platform Admin API.</p>
              <p>Application permissions such as payroll.employee.read belong to tenant applications and are reserved for future external resource authorization.</p>
            </div>
          </Card>
        </div>
        <div className="grid gap-4">
          <Card title="Applications">
            {!canRead && <ErrorState error={{ code: 'access_denied', message: 'Missing iam.resource-servers.read.' }} />}
            {resourceServers.isLoading && <LoadingState />}
            {resourceServers.isError && <ErrorState error={resourceServers.error} />}
            {resourceServers.data && (
              <>
                <DataTable
                  items={resourceServers.data.items}
                  columns={[
                    {
                      header: 'Application',
                      render: (resourceServer) => (
                        <button
                          type="button"
                          className="text-left font-medium text-brand hover:underline"
                          onClick={() => setSelectedResourceServerId(resourceServer.id)}
                        >
                          {resourceServer.name}
                        </button>
                      ),
                    },
                    { header: 'Identifier', render: (resourceServer) => resourceServer.identifier },
                    { header: 'Status', render: (resourceServer) => <Badge>{resourceServer.status}</Badge> },
                    { header: 'Updated', render: (resourceServer) => formatDate(resourceServer.updatedAt) },
                    {
                      header: 'Update',
                      render: (resourceServer) => (
                        <ResourceServerUpdateForm
                          resourceServer={resourceServer}
                          canWrite={canWrite}
                          onSave={(body) => updateResourceServer.mutate({ id: resourceServer.id, body })}
                          onDisable={() => disableResourceServer.mutate(resourceServer.id)}
                          onReactivate={() => reactivateResourceServer.mutate(resourceServer.id)}
                        />
                      ),
                    },
                  ]}
                  emptyTitle="No applications found"
                />
                <Pagination page={resourceServers.data} onPageChange={setPage} />
              </>
            )}
            {(updateResourceServer.isError || disableResourceServer.isError || reactivateResourceServer.isError) && (
              <div className="mt-3">
                <ErrorState error={updateResourceServer.error ?? disableResourceServer.error ?? reactivateResourceServer.error} />
              </div>
            )}
          </Card>
          <Card title={selectedResourceServer ? `${selectedResourceServer.name} permissions` : 'Application permissions'}>
            {!selectedResourceServer && <p className="text-sm text-slate-600">Select an application to manage its permissions.</p>}
            {selectedResourceServer && (
              <div className="grid gap-4">
                <form onSubmit={createApplicationPermission} className="grid gap-3 lg:grid-cols-[1fr_1fr]">
                  <Field label="Permission name">
                    <Input name="name" placeholder="payroll.employee.read" required disabled={!canWrite} />
                  </Field>
                  <Field label="Display name"><Input name="displayName" placeholder="Read employees" disabled={!canWrite} /></Field>
                  <Field label="Description">
                    <Input name="description" placeholder="Read employee records" disabled={!canWrite} />
                  </Field>
                  <div className="flex items-end">
                    <Button type="submit" disabled={createPermission.isPending || !canWrite}>Create permission</Button>
                  </div>
                </form>
                {permissions.isLoading && <LoadingState />}
                {permissions.isError && <ErrorState error={permissions.error} />}
                {permissions.data && (
                  <DataTable
                    items={permissions.data}
                    columns={[
                      { header: 'Permission', render: (permission) => <span className="font-medium">{permission.name}</span> },
                      { header: 'Display name', render: (permission) => permission.displayName || '-' },
                      { header: 'Description', render: (permission) => permission.description || '-' },
                      {
                        header: 'Update',
                        render: (permission) => (
                          <form
                            className="grid min-w-[320px] gap-2"
                            onSubmit={(event) => {
                              event.preventDefault();
                              const form = new FormData(event.currentTarget);
                              updatePermission.mutate({
                                id: selectedResourceServer.id,
                                permissionId: permission.id,
                                body: compact({
                                  name: String(form.get('name') ?? ''),
                                  displayName: String(form.get('displayName') ?? ''),
                                  description: String(form.get('description') ?? ''),
                                }),
                              });
                            }}
                          >
                            <Input name="name" defaultValue={permission.name} disabled={!canWrite} />
                            <Input name="displayName" defaultValue={permission.displayName} disabled={!canWrite} />
                            <Input name="description" defaultValue={permission.description ?? ''} disabled={!canWrite} />
                            <Button type="submit" variant="secondary" disabled={!canWrite}>Save</Button>
                          </form>
                        ),
                      },
                    ]}
                    emptyTitle="No application permissions found"
                  />
                )}
                {(createPermission.isError || updatePermission.isError) && (
                  <ErrorState error={createPermission.error ?? updatePermission.error} />
                )}
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}

function ResourceServerUpdateForm({
  resourceServer,
  canWrite,
  onSave,
  onDisable,
  onReactivate,
}: {
  resourceServer: ResourceServerResponse;
  canWrite: boolean;
  onSave: (body: { identifier?: string; name?: string; description?: string; status?: ResourceServerStatus }) => void;
  onDisable: () => void;
  onReactivate: () => void;
}) {
  return (
    <form
      className="grid min-w-[340px] gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        onSave(compact({
          identifier: String(form.get('identifier') ?? ''),
          name: String(form.get('name') ?? ''),
          description: String(form.get('description') ?? ''),
          status: String(form.get('status') ?? resourceServer.status) as ResourceServerStatus,
        }));
      }}
    >
      <Input name="identifier" defaultValue={resourceServer.identifier} disabled={!canWrite} />
      <Input name="name" defaultValue={resourceServer.name} disabled={!canWrite} />
      <Input name="description" defaultValue={resourceServer.description ?? ''} disabled={!canWrite} />
      <Select name="status" defaultValue={resourceServer.status} disabled={!canWrite}>
        <option value="ACTIVE">ACTIVE</option>
        <option value="DISABLED">DISABLED</option>
      </Select>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="secondary" disabled={!canWrite}>Save</Button>
        {resourceServer.status === 'ACTIVE' ? (
          <Button type="button" variant="danger" onClick={onDisable} disabled={!canWrite}>Disable</Button>
        ) : (
          <Button type="button" variant="secondary" onClick={onReactivate} disabled={!canWrite}>Reactivate</Button>
        )}
      </div>
    </form>
  );
}
