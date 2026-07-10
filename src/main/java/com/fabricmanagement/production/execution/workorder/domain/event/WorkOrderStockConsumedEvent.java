package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a StockUnit is physically consumed for a WorkOrder on the shop floor. */
@Getter
public class WorkOrderStockConsumedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID stockUnitId;
  private final UUID batchId;
  private final BigDecimal consumedWeight;
  private final String unit;

  @JsonCreator
  public WorkOrderStockConsumedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("consumedWeight") BigDecimal consumedWeight,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "WORK_ORDER_STOCK_CONSUMED",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.consumedWeight = consumedWeight;
    this.unit = unit;
  }

  public WorkOrderStockConsumedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      BigDecimal consumedWeight,
      String unit) {
    super(tenantId, "WORK_ORDER_STOCK_CONSUMED");
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.consumedWeight = consumedWeight;
    this.unit = unit;
  }
}
