import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { CurrentUser, getCurrentUser } from '../api/auth';
import { isAccessTokenExpired } from '../api/storage';

interface AuthContextValue {
  user?: CurrentUser;
  isLoading: boolean;
  isAuthenticated: boolean;
  isAdmin: boolean;
  sessionExpired: boolean;
  error: Error | null;
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

  const sessionExpired = isAccessTokenExpired();
  const currentUser = useQuery({
    queryKey: ['current-user'],
    queryFn: getCurrentUser,
    enabled: !sessionExpired,
    retry: false,
  });
  const roles = currentUser.data?.roles ?? [];
  const value: AuthContextValue = {
    user: currentUser.data,
    isLoading: currentUser.isLoading,
    isAuthenticated: Boolean(currentUser.data) && !sessionExpired,
    isAdmin: roles.includes('platform-admin') || roles.includes('tenant-admin'),
    sessionExpired,
    error: currentUser.error,
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
