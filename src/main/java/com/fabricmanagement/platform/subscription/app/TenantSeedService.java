package com.fabricmanagement.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.JobTitlePreset;
import com.fabricmanagement.platform.user.domain.port.JobTitlePresetRepository;
import java.util.*;
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

    UUID originalTenantId = TenantContext.getCurrentTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);

      // Create top-level group departments (replacing former DepartmentCategory)
      Department production =
          createGroupDepartment(organizationId, "Production", "Production-related departments", 1);
      Department administration =
          createGroupDepartment(
              organizationId, "Administration", "Administrative and management departments", 2);
      Department logistics =
          createGroupDepartment(
              organizationId, "Logistics", "Logistics and supply chain departments", 3);
      Department utility =
          createGroupDepartment(organizationId, "Utility", "Auxiliary service departments", 4);
      Department support =
          createGroupDepartment(organizationId, "Support", "Support and service departments", 5);

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
    UUID originalTenantId = TenantContext.getCurrentTenantId();
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
  private Department createGroupDepartment(
      UUID organizationId, String name, String description, int displayOrder) {
    if (departmentRepository.existsByTenantIdAndOrganizationIdAndDepartmentName(
        TenantContext.getCurrentTenantId(), organizationId, name)) {
      return departmentRepository
          .findByTenantIdAndOrganizationIdAndDepartmentName(
              TenantContext.getCurrentTenantId(), organizationId, name)
          .orElseThrow();
    }

    String code = generateDepartmentCode(name);
    Department dept = Department.create(organizationId, name, code, description);
    dept.setDisplayOrder(displayOrder);
    dept.setIsSystemDepartment(true);
    return departmentRepository.save(dept);
  }

  private Department createDepartment(
      UUID organizationId, String name, String description, Department parentDepartment) {
    if (departmentRepository.existsByTenantIdAndOrganizationIdAndDepartmentName(
        TenantContext.getCurrentTenantId(), organizationId, name)) {
      log.debug("Department already exists: {}", name);
      return departmentRepository
          .findByTenantIdAndOrganizationIdAndDepartmentName(
              TenantContext.getCurrentTenantId(), organizationId, name)
          .orElseThrow();
    }

    String departmentCode = generateDepartmentCode(name);
    Department department = Department.create(organizationId, name, departmentCode, description);
    department.setParentDepartment(parentDepartment);
    return departmentRepository.save(department);
  }

  private String generateDepartmentCode(String departmentName) {
    String code = departmentName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    return code.substring(0, Math.min(50, code.length()));
  }

  // ========== Seed Methods ==========

  private void seedProductionDepartments(UUID organizationId, Department parent) {
    createDepartment(
        organizationId,
        "R&D / Product Development",
        "Research, development and fiber formula design",
        parent);
    createDepartment(
        organizationId,
        "Production Planning",
        "Production scheduling, capacity planning and work orders",
        parent);
    createDepartment(
        organizationId,
        "Fiber & Raw Material",
        "Fiber procurement and raw material management",
        parent);
    createDepartment(organizationId, "Yarn Production", "Yarn manufacturing operations", parent);
    createDepartment(
        organizationId, "Weaving & Knitting", "Fabric weaving and knitting operations", parent);
    createDepartment(
        organizationId, "Dyeing & Finishing", "Fabric dyeing and finishing operations", parent);
    createDepartment(
        organizationId, "Quality Control", "Quality assurance and laboratory testing", parent);
  }

  private void seedAdministrativeDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, "Human Resources", "Human resources management", parent);
    createDepartment(
        organizationId, "Finance & Accounting", "Financial management and accounting", parent);
    createDepartment(
        organizationId,
        "Administration Office",
        "General administration and office management",
        parent);
    createDepartment(
        organizationId,
        "Management & Planning",
        "Executive management and strategic planning",
        parent);
  }

  private void seedLogisticsDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, "Warehouse", "Warehouse management and storage", parent);
    createDepartment(
        organizationId, "Procurement & Supply", "Procurement and supply chain management", parent);
    createDepartment(
        organizationId, "Shipping & Transport", "Shipping and transportation management", parent);
  }

  private void seedUtilityDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, "Maintenance", "Equipment maintenance and repair", parent);
    createDepartment(
        organizationId, "Energy & Facilities", "Energy generation and facility operations", parent);
    createDepartment(
        organizationId, "Kitchen & Catering", "Kitchen and cafeteria services", parent);
  }

  private void seedSupportDepartments(UUID organizationId, Department parent) {
    createDepartment(organizationId, "IT Services", "IT support and system administration", parent);
    createDepartment(organizationId, "Security", "Security and access control", parent);
    createDepartment(
        organizationId, "Cleaning Services", "Cleaning and janitorial services", parent);
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
