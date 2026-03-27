package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalPendingEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Onay sistemi event listener'ları.
 *
 * <p>Dinlenen eventler (event-catalog.md):
 *
 * <ul>
 *   <li>ApprovalPending → HIGH — onaylayıcıya
 *   <li>ApprovalRejected → HIGH — talep sahibine
 *   <li>ApprovalApproved → NORMAL — talep sahibine
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalNotificationListener {

  private final NotificationHubService notificationHubService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalPending(ApprovalPendingEvent event) {
    log.info(
        "NotificationHub ← ApprovalPending: entity={} code={}",
        event.getEntityType(),
        event.getEntityCode());

    var payload =
        Map.of(
            "entityType", event.getEntityType(),
            "entityCode", event.getEntityCode(),
            "referenceId", event.getEntityId().toString(),
            "referenceType", event.getEntityType());

    notificationHubService.notify(
        NotificationContext.of(
            event.getTenantId(),
            event.getApproverId(),
            NotificationEventType.APPROVAL_PENDING,
            payload,
            event.getEntityId(),
            event.getEntityType()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    log.info(
        "NotificationHub ← ApprovalRejected: entity={} code={}",
        event.getEntityType(),
        event.getEntityCode());

    var payload =
        Map.of(
            "entityType", event.getEntityType(),
            "entityCode", event.getEntityCode(),
            "rejectionReason", event.getRejectionReason() != null ? event.getRejectionReason() : "",
            "referenceId", event.getEntityId().toString(),
            "referenceType", event.getEntityType());

    notificationHubService.notify(
        NotificationContext.of(
            event.getTenantId(),
            event.getRequesterId(),
            NotificationEventType.APPROVAL_REJECTED,
            payload,
            event.getEntityId(),
            event.getEntityType()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    log.info(
        "NotificationHub ← ApprovalApproved: entity={} code={}",
        event.getEntityType(),
        event.getEntityCode());

    var payload =
        Map.of(
            "entityType", event.getEntityType(),
            "entityCode", event.getEntityCode(),
            "referenceId", event.getEntityId().toString(),
            "referenceType", event.getEntityType());

    notificationHubService.notify(
        NotificationContext.of(
            event.getTenantId(),
            event.getRequesterId(),
            NotificationEventType.APPROVAL_APPROVED,
            payload,
            event.getEntityId(),
            event.getEntityType()));
  }
}
