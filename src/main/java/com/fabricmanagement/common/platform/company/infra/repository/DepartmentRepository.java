package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Department entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.</p>
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Department> findByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    List<Department> findByTenantIdAndCompanyIdAndIsActiveTrue(UUID tenantId, UUID companyId);

    Optional<Department> findByTenantIdAndCompanyIdAndDepartmentName(
        UUID tenantId, UUID companyId, String departmentName);

    boolean existsByTenantIdAndCompanyIdAndDepartmentName(
        UUID tenantId, UUID companyId, String departmentName);

    /**
     * Find all active departments by tenant ID.
     * Used for user creation form dropdown options.
     */
    @Query("SELECT d FROM Department d WHERE d.tenantId = :tenantId AND d.isActive = true ORDER BY d.departmentName")
    List<Department> findByTenantIdAndIsActiveTrue(@Param("tenantId") UUID tenantId);
}

