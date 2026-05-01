package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.production.execution.inventory.domain.event.MinStockAlertEvent;
import com.fabricmanagement.production.execution.inventory.domain.event.ReturnRateExceededEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** IWM (Inventory / Warehouse Management) event listener'ları. */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onMinStockAlert(MinStockAlertEvent event) {
    log.warn(
        "NotificationHub ← MinStockAlert [HIGH]: material={} current={} min={}",
        event.getMaterialCode(),
        event.getCurrentStock(),
        event.getMinimumStock());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> recipientIds =
              departmentRecipientPort.findUsersByDepartmentKeyword(
                  event.getTenantId(), "PROCUREMENT", "Procurement");

          if (!recipientIds.isEmpty()) {
            Map<String, String> payload =
                Map.of(
                    "materialCode", event.getMaterialCode() != null ? event.getMaterialCode() : "",
                    "materialName", event.getMaterialName() != null ? event.getMaterialName() : "",
                    "currentStock",
                        event.getCurrentStock() != null ? event.getCurrentStock().toString() : "0",
                    "minimumStock",
                        event.getMinimumStock() != null ? event.getMinimumStock().toString() : "0");

            notificationHubService.notifyAll(
                recipientIds,
                event.getTenantId(),
                NotificationEventType.MIN_STOCK_ALERT,
                payload,
                event.getMaterialId(),
                "MATERIAL");
          } else {
            log.warn(
                "No recipients found for MinStockAlert (material={})", event.getMaterialCode());
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onReturnRateExceeded(ReturnRateExceededEvent event) {
    log.error(
        "NotificationHub ← ReturnRateExceeded [CRITICAL]: supplier={} rate={}% threshold={}%",
        event.getSupplierName(), event.getReturnRate(), event.getThresholdRate());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> managerIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "QUALITY", "PROCUREMENT");

          if (!managerIds.isEmpty()) {
            Map<String, String> payload =
                Map.of(
                    "supplierName", event.getSupplierName() != null ? event.getSupplierName() : "",
                    "returnRate",
                        event.getReturnRate() != null ? event.getReturnRate().toString() : "0",
                    "thresholdRate",
                        event.getThresholdRate() != null
                            ? event.getThresholdRate().toString()
                            : "0");

            notificationHubService.notifyAll(
                managerIds,
                event.getTenantId(),
                NotificationEventType.RETURN_RATE_EXCEEDED,
                payload,
                event.getSupplierId(),
                "SUPPLIER");
          } else {
            log.warn(
                "No recipients found for ReturnRateExceeded (supplier={})",
                event.getSupplierName());
          }
        });
  }
}
