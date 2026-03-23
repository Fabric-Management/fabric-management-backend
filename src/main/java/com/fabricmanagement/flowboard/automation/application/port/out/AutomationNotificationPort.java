package com.fabricmanagement.flowboard.automation.application.port.out;

import java.util.UUID;

/** AutomationEngine'in notification modülüne soyutlanmış erişim portu (AUT2). */
public interface AutomationNotificationPort {

  /** Yöneticiye bildirim gönderir. */
  void notifyManager(UUID tenantId, UUID boardId, String message, UUID taskId);

  /**
   * Spesifik bir kullanıcıya bildirim gönderir.
   *
   * <p>Default implementasyon backward compatibility için manager notification'a düşer. Concrete
   * implementasyon userId-based routing eklediğinde override edilmelidir.
   */
  default void notifyUser(UUID tenantId, UUID boardId, UUID userId, String message, UUID taskId) {
    notifyManager(tenantId, boardId, message, taskId);
  }
}
