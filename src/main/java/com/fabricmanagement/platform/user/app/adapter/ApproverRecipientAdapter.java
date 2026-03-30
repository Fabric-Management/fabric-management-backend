package com.fabricmanagement.platform.user.app.adapter;

import com.fabricmanagement.approval.domain.port.ApproverRecipientPort;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApproverRecipientAdapter implements ApproverRecipientPort {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findUserIdsByRoleCodes(UUID tenantId, List<String> roleCodes) {
    return userRepository.findByTenantIdAndRole_RoleCodeIn(tenantId, roleCodes).stream()
        .map(User::getId)
        .distinct()
        .toList();
  }
}
