import { useQueries } from '@tanstack/react-query';
import { Activity, ArrowRight, Building2, DatabaseZap, KeyRound, Network, Route, ShieldCheck, UserCog, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import { adminApi } from '../api/adminApi';
import { Badge } from '../components/Badge';
import { Card } from '../components/Card';
import { EmptyState, ErrorState, LoadingState } from '../components/State';
import { useTenantContext } from '../context/TenantContext';
import { PageHeader } from './PageHeader';

export function DashboardPage() {
  const { selectedTenantId, selectedTenant } = useTenantContext();
  const cards = [
    { key: 'tenants', label: 'Tenants', path: '/tenants', icon: Building2, enabled: true, query: () => adminApi.tenants.list({ size: 1 }) },
    { key: 'users', label: 'Users', path: '/users', icon: UserCog, enabled: !!selectedTenantId, query: () => adminApi.users.list({ size: 1, tenantId: selectedTenantId }) },
    { key: 'groups', label: 'Groups', path: '/groups', icon: Users, enabled: !!selectedTenantId, query: () => adminApi.groups.list({ size: 1, tenantId: selectedTenantId }) },
    { key: 'roles', label: 'Roles', path: '/roles', icon: ShieldCheck, enabled: !!selectedTenantId, query: () => adminApi.roles.list({ size: 1, tenantId: selectedTenantId }) },
    { key: 'clients', label: 'OAuth2 clients', path: '/clients', icon: KeyRound, enabled: !!selectedTenantId, query: () => adminApi.clients.list({ size: 1, tenantId: selectedTenantId }) },
    { key: 'auditLogs', label: 'Audit events', path: '/audit-logs', icon: Activity, enabled: !!selectedTenantId, query: () => adminApi.auditLogs.list({ size: 1, tenantId: selectedTenantId }) },
  ];
  const results = useQueries({ queries: cards.map((card) => ({ queryKey: ['dashboard', card.key, selectedTenantId], queryFn: card.query, enabled: card.enabled })) });

  return (
    <>
      <PageHeader title="Identity operations overview" description="Orient the demo, review tenant-scoped inventory, and launch a guided IAM or protocol journey." action={selectedTenant ? <Badge>{selectedTenant.status}</Badge> : undefined} />
      {!selectedTenantId && <div className="mb-4"><EmptyState title="Choose a tenant context" detail="Tenant-scoped counts and workflows appear after you choose an organization in the header. Platform tenant management remains available." action={<Link className="inline-flex min-h-9 items-center rounded-md bg-brand px-3 py-2 text-sm font-medium text-white" to="/tenants">Open tenants</Link>} /></div>}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map((card, index) => {
          const Icon = card.icon;
          const result = results[index];
          return (
            <Link key={card.key} to={card.path} className={`group rounded-xl focus:outline-none ${!card.enabled && card.key !== 'tenants' ? 'pointer-events-none opacity-55' : ''}`} aria-disabled={!card.enabled && card.key !== 'tenants'}>
              <Card className="h-full transition group-hover:-translate-y-0.5 group-hover:border-brand/40 group-hover:shadow-md">
                <div className="flex items-start justify-between">
                  <div><div className="text-sm font-medium text-slate-500">{card.label}</div><div className="mt-2 text-3xl font-semibold tracking-tight text-ink">{!card.enabled ? '—' : result.isLoading ? <span className="animate-pulse text-slate-300">•••</span> : result.data?.totalElements ?? '—'}</div></div>
                  <div className="rounded-lg bg-brand/10 p-3 text-brand"><Icon className="h-5 w-5" /></div>
                </div>
                <div className="mt-4 flex items-center gap-1 text-xs font-medium text-brand">Manage {card.label.toLowerCase()}<ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" /></div>
                {result.isError && <div className="mt-3 text-xs text-[#8f1c13]">Unavailable for the current role or backend state.</div>}
              </Card>
            </Link>
          );
        })}
      </div>
      <div className="mt-6 grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
        <Card title="Demo journeys" description="Each journey uses persisted resources and backend-enforced authorization.">
          <div className="grid gap-3 md:grid-cols-3">
            <Journey to="/iam-workflow" icon={Route} title="IAM lifecycle" detail="Tenant → user → group → role → effective permission" />
            <Journey to="/oauth2-demo" icon={Network} title="OAuth2 & OIDC" detail="PKCE → login → MFA → consent → scoped API" />
            <Journey to="/scim-demo" icon={DatabaseZap} title="SCIM provisioning" detail="Protocol request → directory → audit trail" />
          </div>
        </Card>
        <Card title="Audit readiness" description="Security events are intentionally non-sensitive.">
          {!selectedTenantId && <p className="text-sm text-slate-600">Choose a tenant to check its audit stream.</p>}
          {selectedTenantId && results[5].isLoading && <LoadingState label="Checking tenant audit stream" />}
          {selectedTenantId && results[5].isError && <ErrorState error={results[5].error} onRetry={() => void results[5].refetch()} />}
          {results[5].data && <div><div className="text-3xl font-semibold text-ink">{results[5].data.totalElements}</div><p className="mt-2 text-sm leading-6 text-slate-600">events available for {selectedTenant?.name ?? 'the selected tenant'}, including administration, authentication, MFA, OAuth2, and SCIM outcomes.</p><Link className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline" to="/audit-logs">Review audit evidence <ArrowRight className="h-4 w-4" /></Link></div>}
        </Card>
      </div>
    </>
  );
}

function Journey({ to, icon: Icon, title, detail }: { to: string; icon: typeof Route; title: string; detail: string }) {
  return <Link to={to} className="group rounded-lg border border-line bg-slate-50 p-4 transition hover:border-brand/40 hover:bg-brand/5"><Icon className="h-5 w-5 text-brand" /><div className="mt-3 text-sm font-semibold text-ink">{title}</div><p className="mt-1 text-xs leading-5 text-slate-500">{detail}</p><div className="mt-3 flex items-center gap-1 text-xs font-medium text-brand">Start journey<ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" /></div></Link>;
}
