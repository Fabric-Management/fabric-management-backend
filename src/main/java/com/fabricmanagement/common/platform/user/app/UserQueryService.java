package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.Employee;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User query service — read-only operations with optional caching.
 *
 * <p>Used by UserFacade and controllers for listing and lookup. Cache keys are tenant/organization
 * scoped; invalidation is handled by {@link UserCacheInvalidationService}.
 *
 * <p>All user queries are enriched with Employee (HR) data when available, so UserDto always
 * carries the full picture for internal staff.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueryService {

  private final UserRepository userRepository;
  private final EmployeeService employeeService;
  private final UserWorkLocationService userWorkLocationService;

  @Transactional(readOnly = true)
  public Optional<UserDto> findById(UUID tenantId, UUID userId) {
    log.debug("Finding user: tenantId={}, userId={}", tenantId, userId);
    return userRepository
        .findByTenantIdAndId(tenantId, userId)
        .map(
            user -> {
              UserDto dto =
                  UserDto.from(user, employeeService.getEmployeeByUserId(userId).orElse(null));
              Map<UUID, String> labelMap =
                  userWorkLocationService.getPrimaryLocationLabels(tenantId, List.of(userId));
              dto.setWorkLocationLabel(labelMap.get(userId));
              return dto;
            });
  }

  @Transactional(readOnly = true)
  public Optional<UserDto> findByContactValue(String contactValue) {
    log.debug("Finding user by contact: contactValue=(masked)");
    return userRepository
        .findByContactValue(contactValue)
        .map(
            user ->
                UserDto.from(user, employeeService.getEmployeeByUserId(user.getId()).orElse(null)));
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "users-by-tenant", key = "#tenantId.toString()")
  public List<UserDto> findByTenant(UUID tenantId) {
    log.debug("Finding users by tenant: tenantId={}", tenantId);
    List<User> users = userRepository.findByTenantIdAndIsActiveTrue(tenantId);
    return enrichWithEmployeeData(tenantId, users);
  }

  @Transactional(readOnly = true)
  @Cacheable(
      value = "users-by-organization",
      key = "#tenantId.toString() + '-' + #organizationId.toString()")
  public List<UserDto> findByOrganization(UUID tenantId, UUID organizationId) {
    log.debug(
        "Finding users by organization: tenantId={}, organizationId={}", tenantId, organizationId);
    List<User> users =
        userRepository.findByTenantIdAndOrganizationIdAndIsActiveTrue(tenantId, organizationId);
    return enrichWithEmployeeData(tenantId, users);
  }

  @Transactional(readOnly = true)
  public List<UserDto> findByDepartments(UUID tenantId, Set<UUID> departmentIds) {
    log.debug(
        "Finding users by departments: tenantId={}, departmentCount={}",
        tenantId,
        departmentIds.size());
    if (departmentIds.isEmpty()) {
      return List.of();
    }
    List<User> users = userRepository.findByTenantIdAndDepartmentIds(tenantId, departmentIds);
    return enrichWithEmployeeData(tenantId, users);
  }

  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID userId) {
    return userRepository.existsByTenantIdAndId(tenantId, userId);
  }

  /**
   * Check if contact value exists in tenant (tenant-scoped — for enumeration protection).
   *
   * @param tenantId Tenant ID
   * @param contactValue Contact value (email or phone)
   * @return true if any user in tenant has this contact value
   */
  @Transactional(readOnly = true)
  public boolean contactExistsInTenant(UUID tenantId, String contactValue) {
    log.trace("Checking contact exists in tenant: tenantId={}", tenantId);
    return userRepository.existsByTenantIdAndContactValue(tenantId, contactValue);
  }

  /** Global contact exists (any tenant) — use only where cross-tenant check is required. */
  @Transactional(readOnly = true)
  public boolean contactExists(String contactValue) {
    return userRepository.existsByContactValue(contactValue);
  }

  /**
   * Batch-enrich a list of users with Employee data and primary work-location labels. Two batch
   * queries avoid N+1.
   */
  private List<UserDto> enrichWithEmployeeData(UUID tenantId, List<User> users) {
    if (users.isEmpty()) {
      return List.of();
    }
    List<UUID> userIds = users.stream().map(User::getId).toList();
    Map<UUID, Employee> employeeMap = employeeService.getEmployeesByUserIds(tenantId, userIds);
    Map<UUID, String> labelMap =
        userWorkLocationService.getPrimaryLocationLabels(tenantId, userIds);

    return users.stream()
        .map(
            user -> {
              UserDto dto = UserDto.from(user, employeeMap.get(user.getId()));
              dto.setWorkLocationLabel(labelMap.get(user.getId()));
              return dto;
            })
        .toList();
  }
}
