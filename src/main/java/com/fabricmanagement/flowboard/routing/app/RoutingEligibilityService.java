package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoutingEligibilityService {
  private final RoutingUserQueryPort users;
  private final SalesOrderWriteScopePort salesScope;

  public RoutingUserQueryPort.UserPermissions user(UUID tenantId, UUID userId) {
    return users.findFresh(tenantId, userId);
  }

  public List<RoutingReason> candidacy(UUID tenantId, RoutingUserQueryPort.UserPermissions user) {
    var reasons = new ArrayList<RoutingReason>();
    if (!user.active() || !tenantId.equals(user.tenantId())) reasons.add(RoutingReason.INACTIVE);
    if (!user.permissions()
        .can(PermissionKey.FLOWBOARD_WRITE.resource(), PermissionKey.FLOWBOARD_WRITE.action()))
      reasons.add(RoutingReason.MISSING_FLOWBOARD_WRITE);
    if (!user.permissions()
        .can(PermissionKey.SALES_WRITE.resource(), PermissionKey.SALES_WRITE.action()))
      reasons.add(RoutingReason.MISSING_SALES_WRITE);
    return List.copyOf(reasons);
  }

  public List<RoutingReason> eligibility(
      UUID tenantId, UUID orderId, RoutingUserQueryPort.UserPermissions user, boolean member) {
    var reasons = new ArrayList<>(candidacy(tenantId, user));
    if (!member) reasons.add(RoutingReason.NOT_A_MEMBER);
    if (reasons.isEmpty() && !salesScope.isAllowed(tenantId, user.userId(), orderId))
      reasons.add(RoutingReason.OUTSIDE_SALES_WRITE_SCOPE);
    return List.copyOf(reasons);
  }

  public boolean writeScopeAllowed(UUID tenantId, UUID orderId, UUID userId) {
    return salesScope.isAllowed(tenantId, userId, orderId);
  }
}
