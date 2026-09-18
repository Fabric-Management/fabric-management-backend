package com.fabricmanagement.flowboard.routing.domain;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import java.util.Set;

public enum RoutingPoolKey {
  ORDER_COVER;

  public Set<PermissionKey> requiredPermissions() {
    return Set.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.SALES_WRITE);
  }
}
