package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderEditBase;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.AgreementValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ContactValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.DeliveryTermsValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.PricingValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ProfileRef;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.QuantityValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.RequestedDateValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.SpecificationValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.ToleranceValue;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot.WidthValue;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderEditBaseRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Server edit bases (CEDIT-02 §3): taking one when the edit form opens, projecting an order into
 * its edit keys, and the order view that goes with a base. The opening read runs in one {@code
 * REPEATABLE READ} transaction, so the base and the form's order come from one consistent snapshot
 * even while a writer that does not lock the order commits in between.
 */
@Component
public class SalesOrderEditBases {

  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final OrderLineAllocationRepository allocations;
  private final OrderEditBaseRepository bases;
  private final SalesOrderAccessPolicy accessPolicy;
  private final SalesOrderCapabilityService capabilities;
  private final TradingPartnerService partners;
  private final SalesOrderEditProperties properties;
  private final Clock clock;
  private final TransactionTemplate repeatableRead;

  public SalesOrderEditBases(
      SalesOrderRepository orders,
      SalesOrderLineRepository lines,
      OrderLineAllocationRepository allocations,
      OrderEditBaseRepository bases,
      SalesOrderAccessPolicy accessPolicy,
      SalesOrderCapabilityService capabilities,
      TradingPartnerService partners,
      SalesOrderEditProperties properties,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.orders = orders;
    this.lines = lines;
    this.allocations = allocations;
    this.bases = bases;
    this.accessPolicy = accessPolicy;
    this.capabilities = capabilities;
    this.partners = partners;
    this.properties = properties;
    this.clock = clock;
    // Not read-only: the base row is inserted in the same transaction as the read it records.
    this.repeatableRead = new TransactionTemplate(transactionManager);
    this.repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.repeatableRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
  }

  /**
   * Takes the base of a newly opened edit form: write access checked fresh, the draft editable,
   * then the order, its lines and allocations read once and stored as an immutable base.
   */
  public SalesOrderEditBase open(UUID orderId, UUID actor, Authentication authentication) {
    // Joining an outer transaction would silently lose the REPEATABLE READ snapshot.
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Opening an edit base starts its own transaction");
    }
    return repeatableRead.execute(
        status -> {
          UUID tenantId = TenantContext.requireTenantId();
          SalesOrder order = readable(tenantId, orderId, actor);
          if (!accessPolicy.canWrite(tenantId, actor, order, PermissionFreshness.FRESH)) {
            throw new AccessDeniedException("You do not have access to update this sales order.");
          }
          assertEditable(order);
          List<SalesOrderLine> active =
              lines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                  tenantId, order.getId());
          OrderEditSnapshot content = project(order, active);
          OrderEditBase base =
              bases.save(
                  OrderEditBase.opened(
                      order.getId(), actor, content, clock.instant(), properties.getBaseTtl()));
          return toDto(base, view(order, active, actor, authentication));
        });
  }

  /** The order a save may read: active and in the actor's read scope, otherwise not found. */
  SalesOrder readable(UUID tenantId, UUID orderId, UUID actor) {
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .filter(order -> Boolean.TRUE.equals(order.getIsActive()))
        .filter(order -> accessPolicy.canRead(tenantId, actor, order))
        .orElseThrow(() -> new NotFoundException("Sales order not found: " + orderId));
  }

  /**
   * Whether the draft's content may change now, in the order a save checks it (CEDIT-02 §5.1): the
   * commercial content lock, then the status, then the flow stage.
   */
  static void assertEditable(SalesOrder order) {
    order.assertCommercialContentEditable();
    if (!order.getStatus().canEdit()) {
      throw new OrderDomainException(
          "Cannot edit order "
              + order.getOrderNumber()
              + ": current status "
              + order.getStatus()
              + " does not allow editing. Only DRAFT orders can be modified.",
          409);
    }
    if (order.getFlowStage() != OrderFlowStage.DRAFT) {
      throw new OrderDomainException(
          "Order "
              + order.getOrderNumber()
              + " is "
              + order.getFlowStage()
              + ": withdraw it to the draft before editing",
          409);
    }
  }

  /** The edit projection of an order with the given active lines, at the order's version. */
  OrderEditSnapshot project(SalesOrder order, List<SalesOrderLine> activeLines) {
    Map<UUID, List<OrderLineAllocation>> allocationsByLine =
        allocations
            .findByTenantIdAndSalesOrderId(TenantContext.requireTenantId(), order.getId())
            .stream()
            .collect(Collectors.groupingBy(OrderLineAllocation::getLineId));
    List<OrderEditSnapshot.Line> projected =
        activeLines.stream()
            .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
            .map(line -> line(line, allocationsByLine.getOrDefault(line.getId(), List.of())))
            .toList();
    return new OrderEditSnapshot(
        OrderEditSnapshot.SCHEMA, order.getVersion(), header(order), projected);
  }

  static OrderEditSnapshot.Header header(SalesOrder order) {
    return new OrderEditSnapshot.Header(
        order.getCustomerReference(),
        order.getOrderDate(),
        RequestedDateValue.of(order.getRequestedDate()),
        new DeliveryTermsValue(
            order.getDeliveryTerm(),
            order.getDeliveryPlace(),
            order.getIncotermsVersion(),
            order.getDeliveryTermStatus(),
            order.getDeliveryContractReference()),
        order.getPaymentTerms(),
        new AgreementValue(order.getAgreementContext(), order.getAgreementContextNote()),
        new ContactValue(
            order.getContactName(),
            order.getContactEmail(),
            order.getContactPhone(),
            order.isContactWhatsapp()),
        order.getShippingAddress(),
        order.getBillingAddress(),
        order.getShippingMethod(),
        order.getNotes(),
        order.getDeadline());
  }

  static OrderEditSnapshot.Line line(SalesOrderLine line, List<OrderLineAllocation> allocated) {
    ProfileRef profile =
        line.getRequirementProfileId() == null
            ? null
            : new ProfileRef(
                line.getRequirementProfileId(),
                line.getRequirementProfileVersion(),
                line.getRequirementProfileFingerprint());
    return new OrderEditSnapshot.Line(
        line.getId(),
        line.getVersion() == null ? 0L : line.getVersion(),
        line.getProductId(),
        line.getProductDesc(),
        line.getColorId(),
        new WidthValue(line.getFinishedWidth(), line.getFinishedWidthUnit()),
        line.getRequestedDeliveryDate(),
        line.isSingleLotRequired(),
        line.getShipmentPreference(),
        new QuantityValue(line.getRequestedQty(), line.getUnit()),
        new PricingValue(
            line.getCurrency(),
            line.getUnitPriceAmount(),
            line.getDiscountAmountValue(),
            line.getTaxAmountValue()),
        new ToleranceValue(line.getToleranceUpPct(), line.getToleranceDownPct()),
        new SpecificationValue(line.getModuleType(), line.getModuleSpecs(), profile),
        allocationDigest(line, allocated));
  }

  /** The canonical digest of a line's delivery allocations: delivery, quantity and unit. */
  static String allocationDigest(SalesOrderLine line, List<OrderLineAllocation> allocated) {
    List<Map<String, Object>> parts =
        allocated.stream()
            .sorted(Comparator.comparing(allocation -> allocation.getDeliveryId().toString()))
            .map(
                allocation -> {
                  Map<String, Object> part = new LinkedHashMap<>();
                  part.put("deliveryId", allocation.getDeliveryId());
                  part.put("quantity", allocation.getQuantity());
                  part.put("unit", line.getUnit());
                  return part;
                })
            .toList();
    return CanonicalJsonFingerprint.of(parts);
  }

  /** The order as the detail view shows it, with the caller's capabilities. */
  SalesOrderDto view(
      SalesOrder order,
      List<SalesOrderLine> activeLines,
      UUID actor,
      Authentication authentication) {
    UUID tenantId = TenantContext.requireTenantId();
    List<SalesOrderLine> shown =
        activeLines.stream()
            .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
            .sorted(
                Comparator.comparing(
                        SalesOrderLine::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(line -> line.getId().toString()))
            .toList();
    TradingPartnerDto partner =
        partners.findById(tenantId, order.getTradingPartnerId()).orElse(null);
    SalesOrderDto dto =
        SalesOrderDto.from(
            order,
            partner,
            shown.stream().map(SalesOrderService::lineResponse).toList(),
            OrderCurrencyTotals.of(shown));
    dto.setCapabilities(capabilities.resolve(tenantId, actor, authentication, order));
    return dto;
  }

  /** Stores a base derived from a save, a conflict or an expired-base review. */
  OrderEditBase derived(
      UUID orderId,
      UUID actor,
      OrderEditSnapshot content,
      OrderEditBase.Origin origin,
      UUID parentBaseId,
      UUID operationId,
      Map<Slot, String> guard,
      Instant now) {
    return bases.save(
        OrderEditBase.derived(
            orderId,
            actor,
            content,
            origin,
            parentBaseId,
            operationId,
            guard,
            now,
            properties.getBaseTtl()));
  }

  static SalesOrderEditBase toDto(OrderEditBase base, SalesOrderDto order) {
    return new SalesOrderEditBase(
        base.getId(),
        base.getSalesOrderId(),
        base.getOrderVersion(),
        base.getCapturedAt(),
        base.getExpiresAt(),
        order);
  }
}
