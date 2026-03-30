package com.fabricmanagement.common.infrastructure.approval;

import java.util.UUID;

public interface ApprovalPort {
  boolean requiresApproval(
      UUID tenantId, UUID userId, String entityType, UUID entityId, int expiryHours);
}
