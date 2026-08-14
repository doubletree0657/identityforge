-- Keep persisted OAuth2 grants aligned with the security state that produced them.
-- These triggers are deliberately database-local; distributed denylist/session
-- infrastructure remains outside this milestone.

CREATE INDEX idx_oauth2_authorization_principal_client
    ON oauth2_authorization (principal_name, registered_client_id);
CREATE INDEX idx_oauth2_authorization_access_expires
    ON oauth2_authorization (access_token_expires_at);
CREATE INDEX idx_oauth2_authorization_refresh_expires
    ON oauth2_authorization (refresh_token_expires_at);

CREATE TABLE oauth2_refresh_token_history (
    token_hash CHAR(64) PRIMARY KEY,
    authorization_id VARCHAR(100) NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_refresh_token_history_authorization
        FOREIGN KEY (authorization_id) REFERENCES oauth2_authorization (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_history_expires
    ON oauth2_refresh_token_history (expires_at);

CREATE FUNCTION revoke_oauth2_authorizations_for_user_id() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM oauth2_authorization oa
    USING users u, clients c
    WHERE u.id = NEW.user_id
      AND c.tenant_id = u.tenant_id
      AND oa.registered_client_id = c.id::text
      AND oa.principal_name = u.username;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_on_security_version_change
AFTER UPDATE OF credentials_version ON password_credentials
FOR EACH ROW
WHEN (OLD.credentials_version IS DISTINCT FROM NEW.credentials_version)
EXECUTE FUNCTION revoke_oauth2_authorizations_for_user_id();

CREATE FUNCTION revoke_oauth2_authorizations_for_deleted_user() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM oauth2_authorization oa
    USING clients c
    WHERE c.tenant_id = OLD.tenant_id
      AND oa.registered_client_id = c.id::text
      AND oa.principal_name = OLD.username;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_before_user_delete
BEFORE DELETE ON users
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_deleted_user();

CREATE FUNCTION revoke_oauth2_authorizations_for_renamed_user() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM oauth2_authorization oa
    USING clients c
    WHERE c.tenant_id = OLD.tenant_id
      AND oa.registered_client_id = c.id::text
      AND oa.principal_name = OLD.username;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_on_username_change
AFTER UPDATE OF username ON users
FOR EACH ROW
WHEN (OLD.username IS DISTINCT FROM NEW.username)
EXECUTE FUNCTION revoke_oauth2_authorizations_for_renamed_user();

CREATE FUNCTION revoke_oauth2_authorizations_for_tenant() RETURNS TRIGGER AS $$
BEGIN
    UPDATE password_credentials pc
       SET credentials_version = credentials_version + 1,
           updated_at = current_timestamp
      FROM users u
     WHERE pc.user_id = u.id
       AND u.tenant_id = NEW.id;
    DELETE FROM oauth2_authorization oa
    USING clients c
    WHERE c.tenant_id = NEW.id
      AND oa.registered_client_id = c.id::text;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_on_tenant_status_change
AFTER UPDATE OF status ON tenants
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION revoke_oauth2_authorizations_for_tenant();

CREATE FUNCTION revoke_oauth2_authorizations_for_client() RETURNS TRIGGER AS $$
DECLARE
    affected_client_id UUID;
BEGIN
    affected_client_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END;
    DELETE FROM oauth2_authorization WHERE registered_client_id = affected_client_id::text;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_before_client_delete
BEFORE DELETE ON clients
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client();

CREATE TRIGGER revoke_oauth2_on_client_update
AFTER UPDATE ON clients
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client();

CREATE FUNCTION revoke_oauth2_authorizations_for_client_relation() RETURNS TRIGGER AS $$
DECLARE
    affected_client_id UUID;
BEGIN
    affected_client_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.client_id ELSE NEW.client_id END;
    DELETE FROM oauth2_authorization WHERE registered_client_id = affected_client_id::text;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_on_client_redirect_uri_change
AFTER INSERT OR UPDATE OR DELETE ON client_redirect_uris
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client_relation();

CREATE TRIGGER revoke_oauth2_on_client_grant_type_change
AFTER INSERT OR UPDATE OR DELETE ON client_grant_types
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client_relation();

CREATE TRIGGER revoke_oauth2_on_client_scope_change
AFTER INSERT OR UPDATE OR DELETE ON client_scopes
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client_relation();

CREATE TRIGGER revoke_oauth2_on_client_auth_method_change
AFTER INSERT OR UPDATE OR DELETE ON client_authentication_methods
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client_relation();

CREATE TRIGGER revoke_oauth2_on_client_permission_change
AFTER INSERT OR UPDATE OR DELETE ON client_allowed_resource_permissions
FOR EACH ROW
EXECUTE FUNCTION revoke_oauth2_authorizations_for_client_relation();

CREATE FUNCTION revoke_oauth2_authorizations_for_resource_server() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM oauth2_authorization oa
    USING clients c
    WHERE c.resource_server_id = NEW.id
      AND oa.registered_client_id = c.id::text;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER revoke_oauth2_on_resource_server_status_change
AFTER UPDATE OF status ON resource_servers
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION revoke_oauth2_authorizations_for_resource_server();
