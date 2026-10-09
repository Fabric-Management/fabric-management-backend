package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.ReadReach;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository.LiveRevisionView;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Access of the open edit form's side channels — edit sessions (CEDIT-06) and field leases
 * (CEDIT-07) — decided again on every call from current data: the tenant may use the platform now,
 * the user is still active, the user's sales permission and scope are read fresh, the order is
 * active and in that scope. An access token issued before a suspension, a deactivation or a
 * permission change is never enough. An order the caller cannot read is not found, whatever the
 * reason, so nothing is revealed about other tenants or scopes.
 */
@Component
@RequiredArgsConstructor
class SalesOrderEditAccess {

  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy accessPolicy;
  private final UserQueryService users;
  private final CurrentTenantAccessPort tenantAccess;

  /**
   * The tenant may use the platform and the user is active now; otherwise the order is not found.
   * The safe save calls this before its own permission and scope checks (CEDIT-07 L12).
   */
  void requireActiveCaller(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    if (!tenantAccess.currentTenantHasAccess() || !users.isActive(tenantId, actor)) {
      throw notFound(orderId);
    }
  }

  /** The order's committed live view, when the caller may read it now. */
  LiveRevisionView requireReadable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    requireActiveCaller(orderId, actor);
    ReadReach reach = accessPolicy.readReach(tenantId, actor, PermissionFreshness.FRESH);
    if (!reach.any()) {
      throw notFound(orderId);
    }
    return orders
        .findLiveRevisionViewByTenantIdAndId(tenantId, orderId)
        .filter(order -> Boolean.TRUE.equals(order.getIsActive()))
        .filter(order -> reach.permits(order.getTenantId(), order.getCreatedBy()))
        .orElseThrow(() -> notFound(orderId));
  }

  /** As {@link #requireReadable}, and the caller may write the order now; the order entity. */
  SalesOrder requireWritable(UUID orderId, UUID actor) {
    requireReadable(orderId, actor);
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order =
        orders
            .findByTenantIdAndId(tenantId, orderId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> notFound(orderId));
    if (!accessPolicy.canWrite(tenantId, actor, order, PermissionFreshness.FRESH)) {
      throw new AccessDeniedException("You do not have access to edit this sales order.");
    }
    return order;
  }

  /** A person's display name in the bound tenant; empty when the account has none. */
  Optional<String> displayName(UUID userId) {
    return users
        .findById(TenantContext.requireTenantId(), userId)
        .map(UserDto::getDisplayName)
        .filter(name -> !name.isBlank());
  }

  static NotFoundException notFound(UUID orderId) {
    return new NotFoundException("Sales order not found: " + orderId);
  }
}
