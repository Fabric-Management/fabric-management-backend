package com.fabricmanagement.iwm.rules.domain.event;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MinStockAlertEvent {
  private final UUID tenantId;
  private final UUID materialId;
  private final UUID locationId;
  private final BigDecimal currentQty;
  private final BigDecimal minQty;
  private final String unit;
}
