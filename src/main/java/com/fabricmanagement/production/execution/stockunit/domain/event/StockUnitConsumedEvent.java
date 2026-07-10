package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when weight is consumed from a StockUnit.
 *
 * <p>Listeners: IWM (stock movement), Batch reconciliation.
 */
@Getter
public class StockUnitConsumedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final BigDecimal consumedAmount;
  private final BigDecimal remainingWeight;
  private final String unit;

  @JsonCreator
  public StockUnitConsumedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("barcode") String barcode,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("consumedAmount") BigDecimal consumedAmount,
      @JsonProperty("remainingWeight") BigDecimal remainingWeight,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "STOCK_UNIT_CONSUMED",
        occurredAt,
        correlationId);
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.consumedAmount = consumedAmount;
    this.remainingWeight = remainingWeight;
    this.unit = unit;
  }

  public StockUnitConsumedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      BigDecimal consumedAmount,
      BigDecimal remainingWeight,
      String unit) {
    super(tenantId, "STOCK_UNIT_CONSUMED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.consumedAmount = consumedAmount;
    this.remainingWeight = remainingWeight;
    this.unit = unit;
  }
}
