package io.github.doubletree.iam.platform.repository;

import io.github.doubletree.iam.platform.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByAction(String action);
}
