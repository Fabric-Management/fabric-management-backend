package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoutingEligibilityService {
  private final RoutingUserQueryPort users;
  private final SalesOrderWriteScopePort salesScope;

  private final CollectionFollowUpPort collections;

  public RoutingEligibilityService(
      RoutingUserQueryPort users,
      SalesOrderWriteScopePort salesScope,
      CollectionFollowUpPort collections) {
    this.users = users;
    this.salesScope = salesScope;
    this.collections = collections;
  }

  public void assignCollection(UUID subject, UUID owner) {
    collections.assign(subject, owner);
  }

  public void lockCollection() {
    collections.lock();
  }

  public RoutingUserQueryPort.UserPermissions user(UUID tenantId, UUID userId) {
    return users.findFresh(tenantId, userId);
  }

  public List<RoutingReason> candidacy(UUID tenantId, RoutingUserQueryPort.UserPermissions user) {
    return candidacy(tenantId, user, RoutingPoolKey.ORDER_COVER);
  }

  public List<RoutingReason> candidacy(
      UUID tenantId, RoutingUserQueryPort.UserPermissions user, RoutingPoolKey pool) {
    var reasons = new ArrayList<RoutingReason>();
    if (!user.active() || !tenantId.equals(user.tenantId())) reasons.add(RoutingReason.INACTIVE);
    if (!user.permissions()
        .can(PermissionKey.FLOWBOARD_WRITE.resource(), PermissionKey.FLOWBOARD_WRITE.action()))
      reasons.add(RoutingReason.MISSING_FLOWBOARD_WRITE);
    var required =
        pool == RoutingPoolKey.COLLECTION_FOLLOW_UP
            ? PermissionKey.FINANCE_READ
            : PermissionKey.SALES_WRITE;
    if (!user.permissions().can(required.resource(), required.action()))
      reasons.add(
          pool == RoutingPoolKey.COLLECTION_FOLLOW_UP
              ? RoutingReason.MISSING_FINANCE_READ
              : RoutingReason.MISSING_SALES_WRITE);
    return List.copyOf(reasons);
  }

  public List<RoutingReason> eligibility(
      UUID tenantId, UUID orderId, RoutingUserQueryPort.UserPermissions user, boolean member) {
    return eligibility(tenantId, orderId, user, member, RoutingPoolKey.ORDER_COVER);
  }

  public List<RoutingReason> eligibility(
      UUID tenantId,
      UUID subject,
      RoutingUserQueryPort.UserPermissions user,
      boolean member,
      RoutingPoolKey pool) {
    var reasons = new ArrayList<>(candidacy(tenantId, user, pool));
    if (!member) reasons.add(RoutingReason.NOT_A_MEMBER);
    if (reasons.isEmpty()) {
      boolean allowed =
          pool == RoutingPoolKey.COLLECTION_FOLLOW_UP
              ? collections.eligible(user.userId(), subject)
              : salesScope.isAllowed(tenantId, user.userId(), subject);
      if (!allowed)
        reasons.add(
            pool == RoutingPoolKey.COLLECTION_FOLLOW_UP
                ? RoutingReason.OUTSIDE_FINANCE_READ_SCOPE
                : RoutingReason.OUTSIDE_SALES_WRITE_SCOPE);
    }
    return List.copyOf(reasons);
  }

  public boolean writeScopeAllowed(UUID tenantId, UUID orderId, UUID userId) {
    return salesScope.isAllowed(tenantId, userId, orderId);
  }
}
