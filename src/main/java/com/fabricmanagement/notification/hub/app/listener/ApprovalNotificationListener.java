package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalPendingEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.platform.user.domain.SystemUser;
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

  private static String nonNull(String value) {
    return value != null ? value : "";
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalPending(ApprovalPendingEvent event) {
    log.info(
        "NotificationHub ← ApprovalPending: entity={} code={}",
        event.getEntityType(),
        event.getEntityCode());

    var payload =
        Map.of(
            "entityType", nonNull(event.getEntityType()),
            "entityCode", nonNull(event.getEntityCode()),
            "referenceId", event.getEntityId().toString(),
            "referenceType", nonNull(event.getEntityType()));

    List<UUID> recipients = event.getNotifyRecipientIds();
    if (recipients != null && !recipients.isEmpty()) {
      notificationHubService.notifyAll(
          recipients,
          event.getTenantId(),
          NotificationEventType.APPROVAL_PENDING,
          payload,
          event.getEntityId(),
          event.getEntityType());
      return;
    }
    if (event.getApproverId() != null) {
      notificationHubService.notify(
          NotificationContext.of(
              event.getTenantId(),
              event.getApproverId(),
              NotificationEventType.APPROVAL_PENDING,
              payload,
              event.getEntityId(),
              event.getEntityType()));
      return;
    }
    log.warn(
        "ApprovalPending: no recipients (entity={} {}) — skipping notification enqueue",
        event.getEntityType(),
        event.getEntityCode());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onApprovalRejected(ApprovalRejectedEvent event) {
    log.info(
        "NotificationHub ← ApprovalRejected: entity={} code={}",
        event.getEntityType(),
        event.getEntityCode());

    if (!isRealTenantUser(event.getRequesterId())) {
      log.debug(
          "ApprovalRejected: requester not a DB user (id={}) — skipping notification",
          event.getRequesterId());
      return;
    }

    var payload =
        Map.of(
            "entityType", nonNull(event.getEntityType()),
            "entityCode", nonNull(event.getEntityCode()),
            "rejectionReason", nonNull(event.getRejectionReason()),
            "referenceId", event.getEntityId().toString(),
            "referenceType", nonNull(event.getEntityType()));

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

    if (!isRealTenantUser(event.getRequesterId())) {
      log.debug(
          "ApprovalApproved: requester not a DB user (id={}) — skipping notification",
          event.getRequesterId());
      return;
    }

    var payload =
        Map.of(
            "entityType", nonNull(event.getEntityType()),
            "entityCode", nonNull(event.getEntityCode()),
            "referenceId", event.getEntityId().toString(),
            "referenceType", nonNull(event.getEntityType()));

    notificationHubService.notify(
        NotificationContext.of(
            event.getTenantId(),
            event.getRequesterId(),
            NotificationEventType.APPROVAL_APPROVED,
            payload,
            event.getEntityId(),
            event.getEntityType()));
  }

  private static boolean isRealTenantUser(UUID userId) {
    return userId != null && !SystemUser.ID.equals(userId);
  }
}
