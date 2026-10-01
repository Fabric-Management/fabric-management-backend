package com.fabricmanagement.common.infrastructure.approval;

import java.util.UUID;

public interface ApprovalPort {
  boolean requiresApproval(UUID tenantId, UUID userId, String entityType, UUID entityId);

  boolean requiresApproval(
      UUID tenantId,
      UUID userId,
      String entityType,
      UUID entityId,
      java.math.BigDecimal amount,
      String currency);

  /**
   * For an entity that amounts to several currencies at once (a multi-currency sales order). Each
   * amount threshold is evaluated in its policy's currency: every amount is converted to it and the
   * converted amounts are summed. The amounts themselves are never changed.
   */
  boolean requiresApproval(
      UUID tenantId,
      UUID userId,
      String entityType,
      UUID entityId,
      java.util.List<com.fabricmanagement.common.util.Money> amounts);
}
