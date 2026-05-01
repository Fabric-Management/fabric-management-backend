package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field-level access control for user profile updates.
 *
 * <p>Delegates to {@link PermissionEvaluator} for department-based permission model:
 *
 * <ul>
 *   <li>FULL_ACCESS (Admin role / Admin Office / HR dept): Full access to all profiles
 *   <li>Self-update: Always denied (must go through update request flow)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfilePermissionService {

  private final PermissionEvaluator permissionEvaluator;
  private final UserRepository userRepository;

  private PermissionResult getPermissions(UUID requesterId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    User user =
        userRepository
            .findByTenantIdAndId(tenantId, requesterId)
            .orElseThrow(() -> new NotFoundException("User not found: " + requesterId));

    String roleCode = user.getRole() != null ? user.getRole().getRoleCode() : null;
    List<String> departmentCodes =
        user.getUserDepartments().stream()
            .filter(ud -> Boolean.TRUE.equals(ud.getIsActive()))
            .map(ud -> ud.getDepartment() != null ? ud.getDepartment().getDepartmentCode() : null)
            .filter(c -> c != null)
            .collect(Collectors.toList());

    return permissionEvaluator.evaluate(tenantId, roleCode, departmentCodes, requesterId);
  }

  @Transactional(readOnly = true)
  public boolean canUpdateWorkProfile(UUID requesterId, UUID targetUserId) {
    if (requesterId.equals(targetUserId)) {
      return false;
    }
    return getPermissions(requesterId).can("members", "write");
  }

  @Transactional(readOnly = true)
  public boolean canUpdatePersonalProfile(UUID requesterId, UUID targetUserId) {
    if (requesterId.equals(targetUserId)) {
      return false;
    }
    return getPermissions(requesterId).can("members", "write");
  }

  @Transactional(readOnly = true)
  public boolean canViewProfile(UUID requesterId, UUID targetUserId, ProfileCategory category) {
    if (requesterId.equals(targetUserId)) {
      return true;
    }
    PermissionResult perms = getPermissions(requesterId);
    if (!perms.can("members", "read")) return false;
    DataScope scope = perms.scopeOf("members", "read");
    return scope != null && scope.ordinal() >= DataScope.ORGANIZATION.ordinal();
  }
}
