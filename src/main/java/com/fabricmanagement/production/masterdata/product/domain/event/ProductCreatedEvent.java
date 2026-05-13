package com.fabricmanagement.production.masterdata.product.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
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

  public ProductCreatedEvent(UUID tenantId, UUID productId, ProductType productType) {
    super(tenantId, "PRODUCT_CREATED");
    this.productId = productId;
    this.productType = productType;
  }
}
