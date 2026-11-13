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

/**
 * Seeder for Nexus Fabrics, the development sample company.
 *
 * <p>PLATFORM-BOUNDARY-1: a full sample mill with every persona, kept for development and visual
 * checks only. {@link DevSeedDataRunner} runs it in the {@code dev} and {@code docker} profiles, so
 * it never exists in a live installation. It holds business personas only: platform staff belong to
 * Longward's platform tenant. Companies trying FabricOS get their own demo tenant at registration,
 * with personas for their line of work; nothing is copied from this tenant any more.
 */
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
                      .country("GB")
                      .settings(settings)
                      .type(TenantType.TEMPLATE)
                      .trialDays(365) // Dev env, don't expire soon
                      .build();

              TenantDto tenant = tenantService.createTenant(request);
              log.info("Created Default Tenant: {} with ID: {}", tenant.getName(), tenant.getId());

              // Clone platform roles from Golden Template into the newly created Nexus Fabrics
              // tenant. The tenant row is written on this transaction's connection
              // (ONBOARD-ATOMIC-1)
              // and is invisible to any other connection until it commits, so the copies are
              // written on this connection too.
              UUID goldenTemplateId = tenantClonerService.findTemplateTenantId();
              if (goldenTemplateId != null) {
                int clonedRoles =
                    tenantClonerService.cloneRolesInCallerTransaction(
                        goldenTemplateId, tenant.getId());
                log.info("Cloned {} roles into Nexus Fabrics", clonedRoles);
              } else {
                log.warn("Golden Template not found, roles were not cloned into Nexus Fabrics!");
              }

              // Clone production reference data, then missing system Property Registry and yarn
              // catalogue rows from Golden Template, so the sample company is complete.
              int refTables =
                  tenantClonerService.cloneReferenceDataInCallerTransaction(tenant.getId());
              log.info("Cloned {} reference data tables into Nexus Fabrics", refTables);

              // The notification catalogue, so the sample company's notifications read as in
              // any registered company.
              if (goldenTemplateId != null) {
                int clonedTemplates =
                    tenantClonerService.cloneNotificationDataInCallerTransaction(
                        goldenTemplateId, tenant.getId());
                log.info("Cloned {} notification templates into Nexus Fabrics", clonedTemplates);
              }

              // Return tenant ID to seed transactions outside this transaction boundary
              return tenant.getId();
            });

    // Seed demo transactions if enabled
    if (tenantId != null) {
      try {
        demoTransactionSeeder.seedIfEnabledFor(tenantId);
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
