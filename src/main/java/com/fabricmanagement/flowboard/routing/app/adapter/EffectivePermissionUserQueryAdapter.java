package com.fabricmanagement.flowboard.routing.app.adapter;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.domain.port.out.EffectivePermissionUserQueryPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** FlowBoard adapter for querying users with an effective permission. */
@Component
@RequiredArgsConstructor
public class EffectivePermissionUserQueryAdapter implements EffectivePermissionUserQueryPort {

  private final UserQueryService userQueryService;

  @Override
  public Set<UUID> findUsersWithAction(UUID tenantId, PermissionKey permissionKey) {
    return userQueryService.findUsersWithAction(tenantId, permissionKey);
  }
}
