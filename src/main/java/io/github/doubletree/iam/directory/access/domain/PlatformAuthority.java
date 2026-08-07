package io.github.doubletree.iam.directory.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_authorities")
public class PlatformAuthority {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt = Instant.now();

    protected PlatformAuthority() {
    }

    public static PlatformAuthority grant(UUID userId, UUID grantedBy) {
        PlatformAuthority authority = new PlatformAuthority();
        authority.userId = userId;
        authority.grantedBy = grantedBy;
        return authority;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
