package com.fabricmanagement.flowboard.routing.app.adapter;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingUserQueryPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingUserQueryAdapter implements RoutingUserQueryPort {
  private final UserQueryService users;
  private final PermissionEvaluator evaluator;

  @Override
  public UserPermissions findFresh(UUID tenantId, UUID userId) {
    var user = users.findById(tenantId, userId).orElse(null);
    if (user == null || !tenantId.equals(user.getTenantId())) {
      return new UserPermissions(
          tenantId,
          userId,
          user == null ? null : user.getDisplayName(),
          false,
          new PermissionResult(java.util.Map.of(), false));
    }
    var identity = users.findPermissionIdentity(tenantId, userId).orElse(null);
    var permissions =
        identity == null
            ? new PermissionResult(java.util.Map.of(), false)
            : evaluator.evaluateFresh(
                tenantId, identity.roleCode(), identity.departmentCodes(), userId);
    return new UserPermissions(
        tenantId,
        userId,
        user.getDisplayName(),
        Boolean.TRUE.equals(user.getIsActive()),
        permissions);
  }
}
