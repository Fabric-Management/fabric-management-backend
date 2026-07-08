package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDisplayNameResolver {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public Optional<String> resolveDisplayName(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) {
      return Optional.empty();
    }
    return userRepository.findByTenantIdAndId(tenantId, userId).map(User::getDisplayName);
  }
}
