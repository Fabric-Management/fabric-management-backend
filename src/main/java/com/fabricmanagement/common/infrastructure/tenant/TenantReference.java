package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/**
 * Minimal tenant bilgisi — cross-module kullanım için ACL DTO. Tenant entity'sinin domain
 * detaylarını (status, settings, subscription) sızdırmaz.
 */
public record TenantReference(UUID id, String uid, String name, String type) {}
