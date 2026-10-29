package com.fabricmanagement.flowboard.routing.domain;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import java.util.Set;

public enum RoutingPoolKey {
  ORDER_COVER,
  COLLECTION_FOLLOW_UP;

  public Set<PermissionKey> requiredPermissions() {
    return this == COLLECTION_FOLLOW_UP
        ? Set.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.FINANCE_READ)
        : Set.of(PermissionKey.FLOWBOARD_WRITE, PermissionKey.SALES_WRITE);
  }
}
