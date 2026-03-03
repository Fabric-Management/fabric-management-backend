package com.fabricmanagement.common.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.util.DuplicateValidator;
import com.fabricmanagement.common.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.common.platform.organization.domain.Department;
import com.fabricmanagement.common.platform.organization.dto.DepartmentDto;
import com.fabricmanagement.common.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.subscription.dto.UserCreationOptionsDto;
import com.fabricmanagement.common.platform.user.app.RoleService;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.RoleScope;
import com.fabricmanagement.common.platform.user.dto.RoleDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for providing user creation form options in a single response.
 *
 * <p>Returns roles (filtered by scope) and departments (hierarchical via parentDepartment).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCreationOptionsService {

  private final RoleService roleService;
  private final DepartmentRepository departmentRepository;
  private final TenantSeedService tenantSeedService;
  private final OrganizationFacade organizationFacade;

  /**
   * Get options for creating an internal employee.
   *
   * <p>Returns only INTERNAL-scoped roles and tenant departments.
   */
  @Transactional(readOnly = true)
  public UserCreationOptionsDto getUserCreationOptions() {
    return getOptions(RoleScope.INTERNAL);
  }

  /**
   * Get options for inviting a partner user.
   *
   * <p>Returns only PARTNER-scoped roles and tenant departments.
   */
  @Transactional(readOnly = true)
  public UserCreationOptionsDto getPartnerCreationOptions() {
    return getOptions(RoleScope.PARTNER);
  }

  private UserCreationOptionsDto getOptions(RoleScope scope) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting user creation options: tenantId={}, scope={}", tenantId, scope);

    UUID organizationId =
        organizationFacade.getRootOrganization().map(o -> o.getId()).orElse(tenantId);

    if (!tenantSeedService.isTenantSeeded(tenantId, organizationId)) {
      log.info("Tenant not seeded, auto-seeding departments: tenantId={}", tenantId);
      tenantSeedService.seedDepartments(tenantId, organizationId);
    }

    List<Role> roles = roleService.findByScope(scope);
    List<Department> departments = departmentRepository.findByTenantIdAndIsActiveTrue(tenantId);

    List<RoleDto> roleDtos = roles.stream().map(RoleDto::from).collect(Collectors.toList());
    List<DepartmentDto> departmentDtos =
        departments.stream().map(DepartmentDto::from).collect(Collectors.toList());

    log.debug(
        "User creation options: scope={}, roles={}, departments={}",
        scope,
        roleDtos.size(),
        departmentDtos.size());

    Map<String, Boolean> validations = new HashMap<>();
    validations.put(
        "roles",
        DuplicateValidator.validateAndLog(
            roleDtos, role -> role.getRoleName() != null ? role.getRoleName() : "", "roles"));
    validations.put(
        "departments",
        DuplicateValidator.validateAndLog(
            departmentDtos,
            dept -> dept.getDepartmentName() != null ? dept.getDepartmentName() : "",
            "departments"));

    DuplicateValidator.validateAll(validations);

    return UserCreationOptionsDto.builder().roles(roleDtos).departments(departmentDtos).build();
  }
}
