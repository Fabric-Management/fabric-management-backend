package com.fabricmanagement.production.execution.inventory.api.facade;

import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import java.math.BigDecimal;
import java.util.UUID;

/** Cross-module communication port for Inventory transactions. */
public interface InventoryFacade {

  void logTransaction(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType txType,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      UUID referenceId,
      String referenceTypeStr,
      String remarks,
      InventoryTransactionReasonCode reasonCode,
      String idempotencyKey);

  BigDecimal getAvailableQuantityByMaterial(UUID tenantId, UUID materialId);
}
