package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.domain.TenantSettings;
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

  public static final String TENANT_NAME = "Akkayalar Textile";
  public static final String TENANT_SLUG =
      "akkayalar-textile"; // Derived manually based on service logic

  private final TenantService tenantService;
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
                  .billingEmail("admin@akkayalar.com")
                  .country("TR")
                  .settings(settings)
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
