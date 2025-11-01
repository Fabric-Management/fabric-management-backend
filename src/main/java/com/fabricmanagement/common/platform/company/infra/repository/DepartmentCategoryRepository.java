package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DepartmentCategory entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.</p>
 */
@Repository
public interface DepartmentCategoryRepository extends JpaRepository<DepartmentCategory, UUID> {

    Optional<DepartmentCategory> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DepartmentCategory> findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID tenantId);

    Optional<DepartmentCategory> findByTenantIdAndCategoryName(UUID tenantId, String categoryName);

    boolean existsByTenantIdAndCategoryName(UUID tenantId, String categoryName);
}

