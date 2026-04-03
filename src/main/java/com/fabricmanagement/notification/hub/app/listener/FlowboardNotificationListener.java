package com.fabricmanagement.notification.hub.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.automation.domain.event.AutomationAlertRequestedEvent;
import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlowboardNotificationListener {

  private final NotificationHubService notificationHubService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onAutomationAlert(AutomationAlertRequestedEvent event) {
    log.info(
        "NotificationHub ← AutomationAlertRequested: board={}, task={}",
        event.getBoardId(),
        event.getTaskId());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          if (event.getRecipientId() == null) {
            log.warn(
                "RecipientId is null for AutomationAlertRequestedEvent (board={}). Skipping"
                    + " notification.",
                event.getBoardId());
            return;
          }

          var payload =
              Map.of(
                  "boardId", event.getBoardId().toString(),
                  "taskId", event.getTaskId().toString(),
                  "message", event.getMessage() != null ? event.getMessage() : "");

          notificationHubService.notify(
              NotificationContext.of(
                  event.getTenantId(),
                  event.getRecipientId(),
                  NotificationEventType.AUTOMATION_ALERT,
                  payload,
                  event.getTaskId(),
                  "FLOWBOARD_TASK"));
        });
  }
}
