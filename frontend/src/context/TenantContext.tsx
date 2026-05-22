import { createContext, ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { adminApi } from '../api/adminApi';
import { TenantResponse } from '../types/api';

const TENANT_STORAGE_KEY = 'iam-admin.selectedTenantId';

interface TenantContextValue {
  tenants: TenantResponse[];
  selectedTenantId: string;
  selectedTenant?: TenantResponse;
  setSelectedTenantId: (tenantId: string) => void;
  isLoading: boolean;
  error: Error | null;
}

const TenantContext = createContext<TenantContextValue | null>(null);

export function TenantProvider({ children }: { children: ReactNode }) {
  const [selectedTenantId, setSelectedTenantIdState] = useState(() => localStorage.getItem(TENANT_STORAGE_KEY) ?? '');
  const tenants = useQuery({
    queryKey: ['tenant-context-tenants'],
    queryFn: () => adminApi.tenants.list({ size: 100 }),
  });

  const tenantItems = tenants.data?.items ?? [];
  const selectedTenant = tenantItems.find((tenant) => tenant.id === selectedTenantId);

  useEffect(() => {
    if (selectedTenantId && tenants.data && !selectedTenant) {
      setSelectedTenantIdState('');
      localStorage.removeItem(TENANT_STORAGE_KEY);
      return;
    }
    if (!selectedTenantId && tenantItems.length === 1) {
      setSelectedTenantIdState(tenantItems[0].id);
      localStorage.setItem(TENANT_STORAGE_KEY, tenantItems[0].id);
    }
  }, [selectedTenantId, selectedTenant, tenantItems, tenants.data]);

  const value = useMemo<TenantContextValue>(() => ({
    tenants: tenantItems,
    selectedTenantId,
    selectedTenant,
    setSelectedTenantId: (tenantId: string) => {
      setSelectedTenantIdState(tenantId);
      if (tenantId) {
        localStorage.setItem(TENANT_STORAGE_KEY, tenantId);
      } else {
        localStorage.removeItem(TENANT_STORAGE_KEY);
      }
    },
    isLoading: tenants.isLoading,
    error: tenants.error,
  }), [tenantItems, selectedTenant, selectedTenantId, tenants.error, tenants.isLoading]);

  return <TenantContext.Provider value={value}>{children}</TenantContext.Provider>;
}

export function useTenantContext() {
  const context = useContext(TenantContext);
  if (!context) {
    throw new Error('useTenantContext must be used inside TenantProvider');
  }
  return context;
}
