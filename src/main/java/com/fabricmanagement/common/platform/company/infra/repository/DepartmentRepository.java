package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

