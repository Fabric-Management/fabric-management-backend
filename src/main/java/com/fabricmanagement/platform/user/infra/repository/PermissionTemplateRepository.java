package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for PermissionTemplate entity.
 *
 * <p>Each tenant has its own permission templates (cloned from the TEMPLATE tenant during
 * onboarding). No cross-tenant OR SYSTEM fallback queries needed.
 */
@Repository
public interface PermissionTemplateRepository extends JpaRepository<PermissionTemplate, UUID> {

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE pt.roleCode = :roleCode "
          + "AND ((:deptCode IS NULL AND pt.departmentCode IS NULL) "
          + "     OR pt.departmentCode = :deptCode "
          + "     OR pt.departmentCode IS NULL) "
          + "AND pt.tenantId = :tenantId "
          + "AND pt.isActive = true "
          + "ORDER BY pt.departmentCode NULLS LAST")
  List<PermissionTemplate> findEffectiveTemplates(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCode") String departmentCode);

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE pt.roleCode = :roleCode "
          + "AND (pt.departmentCode IN :deptCodes OR pt.departmentCode IS NULL) "
          + "AND pt.tenantId = :tenantId "
          + "AND pt.isActive = true "
          + "ORDER BY pt.departmentCode NULLS LAST")
  List<PermissionTemplate> findEffectiveTemplatesForDepartments(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCodes") List<String> deptCodes);

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE "
          + "pt.tenantId = :tenantId "
          + "AND (:roleCode IS NULL OR pt.roleCode = :roleCode) "
          + "AND (:deptCode IS NULL OR pt.departmentCode = :deptCode) "
          + "AND pt.isActive = true "
          + "ORDER BY pt.roleCode ASC, pt.departmentCode ASC")
  List<PermissionTemplate> findTemplatesList(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCode") String departmentCode);

  boolean existsByTenantId(UUID tenantId);

  /**
   * All templates owned by a tenant, active or not.
   *
   * <p>Used by the seeder to diff its desired set against what is already stored. It must not
   * filter on {@code isActive}: a deactivated row still occupies the {@code
   * uq_permission_template_effective} key and re-inserting it would violate the constraint.
   */
  List<PermissionTemplate> findByTenantId(UUID tenantId);
}
