package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repairs legacy name-derived system department codes within the active tenant context. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemDepartmentCodeRepairService {

  private final DepartmentRepository departmentRepository;

  @Transactional
  public int repairTenant(UUID tenantId) {
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            departmentRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(Collectors.groupingBy(Department::getOrganizationId))
                .values()
                .stream()
                .mapToInt(this::repairOrganization)
                .sum());
  }

  private int repairOrganization(List<Department> departments) {
    UUID organizationId = departments.getFirst().getOrganizationId();
    Map<String, Department> byCode =
        departments.stream()
            .collect(
                Collectors.toMap(Department::getDepartmentCode, Function.identity(), (a, b) -> a));
    Map<String, Department> byName =
        departments.stream()
            .collect(
                Collectors.toMap(Department::getDepartmentName, Function.identity(), (a, b) -> a));

    int repaired = 0;
    Department productionParent =
        ensureGroup(organizationId, SystemDepartment.PRODUCTION, byCode, byName);
    Department administrationParent =
        ensureGroup(organizationId, SystemDepartment.ADMINISTRATION, byCode, byName);
    Department logisticsParent =
        ensureGroup(organizationId, SystemDepartment.LOGISTICS, byCode, byName);
    Department utilityParent =
        ensureGroup(organizationId, SystemDepartment.UTILITY, byCode, byName);
    Department supportParent =
        ensureGroup(organizationId, SystemDepartment.SUPPORT, byCode, byName);

    repaired += ensureDepartment(SystemDepartment.RD, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.PLANNING, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.FIBER, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.YARN, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.WEAVING, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.KNITTING, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.DYEING, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.GARMENT, productionParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.QUALITY, productionParent, byCode, byName);

    repaired += ensureDepartment(SystemDepartment.HR, administrationParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.FINANCE, administrationParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.SALES, administrationParent, byCode, byName);
    repaired +=
        ensureDepartment(
            SystemDepartment.ADMINISTRATION_OFFICE, administrationParent, byCode, byName);
    repaired +=
        ensureDepartment(
            SystemDepartment.MANAGEMENT_PLANNING, administrationParent, byCode, byName);

    repaired += ensureDepartment(SystemDepartment.WAREHOUSE, logisticsParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.PROCUREMENT, logisticsParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.SHIPPING, logisticsParent, byCode, byName);

    repaired += ensureDepartment(SystemDepartment.MAINTENANCE, utilityParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.ENERGY_FACILITIES, utilityParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.KITCHEN_CATERING, utilityParent, byCode, byName);

    repaired += ensureDepartment(SystemDepartment.IT_SERVICES, supportParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.SECURITY, supportParent, byCode, byName);
    repaired += ensureDepartment(SystemDepartment.CLEANING_SERVICES, supportParent, byCode, byName);
    return repaired;
  }

  private Department ensureGroup(
      UUID organizationId,
      SystemDepartment systemDepartment,
      Map<String, Department> byCode,
      Map<String, Department> byName) {
    Department group = byCode.get(systemDepartment.code());
    if (group != null) {
      updateDepartment(group, systemDepartment, null);
      return group;
    }
    group = byName.get(systemDepartment.displayName());
    if (group != null) {
      updateDepartment(group, systemDepartment, null);
      byCode.put(systemDepartment.code(), group);
      return group;
    }
    Department created =
        Department.create(
            organizationId,
            systemDepartment.displayName(),
            systemDepartment.code(),
            systemDepartment.description());
    created.setDisplayOrder(systemDepartment.displayOrder());
    created.setIsSystemDepartment(true);
    departmentRepository.save(created);
    byCode.put(systemDepartment.code(), created);
    byName.put(systemDepartment.displayName(), created);
    log.info("Created missing system department group: {}", systemDepartment.code());
    return created;
  }

  private int ensureDepartment(
      SystemDepartment systemDepartment,
      Department parent,
      Map<String, Department> byCode,
      Map<String, Department> byName) {
    Department department = byCode.get(systemDepartment.code());
    if (department != null) {
      return updateDepartment(department, systemDepartment, parent) ? 1 : 0;
    }

    department = byName.get(systemDepartment.displayName());
    if (department == null) {
      department = byCode.get(legacyCode(systemDepartment.displayName()));
    }
    if (department == null && systemDepartment == SystemDepartment.WEAVING) {
      department = byName.get("Weaving & Knitting");
    }

    if (department != null) {
      boolean changed = updateDepartment(department, systemDepartment, parent);
      byCode.put(systemDepartment.code(), department);
      return changed ? 1 : 0;
    }

    Department created =
        Department.create(
            parent.getOrganizationId(),
            systemDepartment.displayName(),
            systemDepartment.code(),
            systemDepartment.description());
    created.setParentDepartment(parent);
    created.setDisplayOrder(systemDepartment.displayOrder());
    created.setIsSystemDepartment(true);
    departmentRepository.save(created);
    byCode.put(systemDepartment.code(), created);
    byName.put(systemDepartment.displayName(), created);
    log.info("Created missing system department: {}", systemDepartment.code());
    return 1;
  }

  private boolean updateDepartment(
      Department department, SystemDepartment systemDepartment, Department parent) {
    boolean changed = false;
    if (!Objects.equals(department.getDepartmentCode(), systemDepartment.code())) {
      department.setDepartmentCode(systemDepartment.code());
      changed = true;
    }
    if (!Objects.equals(department.getDepartmentName(), systemDepartment.displayName())) {
      department.setDepartmentName(systemDepartment.displayName());
      changed = true;
    }
    if (!Objects.equals(department.getDescription(), systemDepartment.description())) {
      department.setDescription(systemDepartment.description());
      changed = true;
    }
    if (!Objects.equals(department.getDisplayOrder(), systemDepartment.displayOrder())) {
      department.setDisplayOrder(systemDepartment.displayOrder());
      changed = true;
    }
    if (!Boolean.TRUE.equals(department.getIsSystemDepartment())) {
      department.setIsSystemDepartment(true);
      changed = true;
    }
    if (parent != null && !Objects.equals(parent.getId(), parentId(department))) {
      department.setParentDepartment(parent);
      changed = true;
    }
    if (changed) {
      departmentRepository.save(department);
    }
    return changed;
  }

  private UUID parentId(Department department) {
    Department parent = department.getParentDepartment();
    return parent != null ? parent.getId() : null;
  }

  private String legacyCode(String departmentName) {
    String code = departmentName.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z0-9]", "");
    return code.substring(0, Math.min(50, code.length()));
  }
}
