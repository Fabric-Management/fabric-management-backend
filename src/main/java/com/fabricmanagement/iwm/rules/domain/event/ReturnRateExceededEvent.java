package com.fabricmanagement.iwm.rules.domain.event;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReturnRateExceededEvent {
  private final UUID tenantId;
  private final UUID tradingPartnerId;
  private final BigDecimal currentRate;
  private final BigDecimal thresholdRate;
  private final Integer windowDays;
}
