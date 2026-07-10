package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a StockUnit reaches zero weight and transitions to DEPLETED.
 *
 * <p>Listeners: Batch reconciliation service, IWM location capacity release.
 */
@Getter
public class StockUnitDepletedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final UUID locationId;

  /** Total weight consumed over the lifetime of this unit (== initialWeight). */
  private final BigDecimal totalConsumedWeight;

  private final String unit;

  @JsonCreator
  public StockUnitDepletedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("barcode") String barcode,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("totalConsumedWeight") BigDecimal totalConsumedWeight,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "STOCK_UNIT_DEPLETED",
        occurredAt,
        correlationId);
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.locationId = locationId;
    this.totalConsumedWeight = totalConsumedWeight;
    this.unit = unit;
  }

  public StockUnitDepletedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      UUID locationId,
      BigDecimal totalConsumedWeight,
      String unit) {
    super(tenantId, "STOCK_UNIT_DEPLETED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.locationId = locationId;
    this.totalConsumedWeight = totalConsumedWeight;
    this.unit = unit;
  }
}
