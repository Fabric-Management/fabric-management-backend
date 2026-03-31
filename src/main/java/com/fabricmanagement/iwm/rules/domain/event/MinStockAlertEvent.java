package com.fabricmanagement.iwm.rules.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class MinStockAlertEvent extends DomainEvent {
  private final UUID materialId;
  private final UUID locationId;
  private final BigDecimal currentQty;
  private final BigDecimal minQty;
  private final String unit;

  @Builder
  public MinStockAlertEvent(
      UUID tenantId,
      UUID materialId,
      UUID locationId,
      BigDecimal currentQty,
      BigDecimal minQty,
      String unit) {
    super(tenantId, "MIN_STOCK_ALERT");
    this.materialId = materialId;
    this.locationId = locationId;
    this.currentQty = currentQty;
    this.minQty = minQty;
    this.unit = unit;
  }
}
