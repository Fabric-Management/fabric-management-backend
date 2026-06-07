package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
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

  private final TenantSystemService tenantService;
  private final OrganizationService organizationService;
  private final DepartmentRepository departmentRepository;
  private final TransactionTemplate transactionTemplate;

  private static final String TAX_ID = "NXF-1234567890";

  private static final Map<String, String> EXPECTED_DEPARTMENTS = new java.util.LinkedHashMap<>();

  static {
    // Support group
    EXPECTED_DEPARTMENTS.put("Human Resources", "HR");
    EXPECTED_DEPARTMENTS.put("Finance & Accounting", "FINANCE");
    EXPECTED_DEPARTMENTS.put("Sales & Marketing", "SALES");
    EXPECTED_DEPARTMENTS.put("Procurement", "PROCUREMENT");
    EXPECTED_DEPARTMENTS.put("Quality Control", "QUALITY");
    EXPECTED_DEPARTMENTS.put("Warehouse & Logistics", "WAREHOUSE");
    EXPECTED_DEPARTMENTS.put("Shipping & Transport", "SHIPPING");
    // Production group
    EXPECTED_DEPARTMENTS.put("Fiber Processing", "FIBER");
    EXPECTED_DEPARTMENTS.put("Yarn Production", "YARN");
    EXPECTED_DEPARTMENTS.put("Weaving", "WEAVING");
    EXPECTED_DEPARTMENTS.put("Knitting", "KNITTING");
    EXPECTED_DEPARTMENTS.put("Dyeing & Finishing", "DYEING");
    EXPECTED_DEPARTMENTS.put("Garment Production", "GARMENT");
    EXPECTED_DEPARTMENTS.put("Production Planning", "PLANNING");
    EXPECTED_DEPARTMENTS.put("R&D & Product Development", "RD");
  }

  private static final Map<String, String> GROUP_MAPPING =
      Map.ofEntries(
          Map.entry("HR", "SUPPORT"),
          Map.entry("FINANCE", "SUPPORT"),
          Map.entry("SALES", "SUPPORT"),
          Map.entry("PROCUREMENT", "SUPPORT"),
          Map.entry("QUALITY", "SUPPORT"),
          Map.entry("WAREHOUSE", "SUPPORT"),
          Map.entry("SHIPPING", "SUPPORT"),
          Map.entry("FIBER", "PRODUCTION"),
          Map.entry("YARN", "PRODUCTION"),
          Map.entry("WEAVING", "PRODUCTION"),
          Map.entry("KNITTING", "PRODUCTION"),
          Map.entry("DYEING", "PRODUCTION"),
          Map.entry("GARMENT", "PRODUCTION"),
          Map.entry("PLANNING", "PRODUCTION"),
          Map.entry("RD", "PRODUCTION"));

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
                    dp.setDepartmentGroup(GROUP_MAPPING.get(dpCode));
                    dp.setIsActive(true);

                    dp = departmentRepository.save(dp);
                    createdDepartments.put(dpCode, dp);
                    log.info(
                        "Created department: {} (group: {})", dpName, GROUP_MAPPING.get(dpCode));
                  } else {
                    log.debug("Department already exists, skipping: {}", dpName);

                    // Retroactively assign group if missing (idempotency for existing data)
                    if (dp.getDepartmentGroup() == null && GROUP_MAPPING.containsKey(dpCode)) {
                      dp.setDepartmentGroup(GROUP_MAPPING.get(dpCode));
                      departmentRepository.save(dp);
                      log.info(
                          "Updated department {} with group {}", dpName, GROUP_MAPPING.get(dpCode));
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
