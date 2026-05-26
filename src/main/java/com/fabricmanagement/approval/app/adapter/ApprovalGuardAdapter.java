package com.fabricmanagement.approval.app.adapter;

import com.fabricmanagement.approval.app.ApprovalGuardService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApprovalGuardAdapter implements ApprovalPort {
  private final ApprovalGuardService approvalGuardService;

  @Override
  public boolean requiresApproval(
      UUID tenantId, UUID userId, String entityType, UUID entityId, int expiryHours) {
    return requiresApproval(tenantId, userId, entityType, entityId, expiryHours, null, null);
  }

  @Override
  public boolean requiresApproval(
      UUID tenantId,
      UUID userId,
      String entityType,
      UUID entityId,
      int expiryHours,
      java.math.BigDecimal amount,
      String currency) {
    ApprovalEntityType type = ApprovalEntityType.valueOf(entityType);
    return approvalGuardService.checkAndEnforceApproval(
        tenantId, userId, type, entityId, expiryHours, amount, currency);
  }
}
