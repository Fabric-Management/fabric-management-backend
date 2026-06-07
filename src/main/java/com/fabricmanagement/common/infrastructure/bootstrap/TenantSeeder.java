package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
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
  private final TransactionTemplate transactionTemplate;

  @Override
  public boolean isSeeded() {
    return tenantService.findBySlug(TENANT_SLUG).isPresent();
  }

  @Override
  public void seed() {
    transactionTemplate.executeWithoutResult(
        status -> {
          TenantSettings settings = TenantSettings.forTurkey();
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
        });
  }

  @Override
  public int getOrder() {
    return 10;
  }
}
