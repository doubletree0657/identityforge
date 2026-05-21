import { useQueries } from '@tanstack/react-query';
import { Activity, Building2, KeyRound, ShieldCheck, UserCog, Users } from 'lucide-react';
import { adminApi } from '../api/adminApi';
import { Card } from '../components/Card';
import { ErrorState, LoadingState } from '../components/State';
import { PageHeader } from './PageHeader';

const cards = [
  { key: 'tenants', label: 'Tenants', icon: Building2, query: () => adminApi.tenants.list({ size: 1 }) },
  { key: 'users', label: 'Users', icon: UserCog, query: () => adminApi.users.list({ size: 1 }) },
  { key: 'groups', label: 'Groups', icon: Users, query: () => adminApi.groups.list({ size: 1 }) },
  { key: 'roles', label: 'Roles', icon: ShieldCheck, query: () => adminApi.roles.list({ size: 1 }) },
  { key: 'clients', label: 'Clients', icon: KeyRound, query: () => adminApi.clients.list({ size: 1 }) },
  { key: 'auditLogs', label: 'Audit Logs', icon: Activity, query: () => adminApi.auditLogs.list({ size: 1 }) },
];

export function DashboardPage() {
  const results = useQueries({
    queries: cards.map((card) => ({
      queryKey: ['dashboard', card.key],
      queryFn: card.query,
    })),
  });

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="A compact overview of the IAM platform using the same paginated Admin APIs as the management screens."
      />
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((card, index) => {
          const Icon = card.icon;
          const result = results[index];
          return (
            <Card key={card.key}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-medium text-slate-500">{card.label}</div>
                  <div className="mt-2 text-3xl font-semibold text-ink">
                    {result.isLoading ? '-' : result.data?.totalElements ?? '-'}
                  </div>
                </div>
                <div className="rounded-lg bg-brand/10 p-3 text-brand">
                  <Icon className="h-6 w-6" />
                </div>
              </div>
              {result.isError && <div className="mt-3 text-xs text-[#8f1c13]">Unavailable with the current token or backend state.</div>}
            </Card>
          );
        })}
      </div>
      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <Card title="API access">
          <p className="text-sm text-slate-600">
            Admin APIs require bearer tokens with `iam.read` for GET requests and `iam.write` for write operations.
            Paste a local development access token in the header panel to start using the console.
          </p>
        </Card>
        <Card title="Recent audit readiness">
          {results[5].isLoading && <LoadingState label="Loading audit metadata" />}
          {results[5].isError && <ErrorState error={results[5].error} />}
          {results[5].data && (
            <p className="text-sm text-slate-600">
              The audit API is available and currently reports {results[5].data.totalElements} event records.
            </p>
          )}
        </Card>
      </div>
    </>
  );
}
