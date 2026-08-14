import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Field, Input, Select } from '../components/Form';
import { Pagination } from '../components/Pagination';
import { ErrorState, LoadingState } from '../components/State';
import { DataTable } from '../components/Table';
import { useTenantContext } from '../context/TenantContext';
import { AuditResult } from '../types/api';
import { formatDate } from '../utils/format';
import { PageHeader } from './PageHeader';

export function AuditLogsPage() {
  const [page, setPage] = useState(0);
  const [searchParams] = useSearchParams();
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const initialFilters = {
    action: searchParams.get('action') ?? '',
    resourceType: searchParams.get('resourceType') ?? '',
    resourceId: searchParams.get('resourceId') ?? '',
    result: (searchParams.get('result') ?? '') as AuditResult | '',
  };
  const [filters, setFilters] = useState(initialFilters);
  const [draftFilters, setDraftFilters] = useState(initialFilters);
  const auditLogs = useQuery({
    queryKey: ['audit-logs', page, selectedTenantId, filters],
    queryFn: () => adminApi.auditLogs.list({
      page,
      size: 20,
      tenantId: selectedTenantId,
      action: filters.action,
      resourceType: filters.resourceType,
      resourceId: filters.resourceId,
      result: filters.result || undefined,
    }),
    enabled: !!selectedTenantId,
  });

  return (
    <>
      <PageHeader title="Audit Logs" description="Query administrative and security events without exposing sensitive values." />
      <Card title="Filters">
        {!selectedTenantId && <p className="mb-3 text-sm text-slate-600">Select a tenant to load audit events for tenant-scoped exploration.</p>}
        <form className="grid gap-3 md:grid-cols-5" onSubmit={(event) => { event.preventDefault(); setFilters(draftFilters); setPage(0); }}>
          <Field label="tenant">
            <Input value={selectedTenant?.name ?? 'No tenant selected'} disabled />
          </Field>
          {(['action', 'resourceType', 'resourceId'] as const).map((field) => (
            <Field key={field} label={field}>
              <Input
                value={draftFilters[field]}
                onChange={(event) => setDraftFilters((current) => ({ ...current, [field]: event.target.value }))}
              />
            </Field>
          ))}
          <Field label="result">
            <Select
              value={draftFilters.result}
              onChange={(event) => setDraftFilters((current) => ({ ...current, result: event.target.value as AuditResult | '' }))}
            >
              <option value="">Any result</option>
              <option value="SUCCESS">SUCCESS</option>
              <option value="FAILURE">FAILURE</option>
            </Select>
          </Field>
          <div className="flex items-end gap-2 md:col-span-5">
            <Button type="submit" disabled={!selectedTenantId}>Apply filters</Button>
            <Button type="button" variant="secondary" onClick={() => { const empty = { action: '', resourceType: '', resourceId: '', result: '' as AuditResult | '' }; setDraftFilters(empty); setFilters(empty); setPage(0); }}>Clear</Button>
          </div>
        </form>
      </Card>
      <div className="mt-4">
        <Card title="Events">
          {auditLogs.isLoading && <LoadingState />}
          {auditLogs.isError && <ErrorState error={auditLogs.error} onRetry={() => void auditLogs.refetch()} />}
          {auditLogs.data && (
            <>
              <DataTable
                items={auditLogs.data.items}
                getKey={(log) => log.id}
                emptyTitle="No audit events match these filters"
                emptyDetail="Clear one or more exact-match filters, or perform a workflow that emits a security event."
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
