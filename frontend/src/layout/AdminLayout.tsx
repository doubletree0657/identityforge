import {
  Activity,
  Building2,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LockKeyhole,
  Network,
  ShieldCheck,
  UserCog,
  Users,
} from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';
import { TokenPanel } from './TokenPanel';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/tenants', label: 'Tenants', icon: Building2 },
  { to: '/users', label: 'Users', icon: UserCog },
  { to: '/groups', label: 'Groups', icon: Users },
  { to: '/roles', label: 'Roles', icon: ShieldCheck },
  { to: '/permissions', label: 'Permissions', icon: ListChecks },
  { to: '/clients', label: 'OAuth2 Clients', icon: KeyRound },
  { to: '/mfa', label: 'MFA', icon: LockKeyhole },
  { to: '/audit-logs', label: 'Audit Logs', icon: Activity },
  { to: '/oauth2-demo', label: 'OAuth2 Demo', icon: Network },
];

export function AdminLayout() {
  return (
    <div className="min-h-screen bg-[#f5f7fb]">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-white lg:block">
        <div className="border-b border-line px-5 py-5">
          <div className="text-lg font-semibold text-ink">IAM Admin</div>
          <div className="mt-1 text-xs text-slate-500">International IAM Platform</div>
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
              <div className="text-xs text-slate-500">Development console for scope-protected Admin APIs</div>
            </div>
            <TokenPanel />
          </div>
        </header>
        <main className="mx-auto max-w-7xl px-4 py-6 lg:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
