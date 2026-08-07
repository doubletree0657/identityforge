package io.github.doubletree.iam.directory.application;
import io.github.doubletree.iam.audit.application.AuditApplicationService;

import io.github.doubletree.iam.shared.exception.EntityNotFoundException;
import io.github.doubletree.iam.shared.exception.ValidationException;
import io.github.doubletree.iam.directory.domain.Tenant;
import io.github.doubletree.iam.directory.domain.TenantStatus;
import io.github.doubletree.iam.directory.infrastructure.persistence.TenantRepository;
import io.github.doubletree.iam.directory.access.application.AdminAuthorizationService;
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
    private final SystemPermissionCatalogService systemPermissionCatalogService;

    public TenantApplicationService(
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService,
            SystemPermissionCatalogService systemPermissionCatalogService) {
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
        this.systemPermissionCatalogService = systemPermissionCatalogService;
    }

    @Transactional
    public Tenant createTenant(String name) {
        return createTenant(name, slugify(name));
    }

    @Transactional
    public Tenant createTenant(String name, String slug) {
        if (!adminAuthorizationService.isPlatformAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Only platform administrators can create tenants");
        }
        String normalizedSlug = normalizeSlug(slug);
        ensureSlugAvailable(normalizedSlug, null);
        Tenant candidate = Tenant.create(name, normalizedSlug);
        Tenant tenant = tenantRepository.save(candidate);
        systemPermissionCatalogService.seedRoleTemplates(tenant);
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
            String normalizedSlug = normalizeSlug(slug);
            if (!tenant.getSlug().equals(normalizedSlug)) {
                throw new ValidationException("Tenant realm slug is immutable after creation");
            }
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

    private String normalizeSlug(String value) {
        String slug = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) {
            throw new ValidationException(
                    "Tenant realm slug must be 1-63 lowercase letters, numbers, or internal hyphens");
        }
        return slug;
    }
}
