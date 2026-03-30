package com.fabricmanagement.platform.user.app.adapter;

import com.fabricmanagement.approval.domain.UserTrustLevel;
import com.fabricmanagement.approval.domain.port.UserTrustMutationPort;
import com.fabricmanagement.platform.user.app.UserService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserTrustMutationAdapter implements UserTrustMutationPort {
  private final UserRepository userRepository;
  private final UserService userService;

  @Override
  public Optional<UserTrustLevel> findTrustLevel(UUID tenantId, UUID userId) {
    return userRepository.findByTenantIdAndId(tenantId, userId).map(User::getTrustLevel);
  }

  @Override
  public void upgradeTrustLevel(UUID tenantId, UUID userId, UserTrustLevel newLevel) {
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    user.setTrustLevel(newLevel);
    userRepository.save(user);
  }

  @Override
  public void deactivateUser(UUID tenantId, UUID userId, String reason) {
    userService.deactivateUser(tenantId, userId, reason);
  }
}
