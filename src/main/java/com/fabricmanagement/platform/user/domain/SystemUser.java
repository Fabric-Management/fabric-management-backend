package com.fabricmanagement.platform.user.domain;

import java.util.UUID;

/**
 * Sistem tarafından otomatik gerçekleştirilen işlemler (ör. otomasyon, zamanlayıcı) için rezerve
 * edilmiş hayali kullanıcı.
 *
 * <p>FlowBoard otomasyon kuralları (AutomationEngine) ve eskalasyon görevleri, "kapsanan" Task
 * işlemlerini yaparken requestingUserId olarak bu ID'yi kullanır.
 *
 * <p>Veritabanında fiziksel olarak bulunması gerekmez, id null geçilemeyen foreign key'lerde veya
 * audit loglarında kullanılır.
 */
public final class SystemUser {

  public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  private SystemUser() {
    // Utility class
  }
}
