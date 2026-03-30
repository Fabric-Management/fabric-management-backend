package com.fabricmanagement.flowboard.automation.infra.adapter;

import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the AutomationNotificationPort. Integrates with the platform
 * communication/notification module to send alerts.
 */
@Component
@Slf4j
public class AutomationNotificationAdapter implements AutomationNotificationPort {

  @Override
  public void notifyManager(UUID tenantId, UUID boardId, String message, UUID taskId) {
    // TODO: Phase 3 Notification - integrate with actual NotificationService via cross-module
    // facade
    log.info(
        "Automation alert to Manager | tenant: {}, board: {}, task: {}, msg: {}",
        tenantId,
        boardId,
        taskId,
        message);
  }

  @Override
  public void notifyUser(UUID tenantId, UUID boardId, UUID userId, String message, UUID taskId) {
    // TODO: Phase 3 Notification - integrate with actual NotificationService via cross-module
    // facade
    log.info(
        "Automation alert to User {} | tenant: {}, board: {}, task: {}, msg: {}",
        userId,
        tenantId,
        boardId,
        taskId,
        message);
  }
}
