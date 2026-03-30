package com.fabricmanagement.approval.domain.port;

import com.fabricmanagement.approval.domain.UserTrustLevel;
import java.util.UUID;

public interface UserTrustLevelPort {
  UserTrustLevel resolveTrustLevel(UUID tenantId, UUID userId);
}
