import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Card } from '../components/Card';
import { Field, Input } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useTenantContext } from '../context/TenantContext';
import { formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [searchParams] = useSearchParams();
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const [filters, setFilters] = useState({
    action: searchParams.get('action') ?? '',
    resourceType: searchParams.get('resourceType') ?? '',
    resourceId: searchParams.get('resourceId') ?? '',
  });
  const auditLogs = useQuery({
    queryKey: ['audit-logs', page, selectedTenantId, filters],
    queryFn: () => adminApi.auditLogs.list({ page, size: 20, tenantId: selectedTenantId, ...filters }),
    enabled: !!selectedTenantId,
  });

  return (
    <>
      <PageHeader title="Audit Logs" description="Query administrative and security events without exposing sensitive values." />
      <Card title="Filters">
        {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant to load audit events for tenant-scoped exploration.</p>}
        <div className="grid gap-3 md:grid-cols-4">
          <Field label="tenant">
            <Input value={selectedTenant?.name ?? 'No tenant selected'} disabled />
          </Field>
          {(['action', 'resourceType', 'resourceId'] as const).map((field) => (
            <Field key={field} label={field}>
              <Input
                value={filters[field]}
                onChange={(event) => {
                  setFilters((current) => ({ ...current, [field]: event.target.value }));
                  setPage(0);
                }}
              />
            </Field>
          ))}
        </div>
      </Card>
      <div className="mt-4">
        <Card title="Events">
          {auditLogs.isLoading && <LoadingState />}
          {auditLogs.isError && <ErrorState error={auditLogs.error} />}
          {auditLogs.data && (
            <>
              <DataTable
                items={auditLogs.data.items}
                columns={[
                  { header: 'When', render: (log) => formatDate(log.createdAt) },
                  { header: 'Action', render: (log) => <span className="font-medium">{log.action}</span> },
                  { header: 'Resource', render: (log) => `${log.resourceType} ${log.resourceId}` },
                  { header: 'Tenant', render: (log) => log.tenantId || '-' },
                  { header: 'Actor', render: (log) => <Badge>{log.actorType}</Badge> },
                  { header: 'Result', render: (log) => <Badge>{log.result}</Badge> },
                ]}
              />
              <Pagination page={auditLogs.data} onPageChange={setPage} />
            </>
          )}
        </Card>
      </div>
    </>
  );
}
