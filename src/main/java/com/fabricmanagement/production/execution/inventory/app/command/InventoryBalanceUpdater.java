package com.fabricmanagement.production.execution.inventory.app.command;

import com.fabricmanagement.production.execution.inventory.domain.InventoryBalance;
import com.fabricmanagement.production.execution.inventory.domain.event.InventoryTransactionCreatedEvent;
import com.fabricmanagement.production.execution.inventory.infra.repository.InventoryBalanceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryBalanceUpdater {

  private final InventoryBalanceRepository inventoryBalanceRepository;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onTransactionCreated(InventoryTransactionCreatedEvent event) {
    log.debug(
        "Updating inventory balance for batchId={}, locationId={}, type={}",
        event.getBatchId(),
        event.getLocationId(),
        event.getTransactionType());

    Optional<InventoryBalance> balanceOpt =
        event.getLocationId() == null
            ? inventoryBalanceRepository.findByBatchIdAndLocationIdIsNull(event.getBatchId())
            : inventoryBalanceRepository.findByBatchIdAndLocationId(
                event.getBatchId(), event.getLocationId());

    InventoryBalance balance =
        balanceOpt.orElseGet(
            () ->
                InventoryBalance.create(
                    event.getTenantId(),
                    event.getBatchId(),
                    event.getLocationId(),
                    event.getUnit()));

    switch (event.getTransactionType()) {
      case RECEIPT:
      case SPLIT_IN:
      case TRANSFER_IN:
      case RETURN:
        balance.setQuantity(balance.getQuantity().add(event.getQuantity()));
        break;

      case CONSUMPTION:
        balance.setQuantity(balance.getQuantity().subtract(event.getQuantity()));
        balance.setConsumedQuantity(balance.getConsumedQuantity().add(event.getQuantity()));
        break;

      case WASTE:
        balance.setQuantity(balance.getQuantity().subtract(event.getQuantity()));
        balance.setWasteQuantity(balance.getWasteQuantity().add(event.getQuantity()));
        break;

      case SPLIT_OUT:
      case TRANSFER_OUT:
      case SAMPLE:
        balance.setQuantity(balance.getQuantity().subtract(event.getQuantity()));
        break;

      case RESERVATION:
        balance.setReservedQuantity(balance.getReservedQuantity().add(event.getQuantity()));
        break;

      case RESERVATION_RELEASE:
        balance.setReservedQuantity(balance.getReservedQuantity().subtract(event.getQuantity()));
        break;

      case ADJUSTMENT:
        balance.setQuantity(balance.getQuantity().add(event.getQuantity()));
        break;

      case QUALITY_TEST:
        break;

      default:
        break;
    }

    balance.setLastTransactionId(event.getTransactionId());
    balance.setLastTransactionDate(event.getTransactionDate());

    inventoryBalanceRepository.save(balance);
  }
}
