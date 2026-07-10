package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a StockUnit's quality grade is changed.
 *
 * <p>Carries both old and new grade IDs. Listeners can use these to fetch the full grade details if
 * needed.
 *
 * <p>Listeners: notification hub (grade demotion alert), audit trail.
 */
@Getter
public class StockUnitGradeChangedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final UUID previousGradeId;
  private final UUID newGradeId;

  /** True if this was a promotion (upgrade to better quality) — requires prior approval. */
  private final boolean promotion;

  @JsonCreator
  public StockUnitGradeChangedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("barcode") String barcode,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("previousGradeId") UUID previousGradeId,
      @JsonProperty("newGradeId") UUID newGradeId,
      @JsonProperty("promotion") boolean promotion) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "STOCK_UNIT_GRADE_CHANGED",
        occurredAt,
        correlationId);
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.previousGradeId = previousGradeId;
    this.newGradeId = newGradeId;
    this.promotion = promotion;
  }

  public StockUnitGradeChangedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      UUID previousGradeId,
      UUID newGradeId,
      boolean promotion) {
    super(tenantId, "STOCK_UNIT_GRADE_CHANGED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.previousGradeId = previousGradeId;
    this.newGradeId = newGradeId;
    this.promotion = promotion;
  }
}
