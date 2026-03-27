package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.RoleScope;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

  private final RoleRepository roleRepository;

  /** Returns all active platform roles (all scopes). */
  @Transactional(readOnly = true)
  public List<Role> findAll() {
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    return roleRepository.findByTenantIdAndIsActiveTrue(systemTenantId);
  }

  /** Returns active platform roles filtered by scope. */
  @Transactional(readOnly = true)
  public List<Role> findByScope(RoleScope scope) {
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    return roleRepository.findByTenantIdAndRoleScopeAndIsActiveTrue(systemTenantId, scope);
  }

  @Transactional(readOnly = true)
  public Optional<Role> findById(UUID roleId) {
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    Optional<Role> systemRole = roleRepository.findByTenantIdAndId(systemTenantId, roleId);
    if (systemRole.isPresent()) {
      return systemRole;
    }
    UUID tenantId = TenantContext.getCurrentTenantId();
    return roleRepository.findByTenantIdAndId(tenantId, roleId);
  }

  @Transactional(readOnly = true)
  public Optional<Role> findByCode(String roleCode) {
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    Optional<Role> systemRole = roleRepository.findByTenantIdAndRoleCode(systemTenantId, roleCode);
    if (systemRole.isPresent()) {
      return systemRole;
    }
    UUID tenantId = TenantContext.getCurrentTenantId();
    return roleRepository.findByTenantIdAndRoleCode(tenantId, roleCode);
  }

  /**
   * Resolve tenant admin role from platform. Used only during tenant onboarding.
   *
   * @throws IllegalStateException if no ADMIN role exists
   */
  @Transactional(readOnly = true)
  public Role findTenantAdminRoleOrThrow(UUID forTenantIdLogging) {
    UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
    Optional<Role> admin = roleRepository.findByTenantIdAndRoleCode(systemTenantId, "ADMIN");
    if (admin.isPresent()) {
      return admin.get();
    }
    List<Role> internalRoles =
        roleRepository.findByTenantIdAndRoleScopeAndIsActiveTrue(
            systemTenantId, RoleScope.INTERNAL);
    if (!internalRoles.isEmpty()) {
      log.warn(
          "ADMIN role not found, using first internal role for tenant admin: tenantId={}",
          forTenantIdLogging);
      return internalRoles.get(0);
    }
    throw new IllegalStateException(
        "Platform roles not initialized. Ensure role seed migration has been executed.");
  }

  @Transactional
  public Role create(String roleName, String roleCode, String description) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (roleRepository.existsByTenantIdAndRoleCode(tenantId, roleCode)) {
      throw new IllegalArgumentException("Role with code '" + roleCode + "' already exists");
    }
    Role role = Role.create(roleName, roleCode, description);
    return roleRepository.save(role);
  }

  @Transactional
  public Role update(UUID roleId, String roleName, String description) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Role role =
        roleRepository
            .findByTenantIdAndId(tenantId, roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));
    role.setRoleName(roleName);
    role.setDescription(description);
    return roleRepository.save(role);
  }

  @Transactional
  public void deactivate(UUID roleId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Role role =
        roleRepository
            .findByTenantIdAndId(tenantId, roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));
    role.delete();
    roleRepository.save(role);
  }
}
