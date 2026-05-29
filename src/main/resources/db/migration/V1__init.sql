CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenants_name UNIQUE (name),
    CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_number VARCHAR(64),
    phone_number_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    given_name VARCHAR(255),
    family_name VARCHAR(255),
    preferred_name VARCHAR(255),
    locale VARCHAR(32),
    timezone VARCHAR(64),
    avatar_url VARCHAR(1024),
    job_title VARCHAR(255),
    department VARCHAR(255),
    organization VARCHAR(255),
    employee_number VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE password_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    password_hash VARCHAR(255),
    password_updated_at TIMESTAMPTZ,
    password_reset_required BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_password_credentials_user UNIQUE (user_id),
    CONSTRAINT fk_password_credentials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE totp_credentials (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    secret_ciphertext VARCHAR(1024) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_totp_credentials_user UNIQUE (user_id),
    CONSTRAINT fk_totp_credentials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE user_attributes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    attribute_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_attributes_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_user_attributes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    category VARCHAR(64),
    system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_permissions_name UNIQUE (name)
);

CREATE UNIQUE INDEX uq_permissions_system_name
    ON permissions (name)
    WHERE system_managed = TRUE;

CREATE INDEX idx_permissions_system_managed ON permissions (system_managed);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE TABLE resource_servers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_resource_servers_tenant_identifier UNIQUE (tenant_id, identifier),
    CONSTRAINT fk_resource_servers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE resource_permissions (
    id UUID PRIMARY KEY,
    resource_server_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_resource_permissions_server_name UNIQUE (resource_server_id, name),
    CONSTRAINT fk_resource_permissions_server FOREIGN KEY (resource_server_id) REFERENCES resource_servers (id) ON DELETE CASCADE
);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_groups_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_groups_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE group_memberships (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_group_memberships_group_user UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_memberships_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE group_roles (
    group_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (group_id, role_id),
    CONSTRAINT fk_group_roles_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE clients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    resource_server_id UUID,
    client_id VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    client_type VARCHAR(32) NOT NULL DEFAULT 'CONFIDENTIAL',
    client_secret_hash VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    require_pkce BOOLEAN NOT NULL DEFAULT TRUE,
    require_consent BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_clients_tenant_client_id UNIQUE (tenant_id, client_id),
    CONSTRAINT fk_clients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_clients_resource_server FOREIGN KEY (resource_server_id) REFERENCES resource_servers (id)
);

CREATE TABLE client_redirect_uris (
    client_id UUID NOT NULL,
    redirect_uri VARCHAR(1024) NOT NULL,
    PRIMARY KEY (client_id, redirect_uri),
    CONSTRAINT fk_client_redirect_uris_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE TABLE client_grant_types (
    client_id UUID NOT NULL,
    grant_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (client_id, grant_type),
    CONSTRAINT fk_client_grant_types_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE TABLE client_scopes (
    client_id UUID NOT NULL,
    scope VARCHAR(255) NOT NULL,
    PRIMARY KEY (client_id, scope),
    CONSTRAINT fk_client_scopes_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE TABLE client_authentication_methods (
    client_id UUID NOT NULL,
    authentication_method VARCHAR(128) NOT NULL,
    PRIMARY KEY (client_id, authentication_method),
    CONSTRAINT fk_client_authentication_methods_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE
);

CREATE TABLE client_allowed_resource_permissions (
    client_id UUID NOT NULL,
    resource_permission_id UUID NOT NULL,
    PRIMARY KEY (client_id, resource_permission_id),
    CONSTRAINT fk_client_allowed_resource_permissions_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_client_allowed_resource_permissions_permission FOREIGN KEY (resource_permission_id) REFERENCES resource_permissions (id) ON DELETE CASCADE
);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000) DEFAULT NULL,
    attributes TEXT DEFAULT NULL,
    state VARCHAR(500) DEFAULT NULL,
    authorization_code_value TEXT DEFAULT NULL,
    authorization_code_issued_at TIMESTAMPTZ DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ DEFAULT NULL,
    authorization_code_metadata TEXT DEFAULT NULL,
    access_token_value TEXT DEFAULT NULL,
    access_token_issued_at TIMESTAMPTZ DEFAULT NULL,
    access_token_expires_at TIMESTAMPTZ DEFAULT NULL,
    access_token_metadata TEXT DEFAULT NULL,
    access_token_type VARCHAR(100) DEFAULT NULL,
    access_token_scopes VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value TEXT DEFAULT NULL,
    oidc_id_token_issued_at TIMESTAMPTZ DEFAULT NULL,
    oidc_id_token_expires_at TIMESTAMPTZ DEFAULT NULL,
    oidc_id_token_metadata TEXT DEFAULT NULL,
    refresh_token_value TEXT DEFAULT NULL,
    refresh_token_issued_at TIMESTAMPTZ DEFAULT NULL,
    refresh_token_expires_at TIMESTAMPTZ DEFAULT NULL,
    refresh_token_metadata TEXT DEFAULT NULL,
    user_code_value TEXT DEFAULT NULL,
    user_code_issued_at TIMESTAMPTZ DEFAULT NULL,
    user_code_expires_at TIMESTAMPTZ DEFAULT NULL,
    user_code_metadata TEXT DEFAULT NULL,
    device_code_value TEXT DEFAULT NULL,
    device_code_issued_at TIMESTAMPTZ DEFAULT NULL,
    device_code_expires_at TIMESTAMPTZ DEFAULT NULL,
    device_code_metadata TEXT DEFAULT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_type VARCHAR(32) NOT NULL,
    actor_id UUID,
    action VARCHAR(255) NOT NULL,
    resource_type VARCHAR(255) NOT NULL,
    resource_id UUID NOT NULL,
    result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    ip_address VARCHAR(128),
    user_agent VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_tenant_account_status ON users (tenant_id, account_status);
CREATE INDEX idx_totp_credentials_user_id ON totp_credentials (user_id);
CREATE INDEX idx_roles_tenant_id ON roles (tenant_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
CREATE INDEX idx_resource_servers_tenant_id ON resource_servers (tenant_id);
CREATE INDEX idx_resource_permissions_server_id ON resource_permissions (resource_server_id);
CREATE INDEX idx_groups_tenant_id ON groups (tenant_id);
CREATE INDEX idx_group_memberships_user_id ON group_memberships (user_id);
CREATE INDEX idx_group_roles_role_id ON group_roles (role_id);
CREATE INDEX idx_clients_tenant_id ON clients (tenant_id);
CREATE INDEX idx_clients_resource_server_id ON clients (resource_server_id);
CREATE INDEX idx_client_allowed_resource_permissions_permission_id ON client_allowed_resource_permissions (resource_permission_id);
CREATE INDEX idx_oauth2_authorization_registered_client_id ON oauth2_authorization (registered_client_id);
CREATE INDEX idx_oauth2_authorization_principal_name ON oauth2_authorization (principal_name);
CREATE INDEX idx_oauth2_authorization_consent_principal_name ON oauth2_authorization_consent (principal_name);
CREATE INDEX idx_audit_logs_tenant_id_created_at ON audit_logs (tenant_id, created_at);
CREATE INDEX idx_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_logs_action_created_at ON audit_logs (action, created_at);
