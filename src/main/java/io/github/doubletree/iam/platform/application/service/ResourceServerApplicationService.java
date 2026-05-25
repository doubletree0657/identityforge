package io.github.doubletree.iam.platform.application.service;

import io.github.doubletree.iam.platform.application.exception.EntityNotFoundException;
import io.github.doubletree.iam.platform.application.exception.ValidationException;
import io.github.doubletree.iam.platform.domain.ResourcePermission;
import io.github.doubletree.iam.platform.domain.ResourceServer;
import io.github.doubletree.iam.platform.domain.ResourceServerStatus;
import io.github.doubletree.iam.platform.domain.Tenant;
import io.github.doubletree.iam.platform.repository.ResourcePermissionRepository;
import io.github.doubletree.iam.platform.repository.ResourceServerRepository;
import io.github.doubletree.iam.platform.repository.TenantRepository;
import io.github.doubletree.iam.platform.security.AdminAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceServerApplicationService {

    private final ResourceServerRepository resourceServerRepository;
    private final ResourcePermissionRepository resourcePermissionRepository;
    private final TenantRepository tenantRepository;
    private final AuditApplicationService auditApplicationService;
    private final AdminAuthorizationService adminAuthorizationService;

    public ResourceServerApplicationService(
            ResourceServerRepository resourceServerRepository,
            ResourcePermissionRepository resourcePermissionRepository,
            TenantRepository tenantRepository,
            AuditApplicationService auditApplicationService,
            AdminAuthorizationService adminAuthorizationService) {
        this.resourceServerRepository = resourceServerRepository;
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.tenantRepository = tenantRepository;
        this.auditApplicationService = auditApplicationService;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Transactional
    public ResourceServer createResourceServer(UUID tenantId, String identifier, String name, String description) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        adminAuthorizationService.assertTenantAccess(tenant.getId());
        ensureIdentifierAvailable(tenant.getId(), identifier, null);

        ResourceServer resourceServer = ResourceServer.create(tenant, identifier, name);
        resourceServer.setDescription(description);
        ResourceServer saved = resourceServerRepository.save(resourceServer);
        auditApplicationService.recordEvent(saved.getTenant().getId(), "RESOURCE_SERVER_CREATED", "RESOURCE_SERVER", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ResourceServer> listResourceServers(UUID tenantId, Pageable pageable) {
        UUID allowedTenantId = adminAuthorizationService.tenantIdForList(tenantId);
        if (allowedTenantId == null) {
            return resourceServerRepository.findAll(pageable);
        }
        return resourceServerRepository.findByTenantId(allowedTenantId, pageable);
    }

    @Transactional(readOnly = true)
    public ResourceServer findResourceServer(UUID resourceServerId) {
        return loadResourceServer(resourceServerId);
    }

    @Transactional
    public ResourceServer updateResourceServer(
            UUID resourceServerId,
            String identifier,
            String name,
            String description,
            ResourceServerStatus status) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        if (identifier != null) {
            ensureIdentifierAvailable(resourceServer.getTenant().getId(), identifier, resourceServer.getId());
            resourceServer.setIdentifier(identifier);
        }
        if (name != null) {
            resourceServer.setName(name);
        }
        if (description != null) {
            resourceServer.setDescription(description);
        }
        if (status != null) {
            resourceServer.setStatus(status);
        }
        ResourceServer saved = resourceServerRepository.save(resourceServer);
        auditApplicationService.recordEvent(saved.getTenant().getId(), "RESOURCE_SERVER_UPDATED", "RESOURCE_SERVER", saved.getId());
        return saved;
    }

    @Transactional
    public ResourceServer disableResourceServer(UUID resourceServerId) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        resourceServer.setStatus(ResourceServerStatus.DISABLED);
        ResourceServer saved = resourceServerRepository.save(resourceServer);
        auditApplicationService.recordEvent(saved.getTenant().getId(), "RESOURCE_SERVER_DISABLED", "RESOURCE_SERVER", saved.getId());
        return saved;
    }

    @Transactional
    public ResourceServer reactivateResourceServer(UUID resourceServerId) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        resourceServer.setStatus(ResourceServerStatus.ACTIVE);
        ResourceServer saved = resourceServerRepository.save(resourceServer);
        auditApplicationService.recordEvent(saved.getTenant().getId(), "RESOURCE_SERVER_REACTIVATED", "RESOURCE_SERVER", saved.getId());
        return saved;
    }

    @Transactional
    public ResourcePermission createResourcePermission(
            UUID resourceServerId,
            String name,
            String displayName,
            String description) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        ensurePermissionNameAvailable(resourceServer.getId(), name, null);

        ResourcePermission permission = resourcePermissionRepository.save(
                ResourcePermission.create(resourceServer, name, displayName, description));
        auditApplicationService.recordEvent(
                resourceServer.getTenant().getId(), "RESOURCE_PERMISSION_CREATED", "RESOURCE_PERMISSION", permission.getId());
        return permission;
    }

    @Transactional(readOnly = true)
    public List<ResourcePermission> listResourcePermissions(UUID resourceServerId) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        return resourcePermissionRepository.findByResourceServerId(resourceServer.getId());
    }

    @Transactional
    public ResourcePermission updateResourcePermission(
            UUID resourceServerId,
            UUID permissionId,
            String name,
            String displayName,
            String description) {
        ResourceServer resourceServer = loadResourceServer(resourceServerId);
        ResourcePermission permission = resourcePermissionRepository.findById(permissionId)
                .orElseThrow(() -> new EntityNotFoundException("Resource permission not found: " + permissionId));
        if (!permission.getResourceServer().getId().equals(resourceServer.getId())) {
            throw new EntityNotFoundException("Resource permission not found: " + permissionId);
        }
        if (name != null) {
            ensurePermissionNameAvailable(resourceServer.getId(), name, permission.getId());
            permission.setName(name);
        }
        if (displayName != null) {
            permission.setDisplayName(displayName);
        }
        if (description != null) {
            permission.setDescription(description);
        }
        ResourcePermission saved = resourcePermissionRepository.save(permission);
        auditApplicationService.recordEvent(
                resourceServer.getTenant().getId(), "RESOURCE_PERMISSION_UPDATED", "RESOURCE_PERMISSION", saved.getId());
        return saved;
    }

    private ResourceServer loadResourceServer(UUID resourceServerId) {
        ResourceServer resourceServer = resourceServerRepository.findById(resourceServerId)
                .orElseThrow(() -> new EntityNotFoundException("Resource server not found: " + resourceServerId));
        adminAuthorizationService.assertTenantAccess(resourceServer.getTenant().getId());
        return resourceServer;
    }

    private void ensureIdentifierAvailable(UUID tenantId, String identifier, UUID currentResourceServerId) {
        resourceServerRepository.findByTenantIdAndIdentifier(tenantId, identifier).ifPresent(existing -> {
            if (currentResourceServerId == null || !existing.getId().equals(currentResourceServerId)) {
                throw new ValidationException("Resource server identifier already exists in tenant: " + identifier);
            }
        });
    }

    private void ensurePermissionNameAvailable(UUID resourceServerId, String name, UUID currentPermissionId) {
        resourcePermissionRepository.findByResourceServerIdAndName(resourceServerId, name).ifPresent(existing -> {
            if (currentPermissionId == null || !existing.getId().equals(currentPermissionId)) {
                throw new ValidationException("Resource permission name already exists in resource server: " + name);
            }
        });
    }
}
