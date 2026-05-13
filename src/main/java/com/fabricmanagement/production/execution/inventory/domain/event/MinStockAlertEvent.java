package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
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
