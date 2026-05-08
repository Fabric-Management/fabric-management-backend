package com.fabricmanagement.platform.organization.infra.repository;

import com.fabricmanagement.platform.organization.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Department entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

  Optional<Department> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Department> findByTenantIdAndOrganizationId(UUID tenantId, UUID organizationId);

  List<Department> findByTenantIdAndOrganizationIdAndIsActiveTrue(
      UUID tenantId, UUID organizationId);

  Optional<Department> findByTenantIdAndOrganizationIdAndDepartmentName(
      UUID tenantId, UUID organizationId, String departmentName);

  Optional<Department> findByTenantIdAndOrganizationIdAndDepartmentCode(
      UUID tenantId, UUID organizationId, String departmentCode);

  boolean existsByTenantIdAndOrganizationIdAndDepartmentName(
      UUID tenantId, UUID organizationId, String departmentName);

  @Query(
      value =
          """
      WITH RECURSIVE dept_tree AS (
          SELECT id, department_code, parent_department_id
          FROM common_company.common_department WHERE department_code = :deptCode AND tenant_id = :tenantId
          UNION ALL
          SELECT d.id, d.department_code, d.parent_department_id
          FROM common_company.common_department d
          INNER JOIN dept_tree dt ON d.id = dt.parent_department_id
          WHERE d.tenant_id = :tenantId
      )
      SELECT department_code FROM dept_tree
      """,
      nativeQuery = true)
  List<String> findAncestorCodes(
      @Param("tenantId") UUID tenantId, @Param("deptCode") String deptCode);

  @Query(
      value =
          """
      WITH RECURSIVE dept_tree AS (
          SELECT id FROM common_company.common_department
          WHERE department_code = :deptCode AND tenant_id = :tenantId
          UNION ALL
          SELECT d.id FROM common_company.common_department d
          INNER JOIN dept_tree dt ON d.parent_department_id = dt.id
          WHERE d.tenant_id = :tenantId
      )
      SELECT id FROM dept_tree
      """,
      nativeQuery = true)
  List<UUID> findDescendantIds(
      @Param("tenantId") UUID tenantId, @Param("deptCode") String deptCode);

  /** Find all active departments by tenant ID. Used for user creation form dropdown options. */
  @Query(
      "SELECT d FROM Department d WHERE d.tenantId = :tenantId AND d.isActive = true ORDER BY d.departmentName")
  List<Department> findByTenantIdAndIsActiveTrue(@Param("tenantId") UUID tenantId);
}
