package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.logistics.shipment.domain.event.ShipmentLineConfirmedEvent;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesOrderEventListener {

  private final SalesOrderLineRepository salesOrderLineRepository;

  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onShipmentLineConfirmed(ShipmentLineConfirmedEvent event) {
    log.debug(
        "Handling ShipmentLineConfirmedEvent: shipmentLineId={}, salesOrderLineId={}, qty={}",
        event.getShipmentLineId(),
        event.getSalesOrderLineId(),
        event.getConfirmedQuantity());

    SalesOrderLine line =
        salesOrderLineRepository.findById(event.getSalesOrderLineId()).orElse(null);

    if (line == null) {
      log.error(
          "SalesOrderLine not found for shipment line confirmed: {}", event.getSalesOrderLineId());
      return;
    }

    line.addShippedQuantity(event.getShipmentLineId(), event.getConfirmedQuantity());
    salesOrderLineRepository.save(line);

    log.info(
        "Updated shipped quantity for SalesOrderLine {}. New shippedQty: {}",
        line.getId(),
        line.getShippedQty());
  }
}
