package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

/** Tedarikçi iade oranı eşiği aştı — CRITICAL. Kalite ve tedarik yöneticisine anında bildirim. */
@Getter
public class ReturnRateExceededEvent extends DomainEvent {

  private final UUID supplierId;
  private final String supplierName;
  private final BigDecimal returnRate; // %
  private final BigDecimal thresholdRate; // %
  private final int periodDays;

  public ReturnRateExceededEvent(
      UUID tenantId,
      UUID supplierId,
      String supplierName,
      BigDecimal returnRate,
      BigDecimal thresholdRate,
      int periodDays) {
    super(tenantId, "RETURN_RATE_EXCEEDED");
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.returnRate = returnRate;
    this.thresholdRate = thresholdRate;
    this.periodDays = periodDays;
  }
}
