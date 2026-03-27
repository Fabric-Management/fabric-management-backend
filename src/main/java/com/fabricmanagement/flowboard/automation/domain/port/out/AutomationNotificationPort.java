package com.fabricmanagement.flowboard.automation.domain.port.out;

import java.util.UUID;

/** Abstracted access port to the notification module for the AutomationEngine (AUT2). */
public interface AutomationNotificationPort {

  /** Sends a notification to the manager. */
  void notifyManager(UUID tenantId, UUID boardId, String message, UUID taskId);

  /**
   * Sends a notification to a specific user.
   *
   * <p>Default implementation falls back to manager notification for backward compatibility.
   * Concrete implementation should override this when userId-based routing is added.
   */
  default void notifyUser(UUID tenantId, UUID boardId, UUID userId, String message, UUID taskId) {
    notifyManager(tenantId, boardId, message, taskId);
  }
}
