package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.DefaultSource;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermSetting;
import com.fabricmanagement.sales.salesorder.domain.EventComparison;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import com.fabricmanagement.sales.salesorder.domain.RequestedDate;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.DeliveryTermInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.DeliveryTermView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RequestedDateInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RequestedDateView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An order's commercial delivery plan and how its lines are allocated to it (ADR-0014 D8). Physical
 * shipments are linked to these plan entries later by logistics.
 */
public final class OrderDeliveryDtos {

  private OrderDeliveryDtos() {}

  @Schema(name = "OrderDeliveryContentInput")
  public record DeliveryContentInput(
      @Valid PartyInput consignee,
      @Valid AddressInput shipTo,
      @Schema(description = "ORDER_DEFAULT (default) follows the order's term")
          DefaultSource termSource,
      @Valid DeliveryTermInput term,
      @Schema(description = "ORDER_DEFAULT (default) follows the order's requested date")
          DefaultSource requestedDateSource,
      @Valid RequestedDateInput requestedDate,
      String transportPreference,
      boolean shipComplete) {}

  @Schema(name = "SaveOrderDeliveryRequest")
  public record SaveDeliveryRequest(
      @NotNull Long expectedVersion, @NotNull @Valid DeliveryContentInput content) {}

  @Schema(name = "OrderLineAllocationInput")
  public record AllocationInput(@NotNull UUID lineId, @NotNull BigDecimal quantity) {}

  @Schema(name = "SetOrderDeliveryAllocationsRequest")
  public record SetAllocationsRequest(
      @NotNull Long expectedVersion, @NotNull List<@NotNull @Valid AllocationInput> allocations) {}

  @Schema(name = "OrderLineAllocationView")
  public record AllocationView(UUID lineId, BigDecimal quantity) {
    static AllocationView of(OrderLineAllocation value) {
      return new AllocationView(value.getLineId(), value.getQuantity());
    }
  }

  @Schema(name = "OrderDeliveryView")
  public record DeliveryView(
      UUID id,
      int sequenceNo,
      PartyView consignee,
      AddressView shipTo,
      DefaultSource termSource,
      @Schema(description = "This delivery's own term; empty when it follows the order")
          DeliveryTermView ownTerm,
      @Schema(description = "The term that applies") DeliveryTermView effectiveTerm,
      DefaultSource requestedDateSource,
      RequestedDateView ownRequestedDate,
      RequestedDateView effectiveRequestedDate,
      @Schema(
              description =
                  "How the customer's requested event compares with the term's event. UNKNOWN when"
                      + " the customer did not say what the date means, no date was requested or"
                      + " there is no term. DIFFERENT dates describe different moments and are never"
                      + " converted into each other.")
          EventComparison requestedEventVsTerm,
      String transportPreference,
      boolean shipComplete,
      List<AllocationView> allocations) {

    public static DeliveryView of(
        SalesOrder order, OrderDelivery delivery, List<OrderLineAllocation> allocations) {
      DeliveryTermSetting term = delivery.effectiveTerm(order);
      RequestedDate requested = delivery.effectiveRequestedDate(order);
      EventComparison comparison =
          requested.status() == RequestedDateStatus.REQUESTED
              ? requested.event().compareWith(term.terms().event())
              : EventComparison.UNKNOWN;
      return new DeliveryView(
          delivery.getId(),
          delivery.getSequenceNo(),
          PartyView.of(delivery.getConsignee()),
          AddressView.of(delivery.getShipTo()),
          delivery.getTermSource(),
          DeliveryTermView.of(delivery.getOwnTerm()),
          DeliveryTermView.of(term),
          delivery.getRequestedDateSource(),
          RequestedDateView.of(delivery.getOwnRequestedDate()),
          RequestedDateView.of(requested),
          comparison,
          delivery.getTransportPreference(),
          delivery.isShipComplete(),
          allocations.stream().map(AllocationView::of).toList());
    }
  }

  @Schema(name = "OrderLineAllocationSummary")
  public record LineAllocationSummary(
      UUID lineId,
      BigDecimal quantity,
      String unit,
      BigDecimal allocated,
      @Schema(description = "Not yet in any delivery; never assumed to go in one")
          BigDecimal unallocated) {}

  @Schema(name = "OrderDeliveriesView")
  public record DeliveriesView(
      UUID orderId,
      @Schema(description = "Send it back as expectedVersion with the next write")
          Long orderVersion,
      DeliveryTermView defaultTerm,
      RequestedDateView defaultRequestedDate,
      List<DeliveryView> deliveries,
      List<LineAllocationSummary> lines) {}
}
