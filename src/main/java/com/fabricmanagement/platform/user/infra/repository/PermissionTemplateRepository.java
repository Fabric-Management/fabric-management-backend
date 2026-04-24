package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionTemplateRepository extends JpaRepository<PermissionTemplate, UUID> {

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE pt.roleCode = :roleCode "
          + "AND ((:deptCode IS NULL AND pt.departmentCode IS NULL) "
          + "     OR pt.departmentCode = :deptCode "
          + "     OR pt.departmentCode IS NULL) "
          + "AND (pt.tenantId = :tenantId OR pt.tenantId = :#{T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).SYSTEM_TENANT_ID}) "
          + "AND pt.isActive = true "
          + "ORDER BY pt.tenantId NULLS LAST")
  List<PermissionTemplate> findEffectiveTemplates(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCode") String departmentCode);

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE pt.roleCode = :roleCode "
          + "AND (pt.departmentCode IN :deptCodes OR pt.departmentCode IS NULL) "
          + "AND (pt.tenantId = :tenantId OR pt.tenantId = :#{T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).SYSTEM_TENANT_ID}) "
          + "AND pt.isActive = true "
          + "ORDER BY pt.departmentCode NULLS LAST, pt.tenantId NULLS LAST")
  List<PermissionTemplate> findEffectiveTemplatesForDepartments(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCodes") List<String> deptCodes);

  @Query(
      "SELECT pt FROM PermissionTemplate pt WHERE "
          + "(pt.tenantId = :tenantId OR pt.tenantId = :#{T(com.fabricmanagement.common.infrastructure.persistence.TenantContext).SYSTEM_TENANT_ID}) "
          + "AND (:roleCode IS NULL OR pt.roleCode = :roleCode) "
          + "AND (:deptCode IS NULL OR pt.departmentCode = :deptCode) "
          + "AND pt.isActive = true "
          + "ORDER BY pt.roleCode ASC, pt.departmentCode ASC, pt.tenantId NULLS LAST")
  List<PermissionTemplate> findTemplatesList(
      @Param("tenantId") UUID tenantId,
      @Param("roleCode") String roleCode,
      @Param("deptCode") String departmentCode);

  boolean existsByTenantId(UUID tenantId);
}
