package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.domain.TenantStatus;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantApplicationService {

    private final TenantRepository tenantRepository;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;

    public TenantApplicationService(
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public Tenant createTenant(String name) {
        if (!adminAuthorizationService.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Only platform administrators can create tenants");
        }
        String slug = slugify(name);
        ensureSlugAvailable(slug, null);
        Tenant candidate = Tenant.create(name);
        candidate.setSlug(slug);
        Tenant tenant = tenantRepository.save(candidate);
        auditApplicationService.recordEvent(tenant.getId(), "TENANT_CREATED", "TENANT", tenant.getId());
        return tenant;
    }

    @Transactional(readOnly = true)
    public Page<Tenant> listTenants(Pageable pageable) {
        UUID currentTenantId = adminAuthorizationService.tenantIdForList(null);
        if (currentTenantId != null) {
            return tenantRepository.findById(currentTenantId)
                    .<Page<Tenant>>map(tenant -> new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(tenant), pageable, 1))
                    .orElseGet(() -> org.springframework.data.domain.Page.empty(pageable));
        }
        return tenantRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Tenant findTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());
        return tenant;
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, String name, String slug, TenantStatus status) {
        Tenant tenant = findTenant(tenantId);
        if (!adminAuthorizationService.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Only platform administrators can update tenants");
        }
        if (name != null) {
            tenant.setName(name);
        }
        if (slug != null) {
            ensureSlugAvailable(slug, tenant.getId());
            tenant.setSlug(slug);
        }
        if (status != null) {
            tenant.setStatus(status);
        }
        Tenant savedTenant = tenantRepository.save(tenant);
        auditApplicationService.recordEvent(savedTenant.getId(), "TENANT_UPDATED", "TENANT", savedTenant.getId());
        return savedTenant;
    }

    private void ensureSlugAvailable(String slug, UUID currentTenantId) {
        tenantRepository.findBySlug(slug).ifPresent(existingTenant -> {
            if (currentTenantId == null || !existingTenant.getId().equals(currentTenantId)) {
                throw new ValidationException("Tenant slug already exists: " + slug);
            }
        });
    }

    private String slugify(String value) {
        String slug = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "tenant" : slug;
    }
}
