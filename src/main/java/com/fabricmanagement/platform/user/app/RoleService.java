package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionCacheInvalidation;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.RoleScope;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.platform.user.security.MemberAdministrationAccess;
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
  private final PermissionCacheInvalidation permissionCache;
  private final MemberAdministrationAccess access;
  private final UserRepository users;

  /**
   * Returns all active roles for the current tenant that a tenant may see and assign. The platform
   * staff role is never among them (PLATFORM-BOUNDARY-1).
   */
  @Transactional(readOnly = true)
  public List<Role> findAll() {
    UUID tenantId = TenantContext.requireTenantId();
    return roleRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .filter(role -> !role.isPlatformScope())
        .toList();
  }

  /** Returns active roles for the current tenant filtered by scope. */
  @Transactional(readOnly = true)
  public List<Role> findByScope(RoleScope scope) {
    UUID tenantId = TenantContext.requireTenantId();
    return roleRepository.findByTenantIdAndRoleScopeAndIsActiveTrue(tenantId, scope).stream()
        .filter(role -> !role.isPlatformScope())
        .toList();
  }

  /** A role a tenant may see and assign: the platform staff role is reported as absent. */
  @Transactional(readOnly = true)
  public Optional<Role> findById(UUID roleId) {
    UUID tenantId = TenantContext.requireTenantId();
    return roleRepository
        .findByTenantIdAndId(tenantId, roleId)
        .filter(role -> !role.isPlatformScope());
  }

  @Transactional(readOnly = true)
  public Optional<Role> findByCode(String roleCode) {
    UUID tenantId = TenantContext.requireTenantId();
    return roleRepository.findByTenantIdAndRoleCode(tenantId, roleCode);
  }

  /**
   * Resolve tenant admin role. Each tenant has its own ADMIN role (cloned from template).
   *
   * @param tenantId the tenant to look up the ADMIN role for
   * @throws PlatformDomainException if no ADMIN role exists for the tenant
   */
  @Transactional(readOnly = true)
  public Role findTenantAdminRoleOrThrow(UUID tenantId) {
    Optional<Role> admin = roleRepository.findByTenantIdAndRoleCode(tenantId, "ADMIN");
    if (admin.isPresent()) {
      return admin.get();
    }
    List<Role> internalRoles =
        roleRepository.findByTenantIdAndRoleScopeAndIsActiveTrue(tenantId, RoleScope.INTERNAL);
    if (!internalRoles.isEmpty()) {
      log.warn(
          "ADMIN role not found, using first internal role for tenant admin: tenantId={}",
          tenantId);
      return internalRoles.getFirst();
    }
    throw new PlatformDomainException(
        "Platform roles not initialized for tenant. Ensure role cloning completed during"
            + " onboarding.",
        "PLATFORM_ROLES_NOT_INITIALIZED",
        500);
  }

  @Transactional
  public Role create(String roleName, String roleCode, String description) {
    UUID tenantId = TenantContext.requireTenantId();
    var actor = access.requireManage();
    access.requireRoleCreation(actor, roleCode);
    // The platform staff code is reserved: it already exists, as Longward's role.
    if (Role.isPlatformRoleCode(roleCode)
        || roleRepository.existsByTenantIdAndRoleCode(tenantId, roleCode)) {
      throw new PlatformDomainException(
          "Role with code already exists",
          "USER_ROLE_ALREADY_EXISTS",
          409,
          new Object[] {roleCode});
    }
    Role role = Role.create(roleName, roleCode, description);
    Role saved = roleRepository.save(role);
    permissionCache.evictRolesAfterCommit(tenantId);
    return saved;
  }

  @Transactional
  public Role update(UUID roleId, String roleName, String description) {
    UUID tenantId = TenantContext.requireTenantId();
    Role role =
        roleRepository
            .findByTenantIdAndId(tenantId, roleId)
            .filter(found -> !found.isPlatformScope())
            .orElseThrow(
                () -> new PlatformDomainException("Role not found", "USER_ROLE_NOT_FOUND", 404));
    access.requireManage();
    role.setRoleName(roleName);
    role.setDescription(description);
    Role saved = roleRepository.save(role);
    permissionCache.evictRolesAfterCommit(tenantId);
    return saved;
  }

  @Transactional
  public void deactivate(UUID roleId) {
    UUID tenantId = TenantContext.requireTenantId();
    Role role =
        roleRepository
            .findByTenantIdAndId(tenantId, roleId)
            .filter(found -> !found.isPlatformScope())
            .orElseThrow(
                () -> new PlatformDomainException("Role not found", "USER_ROLE_NOT_FOUND", 404));
    access.requireManage();
    if ("ADMIN".equalsIgnoreCase(role.getRoleCode())) {
      throw new PlatformDomainException(
          "The administrator role cannot be deactivated", "ROLE_ADMIN_PROTECTED", 409);
    }
    role.delete();
    roleRepository.save(role);
    for (UUID userId : users.findIdsByTenantIdAndRoleId(tenantId, roleId)) {
      permissionCache.evictMemberAfterCommit(tenantId, userId);
    }
    permissionCache.evictRolesAfterCommit(tenantId);
  }
}
