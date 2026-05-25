package io.github.doubletree.iam.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client represents an OAuth2 client registration owned by a tenant.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String clientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType clientType = ClientType.CONFIDENTIAL;

    private String clientSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(nullable = false)
    private boolean requirePkce = true;

    @Column(nullable = false)
    private boolean requireConsent = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_server_id")
    private ResourceServer resourceServer;

    @ElementCollection
    @CollectionTable(name = "client_redirect_uris", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri", nullable = false)
    private Set<String> redirectUris = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "client_grant_types", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "grant_type", nullable = false)
    private Set<String> grantTypes = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "client_scopes", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "scope", nullable = false)
    private Set<String> scopes = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "client_authentication_methods", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "authentication_method", nullable = false)
    private Set<String> authenticationMethods = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(
            name = "client_allowed_resource_permissions",
            joinColumns = @JoinColumn(name = "client_id"),
            inverseJoinColumns = @JoinColumn(name = "resource_permission_id"))
    private Set<ResourcePermission> allowedResourcePermissions = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected Client() {
    }

    public static Client create(Tenant tenant, String clientId, String clientName) {
        Client client = new Client();
        client.setTenant(tenant);
        client.setClientId(clientId);
        client.setClientName(clientName);
        client.addGrantType("authorization_code");
        client.addScope("iam.read");
        client.addAuthenticationMethod("client_secret_basic");
        return client;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public void setClientType(ClientType clientType) {
        if (id != null && this.clientType != null && clientType != null && this.clientType != clientType) {
            throw new IllegalArgumentException("Client type is immutable after creation");
        }
        if (clientType == ClientType.PUBLIC && hasText(clientSecretHash)) {
            throw new IllegalArgumentException("Public clients must not have a client secret");
        }
        this.clientType = clientType;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public void setClientSecretHash(String clientSecretHash) {
        if (clientType == ClientType.PUBLIC && hasText(clientSecretHash)) {
            throw new IllegalArgumentException("Public clients must not have a client secret");
        }
        this.clientSecretHash = clientSecretHash;
    }

    public ClientStatus getStatus() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public boolean isRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(boolean requirePkce) {
        if (clientType == ClientType.PUBLIC && !requirePkce) {
            throw new IllegalArgumentException("Public clients must require PKCE");
        }
        this.requirePkce = requirePkce;
    }

    public boolean isRequireConsent() {
        return requireConsent;
    }

    public void setRequireConsent(boolean requireConsent) {
        this.requireConsent = requireConsent;
    }

    public Set<String> getRedirectUris() {
        return Collections.unmodifiableSet(redirectUris);
    }

    public void setRedirectUris(Set<String> redirectUris) {
        replaceRedirectUris(redirectUris);
    }

    public Set<String> getGrantTypes() {
        return Collections.unmodifiableSet(grantTypes);
    }

    public void setGrantTypes(Set<String> grantTypes) {
        replaceGrantTypes(grantTypes);
    }

    public Set<String> getScopes() {
        return Collections.unmodifiableSet(scopes);
    }

    public void setScopes(Set<String> scopes) {
        replaceScopes(scopes);
    }

    public Set<String> getAuthenticationMethods() {
        return Collections.unmodifiableSet(authenticationMethods);
    }

    public void setAuthenticationMethods(Set<String> authenticationMethods) {
        replaceAuthenticationMethods(authenticationMethods);
    }

    public ResourceServer getResourceServer() {
        return resourceServer;
    }

    public void setResourceServer(ResourceServer resourceServer) {
        this.resourceServer = resourceServer;
    }

    public Set<ResourcePermission> getAllowedResourcePermissions() {
        return Collections.unmodifiableSet(allowedResourcePermissions);
    }

    public void addAllowedResourcePermission(ResourcePermission permission) {
        if (permission != null) {
            allowedResourcePermissions.add(permission);
        }
    }

    public void removeAllowedResourcePermission(ResourcePermission permission) {
        if (permission != null) {
            allowedResourcePermissions.remove(permission);
        }
    }

    public void clearAllowedResourcePermissions() {
        allowedResourcePermissions.clear();
    }

    public void replaceRedirectUris(Set<String> redirectUris) {
        rejectBlankValues(redirectUris, "redirect URI");
        this.redirectUris.clear();
        if (redirectUris != null) {
            this.redirectUris.addAll(redirectUris);
        }
    }

    public void replaceGrantTypes(Set<String> grantTypes) {
        rejectBlankValues(grantTypes, "grant type");
        this.grantTypes.clear();
        if (grantTypes != null) {
            this.grantTypes.addAll(grantTypes);
        }
    }

    public void replaceScopes(Set<String> scopes) {
        rejectBlankValues(scopes, "scope");
        this.scopes.clear();
        if (scopes != null) {
            this.scopes.addAll(scopes);
        }
    }

    public void replaceAuthenticationMethods(Set<String> authenticationMethods) {
        rejectBlankValues(authenticationMethods, "authentication method");
        this.authenticationMethods.clear();
        if (authenticationMethods != null) {
            this.authenticationMethods.addAll(authenticationMethods);
        }
    }

    public void addRedirectUri(String redirectUri) {
        rejectBlankValue(redirectUri, "redirect URI");
        redirectUris.add(redirectUri);
    }

    public void addGrantType(String grantType) {
        rejectBlankValue(grantType, "grant type");
        grantTypes.add(grantType);
    }

    public void addScope(String scope) {
        rejectBlankValue(scope, "scope");
        scopes.add(scope);
    }

    public void addAuthenticationMethod(String authenticationMethod) {
        rejectBlankValue(authenticationMethod, "authentication method");
        authenticationMethods.add(authenticationMethod);
    }

    public void validateRegistration() {
        rejectBlankValues(redirectUris, "redirect URI");
        rejectBlankValues(grantTypes, "grant type");
        rejectBlankValues(scopes, "scope");
        rejectBlankValues(authenticationMethods, "authentication method");
        if (grantTypes != null
                && grantTypes.contains("authorization_code")
                && (redirectUris == null || redirectUris.isEmpty())) {
            throw new IllegalArgumentException("Authorization code clients must have at least one redirect URI");
        }
        if (clientType == ClientType.PUBLIC) {
            if (hasText(clientSecretHash)) {
                throw new IllegalArgumentException("Public clients must not have a client secret");
            }
            if (!requirePkce) {
                throw new IllegalArgumentException("Public clients must require PKCE");
            }
            if (authenticationMethods != null
                    && authenticationMethods.stream().anyMatch(method -> method.startsWith("client_secret"))) {
                throw new IllegalArgumentException("Public clients must not use client secret authentication");
            }
        }
        if (clientType == ClientType.CONFIDENTIAL
                && authenticationMethods != null
                && authenticationMethods.stream().anyMatch(method -> method.startsWith("client_secret"))
                && !hasText(clientSecretHash)) {
            throw new IllegalArgumentException(
                    "Confidential clients using client secret authentication must have a client secret");
        }
    }

    private void rejectBlankValues(Set<String> values, String valueName) {
        if (values != null && values.stream().anyMatch(value -> !hasText(value))) {
            throw new IllegalArgumentException("Client " + valueName + " must not be blank");
        }
    }

    private void rejectBlankValue(String value, String valueName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Client " + valueName + " must not be blank");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
