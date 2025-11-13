package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.infra.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Role Service - Business logic for role management.
 *
 * <p>Handles dynamic role management (database-driven, not enums).</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Role CRUD with tenant isolation</li>
 *   <li>Role code uniqueness validation</li>
 *   <li>Role assignment to users</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding all roles: tenantId={}", tenantId);

        // ✅ Platform-level roles only: All tenants use the same platform roles
        // Tenant-specific roles are not returned (they can be created but are not shown in standard lists)
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        List<Role> systemRoles = roleRepository.findByTenantIdAndIsActiveTrue(systemTenantId);
        
        // ✅ Log clarification: Platform roles come from SYSTEM_TENANT_ID, not from current tenant
        log.debug("Found {} platform roles (from SYSTEM_TENANT_ID) for current tenant context: tenantId={}", 
            systemRoles.size(), tenantId);
        return systemRoles;
    }

    @Transactional(readOnly = true)
    public Optional<Role> findById(UUID roleId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding role: tenantId={}, roleId={}", tenantId, roleId);

        // ✅ Platform-level roles only: Check system tenant first
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        Optional<Role> systemRole = roleRepository.findByTenantIdAndId(systemTenantId, roleId);
        if (systemRole.isPresent()) {
            return systemRole;
        }
        
        // Fallback: Check tenant-specific roles (for backward compatibility)
        // Note: Tenant-specific roles are not shown in findAll() but can be queried by ID
        return roleRepository.findByTenantIdAndId(tenantId, roleId);
    }

    @Transactional(readOnly = true)
    public Optional<Role> findByCode(String roleCode) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding role by code: tenantId={}, roleCode={}", tenantId, roleCode);

        // ✅ Platform-level roles only: Check system tenant first
        UUID systemTenantId = TenantContext.SYSTEM_TENANT_ID;
        Optional<Role> systemRole = roleRepository.findByTenantIdAndRoleCode(systemTenantId, roleCode);
        if (systemRole.isPresent()) {
            return systemRole;
        }
        
        // Fallback: Check tenant-specific roles (for backward compatibility)
        // Note: Tenant-specific roles are not shown in findAll() but can be queried by code
        return roleRepository.findByTenantIdAndRoleCode(tenantId, roleCode);
    }

    @Transactional
    public Role create(String roleName, String roleCode, String description) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Creating role: tenantId={}, roleName={}, roleCode={}", tenantId, roleName, roleCode);

        if (roleRepository.existsByTenantIdAndRoleCode(tenantId, roleCode)) {
            throw new IllegalArgumentException("Role with code '" + roleCode + "' already exists");
        }

        Role role = Role.create(roleName, roleCode, description);
        Role saved = roleRepository.save(role);

        log.info("Role created: id={}, uid={}, code={}", saved.getId(), saved.getUid(), saved.getRoleCode());

        return saved;
    }

    @Transactional
    public Role update(UUID roleId, String roleName, String description) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating role: tenantId={}, roleId={}", tenantId, roleId);

        Role role = roleRepository.findByTenantIdAndId(tenantId, roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.setRoleName(roleName);
        role.setDescription(description);

        Role saved = roleRepository.save(role);

        log.info("Role updated: id={}, code={}", saved.getId(), saved.getRoleCode());

        return saved;
    }

    @Transactional
    public void deactivate(UUID roleId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Deactivating role: tenantId={}, roleId={}", tenantId, roleId);

        Role role = roleRepository.findByTenantIdAndId(tenantId, roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.delete();
        roleRepository.save(role);

        log.warn("Role deactivated: id={}, code={}", role.getId(), role.getRoleCode());
    }
}

