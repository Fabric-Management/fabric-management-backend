package com.fabricmanagement.production.execution.stockunit.app.listener;

import com.fabricmanagement.production.execution.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitTransferredEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockUnitInventoryBridgeListener {

  private final InventoryFacade inventoryFacade;

  /**
   * StockUnit location transfer → InventoryTransaction TRANSFER_OUT + TRANSFER_IN
   *
   * <p>Note: This does NOT duplicate BatchTransferredEvent handling. Batch transfer = entire lot
   * moves. StockUnit transfer = single physical unit moves.
   */
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onStockUnitTransferred(StockUnitTransferredEvent event) {
    UUID batchId = event.getBatchId();
    BigDecimal weight = event.getWeight();
    String unitStr = event.getUnit();

    log.debug(
        "Bridging StockUnitTransferredEvent to InventoryTransaction for StockUnit {}",
        event.getBarcode());

    inventoryFacade.logTransaction(
        event.getTenantId(),
        batchId,
        InventoryTransactionType.TRANSFER_OUT,
        weight,
        unitStr,
        event.getFromLocationId(),
        event.getStockUnitId(),
        "STOCK_UNIT",
        "StockUnit transfer out: " + event.getBarcode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_SU_OUT");

    inventoryFacade.logTransaction(
        event.getTenantId(),
        batchId,
        InventoryTransactionType.TRANSFER_IN,
        weight,
        unitStr,
        event.getToLocationId(),
        event.getStockUnitId(),
        "STOCK_UNIT",
        "StockUnit transfer in: " + event.getBarcode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_SU_IN");
  }
}
