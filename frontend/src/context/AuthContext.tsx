import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CurrentUser, getCurrentUser } from '../api/auth';
import { getAccessToken, isAccessTokenExpired } from '../api/storage';
import { isAuthenticationFailure } from '../utils/authErrors';

export interface AuthContextValue {
  user?: CurrentUser;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  hasPermission: (permission: string) => boolean;
  sessionExpired: boolean;
  authenticationFailed: boolean;
  error: Error | null;
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [, setAuthStorageVersion] = useState(0);
  useEffect(() => {
    const onAuthStorageChanged = () => {
      setAuthStorageVersion((version) => version + 1);
      void queryClient.invalidateQueries({ queryKey: ['current-user'] });
    };
    window.addEventListener('admin-console-settings-changed', onAuthStorageChanged);
    return () => window.removeEventListener('admin-console-settings-changed', onAuthStorageChanged);
  }, [queryClient]);

  const hasAccessToken = Boolean(getAccessToken());
  const sessionExpired = hasAccessToken && isAccessTokenExpired();
  const currentUser = useQuery({
    queryKey: ['current-user'],
    queryFn: getCurrentUser,
    enabled: hasAccessToken && !sessionExpired,
    retry: false,
  });
  const roles = currentUser.data?.effectiveRoles ?? currentUser.data?.roles ?? [];
  const permissions = currentUser.data?.effectivePermissions ?? [];
  const hasPermission = (permission: string) =>
    roles.includes('platform-admin') || permissions.includes('iam.admin') || permissions.includes(permission);
  const value: AuthContextValue = {
    user: currentUser.data,
    isLoading: currentUser.isLoading,
    isAuthenticated: Boolean(currentUser.data) && !sessionExpired,
    isAdmin: Boolean(currentUser.data?.isPlatformAdmin || currentUser.data?.isTenantAdmin)
      || roles.includes('platform-admin')
      || roles.includes('tenant-admin')
      || permissions.some((permission) => SYSTEM_IAM_PERMISSIONS.has(permission)),
    hasPermission,
    sessionExpired,
    authenticationFailed: isAuthenticationFailure(currentUser.error),
    error: currentUser.error,
    retry: () => { void currentUser.refetch(); },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

const SYSTEM_IAM_PERMISSIONS = new Set([
  'iam.admin',
  'iam.tenants.read',
  'iam.tenants.write',
  'iam.users.read',
  'iam.users.write',
  'iam.groups.read',
  'iam.groups.write',
  'iam.roles.read',
  'iam.roles.write',
  'iam.permissions.read',
  'iam.permissions.write',
  'iam.resource-servers.read',
  'iam.resource-servers.write',
  'iam.clients.read',
  'iam.clients.write',
  'iam.audit.read',
  'iam.mfa.manage',
]);
