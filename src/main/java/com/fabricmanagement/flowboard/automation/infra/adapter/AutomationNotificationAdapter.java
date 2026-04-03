package com.fabricmanagement.flowboard.automation.infra.adapter;

import com.fabricmanagement.flowboard.automation.domain.event.AutomationAlertRequestedEvent;
import com.fabricmanagement.flowboard.automation.domain.port.out.AutomationNotificationPort;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the AutomationNotificationPort. Integrates with the platform
 * communication/notification module to send alerts.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AutomationNotificationAdapter implements AutomationNotificationPort {

  private final ApplicationEventPublisher publisher;
  private final BoardRepository boardRepository;

  @Override
  public void notifyManager(UUID tenantId, UUID boardId, String message, UUID taskId) {
    UUID managerUserId = boardRepository.findManagerUserId(boardId).orElse(null);

    if (managerUserId == null) {
      log.warn("Manager not found for board: {}, skipping automation alert.", boardId);
      return;
    }

    publisher.publishEvent(
        AutomationAlertRequestedEvent.forUser(tenantId, boardId, taskId, managerUserId, message));

    log.debug(
        "Automation alert to Manager (resolved user: {}) | tenant: {}, board: {}, task: {}, msg: {}",
        managerUserId,
        tenantId,
        boardId,
        taskId,
        message);
  }

  @Override
  public void notifyUser(UUID tenantId, UUID boardId, UUID userId, String message, UUID taskId) {
    publisher.publishEvent(
        AutomationAlertRequestedEvent.forUser(tenantId, boardId, taskId, userId, message));

    log.debug(
        "Automation alert to User {} | tenant: {}, board: {}, task: {}, msg: {}",
        userId,
        tenantId,
        boardId,
        taskId,
        message);
  }
}
