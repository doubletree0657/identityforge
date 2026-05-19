package io.github.doubletree.iam.platform.web.dto;

import io.github.doubletree.iam.platform.domain.TenantStatus;

public record UpdateTenantRequest(String name, String slug, TenantStatus status) {
}
