package com.fabricmanagement.notification.hub.app;

import java.util.Optional;
import java.util.UUID;

/**
 * Kullanıcı bilgisi için modül sınırını aşmadan sorgu arayüzü.
 *
 * <p>Notification modülü, kullanıcı modülüne doğrudan bağımlı olmamalı. Bu interface, notification
 * tarafında tanımlanır; implementasyon common.platform tarafında sağlanır.
 */
public interface NotificationUserQueryService {

  /**
   * Kullanıcının email adresini döndürür (EMAIL kanalı için). Kullanıcı bulunamazsa veya email
   * yoksa Optional.empty() döner.
   */
  Optional<String> findEmailByUserId(UUID userId);

  /**
   * Kullanıcının push token'ını döndürür (PUSH kanalı için). Push token yoksa Optional.empty()
   * döner.
   */
  Optional<String> findPushTokenByUserId(UUID userId);
}
