package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.DepartmentRecipientPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderPendingApprovalEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchQcFailedEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchQcPendingEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Üretim zinciri event listener'ları.
 *
 * <p>Dinlenen eventler (event-catalog.md):
 *
 * <ul>
 *   <li>BatchQcFailedEvent → CRITICAL
 *   <li>BatchQcPendingEvent → NORMAL
 *   <li>WorkOrderPendingApproval → HIGH
 *   <li>WorkOrderApproved → NORMAL
 *   <li>GoodsReceiptConfirmed → NORMAL (alıcı çözümlemesi bekliyor)
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductionNotificationListener {

  private final NotificationHubService notificationHubService;
  private final DepartmentRecipientPort departmentRecipientPort;
  private final UserQueryService userQueryService;

  // ---- CRITICAL ----

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onBatchQcFailed(BatchQcFailedEvent event) {
    log.info("NotificationHub ← BatchQcFailed: batch={}", event.getBatchCode());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var payload =
              Map.of(
                  "batchCode",
                  event.getBatchCode(),
                  "reason",
                  event.getFailureReason() != null ? event.getFailureReason() : "",
                  "referenceId",
                  event.getBatchId().toString(),
                  "referenceType",
                  "BATCH");

          notificationHubService.notify(
              NotificationContext.of(
                  event.getTenantId(),
                  event.getQualityResponsibleUserId(),
                  NotificationEventType.BATCH_QC_FAILED,
                  payload,
                  event.getBatchId(),
                  "BATCH"));
        });
  }

  // ---- HIGH ----

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onWorkOrderPendingApproval(WorkOrderPendingApprovalEvent event) {
    log.info("NotificationHub ← WorkOrderPendingApproval: wo={}", event.getWorkOrderNumber());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var payload =
              Map.of(
                  "workOrderNumber", event.getWorkOrderNumber(),
                  "referenceId", event.getWorkOrderId().toString(),
                  "referenceType", "WORK_ORDER");

          notificationHubService.notify(
              NotificationContext.of(
                  event.getTenantId(),
                  event.getAssignedToUserId(),
                  NotificationEventType.WORK_ORDER_PENDING_APPROVAL,
                  payload,
                  event.getWorkOrderId(),
                  "WORK_ORDER"));
        });
  }

  // ---- NORMAL ----

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onBatchQcPending(BatchQcPendingEvent event) {
    log.info("NotificationHub ← BatchQcPending: batch={}", event.getBatchCode());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var payload =
              Map.of(
                  "batchCode", event.getBatchCode(),
                  "referenceId", event.getBatchId().toString(),
                  "referenceType", "BATCH");

          notificationHubService.notify(
              NotificationContext.of(
                  event.getTenantId(),
                  event.getQualityResponsibleUserId(),
                  NotificationEventType.BATCH_QC_PENDING,
                  payload,
                  event.getBatchId(),
                  "BATCH"));
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onWorkOrderApproved(WorkOrderApprovedEvent event) {
    log.info("NotificationHub ← WorkOrderApproved: wo={}", event.getWorkOrderNumber());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var payload =
              Map.of(
                  "workOrderNumber", event.getWorkOrderNumber(),
                  "referenceId", event.getWorkOrderId().toString(),
                  "referenceType", "WORK_ORDER");

          UUID approvedBy = event.getApprovedByUserId();
          if (approvedBy == null || SystemUser.ID.equals(approvedBy)) {
            var fallback =
                userQueryService.findActiveUserIdsByRoleCodes(
                    event.getTenantId(), List.of("ADMIN", "MANAGER"));
            if (fallback.isEmpty()) {
              log.warn(
                  "WorkOrderApproved: no recipients for wo={} (system approval, no ADMIN/MANAGER"
                      + " users)",
                  event.getWorkOrderNumber());
              return;
            }
            notificationHubService.notifyAll(
                fallback,
                event.getTenantId(),
                NotificationEventType.WORK_ORDER_APPROVED,
                payload,
                event.getWorkOrderId(),
                "WORK_ORDER");
            return;
          }

          notificationHubService.notify(
              NotificationContext.of(
                  event.getTenantId(),
                  approvedBy,
                  NotificationEventType.WORK_ORDER_APPROVED,
                  payload,
                  event.getWorkOrderId(),
                  "WORK_ORDER"));
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onGoodsReceiptConfirmed(GoodsReceiptConfirmedEvent event) {
    log.info(
        "NotificationHub ← GoodsReceiptConfirmed: receipt={} items={}",
        event.getReceiptNumber(),
        event.getItems().size());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          List<UUID> managerIds =
              departmentRecipientPort.findManagersByDepartmentKeyword(
                  event.getTenantId(), "WAREHOUSE", "Warehouse");

          if (!managerIds.isEmpty()) {
            var payload =
                Map.of(
                    "receiptNumber",
                    event.getReceiptNumber() != null ? event.getReceiptNumber() : "",
                    "itemCount",
                    String.valueOf(event.getItems().size()));
            notificationHubService.notifyAll(
                managerIds,
                event.getTenantId(),
                NotificationEventType.GOODS_RECEIPT_CONFIRMED,
                payload,
                event.getReceiptId(),
                "GOODS_RECEIPT");
          } else {
            log.warn(
                "No recipients found for GoodsReceiptConfirmed (receipt={})",
                event.getReceiptNumber());
          }
        });
  }
}
