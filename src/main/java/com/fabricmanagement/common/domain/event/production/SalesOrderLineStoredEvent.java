package com.fabricmanagement.common.domain.event.production;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published by the production module when all outputs for a sales order line have been fully stored
 * in the warehouse. Used to trigger SalesOrderLine status transition to IN_WAREHOUSE.
 */
@Getter
public class SalesOrderLineStoredEvent extends DomainEvent {

  private final UUID salesOrderLineId;
  private final UUID triggeredByStockUnitId;

  public SalesOrderLineStoredEvent(
      UUID tenantId, UUID salesOrderLineId, UUID triggeredByStockUnitId) {
    super(tenantId, "SALES_ORDER_LINE_STORED");
    this.salesOrderLineId = salesOrderLineId;
    this.triggeredByStockUnitId = triggeredByStockUnitId;
  }

  @JsonCreator
  public SalesOrderLineStoredEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderLineId") UUID salesOrderLineId,
      @JsonProperty("triggeredByStockUnitId") UUID triggeredByStockUnitId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SALES_ORDER_LINE_STORED",
        occurredAt,
        correlationId);
    this.salesOrderLineId = salesOrderLineId;
    this.triggeredByStockUnitId = triggeredByStockUnitId;
  }
}
