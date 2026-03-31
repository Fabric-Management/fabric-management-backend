package com.fabricmanagement.notification.hub.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Kullanıcıları ve yöneticileri departman anahtar kelimesine göre çözen port arayüzü. Notification
 * modülünün kendi dilinde "departman" kavramı.
 */
public interface DepartmentRecipientPort {

  /**
   * Verilen anahtar kelimelere (PROCUREMENT, QUALITY vb.) uyan departmanlardaki kullanıcıları
   * döner.
   */
  List<UUID> findUsersByDepartmentKeyword(UUID tenantId, String... keywords);

  /**
   * Belirtilen departmanların yöneticilerini döner. Eğer yönetici atanmamışsa kullanıcılara
   * fallback yapar.
   */
  List<UUID> findManagersByDepartmentKeyword(UUID tenantId, String... keywords);
}
