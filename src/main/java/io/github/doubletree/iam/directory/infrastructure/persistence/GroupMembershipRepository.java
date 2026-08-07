package io.github.doubletree.iam.directory.infrastructure.persistence;

import io.github.doubletree.iam.directory.domain.GroupMembership;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {
}
