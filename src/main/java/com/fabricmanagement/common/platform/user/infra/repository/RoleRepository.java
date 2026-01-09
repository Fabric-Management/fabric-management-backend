package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Role entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

  Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Role> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<Role> findByTenantIdAndRoleCode(UUID tenantId, String roleCode);

  boolean existsByTenantIdAndRoleCode(UUID tenantId, String roleCode);
}
