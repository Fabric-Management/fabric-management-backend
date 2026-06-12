package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeder for the initial Tenant. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSeeder implements DataSeeder {

  public static final String TENANT_NAME = "Nexus Fabrics";
  public static final String TENANT_SLUG =
      "nexus-fabrics"; // Derived manually based on service logic

  private final TenantSystemService tenantService;
  private final TenantClonerService tenantClonerService;
  private final TransactionTemplate transactionTemplate;
  private final DemoTransactionSeeder demoTransactionSeeder;

  @Override
  public boolean isSeeded() {
    return tenantService.findBySlug(TENANT_SLUG).isPresent();
  }

  @Override
  public void seed() {
    UUID tenantId =
        transactionTemplate.execute(
            status -> {
              TenantSettings settings = TenantSettings.defaults();
              CreateTenantRequest request =
                  CreateTenantRequest.builder()
                      .name(TENANT_NAME)
                      .billingEmail("admin@nexusfabrics.com")
                      .country("TR")
                      .settings(settings)
                      .type(TenantType.TEMPLATE)
                      .trialDays(365) // Dev env, don't expire soon
                      .build();

              TenantDto tenant = tenantService.createTenant(request);
              log.info("Created Default Tenant: {} with ID: {}", tenant.getName(), tenant.getId());

              // Clone platform roles from Golden Template into the newly created Nexus Fabrics
              // tenant
              UUID goldenTemplateId = tenantClonerService.findTemplateTenantId();
              if (goldenTemplateId != null) {
                int clonedRoles =
                    tenantClonerService.cloneRolesToTenant(goldenTemplateId, tenant.getId());
                log.info("Cloned {} roles into Nexus Fabrics", clonedRoles);
              } else {
                log.warn("Golden Template not found, roles were not cloned into Nexus Fabrics!");
              }

              // Clone production reference data (categories, ISO codes, certifications, attributes)
              // from Golden Template. This provisions the demo company so playground cloning
              // produces complete tenants. Chain: golden-template → nexus-fabrics → playground.
              int refTables = tenantClonerService.cloneReferenceDataToTenant(tenant.getId());
              log.info("Cloned {} reference data tables into Nexus Fabrics", refTables);

              // Return tenant ID to seed transactions outside this transaction boundary
              return tenant.getId();
            });

    // Seed demo transactions if enabled
    if (tenantId != null) {
      try {
        demoTransactionSeeder.seedFor(tenantId);
      } catch (Exception e) {
        log.error("Startup demo seeding failed, but continuing tenant bootstrap.", e);
      }
    }
  }

  @Override
  public int getOrder() {
    return 10;
  }
}
