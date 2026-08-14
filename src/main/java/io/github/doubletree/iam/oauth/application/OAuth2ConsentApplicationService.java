package io.github.doubletree.iam.oauth.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.oauth.api.OAuth2ConsentView;
import io.github.doubletree.iam.applications.domain.Client;
import io.github.doubletree.iam.directory.domain.User;
import io.github.doubletree.iam.applications.infrastructure.persistence.ClientRepository;
import io.github.doubletree.iam.directory.infrastructure.persistence.UserRepository;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuth2ConsentApplicationService {

    private final JdbcOperations jdbcOperations;
    private final OAuth2AuthorizationConsentService authorizationConsentService;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AdminAuthorizationService adminAuthorizationService;
    private final AuditApplicationService auditApplicationService;
    private final OAuth2AuthorizationLifecycleService authorizationLifecycleService;

    public OAuth2ConsentApplicationService(
            JdbcOperations jdbcOperations,
            OAuth2AuthorizationConsentService authorizationConsentService,
            ClientRepository clientRepository,
            UserRepository userRepository,
            AdminAuthorizationService adminAuthorizationService,
            AuditApplicationService auditApplicationService,
            OAuth2AuthorizationLifecycleService authorizationLifecycleService) {
        this.jdbcOperations = jdbcOperations;
        this.authorizationConsentService = authorizationConsentService;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.adminAuthorizationService = adminAuthorizationService;
        this.auditApplicationService = auditApplicationService;
        this.authorizationLifecycleService = authorizationLifecycleService;
    }

    @Transactional(readOnly = true)
    public List<OAuth2ConsentView> listConsents(UUID userId) {
        if (userId != null) {
            User user = loadUser(userId);
            adminAuthorizationService.assertTenantAccess(user.getTenant().getId());
            return consentRowsForUser(user);
        }
        UUID tenantId = adminAuthorizationService.tenantIdForList(null);
        String sql = """
                select u.id as user_id, u.username, c.client_id, c.client_name, rs.name as resource_server_name, oc.authorities
                from oauth2_authorization_consent oc
                join users u on u.username = oc.principal_name
                join clients c on c.id::text = oc.registered_client_id
                left join resource_servers rs on rs.id = c.resource_server_id
                where (?::uuid is null or c.tenant_id = ?::uuid)
                order by u.username, c.client_name
                """;
        return jdbcOperations.query(sql, (rs, rowNum) -> new OAuth2ConsentView(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("client_id"),
                rs.getString("client_name"),
                scopes(rs.getString("authorities")),
                rs.getString("resource_server_name")), tenantId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<OAuth2ConsentView> listConsentsForCurrentUser(Jwt jwt) {
        User user = loadCurrentUser(jwt);
        return consentRowsForUser(user);
    }

    @Transactional
    public void revokeConsent(UUID userId, String clientId) {
        User user = loadUser(userId);
        adminAuthorizationService.assertTenantAccess(user.getTenant().getId());
        Client client = loadClient(user, clientId);
        removeConsent(user, client);
    }

    @Transactional
    public void revokeCurrentUserConsent(Jwt jwt, String clientId) {
        User user = loadCurrentUser(jwt);
        Client client = loadClient(user, clientId);
        removeConsent(user, client);
    }

    private List<OAuth2ConsentView> consentRowsForUser(User user) {
        String sql = """
                select c.client_id, c.client_name, rs.name as resource_server_name, oc.authorities
                from oauth2_authorization_consent oc
                join clients c on c.id::text = oc.registered_client_id
                left join resource_servers rs on rs.id = c.resource_server_id
                where oc.principal_name = ? and c.tenant_id = ?
                order by c.client_name
                """;
        return jdbcOperations.query(sql, (rs, rowNum) -> new OAuth2ConsentView(
                user.getId(),
                user.getUsername(),
                rs.getString("client_id"),
                rs.getString("client_name"),
                scopes(rs.getString("authorities")),
                rs.getString("resource_server_name")), user.getUsername(), user.getTenant().getId());
    }

    private void removeConsent(User user, Client client) {
        var consent = authorizationConsentService.findById(client.getId().toString(), user.getUsername());
        if (consent != null) {
            authorizationConsentService.remove(consent);
            authorizationLifecycleService.revokeUserClientAuthorizations(user.getId(), client.getClientId());
            auditApplicationService.recordEvent(
                    user.getTenant().getId(), "OAUTH2_CONSENT_REVOKED", "USER", user.getId());
        }
    }

    private Client loadClient(User user, String clientId) {
        return clientRepository.findByTenantIdAndClientId(user.getTenant().getId(), clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private User loadCurrentUser(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");
        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("Current user token is missing user_id");
        }
        return loadUser(UUID.fromString(userId));
    }

    private Set<String> scopes(String authorities) {
        if (authorities == null || authorities.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(authorities.split(","))
                .map(String::trim)
                .filter(authority -> !authority.isBlank())
                .map(authority -> authority.startsWith("SCOPE_") ? authority.substring("SCOPE_".length()) : authority)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
