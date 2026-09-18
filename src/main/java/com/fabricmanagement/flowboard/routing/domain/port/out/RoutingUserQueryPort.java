package com.fabricmanagement.flowboard.routing.domain.port.out;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import java.util.UUID;

public interface RoutingUserQueryPort {
  UserPermissions findFresh(UUID tenantId, UUID userId);

  record UserPermissions(
      UUID tenantId,
      UUID userId,
      String displayName,
      boolean active,
      PermissionResult permissions) {}
}
