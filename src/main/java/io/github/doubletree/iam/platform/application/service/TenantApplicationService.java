package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.TenantStatus;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantApplicationService {

    private final TenantRepository tenantRepository;
    private final AuditApplicationService auditApplicationService;

    public TenantApplicationService(TenantRepository tenantRepository, AuditApplicationService auditApplicationService) {
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
    }

    @Transactional
    public Tenant createTenant(String name) {
        Tenant tenant = tenantRepository.save(Tenant.create(name));
        auditApplicationService.recordEvent(tenant.getId(), "TENANT_CREATED", "TENANT", tenant.getId());
        return tenant;
    }

    @Transactional(readOnly = true)
    public Page<Tenant> listTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, String name, String slug, TenantStatus status) {
        Tenant tenant = findTenant(tenantId);
        if (name != null) {
            tenant.setName(name);
        }
        if (slug != null) {
            tenant.setSlug(slug);
        }
        if (status != null) {
            tenant.setStatus(status);
        }
        Tenant savedTenant = tenantRepository.save(tenant);
        auditApplicationService.recordEvent(savedTenant.getId(), "TENANT_UPDATED", "TENANT", savedTenant.getId());
        return savedTenant;
    }
}
