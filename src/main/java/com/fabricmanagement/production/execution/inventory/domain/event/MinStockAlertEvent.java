package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** Stok minimum eşiğin altına düştü — HIGH. Tedarik departmanına bildirim gönderilir. */
@Getter
public class MinStockAlertEvent extends DomainEvent {

  private final UUID materialId;
  private final String materialCode;
  private final String materialName;
  private final BigDecimal currentStock;
  private final BigDecimal minimumStock;
  private final String unit;

  public MinStockAlertEvent(
      UUID tenantId,
      UUID materialId,
      String materialCode,
      String materialName,
      BigDecimal currentStock,
      BigDecimal minimumStock,
      String unit) {
    super(tenantId, "MIN_STOCK_ALERT");
    this.materialId = materialId;
    this.materialCode = materialCode;
    this.materialName = materialName;
    this.currentStock = currentStock;
    this.minimumStock = minimumStock;
    this.unit = unit;
  }
}
