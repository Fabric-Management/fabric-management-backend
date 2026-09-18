package com.fabricmanagement.flowboard.routing.domain.port.out;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import java.util.Set;
import java.util.UUID;

/** Consumer-owned reverse lookup for active users holding an effective permission. */
public interface EffectivePermissionUserQueryPort {

  Set<UUID> findUsersWithAction(UUID tenantId, PermissionKey permissionKey);
}
