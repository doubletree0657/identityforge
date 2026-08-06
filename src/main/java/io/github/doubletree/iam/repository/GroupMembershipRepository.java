package io.github.doubletree.iam.repository;

import io.github.doubletree.iam.domain.GroupMembership;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {
}
