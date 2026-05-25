export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED' | 'PENDING';
export type ClientType = 'CONFIDENTIAL' | 'PUBLIC';
export type ClientStatus = 'ACTIVE' | 'DISABLED';
export type ResourceServerStatus = 'ACTIVE' | 'DISABLED';
export type UserAttributeValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON';
export type AuditActorType = 'API_CLIENT' | 'USER' | 'SYSTEM';
export type AuditResult = 'SUCCESS' | 'FAILURE';

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ErrorResponse {
  error: string;
  message: string;
}

export interface TenantResponse {
  id: string;
  name: string;
  slug: string;
  status: TenantStatus;
  createdAt: string;
  updatedAt: string;
}

export interface UserResponse {
  id: string;
  tenantId: string;
  username: string;
  displayName: string;
  email?: string;
  emailVerified: boolean;
  phoneNumber?: string;
  phoneNumberVerified: boolean;
  accountStatus: AccountStatus;
  createdAt: string;
  updatedAt: string;
  roleIds: string[];
  groupRoleIds: string[];
  directRoles: string[];
  groupRoles: string[];
  effectiveRoles: string[];
  directPermissions: string[];
  groupPermissions: string[];
  effectivePermissions: string[];
}

export interface UserProfileResponse {
  id?: string;
  userId: string;
  givenName?: string;
  familyName?: string;
  preferredName?: string;
  locale?: string;
  timezone?: string;
  avatarUrl?: string;
  jobTitle?: string;
  department?: string;
  organization?: string;
  employeeNumber?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserAttributeResponse {
  id: string;
  userId: string;
  name: string;
  value: string;
  valueType: UserAttributeValueType;
  createdAt: string;
  updatedAt: string;
}

export interface GroupResponse {
  id: string;
  tenantId: string;
  name: string;
  displayName: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
  memberIds: string[];
  roleIds: string[];
}

export interface RoleResponse {
  id: string;
  tenantId: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  permissionIds: string[];
  userAssignmentCount: number;
  groupAssignmentCount: number;
}

export interface PermissionResponse {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  category: string;
  systemManaged: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ResourceServerResponse {
  id: string;
  tenantId: string;
  identifier: string;
  name: string;
  description?: string;
  status: ResourceServerStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ResourcePermissionResponse {
  id: string;
  resourceServerId: string;
  name: string;
  displayName: string;
  description?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClientResponse {
  id: string;
  tenantId: string;
  clientId: string;
  name: string;
  clientType: ClientType;
  status: ClientStatus;
  requirePkce: boolean;
  requireConsent: boolean;
  resourceServerId?: string;
  resourceServerName?: string;
  redirectUris: string[];
  grantTypes: string[];
  scopes: string[];
  authenticationMethods: string[];
  allowedResourcePermissions: ResourcePermissionResponse[];
}

export interface ClientSecretResponse {
  client: ClientResponse;
  clientSecret?: string;
}

export interface MfaEnrollmentResponse {
  userId: string;
  secret: string;
  otpauthUri?: string;
}

export interface TotpVerificationResponse {
  userId: string;
  verified: boolean;
}

export interface MfaStatusResponse {
  userId: string;
  totpEnabled: boolean;
}

export interface AuditLogResponse {
  id: string;
  tenantId?: string;
  actorType: AuditActorType;
  actorId?: string;
  action: string;
  resourceType: string;
  resourceId: string;
  result: AuditResult;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

export interface QueryParams {
  page?: number;
  size?: number;
  tenantId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  result?: AuditResult;
}
