package com.fabricmanagement.approval.app;

import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onay politikasındaki {@link ApproverRole} için tenant içinde bildirim alıcılarını (aktif
 * kullanıcı id'leri) çözer.
 */
@Service
@RequiredArgsConstructor
public class ApproverRecipientResolver {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<UUID> resolveUsersForApproverRole(UUID tenantId, ApproverRole role) {
    List<String> codes = roleCodesFor(role);
    List<UUID> ids =
        userRepository.findByTenantIdAndRole_RoleCodeIn(tenantId, codes).stream()
            .map(User::getId)
            .distinct()
            .toList();
    if (!ids.isEmpty()) {
      return ids;
    }
    return userRepository.findByTenantIdAndRole_RoleCodeIn(tenantId, List.of("ADMIN")).stream()
        .map(User::getId)
        .distinct()
        .toList();
  }

  private static List<String> roleCodesFor(ApproverRole role) {
    return switch (role) {
      case TENANT_ADMIN -> List.of("ADMIN");
      case DEPARTMENT_MANAGER -> List.of("MANAGER", "SUPERVISOR", "ADMIN");
      case MANAGER -> List.of("MANAGER", "ADMIN");
      case HR -> List.of("MANAGER", "ADMIN");
      case CEO -> List.of("ADMIN");
      case QUALITY_MANAGER -> List.of("MANAGER", "ADMIN");
    };
  }
}
