package com.fabricmanagement.production.masterdata.product.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a new product is created.
 *
 * <p>Listeners: Inventory, Analytics, Audit
 */
@Getter
public class ProductCreatedEvent extends DomainEvent {

  private final UUID productId;
  private final ProductType productType;

  @JsonCreator
  public ProductCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("productType") ProductType productType) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "PRODUCT_CREATED",
        occurredAt,
        correlationId);
    this.productId = productId;
    this.productType = productType;
  }

  public ProductCreatedEvent(UUID tenantId, UUID productId, ProductType productType) {
    super(tenantId, "PRODUCT_CREATED");
    this.productId = productId;
    this.productType = productType;
  }
}
