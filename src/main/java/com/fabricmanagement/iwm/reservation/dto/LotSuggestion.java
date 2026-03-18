package com.fabricmanagement.iwm.reservation.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LotSuggestion {
  private UUID batchId;
  private String lotNumber;
  private UUID locationId;
  private BigDecimal availableQty;
  private OffsetDateTime productionDate;
}
