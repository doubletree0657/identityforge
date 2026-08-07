package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.PasswordCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

    Optional<PasswordCredential> findByUserId(UUID userId);

    @Modifying
    @Query(value = "update password_credentials set credentials_version = credentials_version + 1, "
            + "updated_at = current_timestamp where user_id = :userId", nativeQuery = true)
    int incrementVersionForUser(UUID userId);

    @Modifying
    @Query(value = """
            update password_credentials
               set credentials_version = credentials_version + 1,
                   updated_at = current_timestamp
             where user_id in (
                 select ur.user_id from user_roles ur where ur.role_id = :roleId
                 union
                 select gm.user_id
                   from group_memberships gm
                   join group_roles gr on gr.group_id = gm.group_id
                  where gr.role_id = :roleId
             )
            """, nativeQuery = true)
    int incrementVersionForRoleAssignments(UUID roleId);

    @Modifying
    @Query(value = """
            update password_credentials
               set credentials_version = credentials_version + 1,
                   updated_at = current_timestamp
             where user_id in (
                 select user_id from group_memberships where group_id = :groupId
             )
            """, nativeQuery = true)
    int incrementVersionForGroupMembers(UUID groupId);
}
