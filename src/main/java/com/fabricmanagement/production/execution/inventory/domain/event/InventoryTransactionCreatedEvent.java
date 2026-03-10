package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryTransactionCreatedEvent {
  private final UUID transactionId;
  private final UUID tenantId;
  private final UUID batchId;
  private final InventoryTransactionType transactionType;
  private final BigDecimal quantity;
  private final String unit;
  private final UUID locationId;
  private final Instant transactionDate;
}
