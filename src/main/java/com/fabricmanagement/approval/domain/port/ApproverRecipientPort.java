package com.fabricmanagement.approval.domain.port;

import java.util.List;
import java.util.UUID;

public interface ApproverRecipientPort {
  /**
   * Verilen role kodlarına sahip aktif kullanıcıların ID'lerini döner. Hiç kullanıcı bulunamazsa
   * ADMIN rolündeki kullanıcılara fallback yapılır.
   */
  List<UUID> findUserIdsByRoleCodes(UUID tenantId, List<String> roleCodes);
}
