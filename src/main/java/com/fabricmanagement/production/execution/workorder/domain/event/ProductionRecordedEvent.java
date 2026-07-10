package com.fabricmanagement.production.execution.workorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Published when a StockUnit is recorded as a production record for a WorkOrder. */
@Getter
public class ProductionRecordedEvent extends DomainEvent {

  private final UUID workOrderId;
  private final UUID stockUnitId;
  private final UUID batchId;
  private final BigDecimal outputWeight;
  private final String unit;

  @JsonCreator
  public ProductionRecordedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("workOrderId") UUID workOrderId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("outputWeight") BigDecimal outputWeight,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PRODUCTION_RECORDED",
        occurredAt,
        correlationId);
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.outputWeight = outputWeight;
    this.unit = unit;
  }

  public ProductionRecordedEvent(
      UUID tenantId,
      UUID workOrderId,
      UUID stockUnitId,
      UUID batchId,
      BigDecimal outputWeight,
      String unit) {
    super(tenantId, "PRODUCTION_RECORDED");
    this.workOrderId = workOrderId;
    this.stockUnitId = stockUnitId;
    this.batchId = batchId;
    this.outputWeight = outputWeight;
    this.unit = unit;
  }
}
