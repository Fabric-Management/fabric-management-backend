package com.fabricmanagement.iwm.rules.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class MinStockAlertEvent extends DomainEvent {
  private final UUID productId;
  private final UUID locationId;
  private final BigDecimal currentQty;
  private final BigDecimal minQty;
  private final String unit;

  @JsonCreator
  public MinStockAlertEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("currentQty") BigDecimal currentQty,
      @JsonProperty("minQty") BigDecimal minQty,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "MIN_STOCK_ALERT",
        occurredAt,
        correlationId);
    this.productId = productId;
    this.locationId = locationId;
    this.currentQty = currentQty;
    this.minQty = minQty;
    this.unit = unit;
  }

  @Builder
  public MinStockAlertEvent(
      UUID tenantId,
      UUID productId,
      UUID locationId,
      BigDecimal currentQty,
      BigDecimal minQty,
      String unit) {
    super(tenantId, "MIN_STOCK_ALERT");
    this.productId = productId;
    this.locationId = locationId;
    this.currentQty = currentQty;
    this.minQty = minQty;
    this.unit = unit;
  }
}
