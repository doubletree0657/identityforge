import {
  Activity,
  AppWindow,
  Building2,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LockKeyhole,
  Network,
  RefreshCw,
  Route,
  ShieldCheck,
  UserCog,
  Users,
} from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';
import { Button } from '../components/Button';
import { Select } from '../components/Form';
import { logout } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { useTenantContext } from '../context/TenantContext';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/tenants', label: 'Tenants', icon: Building2 },
  { to: '/users', label: 'Users', icon: UserCog },
  { to: '/groups', label: 'Groups', icon: Users },
  { to: '/roles', label: 'Roles', icon: ShieldCheck },
  { to: '/permissions', label: 'Permissions', icon: ListChecks },
  { to: '/applications', label: 'Applications', icon: AppWindow },
  { to: '/clients', label: 'OAuth2 Clients', icon: KeyRound },
  { to: '/oauth2-consents', label: 'OAuth2 Consents', icon: RefreshCw },
  { to: '/mfa', label: 'MFA', icon: LockKeyhole },
  { to: '/audit-logs', label: 'Audit Logs', icon: Activity },
  { to: '/iam-workflow', label: 'IAM Workflow', icon: Route },
  { to: '/oauth2-demo', label: 'OAuth2 Demo', icon: Network },
];

export function AdminLayout() {
  const { user } = useAuth();
  const { tenants, selectedTenantId, selectedTenant, setSelectedTenantId, isLoading } = useTenantContext();

  return (
    <div className="min-h-screen bg-[#f5f7fb]">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-white lg:block">
        <div className="border-b border-line px-5 py-5">
          <div className="text-lg font-semibold text-ink">IAM Admin</div>
          <div className="mt-1 text-xs text-slate-500">IdentityForge</div>
        </div>
        <nav className="grid gap-1 p-3">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `flex min-h-10 items-center gap-3 rounded-md px-3 text-sm font-medium ${
                    isActive ? 'bg-brand text-white' : 'text-slate-700 hover:bg-slate-100'
                  }`
                }
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>
      </aside>
      <div className="lg:pl-64">
        <header className="sticky top-0 z-10 border-b border-line bg-white/95 px-4 py-3 backdrop-blur lg:px-6">
          <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <div className="text-sm font-semibold text-ink">Admin Console v1</div>
              <div className="text-xs text-slate-500">
                {user ? `Signed in as ${user.displayName || user.username} · ${
                  user.isPlatformAdmin ? 'platform-admin' : user.isTenantAdmin ? 'tenant-admin' : 'non-admin'
                }` : 'Signed in'}
              </div>
            </div>
            <div className="flex flex-col gap-3 xl:flex-row xl:items-center">
              <label className="grid gap-1 text-xs font-medium text-slate-600">
                Current tenant
                <Select
                  value={selectedTenantId}
                  onChange={(event) => setSelectedTenantId(event.target.value)}
                  disabled={isLoading}
                  className="min-w-[260px]"
                >
                  <option value="">{isLoading ? 'Loading tenants...' : 'No tenant selected'}</option>
                  {tenants.map((tenant) => (
                    <option key={tenant.id} value={tenant.id}>
                      {tenant.name} ({tenant.slug})
                    </option>
                  ))}
                </Select>
                {selectedTenant && <span className="text-[11px] text-slate-500">Selected: {selectedTenant.name}</span>}
              </label>
              <Button variant="secondary" onClick={logout}>Logout</Button>
            </div>
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-6 lg:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
