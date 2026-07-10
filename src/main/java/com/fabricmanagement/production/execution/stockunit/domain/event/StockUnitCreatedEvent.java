package com.fabricmanagement.production.execution.stockunit.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a new StockUnit is created (e.g. after GoodsReceipt confirmation).
 *
 * <p>Listeners: IWM (stock movement recording), notification hub.
 */
@Getter
public class StockUnitCreatedEvent extends DomainEvent {

  private final UUID stockUnitId;
  private final String barcode;
  private final UUID batchId;
  private final ProductType productType;
  private final PackageType packageType;
  private final BigDecimal initialWeight;
  private final String unit;
  private final UUID locationId;

  public StockUnitCreatedEvent(
      UUID tenantId,
      UUID stockUnitId,
      String barcode,
      UUID batchId,
      ProductType productType,
      PackageType packageType,
      BigDecimal initialWeight,
      String unit,
      UUID locationId) {
    super(tenantId, "STOCK_UNIT_CREATED");
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.productType = productType;
    this.packageType = packageType;
    this.initialWeight = initialWeight;
    this.unit = unit;
    this.locationId = locationId;
  }

  @JsonCreator
  public StockUnitCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("barcode") String barcode,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("productType") ProductType productType,
      @JsonProperty("packageType") PackageType packageType,
      @JsonProperty("initialWeight") BigDecimal initialWeight,
      @JsonProperty("unit") String unit,
      @JsonProperty("locationId") UUID locationId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "STOCK_UNIT_CREATED",
        occurredAt,
        correlationId);
    this.stockUnitId = stockUnitId;
    this.barcode = barcode;
    this.batchId = batchId;
    this.productType = productType;
    this.packageType = packageType;
    this.initialWeight = initialWeight;
    this.unit = unit;
    this.locationId = locationId;
  }
}
