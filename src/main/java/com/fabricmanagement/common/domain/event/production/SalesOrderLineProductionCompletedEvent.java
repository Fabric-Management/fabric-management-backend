package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published after all active WorkOrders linked to a SalesOrderLine are completed. */
@Getter
public class SalesOrderLineProductionCompletedEvent extends DomainEvent {

  private final UUID salesOrderLineId;
  private final UUID completedByWorkOrderId;

  public SalesOrderLineProductionCompletedEvent(
      UUID tenantId, UUID salesOrderLineId, UUID completedByWorkOrderId) {
    super(tenantId, "SALES_ORDER_LINE_PRODUCTION_COMPLETED");
    this.salesOrderLineId = salesOrderLineId;
    this.completedByWorkOrderId = completedByWorkOrderId;
  }

  @JsonCreator
  public SalesOrderLineProductionCompletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderLineId") UUID salesOrderLineId,
      @JsonProperty("completedByWorkOrderId") UUID completedByWorkOrderId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SALES_ORDER_LINE_PRODUCTION_COMPLETED",
        occurredAt,
        correlationId);
    this.salesOrderLineId = salesOrderLineId;
    this.completedByWorkOrderId = completedByWorkOrderId;
  }
}
