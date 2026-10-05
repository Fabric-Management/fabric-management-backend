package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Reading and section-wise writing of an order (ADR-0014 D10). A write names the order version it
 * was based on; the order is locked and a stale version is rejected, never merged, so concurrent
 * autosaves of different sections cannot silently overwrite each other.
 */
@Component
@RequiredArgsConstructor
class OrderDraftAccess {

  private final SalesOrderRepository orders;
  private final SalesOrderAccessPolicy accessPolicy;
  private final TradingPartnerService partners;

  SalesOrder readable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .filter(value -> accessPolicy.canRead(tenantId, actor, value))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  /** The order, locked for this transaction, when {@code expectedVersion} is still current. */
  SalesOrder writable(UUID orderId, Long expectedVersion, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = readable(orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    SalesOrder locked =
        orders
            .lockByTenantIdAndId(tenantId, order.getId())
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    if (expectedVersion == null || !expectedVersion.equals(locked.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(SalesOrder.class, orderId);
    }
    return locked;
  }

  /** A registered partner named for a role must exist in this tenant. */
  void requireRegistered(PartyReference party) {
    if (party.mode() != PartyMode.PARTNER) {
      return;
    }
    if (partners.findById(TenantContext.requireTenantId(), party.partnerId()).isEmpty()) {
      throw OrderDomainException.rule("PARTNER_NOT_FOUND", "That partner is not registered");
    }
  }

  void flush() {
    orders.flush();
  }
}
