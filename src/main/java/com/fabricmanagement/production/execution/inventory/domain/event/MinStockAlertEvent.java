package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Stok minimum eşiğin altına düştü — HIGH. Tedarik departmanına bildirim gönderilir. */
@Getter
public class MinStockAlertEvent extends DomainEvent {

  private final UUID productId;
  private final String productCode;
  private final String productName;
  private final BigDecimal currentStock;
  private final BigDecimal minimumStock;
  private final String unit;

  @JsonCreator
  public MinStockAlertEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("productCode") String productCode,
      @JsonProperty("productName") String productName,
      @JsonProperty("currentStock") BigDecimal currentStock,
      @JsonProperty("minimumStock") BigDecimal minimumStock,
      @JsonProperty("unit") String unit) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "MIN_STOCK_ALERT",
        occurredAt,
        correlationId);
    this.productId = productId;
    this.productCode = productCode;
    this.productName = productName;
    this.currentStock = currentStock;
    this.minimumStock = minimumStock;
    this.unit = unit;
  }

  public MinStockAlertEvent(
      UUID tenantId,
      UUID productId,
      String productCode,
      String productName,
      BigDecimal currentStock,
      BigDecimal minimumStock,
      String unit) {
    super(tenantId, "MIN_STOCK_ALERT");
    this.productId = productId;
    this.productCode = productCode;
    this.productName = productName;
    this.currentStock = currentStock;
    this.minimumStock = minimumStock;
    this.unit = unit;
  }
}
