package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.AddressSnapshot;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermSetting;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.RequestedDate;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.AllocationInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveriesView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryContentInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryCorrection;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.LineAllocationSummary;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SetAllocationsRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.VerifyDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.DeliveryTermView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RequestedDateView;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An order's deliveries and the allocation of its lines to them (ADR-0014 D8). Deliveries are part
 * of the commercial content: they change only in sales' draft, and every change moves the order's
 * version so a stale autosave of any section is rejected.
 */
@Service
@RequiredArgsConstructor
public class OrderDeliveryService {

  private final OrderDraftAccess access;
  private final OrderDeliveryRepository deliveries;
  private final OrderLineAllocationRepository allocations;
  private final SalesOrderLineRepository lines;
  private final SalesOrderRevision revision;
  private final DeliveryVerificationPolicy verificationPolicy;

  @Transactional(readOnly = true)
  public DeliveriesView list(UUID orderId, UUID actor) {
    return view(access.readable(orderId, actor));
  }

  @Transactional
  public DeliveriesView create(UUID orderId, SaveDeliveryRequest request, UUID actor) {
    SalesOrder order = editable(orderId, request.expectedVersion(), actor);
    int next =
        deliveriesOf(order).stream().mapToInt(OrderDelivery::getSequenceNo).max().orElse(0) + 1;
    OrderDelivery created =
        OrderDelivery.create(order.getId(), next, content(order, request.content()));
    deliveries.save(created);
    restate(order, created, actor);
    return changed(order);
  }

  @Transactional
  public DeliveriesView update(
      UUID orderId, UUID deliveryId, SaveDeliveryRequest request, UUID actor) {
    SalesOrder order = editable(orderId, request.expectedVersion(), actor);
    OrderDelivery delivery = delivery(order, deliveryId);
    delivery.revise(content(order, request.content()));
    restate(order, delivery, actor);
    return changed(order);
  }

  /**
   * Confirms a delivery (CONSIGNEE-1 C8). The endpoint has checked {@code sales:verify-delivery};
   * the order is loaded without the sales scope a verifier may not hold, locked and at the version
   * the verifier saw. A correction (consignee, address, note) is applied first and moves the order
   * version like any content change, so it is refused where the content is not editable; a plain
   * verification changes no order content and leaves the version alone. Verifying also verifies the
   * partner and the address card the delivery uses, so the next order does not ask again.
   */
  @Transactional
  public DeliveriesView verify(
      UUID orderId, UUID deliveryId, VerifyDeliveryRequest request, UUID actor) {
    SalesOrder order = access.verifiable(orderId, request.expectedVersion());
    OrderDelivery delivery = delivery(order, deliveryId);
    DeliveryCorrection correction = request.correction();
    if (correction != null) {
      order.assertCommercialContentEditable();
      PartyReference corrected =
          correction.consignee() == null
              ? PartyReference.NONE
              : correction.consignee().toReference(order.getTradingPartnerId(), "consignee");
      access.requireRegistered(corrected);
      AddressSnapshot shipTo =
          correction.shipTo() == null ? null : correction.shipTo().toSnapshot();
      delivery.correct(corrected, shipTo, correction.note());
      UUID copiedFrom = sourceCardOf(delivery);
      delivery.recordVerification(
          Verification.unverified(), copiedFrom != null && access.cardVerified(copiedFrom));
    }
    delivery.verify(order, actor, Instant.now());
    PartyReference consignee = delivery.getConsignee();
    if (consignee.mode() == PartyMode.PARTNER) {
      access.verifyPartner(consignee.partnerId(), actor);
    }
    UUID card = sourceCardOf(delivery);
    if (card != null) {
      access.verifyCard(card, actor);
    }
    if (correction != null) {
      revision.linesChanged(order);
    }
    access.flush();
    return view(order);
  }

  @Transactional
  public DeliveriesView delete(UUID orderId, UUID deliveryId, Long expectedVersion, UUID actor) {
    SalesOrder order = editable(orderId, expectedVersion, actor);
    OrderDelivery delivery = delivery(order, deliveryId);
    allocations.deleteByDelivery(TenantContext.requireTenantId(), delivery.getId());
    deliveries.delete(delivery);
    return changed(order);
  }

  /**
   * Replaces what one delivery carries. A line belongs to this order and is active; together with
   * the line's other deliveries the allocation never exceeds the line's quantity.
   */
  @Transactional
  public DeliveriesView setAllocations(
      UUID orderId, UUID deliveryId, SetAllocationsRequest request, UUID actor) {
    SalesOrder order = editable(orderId, request.expectedVersion(), actor);
    UUID tenantId = TenantContext.requireTenantId();
    OrderDelivery delivery = delivery(order, deliveryId);
    Map<UUID, SalesOrderLine> activeLines =
        linesOf(order).stream().collect(Collectors.toMap(SalesOrderLine::getId, line -> line));
    Map<UUID, BigDecimal> elsewhere = new HashMap<>();
    for (OrderLineAllocation existing :
        allocations.findByTenantIdAndSalesOrderId(tenantId, order.getId())) {
      if (!existing.getDeliveryId().equals(delivery.getId())) {
        elsewhere.merge(existing.getLineId(), existing.getQuantity(), BigDecimal::add);
      }
    }
    Set<UUID> seen = new HashSet<>();
    List<OrderLineAllocation> replacement = new ArrayList<>();
    for (AllocationInput input : request.allocations()) {
      SalesOrderLine line = activeLines.get(input.lineId());
      if (line == null) {
        throw OrderDomainException.rule("LINE_NOT_IN_ORDER", "That line is not on this order");
      }
      if (!seen.add(input.lineId())) {
        throw OrderDomainException.rule("LINE_ALLOCATED_TWICE", "Give each line once per delivery");
      }
      OrderLineAllocation allocation =
          OrderLineAllocation.of(order.getId(), line.getId(), delivery.getId(), input.quantity());
      BigDecimal total =
          elsewhere.getOrDefault(line.getId(), BigDecimal.ZERO).add(allocation.getQuantity());
      if (total.compareTo(line.getRequestedQty()) > 0) {
        throw OrderDomainException.rule(
            "ALLOCATION_EXCEEDS_LINE",
            "The deliveries carry more of this line than the order has: reduce an allocation");
      }
      replacement.add(allocation);
    }
    allocations.deleteByDelivery(tenantId, delivery.getId());
    allocations.saveAll(replacement);
    return changed(order);
  }

  private SalesOrder editable(UUID orderId, Long expectedVersion, UUID actor) {
    SalesOrder order = access.writable(orderId, expectedVersion, actor);
    order.assertCommercialContentEditable();
    return order;
  }

  private OrderDelivery.Content content(SalesOrder order, DeliveryContentInput input) {
    PartyReference consignee =
        input.consignee() == null
            ? PartyReference.NONE
            : input.consignee().toReference(order.getTradingPartnerId(), "consignee");
    access.requireRegistered(consignee);
    return new OrderDelivery.Content(
        consignee,
        input.shipTo() == null ? null : input.shipTo().toSnapshot(),
        input.termSource(),
        input.term() == null ? DeliveryTermSetting.NONE : input.term().toSetting(),
        input.requestedDateSource(),
        input.requestedDate() == null
            ? RequestedDate.UNKNOWN
            : input.requestedDate().toRequestedDate(),
        input.transportPreference(),
        input.shipComplete(),
        input.note());
  }

  /**
   * The state a save gives the delivery. Verified only when the saver may verify, the delivery
   * could be verified (a decided consignee and a complete address, or EXW), it is not note-only,
   * and the partner and address card it points at are verified: what a verifier types themselves
   * they own, what comes from a card or partner someone else made waits for the verify step.
   */
  private void restate(SalesOrder order, OrderDelivery delivery, UUID actor) {
    UUID card = sourceCardOf(delivery);
    boolean cardVerified = card != null && access.cardVerified(card);
    PartyReference consignee = delivery.getConsignee();
    boolean partnerVerified =
        consignee.mode() != PartyMode.PARTNER || access.partnerVerified(consignee.partnerId());
    boolean confirmed =
        verificationPolicy.actorMayVerify()
            && delivery.verificationBlocker(order).isEmpty()
            && !delivery.isNoteOnly()
            && partnerVerified
            && (card == null || cardVerified);
    delivery.recordVerification(
        confirmed ? Verification.verifiedBy(actor, Instant.now()) : Verification.unverified(),
        cardVerified);
  }

  private static UUID sourceCardOf(OrderDelivery delivery) {
    return delivery.getShipTo() == null ? null : delivery.getShipTo().getSourceAddressId();
  }

  private OrderDelivery delivery(SalesOrder order, UUID deliveryId) {
    return deliveries
        .findByTenantIdAndSalesOrderIdAndId(
            TenantContext.requireTenantId(), order.getId(), deliveryId)
        .orElseThrow(() -> new NotFoundException("Delivery not found: " + deliveryId));
  }

  private List<OrderDelivery> deliveriesOf(SalesOrder order) {
    return deliveries.findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(
        TenantContext.requireTenantId(), order.getId());
  }

  private List<SalesOrderLine> linesOf(SalesOrder order) {
    return lines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
        TenantContext.requireTenantId(), order.getId());
  }

  /** The order's version moves with any delivery change, so stale writes of any section fail. */
  private DeliveriesView changed(SalesOrder order) {
    revision.linesChanged(order);
    access.flush();
    return view(order);
  }

  private DeliveriesView view(SalesOrder order) {
    UUID tenantId = TenantContext.requireTenantId();
    List<OrderLineAllocation> all =
        allocations.findByTenantIdAndSalesOrderId(tenantId, order.getId());
    Map<UUID, List<OrderLineAllocation>> byDelivery =
        all.stream().collect(Collectors.groupingBy(OrderLineAllocation::getDeliveryId));
    Map<UUID, BigDecimal> allocatedByLine = new HashMap<>();
    all.forEach(
        value -> allocatedByLine.merge(value.getLineId(), value.getQuantity(), BigDecimal::add));
    List<DeliveryView> views =
        deliveriesOf(order).stream()
            .map(
                delivery ->
                    DeliveryView.of(
                        order, delivery, byDelivery.getOrDefault(delivery.getId(), List.of())))
            .toList();
    List<LineAllocationSummary> summaries =
        linesOf(order).stream()
            .map(
                line -> {
                  BigDecimal allocated =
                      allocatedByLine.getOrDefault(line.getId(), BigDecimal.ZERO);
                  BigDecimal open = line.getRequestedQty().subtract(allocated);
                  return new LineAllocationSummary(
                      line.getId(),
                      line.getRequestedQty(),
                      line.getUnit(),
                      allocated,
                      open.signum() < 0 ? BigDecimal.ZERO : open);
                })
            .toList();
    return new DeliveriesView(
        order.getId(),
        order.getVersion(),
        DeliveryTermView.of(order.getDeliveryTermSetting()),
        RequestedDateView.of(order.getRequestedDate()),
        views,
        summaries);
  }
}
