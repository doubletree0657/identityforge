package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.MfaRecoveryCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    long countByUserId(UUID userId);

    long countByUserIdAndUsedAtIsNull(UUID userId);

    void deleteByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update MfaRecoveryCode recoveryCode
               set recoveryCode.usedAt = :usedAt
             where recoveryCode.user.id = :userId
               and recoveryCode.codeHash = :codeHash
               and recoveryCode.usedAt is null
            """)
    int markUsedIfAvailable(UUID userId, String codeHash, Instant usedAt);
}
