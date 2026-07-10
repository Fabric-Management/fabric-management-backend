package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/**
 * Satış siparişi iptal edildiğinde yayınlanır.
 *
 * <p>WorkOrderSalesEventListener bu eventi dinler ve ilgili üretim emirlerini (COMPLETED
 * olmayanları) iptal eder (cascade iptal).
 */
@Getter
public class SalesOrderCancelledEvent extends DomainEvent {

  private final UUID salesOrderId;
  private final String orderNumber;
  private final List<UUID> cancelledLineIds;

  public SalesOrderCancelledEvent(
      UUID tenantId, UUID salesOrderId, String orderNumber, List<UUID> cancelledLineIds) {
    super(tenantId, "SalesOrderCancelled");
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.cancelledLineIds = cancelledLineIds != null ? cancelledLineIds : List.of();
  }

  @JsonCreator
  public SalesOrderCancelledEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderId") UUID salesOrderId,
      @JsonProperty("orderNumber") String orderNumber,
      @JsonProperty("cancelledLineIds") List<UUID> cancelledLineIds) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SalesOrderCancelled",
        occurredAt,
        correlationId);
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.cancelledLineIds = cancelledLineIds != null ? cancelledLineIds : List.of();
  }
}
