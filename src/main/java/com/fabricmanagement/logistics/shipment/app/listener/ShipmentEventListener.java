package com.fabricmanagement.logistics.shipment.app.listener;

import com.fabricmanagement.logistics.shipment.domain.Shipment;
import com.fabricmanagement.logistics.shipment.domain.ShipmentLine;
import com.fabricmanagement.logistics.shipment.domain.ShipmentLineBatch;
import com.fabricmanagement.logistics.shipment.domain.event.ShipmentPickedUpEvent;
import com.fabricmanagement.logistics.shipment.infra.repository.ShipmentRepository;
import com.fabricmanagement.production.execution.batch.api.facade.BatchFacade;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventListener {

  private final ShipmentRepository shipmentRepository;
  private final InventoryFacade inventoryFacade;
  private final BatchFacade batchFacade;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onShipmentPickedUp(ShipmentPickedUpEvent event) {
    log.debug("Handling ShipmentPickedUpEvent for shipmentId={}", event.getShipmentId());

    Shipment shipment =
        shipmentRepository
            .findById(event.getShipmentId())
            .orElseThrow(
                () -> new IllegalStateException("Shipment not found: " + event.getShipmentId()));

    if (shipment.getLines() == null) {
      return;
    }

    for (ShipmentLine line : shipment.getLines()) {
      if (line.getBatches() == null) continue;

      for (ShipmentLineBatch lineBatch : line.getBatches()) {
        UUID batchId = lineBatch.getBatchId();

        BatchDto batch =
            batchFacade
                .getById(batchId)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Batch not found for shipment dispatch: " + batchId));

        inventoryFacade.logTransaction(
            event.getTenantId(),
            batchId,
            InventoryTransactionType.SHIPMENT_DISPATCH,
            lineBatch.getQuantity(),
            batch.getUnit(),
            batch.getLocationId(),
            event.getShipmentId(),
            "SHIPMENT",
            "Shipment Picked Up. Line: " + line.getLineNumber(),
            InventoryTransactionReasonCode.NORMAL_OPERATION,
            event.getEventId().toString()
                + "_"
                + line.getId().toString()
                + "_"
                + batchId.toString());
      }
    }
  }
}
