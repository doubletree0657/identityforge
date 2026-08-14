import {
  Activity,
  AppWindow,
  BookOpenCheck,
  Building2,
  ChevronRight,
  DatabaseZap,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LockKeyhole,
  LogOut,
  Menu,
  Network,
  RefreshCw,
  Route,
  ShieldCheck,
  UserCog,
  Users,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { logout } from '../api/auth';
import { Button } from '../components/Button';
import { Select } from '../components/Form';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';

const navSections = [
  { label: 'Overview', items: [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/iam-workflow', label: 'IAM workflow', icon: Route },
  ] },
  { label: 'Directory & access', items: [
    { to: '/tenants', label: 'Tenants', icon: Building2 },
    { to: '/users', label: 'Users', icon: UserCog },
    { to: '/groups', label: 'Groups', icon: Users },
    { to: '/roles', label: 'Roles', icon: ShieldCheck },
    { to: '/permissions', label: 'Permissions', icon: ListChecks },
  ] },
  { label: 'Applications & security', items: [
    { to: '/applications', label: 'Applications', icon: AppWindow },
    { to: '/clients', label: 'OAuth2 clients', icon: KeyRound },
    { to: '/oauth2-consents', label: 'OAuth2 consents', icon: RefreshCw },
    { to: '/mfa', label: 'MFA', icon: LockKeyhole },
    { to: '/audit-logs', label: 'Audit logs', icon: Activity },
  ] },
  { label: 'Protocol demos', items: [
    { to: '/oauth2-demo', label: 'OAuth2 & OIDC', icon: Network },
    { to: '/scim-demo', label: 'SCIM provisioning', icon: DatabaseZap },
  ] },
];

const pageNames = Object.fromEntries(navSections.flatMap((section) => section.items.map((item) => [item.to, item.label])));

export function AdminLayout() {
  const { user } = useAuth();
  const { tenants, selectedTenantId, selectedTenant, setSelectedTenantId, isLoading, error } = useTenantContext();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const location = useLocation();
  const sectionPath = `/${location.pathname.split('/').filter(Boolean)[0] ?? ''}`;
  const pageName = pageNames[sectionPath] ?? 'Admin Console';

  const navigation = (
    <>
      <div className="flex h-[73px] items-center justify-between border-b border-white/10 px-5">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-400/15 text-emerald-300"><BookOpenCheck className="h-5 w-5" /></div>
          <div><div className="font-semibold tracking-tight text-white">IdentityForge</div><div className="text-xs text-slate-400">Administration</div></div>
        </div>
        <button type="button" className="rounded-md p-2 text-slate-300 hover:bg-white/10 lg:hidden" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation"><X className="h-5 w-5" /></button>
      </div>
      <nav className="h-[calc(100vh-73px)] overflow-y-auto p-3" aria-label="Primary navigation">
        {navSections.map((section) => (
          <div key={section.label} className="mb-5">
            <div className="mb-1 px-3 text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">{section.label}</div>
            <div className="grid gap-0.5">
              {section.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink key={item.to} to={item.to} end={item.to === '/'} onClick={() => setMobileNavOpen(false)} className={({ isActive }) => `group flex min-h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium transition ${isActive ? 'bg-brand text-white shadow-sm' : 'text-slate-300 hover:bg-white/5 hover:text-white'}`}>
                    <Icon className="h-4 w-4" />{item.label}
                  </NavLink>
                );
              })}
            </div>
          </div>
        ))}
      </nav>
    </>
  );

  return (
    <div className="min-h-screen bg-canvas">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-[#16242d] lg:block">{navigation}</aside>
      {mobileNavOpen && <button type="button" className="fixed inset-0 z-40 bg-slate-950/50 backdrop-blur-sm lg:hidden" onClick={() => setMobileNavOpen(false)} aria-label="Close navigation overlay" />}
      {mobileNavOpen && <aside className="fixed inset-y-0 left-0 z-50 w-72 bg-[#16242d] shadow-elevated lg:hidden">{navigation}</aside>}
      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-line bg-white/95 px-4 py-3 backdrop-blur lg:px-7">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <button type="button" className="rounded-md border border-line p-2 text-slate-600 hover:bg-slate-50 lg:hidden" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation"><Menu className="h-5 w-5" /></button>
              <div className="min-w-0">
                <div className="flex items-center gap-1 text-xs text-slate-500"><span>Admin Console</span><ChevronRight className="h-3 w-3" /><span className="truncate">{pageName}</span></div>
                <div className="mt-0.5 truncate text-sm font-semibold text-ink">{user?.displayName || user?.username || 'Administrator'}</div>
              </div>
            </div>
            <div className="flex flex-1 flex-wrap items-end justify-end gap-2 sm:flex-none">
              <label className="grid min-w-[220px] flex-1 gap-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500 sm:flex-none">
                Tenant context
                <Select value={selectedTenantId} onChange={(event) => setSelectedTenantId(event.target.value)} disabled={isLoading || Boolean(error)} className="w-full min-w-[220px] normal-case tracking-normal sm:w-auto">
                  <option value="">{isLoading ? 'Loading tenants…' : error ? 'Tenant list unavailable' : 'Select a tenant'}</option>
                  {tenants.map((tenant) => <option key={tenant.id} value={tenant.id}>{tenant.name} · {tenant.slug}</option>)}
                </Select>
              </label>
              <Button variant="ghost" onClick={logout} icon={<LogOut className="h-4 w-4" />} aria-label="Sign out">Sign out</Button>
            </div>
          </div>
          {selectedTenant && <div className="mt-2 text-xs text-slate-500 sm:text-right">Working in <span className="font-medium text-slate-700">{selectedTenant.name}</span> · {selectedTenant.status.toLowerCase()}</div>}
          {error && <div className="mt-2 text-xs font-medium text-[#b42318]">Tenant context could not be loaded. Refresh the page or sign in again.</div>}
        </header>
        <main className="mx-auto max-w-[1500px] px-4 py-6 lg:px-7 lg:py-8"><Outlet /></main>
      </div>
    </div>
  );
}
