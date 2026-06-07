package com.fabricmanagement.sales.salesorder.app.listener;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.logistics.shipment.domain.event.ShipmentLineConfirmedEvent;
import com.fabricmanagement.sales.salesorder.app.ShipmentProgressService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesOrderEventListener {

  private final ShipmentProgressService shipmentProgressService;
  private final IdempotentEventHandler idempotentHandler;

  @ApplicationModuleListener
  @Retryable(
      retryFor = {
        ObjectOptimisticLockingFailureException.class,
        TransientDataAccessException.class
      },
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onShipmentLineConfirmed(ShipmentLineConfirmedEvent event) {
    idempotentHandler.executeOnce(
        event.getEventId(),
        this.getClass(),
        "onShipmentLineConfirmed",
        () -> {
          log.debug(
              "Handling ShipmentLineConfirmedEvent: shipmentLineId={}, salesOrderLineId={}, qty={}",
              event.getShipmentLineId(),
              event.getSalesOrderLineId(),
              event.getConfirmedQuantity());

          // Faz 1 — shippedQty yaz (ayrı tx, nadiren çakışır)
          // ÖNEMLİ: @Retryable tüm metodu (Faz 1 + Faz 2) tekrar çalıştırır.
          // Ancak Faz 1'deki addShippedQuantity() metodundaki 'processedShipmentLineIds' kontrolü
          // sayesinde bu işlem idempotent'tir ve retry sırasında miktar mükerrer artmaz.
          // Lütfen Faz 1'deki bu idempotency guard'ını KESİNLİKLE KALDIRMAYIN!
          UUID salesOrderId =
              shipmentProgressService.recordLineShipment(
                  event.getSalesOrderLineId(),
                  event.getShipmentLineId(),
                  event.getConfirmedQuantity());

          if (salesOrderId == null) {
            return; // Line bulunamadı, service içinde loglandı
          }

          // Faz 2 — header status güncelle (ayrı tx, retry burada çalışır)
          shipmentProgressService.updateOrderShipmentStatus(salesOrderId);
        });
  }

  @Recover
  public void recoverShipmentLineConfirmed(Exception ex, ShipmentLineConfirmedEvent event) {
    log.error(
        "Failed to process ShipmentLineConfirmedEvent after retries. "
            + "shipmentLineId={}, salesOrderLineId={}: {}",
        event.getShipmentLineId(),
        event.getSalesOrderLineId(),
        ex.getMessage(),
        ex);
    throw new RuntimeException("Event processing failed after retries", ex);
  }
}
