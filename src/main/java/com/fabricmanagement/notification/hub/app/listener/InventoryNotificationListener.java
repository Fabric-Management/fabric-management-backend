package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.production.execution.inventory.domain.event.MinStockAlertEvent;
import com.fabricmanagement.production.execution.inventory.domain.event.ReturnRateExceededEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * IWM (Inventory / Warehouse Management) event listener'ları.
 *
 * <p>TODO (FlowBoard departman entegrasyonu sonrası): - MinStockAlert → Tedarik departmanına HIGH
 * bildirim - ReturnRateExceeded → Kalite yöneticisi + Tedarik müdürüne CRITICAL bildirim
 */
@Component
@Slf4j
public class InventoryNotificationListener {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onMinStockAlert(MinStockAlertEvent event) {
    log.warn(
        "NotificationHub ← MinStockAlert [HIGH]: material={} current={} min={}",
        event.getMaterialCode(),
        event.getCurrentStock(),
        event.getMinimumStock());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onReturnRateExceeded(ReturnRateExceededEvent event) {
    log.error(
        "NotificationHub ← ReturnRateExceeded [CRITICAL]: supplier={} rate={}% threshold={}%",
        event.getSupplierName(), event.getReturnRate(), event.getThresholdRate());
  }
}
