package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.communication.api.facade.AddressFacade;
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
  private final AddressFacade addresses;
  private final SalesOrderRevision revision;

  SalesOrder readable(UUID orderId, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
        .filter(value -> accessPolicy.canRead(tenantId, actor, value))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  /**
   * The order, locked for this transaction, when {@code expectedVersion} is still current. The
   * entity read for the access check is reloaded under the lock (CEDIT-03 §5): a locked query does
   * not refresh an entity already in the persistence context, and the version, status and flow
   * stage the caller checks must be the committed ones.
   */
  SalesOrder writable(UUID orderId, Long expectedVersion, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesOrder order = readable(orderId, actor);
    if (!accessPolicy.canWrite(tenantId, actor, order)) {
      throw new AccessDeniedException("You do not have access to update this sales order.");
    }
    return lockedAtVersion(order, expectedVersion);
  }

  /**
   * The order, locked, for someone who verifies its deliveries (CONSIGNEE-1 C8). A verifier is not
   * necessarily the order's sales owner and may hold no sales scope at all: what lets them in is
   * {@code sales:verify-delivery}, checked by the endpoint. The version is still the one they saw.
   */
  SalesOrder verifiable(UUID orderId, Long expectedVersion) {
    SalesOrder order =
        orders
            .findByTenantIdAndId(TenantContext.requireTenantId(), orderId)
            .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
            .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
    return lockedAtVersion(order, expectedVersion);
  }

  private SalesOrder lockedAtVersion(SalesOrder order, Long expectedVersion) {
    revision.lockFresh(order);
    if (!Boolean.TRUE.equals(order.getIsActive())) {
      throw new NotFoundException("Sales order not found: " + order.getId());
    }
    if (expectedVersion == null || !expectedVersion.equals(order.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(SalesOrder.class, order.getId());
    }
    return order;
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

  /** Whether a registered partner is confirmed as a party to deliver to. */
  boolean partnerVerified(UUID partnerId) {
    return partners
        .findById(TenantContext.requireTenantId(), partnerId)
        .map(partner -> partner.getVerificationStatus().isVerified())
        .orElse(false);
  }

  /** Whether the address card is confirmed; false for a card this tenant does not have. */
  boolean cardVerified(UUID addressId) {
    return addresses
        .deliveryVerificationOf(addressId)
        .map(DeliveryVerificationStatus::isVerified)
        .orElse(false);
  }

  /** Confirms the partner a delivery uses; one that is gone is left alone. */
  void verifyPartner(UUID partnerId, UUID verifier) {
    UUID tenantId = TenantContext.requireTenantId();
    if (partners.findById(tenantId, partnerId).isPresent()) {
      partners.verifyDelivery(tenantId, partnerId, verifier);
    }
  }

  /** Confirms the address card a delivery was copied from; a card that is gone is left alone. */
  void verifyCard(UUID addressId, UUID verifier) {
    if (addresses.deliveryVerificationOf(addressId).isPresent()) {
      addresses.verifyForDelivery(addressId, verifier);
    }
  }

  void flush() {
    orders.flush();
  }
}
