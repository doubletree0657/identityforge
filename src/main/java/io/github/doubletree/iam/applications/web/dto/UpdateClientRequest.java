package io.github.doubletree.iam.applications.web.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.github.doubletree.iam.applications.domain.ClientStatus;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public class UpdateClientRequest {

    @Size(min = 1, max = 160)
    private String clientName;

    private ClientStatus status;
    private Boolean requirePkce;
    private Boolean requireConsent;
    private Set<String> redirectUris;
    private Set<String> grantTypes;
    private Set<String> scopes;
    private Set<String> authenticationMethods;
    private UUID resourceServerId;
    private boolean resourceServerIdPresent;

    public String clientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public ClientStatus status() {
        return status;
    }

    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    public Boolean requirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Boolean requirePkce) {
        this.requirePkce = requirePkce;
    }

    public Boolean requireConsent() {
        return requireConsent;
    }

    public void setRequireConsent(Boolean requireConsent) {
        this.requireConsent = requireConsent;
    }

    public Set<String> redirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(Set<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public Set<String> grantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(Set<String> grantTypes) {
        this.grantTypes = grantTypes;
    }

    public Set<String> scopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public Set<String> authenticationMethods() {
        return authenticationMethods;
    }

    public void setAuthenticationMethods(Set<String> authenticationMethods) {
        this.authenticationMethods = authenticationMethods;
    }

    public UUID resourceServerId() {
        return resourceServerId;
    }

    public boolean resourceServerIdPresent() {
        return resourceServerIdPresent;
    }

    @JsonSetter("resourceServerId")
    public void setResourceServerId(String resourceServerId) {
        this.resourceServerIdPresent = true;
        this.resourceServerId = resourceServerId == null || resourceServerId.isBlank()
                ? null
                : UUID.fromString(resourceServerId);
    }
}
