package io.github.doubletree.iam.oauth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns durable OAuth2 grant-family revocation and expired authorization cleanup.
 */
@Service
public class OAuth2AuthorizationLifecycleService {

    private final OAuth2AuthorizationService authorizationService;
    private final JdbcOperations jdbcOperations;

    public OAuth2AuthorizationLifecycleService(
            OAuth2AuthorizationService authorizationService,
            ObjectProvider<JdbcOperations> jdbcOperations) {
        this.authorizationService = authorizationService;
        this.jdbcOperations = jdbcOperations.getIfAvailable();
    }

    @Transactional
    public boolean revokeAuthorizationFamily(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(tokenValue, null);
        if (authorization == null) {
            return false;
        }
        authorizationService.remove(authorization);
        return true;
    }

    @Transactional
    public void recordRefreshTokenRotation(String previousRefreshToken, String currentRefreshToken) {
        if (jdbcOperations == null || isBlank(previousRefreshToken) || isBlank(currentRefreshToken)) {
            return;
        }
        OAuth2Authorization authorization = authorizationService.findByToken(
                currentRefreshToken, OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null || authorization.getRefreshToken() == null) {
            return;
        }
        Instant expiresAt = authorization.getRefreshToken().getToken().getExpiresAt();
        if (expiresAt == null) {
            return;
        }
        jdbcOperations.update("""
                insert into oauth2_refresh_token_history (token_hash, authorization_id, expires_at)
                values (?, ?, ?)
                on conflict (token_hash) do nothing
                """, tokenHash(previousRefreshToken), authorization.getId(), java.sql.Timestamp.from(expiresAt));
    }

    @Transactional
    public boolean revokeReusedRefreshToken(String refreshToken) {
        if (jdbcOperations == null || isBlank(refreshToken)) {
            return false;
        }
        return jdbcOperations.update("""
                delete from oauth2_authorization
                 where id = (
                     select authorization_id
                       from oauth2_refresh_token_history
                      where token_hash = ?
                        and expires_at > current_timestamp
                 )
                """, tokenHash(refreshToken)) > 0;
    }

    @Transactional
    public int revokeUserClientAuthorizations(UUID userId, String clientId) {
        if (jdbcOperations == null || userId == null || clientId == null || clientId.isBlank()) {
            return 0;
        }
        return jdbcOperations.update("""
                delete from oauth2_authorization oa
                 using users u, clients c
                 where u.id = ?
                   and c.client_id = ?
                   and c.tenant_id = u.tenant_id
                   and oa.registered_client_id = c.id::text
                   and oa.principal_name = u.username
                """, userId, clientId);
    }

    @Transactional
    public int purgeExpiredAuthorizations() {
        if (jdbcOperations == null) {
            return 0;
        }
        jdbcOperations.update("delete from oauth2_refresh_token_history where expires_at < current_timestamp");
        return jdbcOperations.update("""
                delete from oauth2_authorization
                 where greatest(
                         coalesce(authorization_code_expires_at, '-infinity'::timestamptz),
                         coalesce(access_token_expires_at, '-infinity'::timestamptz),
                         coalesce(oidc_id_token_expires_at, '-infinity'::timestamptz),
                         coalesce(refresh_token_expires_at, '-infinity'::timestamptz),
                         coalesce(user_code_expires_at, '-infinity'::timestamptz),
                         coalesce(device_code_expires_at, '-infinity'::timestamptz)
                       ) < current_timestamp
                """);
    }

    private String tokenHash(String tokenValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
