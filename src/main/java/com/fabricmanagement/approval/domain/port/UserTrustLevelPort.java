package com.fabricmanagement.approval.domain.port;

import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import java.util.UUID;

public interface UserTrustLevelPort {
  UserTrustLevel resolveTrustLevel(UUID tenantId, UUID userId);
}
