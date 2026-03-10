package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.production.execution.batch.domain.event.*;
import com.fabricmanagement.production.execution.inventory.app.command.InventoryTransactionCommandService;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchEventListener {

  private final InventoryTransactionCommandService inventoryTransactionCommandService;
  private final BatchLineageService batchLineageService;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchCreated(BatchCreatedEvent event) {
    log.debug("Handling BatchCreatedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.RECEIPT,
        event.getQuantity(),
        event.getUnit(),
        event.getLocationId(),
        null,
        null,
        "Initial stock receipt",
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchReserved(BatchReservedEvent event) {
    log.debug("Handling BatchReservedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.RESERVATION,
        event.getQuantity(),
        event.getUnit(),
        event.getLocationId(),
        event.getReservationId(),
        "RESERVATION",
        "Reservation for " + event.getReferenceType(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchReservationReleased(BatchReservationReleasedEvent event) {
    log.debug("Handling BatchReservationReleasedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.RESERVATION_RELEASE,
        event.getReleasedQuantity(),
        event.getUnit(),
        event.getLocationId(),
        event.getReservationId(),
        "RESERVATION",
        "Reservation cancelled",
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchConsumed(BatchConsumedEvent event) {
    log.debug("Handling BatchConsumedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.CONSUMPTION,
        event.getQuantity(),
        event.getUnit(),
        event.getLocationId(),
        event.getReferenceId(),
        event.getReferenceType(),
        "Batch consumption",
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchWasteRecorded(BatchWasteRecordedEvent event) {
    log.debug("Handling BatchWasteRecordedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.WASTE,
        event.getQuantity(),
        event.getUnit(),
        event.getLocationId(),
        null,
        null,
        "Production waste",
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchAdjusted(BatchAdjustedEvent event) {
    log.debug("Handling BatchAdjustedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.ADJUSTMENT,
        event.getDelta(), // Keep the sign for balance updater
        event.getUnit(),
        event.getLocationId(),
        null,
        null,
        event.getReason() + (event.getRemarks() != null ? " — " + event.getRemarks() : ""),
        InventoryTransactionReasonCode.INVENTORY_COUNT_ADJUSTMENT,
        event.getEventId().toString());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchSplit(BatchSplitEvent event) {
    log.debug("Handling BatchSplitEvent for parentBatchId={}", event.getParentBatchId());

    batchLineageService.create(
        CreateBatchLineageRequest.builder()
            .parentBatchId(event.getParentBatchId())
            .childBatchId(event.getChildBatchId())
            .consumedQuantity(event.getSplitQuantity())
            .unit(event.getUnit())
            .consumptionPercentage(new BigDecimal("100"))
            .consumedAt(Instant.now())
            .processReference("SPLIT")
            .remarks(event.getRemarks())
            .build());

    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getParentBatchId(),
        InventoryTransactionType.SPLIT_OUT,
        event.getSplitQuantity(),
        event.getUnit(),
        event.getParentLocationId(),
        event.getChildBatchId(),
        "BATCH",
        "Split to " + event.getChildBatchCode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_OUT");

    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getChildBatchId(),
        InventoryTransactionType.SPLIT_IN,
        event.getSplitQuantity(),
        event.getUnit(),
        event.getChildLocationId(),
        event.getParentBatchId(),
        "BATCH",
        "Split from " + event.getParentBatchCode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_IN");
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchTransferred(BatchTransferredEvent event) {
    log.debug("Handling BatchTransferredEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.TRANSFER_OUT,
        event.getQuantity(),
        event.getUnit(),
        event.getOldLocationId(),
        event.getOldLocationId(),
        "WAREHOUSE_LOCATION",
        "Transfer out. " + (event.getRemarks() != null ? event.getRemarks() : ""),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_OUT");

    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.TRANSFER_IN,
        event.getQuantity(),
        event.getUnit(),
        event.getNewLocationId(),
        event.getNewLocationId(),
        "WAREHOUSE_LOCATION",
        "Transfer in. " + (event.getRemarks() != null ? event.getRemarks() : ""),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_IN");
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchProductionStarted(BatchProductionStartedEvent event) {
    log.debug("Handling BatchProductionStartedEvent for batchId={}", event.getBatchId());
    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.TRANSFER_OUT,
        event.getQuantity(),
        event.getUnit(),
        event.getPreviousLocationId(),
        event.getPreviousLocationId(),
        "PRODUCTION",
        "Start production — transferred to " + event.getMachineCode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_OUT");

    inventoryTransactionCommandService.logTransaction(
        event.getTenantId(),
        event.getBatchId(),
        InventoryTransactionType.TRANSFER_IN,
        event.getQuantity(),
        event.getUnit(),
        event.getMachineLocationId(),
        event.getMachineLocationId(),
        "PRODUCTION",
        "Start production — received at " + event.getMachineCode(),
        InventoryTransactionReasonCode.NORMAL_OPERATION,
        event.getEventId().toString() + "_IN");
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onBatchReservationCompleted(BatchReservationCompletedEvent event) {
    log.debug("Handling BatchReservationCompletedEvent for batchId={}", event.getBatchId());
    if (event.getReleasedQuantity().compareTo(BigDecimal.ZERO) > 0) {
      inventoryTransactionCommandService.logTransaction(
          event.getTenantId(),
          event.getBatchId(),
          InventoryTransactionType.RESERVATION_RELEASE,
          event.getReleasedQuantity(),
          event.getUnit(),
          event.getLocationId(),
          event.getReservationId(),
          "RESERVATION",
          "Reservation completed - remaining stock released",
          InventoryTransactionReasonCode.NORMAL_OPERATION,
          event.getEventId().toString());
    }
  }
}
