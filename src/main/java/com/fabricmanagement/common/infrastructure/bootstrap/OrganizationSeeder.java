package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.List;
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

  private static final List<String> EXPECTED_DEPARTMENTS =
      List.of(
          "Management",
          "Human Resources",
          "Finans ve Satınalma",
          "Üretim",
          "Kalite",
          "Depo ve Lojistik",
          "Satış");

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

          return EXPECTED_DEPARTMENTS.stream().allMatch(existingNames::contains);
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
                for (String dpName : EXPECTED_DEPARTMENTS) {
                  if (departmentRepository
                      .findByTenantIdAndOrganizationIdAndDepartmentName(
                          tenant.getId(), rootOrg.getId(), dpName)
                      .isEmpty()) {
                    Department dp = new Department();
                    dp.setTenantId(tenant.getId());
                    dp.setOrganizationId(rootOrg.getId());
                    dp.setDepartmentName(dpName);
                    dp.setDepartmentCode(
                        dpName.substring(0, Math.min(3, dpName.length())).toUpperCase());
                    dp.setIsActive(true);
                    departmentRepository.save(dp);
                    log.info("Created department: {}", dpName);
                  } else {
                    log.debug("Department already exists, skipping: {}", dpName);
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
