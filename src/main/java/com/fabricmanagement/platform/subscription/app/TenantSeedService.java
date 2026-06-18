package com.fabricmanagement.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.JobTitlePreset;
import com.fabricmanagement.platform.user.domain.port.JobTitlePresetRepository;
import java.util.*;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for seeding default departments for new tenants.
 *
 * <p>Uses parent department hierarchy for grouping. Top-level departments (Production,
 * Administration, etc.) serve as group headers, while child departments are the actual
 * organizational units.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSeedService {

  private final DepartmentRepository departmentRepository;
  private final JobTitlePresetRepository jobTitlePresetRepository;

  @Transactional
  public void seedDepartments(UUID tenantId, UUID organizationId) {
    log.info(
        "Seeding departments for tenant: tenantId={}, organizationId={}", tenantId, organizationId);

    UUID originalTenantId = TenantContext.requireTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);

      // Create top-level group departments (replacing former DepartmentCategory)
      Department production = createGroupDepartment(organizationId, SystemDepartment.PRODUCTION);
      Department administration =
          createGroupDepartment(organizationId, SystemDepartment.ADMINISTRATION);
      Department logistics = createGroupDepartment(organizationId, SystemDepartment.LOGISTICS);
      Department utility = createGroupDepartment(organizationId, SystemDepartment.UTILITY);
      Department support = createGroupDepartment(organizationId, SystemDepartment.SUPPORT);

      seedProductionDepartments(organizationId, production);
      seedAdministrativeDepartments(organizationId, administration);
      seedLogisticsDepartments(organizationId, logistics);
      seedUtilityDepartments(organizationId, utility);
      seedSupportDepartments(organizationId, support);

      seedJobTitles(tenantId);

      log.info("Seeded departments for tenant: tenantId={}", tenantId);
    } finally {
      TenantContext.setCurrentTenantId(originalTenantId);
    }
  }

  @Transactional(readOnly = true)
  public boolean isTenantSeeded(UUID tenantId, UUID organizationId) {
    UUID originalTenantId = TenantContext.requireTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      long departmentCount =
          departmentRepository
              .findByTenantIdAndOrganizationIdAndIsActiveTrue(tenantId, organizationId)
              .size();
      return departmentCount > 0;
    } finally {
      TenantContext.setCurrentTenantId(originalTenantId);
    }
  }

  /** Create a top-level group department (replaces the former DepartmentCategory concept). */
  private Department createGroupDepartment(UUID organizationId, SystemDepartment systemDepartment) {
    if (departmentRepository.existsByTenantIdAndOrganizationIdAndDepartmentName(
        TenantContext.requireTenantId(), organizationId, systemDepartment.displayName())) {
      return departmentRepository
          .findByTenantIdAndOrganizationIdAndDepartmentName(
              TenantContext.requireTenantId(), organizationId, systemDepartment.displayName())
          .orElseThrow();
    }

    Department dept =
        Department.create(
            organizationId,
            systemDepartment.displayName(),
            systemDepartment.code(),
            systemDepartment.description());
    dept.setDisplayOrder(systemDepartment.displayOrder());
    dept.setIsSystemDepartment(true);
    return departmentRepository.save(dept);
  }

  private Department createDepartment(
      UUID organizationId, SystemDepartment systemDepartment, Department parentDepartment) {
    if (departmentRepository.existsByTenantIdAndOrganizationIdAndDepartmentName(
        TenantContext.requireTenantId(), organizationId, systemDepartment.displayName())) {
      log.debug("Department already exists: {}", systemDepartment.displayName());
      return departmentRepository
          .findByTenantIdAndOrganizationIdAndDepartmentName(
              TenantContext.requireTenantId(), organizationId, systemDepartment.displayName())
          .orElseThrow();
    }

    Department department =
        Department.create(
            organizationId,
            systemDepartment.displayName(),
            systemDepartment.code(),
            systemDepartment.description());
    department.setParentDepartment(parentDepartment);
    department.setDisplayOrder(systemDepartment.displayOrder());
    department.setIsSystemDepartment(true);
    return departmentRepository.save(department);
  }

  /** Custom department fallback only. System departments must use {@link SystemDepartment}. */
  private String generateDepartmentCode(String departmentName) {
    String code = departmentName.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
    return code.substring(0, Math.min(50, code.length()));
  }

  // ========== Seed Methods ==========

  private void seedProductionDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, SystemDepartment.RD, parent);
    createDepartment(organizationId, SystemDepartment.PLANNING, parent);
    createDepartment(organizationId, SystemDepartment.FIBER, parent);
    createDepartment(organizationId, SystemDepartment.YARN, parent);
    createDepartment(organizationId, SystemDepartment.WEAVING, parent);
    createDepartment(organizationId, SystemDepartment.KNITTING, parent);
    createDepartment(organizationId, SystemDepartment.DYEING, parent);
    createDepartment(organizationId, SystemDepartment.GARMENT, parent);
    createDepartment(organizationId, SystemDepartment.QUALITY, parent);
  }

  private void seedAdministrativeDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, SystemDepartment.HR, parent);
    createDepartment(organizationId, SystemDepartment.FINANCE, parent);
    createDepartment(organizationId, SystemDepartment.SALES, parent);
    createDepartment(organizationId, SystemDepartment.ADMINISTRATION_OFFICE, parent);
    createDepartment(organizationId, SystemDepartment.MANAGEMENT_PLANNING, parent);
  }

  private void seedLogisticsDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, SystemDepartment.WAREHOUSE, parent);
    createDepartment(organizationId, SystemDepartment.PROCUREMENT, parent);
    createDepartment(organizationId, SystemDepartment.SHIPPING, parent);
  }

  private void seedUtilityDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, SystemDepartment.MAINTENANCE, parent);
    createDepartment(organizationId, SystemDepartment.ENERGY_FACILITIES, parent);
    createDepartment(organizationId, SystemDepartment.KITCHEN_CATERING, parent);
  }

  private void seedSupportDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, SystemDepartment.IT_SERVICES, parent);
    createDepartment(organizationId, SystemDepartment.SECURITY, parent);
    createDepartment(organizationId, SystemDepartment.CLEANING_SERVICES, parent);
  }

  private void seedJobTitles(UUID tenantId) {
    int seededCount = 0;
    for (var preset : JobTitleSeedData.ALL_PRESETS) {
      if (!jobTitlePresetRepository.existsByTenantIdAndJobTitleCode(tenantId, preset.code())) {
        jobTitlePresetRepository.save(
            JobTitlePreset.createSystem(
                preset.code(),
                preset.name(),
                preset.description(),
                preset.roleCode(),
                preset.departmentCode()));
        seededCount++;
      }
    }
    log.info(
        "Seeded {}/{} job title presets for tenant: {}",
        seededCount,
        JobTitleSeedData.ALL_PRESETS.size(),
        tenantId);
  }
}
