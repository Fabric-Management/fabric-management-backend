package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.domain.RoleScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Role> findByTenantIdAndIsActiveTrue(UUID tenantId);

  List<Role> findByTenantIdAndRoleScopeAndIsActiveTrue(UUID tenantId, RoleScope roleScope);

  Optional<Role> findByTenantIdAndRoleCode(UUID tenantId, String roleCode);

  boolean existsByTenantIdAndRoleCode(UUID tenantId, String roleCode);
}
