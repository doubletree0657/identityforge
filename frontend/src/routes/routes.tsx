import { Navigate, RouteObject } from 'react-router-dom';
import { AdminLayout } from '../layout/AdminLayout';
import { AuditLogsPage } from '../pages/AuditLogsPage';
import { ClientsPage } from '../pages/ClientsPage';
import { DashboardPage } from '../pages/DashboardPage';
import { GroupsPage } from '../pages/GroupsPage';
import { IamWorkflowPage } from '../pages/IamWorkflowPage';
import { MfaPage } from '../pages/MfaPage';
import { OAuth2DemoPage } from '../pages/OAuth2DemoPage';
import { PermissionsPage } from '../pages/PermissionsPage';
import { RolesPage } from '../pages/RolesPage';
import { TenantsPage } from '../pages/TenantsPage';
import { UserDetailPage } from '../pages/UserDetailPage';
import { UsersPage } from '../pages/UsersPage';

export const routes: RouteObject[] = [
  {
    element: <AdminLayout />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/tenants', element: <TenantsPage /> },
      { path: '/users', element: <UsersPage /> },
      { path: '/users/:userId', element: <UserDetailPage /> },
      { path: '/groups', element: <GroupsPage /> },
      { path: '/roles', element: <RolesPage /> },
      { path: '/permissions', element: <PermissionsPage /> },
      { path: '/clients', element: <ClientsPage /> },
      { path: '/mfa', element: <MfaPage /> },
      { path: '/audit-logs', element: <AuditLogsPage /> },
      { path: '/iam-workflow', element: <IamWorkflowPage /> },
      { path: '/oauth2-demo', element: <OAuth2DemoPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
];
