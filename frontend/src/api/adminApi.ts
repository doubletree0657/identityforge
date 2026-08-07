import { apiRequest } from './client';
import {
  AuditLogResponse,
  ClientResponse,
  ClientSecretResponse,
  ClientType,
  GroupResponse,
  MfaEnrollmentResponse,
  MfaStatusResponse,
  OAuth2ConsentResponse,
  PageResponse,
  PermissionResponse,
  QueryParams,
  ResourcePermissionResponse,
  ResourceServerResponse,
  ResourceServerStatus,
  RoleResponse,
  TenantResponse,
  TenantStatus,
  TotpVerificationResponse,
  UserAttributeResponse,
  UserAttributeValueType,
  UserProfileResponse,
  UserResponse,
  AccountStatus,
  ClientStatus,
} from '../types/api';

const cleanParams = (params?: object) =>
  Object.fromEntries(Object.entries(params ?? {}).filter(([, value]) => value !== undefined && value !== ''));

export const adminApi = {
  tenants: {
    list: (params?: QueryParams) => apiRequest<PageResponse<TenantResponse>>('GET', '/api/tenants', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<TenantResponse>('GET', `/api/tenants/${id}`),
    create: (body: { name: string; slug: string }) => apiRequest<TenantResponse>('POST', '/api/tenants', body),
    update: (id: string, body: { name?: string; slug?: string; status?: TenantStatus }) =>
      apiRequest<TenantResponse>('PUT', `/api/tenants/${id}`, body),
  },
  users: {
    list: (params?: QueryParams) => apiRequest<PageResponse<UserResponse>>('GET', '/api/users', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<UserResponse>('GET', `/api/users/${id}`),
    create: (body: { tenantId: string; username: string; displayName: string }) =>
      apiRequest<UserResponse>('POST', '/api/users', body),
    update: (
      id: string,
      body: {
        displayName?: string;
        email?: string;
        emailVerified?: boolean;
        phoneNumber?: string;
        phoneNumberVerified?: boolean;
        accountStatus?: AccountStatus;
      },
    ) => apiRequest<UserResponse>('PUT', `/api/users/${id}`, body),
    setPassword: (id: string, body: { newPassword: string; passwordResetRequired?: boolean }) =>
      apiRequest<UserResponse>('PUT', `/api/users/${id}/password`, body),
    assignRole: (userId: string, roleId: string) =>
      apiRequest<UserResponse>('POST', `/api/users/${userId}/roles/${roleId}`),
    removeRole: (userId: string, roleId: string) =>
      apiRequest<UserResponse>('DELETE', `/api/users/${userId}/roles/${roleId}`),
    profile: (userId: string) => apiRequest<UserProfileResponse>('GET', `/api/users/${userId}/profile`),
    updateProfile: (userId: string, body: Partial<UserProfileResponse>) =>
      apiRequest<UserProfileResponse>('PUT', `/api/users/${userId}/profile`, body),
    attributes: (userId: string) => apiRequest<UserAttributeResponse[]>('GET', `/api/users/${userId}/attributes`),
    setAttribute: (userId: string, name: string, body: { value: string; valueType: UserAttributeValueType }) =>
      apiRequest<UserAttributeResponse>('PUT', `/api/users/${userId}/attributes/${encodeURIComponent(name)}`, body),
    deleteAttribute: (userId: string, name: string) =>
      apiRequest<void>('DELETE', `/api/users/${userId}/attributes/${encodeURIComponent(name)}`),
  },
  groups: {
    list: (params?: QueryParams) => apiRequest<PageResponse<GroupResponse>>('GET', '/api/groups', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<GroupResponse>('GET', `/api/groups/${id}`),
    create: (body: { tenantId: string; name: string; displayName?: string; description?: string }) =>
      apiRequest<GroupResponse>('POST', '/api/groups', body),
    update: (id: string, body: { name?: string; displayName?: string; description?: string }) =>
      apiRequest<GroupResponse>('PUT', `/api/groups/${id}`, body),
    addMember: (groupId: string, userId: string) =>
      apiRequest<GroupResponse>('POST', `/api/groups/${groupId}/members/${userId}`),
    removeMember: (groupId: string, userId: string) =>
      apiRequest<GroupResponse>('DELETE', `/api/groups/${groupId}/members/${userId}`),
    members: (groupId: string) => apiRequest<UserResponse[]>('GET', `/api/groups/${groupId}/members`),
    assignRole: (groupId: string, roleId: string) =>
      apiRequest<GroupResponse>('POST', `/api/groups/${groupId}/roles/${roleId}`),
    removeRole: (groupId: string, roleId: string) =>
      apiRequest<GroupResponse>('DELETE', `/api/groups/${groupId}/roles/${roleId}`),
    roles: (groupId: string) => apiRequest<RoleResponse[]>('GET', `/api/groups/${groupId}/roles`),
  },
  roles: {
    list: (params?: QueryParams) => apiRequest<PageResponse<RoleResponse>>('GET', '/api/roles', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<RoleResponse>('GET', `/api/roles/${id}`),
    create: (body: { tenantId: string; name: string }) => apiRequest<RoleResponse>('POST', '/api/roles', body),
    update: (id: string, body: { name: string }) => apiRequest<RoleResponse>('PUT', `/api/roles/${id}`, body),
    assignPermission: (roleId: string, permissionId: string) =>
      apiRequest<RoleResponse>('POST', `/api/roles/${roleId}/permissions/${permissionId}`),
    removePermission: (roleId: string, permissionId: string) =>
      apiRequest<RoleResponse>('DELETE', `/api/roles/${roleId}/permissions/${permissionId}`),
  },
  permissions: {
    list: (params?: QueryParams) =>
      apiRequest<PageResponse<PermissionResponse>>('GET', '/api/permissions', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<PermissionResponse>('GET', `/api/permissions/${id}`),
    create: (body: { name: string }) => apiRequest<PermissionResponse>('POST', '/api/permissions', body),
  },
  resourceServers: {
    list: (params?: QueryParams) =>
      apiRequest<PageResponse<ResourceServerResponse>>('GET', '/api/resource-servers', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<ResourceServerResponse>('GET', `/api/resource-servers/${id}`),
    create: (body: { tenantId: string; identifier: string; name: string; description?: string }) =>
      apiRequest<ResourceServerResponse>('POST', '/api/resource-servers', body),
    update: (id: string, body: { identifier?: string; name?: string; description?: string; status?: ResourceServerStatus }) =>
      apiRequest<ResourceServerResponse>('PUT', `/api/resource-servers/${id}`, body),
    disable: (id: string) => apiRequest<ResourceServerResponse>('POST', `/api/resource-servers/${id}/disable`),
    reactivate: (id: string) => apiRequest<ResourceServerResponse>('POST', `/api/resource-servers/${id}/reactivate`),
    permissions: (id: string) => apiRequest<ResourcePermissionResponse[]>('GET', `/api/resource-servers/${id}/permissions`),
    createPermission: (id: string, body: { name: string; displayName?: string; description?: string }) =>
      apiRequest<ResourcePermissionResponse>('POST', `/api/resource-servers/${id}/permissions`, body),
    updatePermission: (id: string, permissionId: string, body: { name?: string; displayName?: string; description?: string }) =>
      apiRequest<ResourcePermissionResponse>('PUT', `/api/resource-servers/${id}/permissions/${permissionId}`, body),
  },
  clients: {
    list: (params?: QueryParams) => apiRequest<PageResponse<ClientResponse>>('GET', '/api/clients', undefined, cleanParams(params)),
    get: (id: string) => apiRequest<ClientResponse>('GET', `/api/clients/${id}`),
    create: (body: {
      tenantId: string;
      clientId: string;
      name: string;
      clientType?: ClientType;
      requirePkce?: boolean;
      requireConsent?: boolean;
      redirectUris?: string[];
      grantTypes?: string[];
      scopes?: string[];
      authenticationMethods?: string[];
      resourceServerId?: string;
    }) => apiRequest<ClientSecretResponse>('POST', '/api/clients', body),
    update: (id: string, body: {
      clientName?: string;
      status?: ClientStatus;
      requirePkce?: boolean;
      requireConsent?: boolean;
      redirectUris?: string[];
      grantTypes?: string[];
      scopes?: string[];
      authenticationMethods?: string[];
      resourceServerId?: string;
    }) => apiRequest<ClientResponse>('PUT', `/api/clients/${id}`, body),
    rotateSecret: (id: string) => apiRequest<ClientSecretResponse>('POST', `/api/clients/${id}/secret/rotation`),
    resourcePermissions: (id: string) =>
      apiRequest<ResourcePermissionResponse[]>('GET', `/api/clients/${id}/resource-permissions`),
    assignResourcePermission: (id: string, permissionId: string) =>
      apiRequest<ClientResponse>('POST', `/api/clients/${id}/resource-permissions/${permissionId}`),
    removeResourcePermission: (id: string, permissionId: string) =>
      apiRequest<ClientResponse>('DELETE', `/api/clients/${id}/resource-permissions/${permissionId}`),
  },
  oauth2Consents: {
    list: (params?: { userId?: string }) =>
      apiRequest<OAuth2ConsentResponse[]>('GET', '/api/oauth2/consents', undefined, cleanParams(params)),
    me: () => apiRequest<OAuth2ConsentResponse[]>('GET', '/api/oauth2/consents/me'),
    revoke: (clientId: string, userId: string) =>
      apiRequest<void>('DELETE', `/api/oauth2/consents/${encodeURIComponent(clientId)}`, undefined, { userId }),
    revokeMe: (clientId: string) =>
      apiRequest<void>('DELETE', `/api/oauth2/consents/me/${encodeURIComponent(clientId)}`),
  },
  mfa: {
    enrollTotp: (userId: string) => apiRequest<MfaEnrollmentResponse>('POST', `/api/users/${userId}/mfa/totp/enrollment`),
    verifyTotp: (userId: string, code: string) =>
      apiRequest<TotpVerificationResponse>('POST', `/api/users/${userId}/mfa/totp/verification`, { code }),
    disableTotp: (userId: string) => apiRequest<MfaStatusResponse>('DELETE', `/api/users/${userId}/mfa/totp`),
  },
  auditLogs: {
    list: (params?: QueryParams) =>
      apiRequest<PageResponse<AuditLogResponse>>('GET', '/api/audit-logs', undefined, cleanParams(params)),
  },
};
