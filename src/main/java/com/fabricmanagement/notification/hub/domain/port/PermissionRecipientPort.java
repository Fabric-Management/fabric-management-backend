package com.fabricmanagement.notification.hub.domain.port;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the tenant's active users who hold an effective permission.
 *
 * <p>Used when an alert must reach whoever can act on it: the tenant decides who that is by
 * granting the permission, so no recipient is named in code.
 */
public interface PermissionRecipientPort {

  Set<UUID> findUsersWithPermission(UUID tenantId, PermissionKey permissionKey);
}
