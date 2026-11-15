package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository.UserLoginEmail;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The e-mail each user signs in with, for screens outside the auth boundary that must show it. Only
 * the e-mail leaves this service; the login identity itself stays inside auth.
 */
@Service
@RequiredArgsConstructor
public class LoginEmailQueryService {

  private final MembershipRepository membershipRepository;

  /** Login e-mail by user id; a user without a login identity is absent from the map. */
  @Transactional(readOnly = true)
  public Map<UUID, String> findByUserIds(Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    return membershipRepository.findLoginEmailsByUserIds(userIds).stream()
        .collect(
            Collectors.toMap(
                UserLoginEmail::getUserId, UserLoginEmail::getEmail, (first, second) -> first));
  }
}
