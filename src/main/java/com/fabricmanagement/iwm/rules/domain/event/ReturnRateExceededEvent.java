package com.fabricmanagement.iwm.rules.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ReturnRateExceededEvent extends DomainEvent {
  private final UUID tradingPartnerId;
  private final BigDecimal currentRate;
  private final BigDecimal thresholdRate;
  private final Integer windowDays;

  @Builder
  public ReturnRateExceededEvent(
      UUID tenantId,
      UUID tradingPartnerId,
      BigDecimal currentRate,
      BigDecimal thresholdRate,
      Integer windowDays) {
    super(tenantId, "RETURN_RATE_EXCEEDED");
    this.tradingPartnerId = tradingPartnerId;
    this.currentRate = currentRate;
    this.thresholdRate = thresholdRate;
    this.windowDays = windowDays;
  }
}
