package com.fabricmanagement.platform.user.app.adapter;

import com.fabricmanagement.approval.domain.port.UserTrustLevelPort;
import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTrustLevelAdapter implements UserTrustLevelPort {

  private final UserRepository userRepository;

  @Override
  public UserTrustLevel resolveTrustLevel(UUID tenantId, UUID userId) {
    if (SystemUser.ID.equals(userId)) {
      return UserTrustLevel.PROBATION;
    }
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    return user.getTrustLevel();
  }
}
