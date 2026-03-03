package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.domain.Department;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.common.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Department-based access control for team member management.
 *
 * <p>4-tier permission model:
 *
 * <ul>
 *   <li><b>FULL_ACCESS:</b> ADMIN role (any dept) + Administration Office + Human Resources →
 *       create, edit, delete, read all members
 *   <li><b>READ_ALL:</b> Finance &amp; Accounting → read all members, no write
 *   <li><b>DEPARTMENT_ONLY:</b> MANAGER/SUPERVISOR in other depts → read own department members
 *   <li><b>NO_ACCESS:</b> Everyone else → no member visibility
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamAccessService {

  private static final Set<String> FULL_ACCESS_DEPARTMENTS =
      Set.of("ADMINISTRATIONOFFICE", "HUMANRESOURCES");

  private static final Set<String> READ_ALL_DEPARTMENTS = Set.of("FINANCEACCOUNTING");

  private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "PLATFORM_ADMIN");

  private static final Set<String> DEPARTMENT_READ_ROLES =
      Set.of("MANAGER", "SUPERVISOR");

  private final UserRepository userRepository;
  private final UserDepartmentRepository userDepartmentRepository;

  public enum AccessLevel {
    FULL_ACCESS,
    READ_ALL,
    DEPARTMENT_ONLY,
    NO_ACCESS
  }

  @Transactional(readOnly = true)
  public AccessLevel resolveAccessLevel(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    String roleCode = user.getRole() != null ? user.getRole().getRoleCode() : null;

    if (ADMIN_ROLES.contains(roleCode)) {
      return AccessLevel.FULL_ACCESS;
    }

    Set<String> departmentCodes = getUserDepartmentCodes(userId, tenantId);

    if (departmentCodes.stream().anyMatch(FULL_ACCESS_DEPARTMENTS::contains)) {
      return AccessLevel.FULL_ACCESS;
    }

    if (departmentCodes.stream().anyMatch(READ_ALL_DEPARTMENTS::contains)) {
      return AccessLevel.READ_ALL;
    }

    if (DEPARTMENT_READ_ROLES.contains(roleCode)) {
      return AccessLevel.DEPARTMENT_ONLY;
    }

    return AccessLevel.NO_ACCESS;
  }

  /** Check if user can manage (create/edit/delete) members. */
  @Transactional(readOnly = true)
  public boolean canManageMembers(UUID userId) {
    return resolveAccessLevel(userId) == AccessLevel.FULL_ACCESS;
  }

  /** Check if user can view all members (read-only or full access). */
  @Transactional(readOnly = true)
  public boolean canViewAllMembers(UUID userId) {
    AccessLevel level = resolveAccessLevel(userId);
    return level == AccessLevel.FULL_ACCESS || level == AccessLevel.READ_ALL;
  }

  /** Check if user can view at least their own department members. */
  @Transactional(readOnly = true)
  public boolean canViewDepartmentMembers(UUID userId) {
    AccessLevel level = resolveAccessLevel(userId);
    return level != AccessLevel.NO_ACCESS;
  }

  /** Get department IDs for a user (for DEPARTMENT_ONLY scoped queries). */
  @Transactional(readOnly = true)
  public Set<UUID> getUserDepartmentIds(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return userDepartmentRepository.findByTenantIdAndUserId(tenantId, userId).stream()
        .map(UserDepartment::getDepartmentId)
        .collect(Collectors.toSet());
  }

  private Set<String> getUserDepartmentCodes(UUID userId, UUID tenantId) {
    List<UserDepartment> userDepartments =
        userDepartmentRepository.findByTenantIdAndUserId(tenantId, userId);

    return userDepartments.stream()
        .map(UserDepartment::getDepartment)
        .filter(dept -> dept != null)
        .map(Department::getDepartmentCode)
        .collect(Collectors.toSet());
  }
}
