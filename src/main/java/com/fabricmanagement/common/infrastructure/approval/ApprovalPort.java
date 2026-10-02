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

  /** The pending approval request for the entity, if one waits for a decision. */
  java.util.Optional<UUID> pendingRequestId(UUID tenantId, String entityType, UUID entityId);

  /**
   * Cancels the entity's pending approval request, if any: what it asked about changed or was
   * withdrawn, so an approval of it must no longer count.
   */
  void cancelPending(UUID tenantId, String entityType, UUID entityId);
}
