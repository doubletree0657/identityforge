import { Navigate, RouteObject } from 'react-router-dom';
import { AuthGate } from '../layout/AuthGate';
import { AdminLayout } from '../layout/AdminLayout';
import { TenantProvider } from '../context/TenantContext';
import { AccessDeniedPage } from '../pages/AccessDeniedPage';
import { ApplicationsPage } from '../pages/ApplicationsPage';
import { AuditLogsPage } from '../pages/AuditLogsPage';
import { ClientsPage } from '../pages/ClientsPage';
import { DashboardPage } from '../pages/DashboardPage';
import { GroupsPage } from '../pages/GroupsPage';
import { GroupDetailPage } from '../pages/GroupDetailPage';
import { IamWorkflowPage } from '../pages/IamWorkflowPage';
import { LoginPage } from '../pages/LoginPage';
import { MfaPage } from '../pages/MfaPage';
import { OAuth2CallbackPage } from '../pages/OAuth2CallbackPage';
import { OAuth2ConsentsPage } from '../pages/OAuth2ConsentsPage';
import { OAuth2DemoPage } from '../pages/OAuth2DemoPage';
import { PermissionsPage } from '../pages/PermissionsPage';
import { RolesPage } from '../pages/RolesPage';
import { TenantsPage } from '../pages/TenantsPage';
import { UserDetailPage } from '../pages/UserDetailPage';
import { UsersPage } from '../pages/UsersPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/oauth2/callback', element: <OAuth2CallbackPage /> },
  { path: '/access-denied', element: <AccessDeniedPage /> },
  {
    element: <AuthGate><TenantProvider><AdminLayout /></TenantProvider></AuthGate>,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/tenants', element: <TenantsPage /> },
      { path: '/users', element: <UsersPage /> },
      { path: '/users/:userId', element: <UserDetailPage /> },
      { path: '/groups', element: <GroupsPage /> },
      { path: '/groups/:groupId', element: <GroupDetailPage /> },
      { path: '/roles', element: <RolesPage /> },
      { path: '/permissions', element: <PermissionsPage /> },
      { path: '/applications', element: <ApplicationsPage /> },
      { path: '/clients', element: <ClientsPage /> },
      { path: '/oauth2-consents', element: <OAuth2ConsentsPage /> },
      { path: '/mfa', element: <MfaPage /> },
      { path: '/audit-logs', element: <AuditLogsPage /> },
      { path: '/iam-workflow', element: <IamWorkflowPage /> },
      { path: '/oauth2-demo', element: <OAuth2DemoPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
];
