package com.fabricmanagement.approval.domain.port;

import com.fabricmanagement.approval.domain.UserTrustLevel;
import java.util.Optional;
import java.util.UUID;

public interface UserTrustMutationPort {
  Optional<UserTrustLevel> findTrustLevel(UUID tenantId, UUID userId);

  void upgradeTrustLevel(UUID tenantId, UUID userId, UserTrustLevel newLevel);

  void deactivateUser(UUID tenantId, UUID userId, String reason);
}
