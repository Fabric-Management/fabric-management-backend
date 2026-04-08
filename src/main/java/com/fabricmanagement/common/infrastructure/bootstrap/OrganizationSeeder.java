package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeder for the initial Organization and Departments. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final OrganizationService organizationService;
  private final DepartmentRepository departmentRepository;
  private final TransactionTemplate transactionTemplate;

  private static final String TAX_ID = "AKK-1234567890";

  private static final Map<String, String> EXPECTED_DEPARTMENTS = new java.util.LinkedHashMap<>();

  static {
    EXPECTED_DEPARTMENTS.put("Management", "MANAGEMENT");
    EXPECTED_DEPARTMENTS.put("Human Resources", "HUMAN_RESOURCES");
    EXPECTED_DEPARTMENTS.put("Finance", "FINANCE_ACCOUNTING");
    EXPECTED_DEPARTMENTS.put("Procurement", "PROCUREMENT");
    EXPECTED_DEPARTMENTS.put("Production", "PRODUCTION");
    EXPECTED_DEPARTMENTS.put("Yarn Production", "YARN_PRODUCTION");
    EXPECTED_DEPARTMENTS.put("Weaving", "WEAVING");
    EXPECTED_DEPARTMENTS.put("Dyeing & Finishing", "DYEING_FINISHING");
    EXPECTED_DEPARTMENTS.put("Quality", "QUALITY_CONTROL");
    EXPECTED_DEPARTMENTS.put("Warehouse", "WAREHOUSE_LOGISTICS");
    EXPECTED_DEPARTMENTS.put("Sales", "SALES_MARKETING");
  }

  private static final Map<String, String> PARENT_MAPPING =
      Map.of(
          "Yarn Production", "PRODUCTION",
          "Weaving", "PRODUCTION",
          "Dyeing & Finishing", "PRODUCTION");

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> tenantOpt = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (tenantOpt.isEmpty()) {
      return false;
    }

    return TenantContext.executeInTenantContext(
        tenantOpt.get().getId(),
        () -> {
          Optional<OrganizationDto> rootOrg = organizationService.getRootOrganization();
          if (rootOrg.isEmpty()) {
            return false;
          }

          // Granular check: verify ALL expected departments exist, not just "any"
          Set<String> existingNames =
              departmentRepository.findByTenantIdAndIsActiveTrue(tenantOpt.get().getId()).stream()
                  .map(Department::getDepartmentName)
                  .collect(Collectors.toSet());

          return EXPECTED_DEPARTMENTS.keySet().stream().allMatch(existingNames::contains);
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(
                () -> new IllegalStateException("Tenant must be seeded before Organization"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                // 1. Check Root Org or Create
                OrganizationDto rootOrg =
                    organizationService
                        .getRootOrganization()
                        .orElseGet(
                            () -> {
                              log.info("Creating Root Organization...");
                              return organizationService.createRootOrganization(
                                  tenant.getId(),
                                  TenantSeeder.TENANT_NAME,
                                  TAX_ID,
                                  OrganizationType.VERTICAL_MILL);
                            });

                // 2. Create Departments - granular per-record idempotency
                log.info("Creating Departments for org: {}", rootOrg.getId());
                Map<String, Department> createdDepartments = new java.util.HashMap<>();

                // Fetch all existing so we don't duplicate logic
                departmentRepository
                    .findByTenantIdAndIsActiveTrue(tenant.getId())
                    .forEach(d -> createdDepartments.put(d.getDepartmentCode(), d));

                for (Map.Entry<String, String> entry : EXPECTED_DEPARTMENTS.entrySet()) {
                  String dpName = entry.getKey();
                  String dpCode = entry.getValue();

                  Department dp = createdDepartments.get(dpCode);
                  if (dp == null) {
                    dp = new Department();
                    dp.setTenantId(tenant.getId());
                    dp.setOrganizationId(rootOrg.getId());
                    dp.setDepartmentName(dpName);
                    dp.setDepartmentCode(dpCode);
                    dp.setIsActive(true);

                    // Assign parent if mapped and parent exists
                    String parentCode = PARENT_MAPPING.get(dpName);
                    if (parentCode != null && createdDepartments.containsKey(parentCode)) {
                      dp.setParentDepartment(createdDepartments.get(parentCode));
                    }

                    departmentRepository.save(dp);
                    createdDepartments.put(dpCode, dp);
                    log.info("Created department: {} (Parent: {})", dpName, parentCode);
                  } else {
                    log.debug("Department already exists, skipping: {}", dpName);

                    // Retroactively map parent if missing (for dev-tools idempotency)
                    String parentCode = PARENT_MAPPING.get(dpName);
                    if (parentCode != null
                        && dp.getParentDepartment() == null
                        && createdDepartments.containsKey(parentCode)) {
                      dp.setParentDepartment(createdDepartments.get(parentCode));
                      departmentRepository.save(dp);
                      log.info("Updated department {} with parent {}", dpName, parentCode);
                    }
                  }
                }
              });
        });
  }

  @Override
  public int getOrder() {
    return 20;
  }
}
