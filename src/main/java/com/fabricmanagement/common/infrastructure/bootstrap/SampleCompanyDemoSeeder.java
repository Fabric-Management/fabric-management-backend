package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Seeds the demo transactions (finance, sales orders, production, costing, purchasing, quotes) of
 * Nexus Fabrics, the development sample company (TD-16).
 *
 * <p>Runs after {@link OrganizationSeeder} and {@link UserSeeder}: the finance demo sets the
 * reporting currency on the company's root organisation and the purchasing demo is recorded as a
 * persona's work, so both must exist first. It used to run inside {@link TenantSeeder}, before the
 * organisation existed; the reporting currency then fell back to the platform default and the
 * finance demo failed on a missing EUR→GBP rate. Runs before {@link TradingPartnerSeeder}, which
 * adds the same demo customer this seeder's idempotency check looks for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SampleCompanyDemoSeeder implements DataSeeder {

  private final TenantSystemService tenantService;
  private final TradingPartnerService tradingPartnerService;
  private final DemoTransactionSeeder demoTransactionSeeder;

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> sampleCompany = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (sampleCompany.isEmpty()) {
      return true;
    }
    return TenantContext.executeInTenantContext(
        sampleCompany.get().getId(),
        () ->
            !tradingPartnerService
                .searchByName(sampleCompany.get().getId(), DemoTransactionSeeder.DEMO_CUSTOMER_NAME)
                .isEmpty());
  }

  @Override
  public void seed() {
    TenantDto sampleCompany =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(() -> new IllegalStateException("Nexus Fabrics must be seeded first"));
    // A failure is logged with its cause and does not stop the seeders after this one: trading
    // partners, warehouses and products do not depend on the demo transactions.
    try {
      demoTransactionSeeder.seedIfEnabledFor(sampleCompany.getId());
    } catch (RuntimeException ex) {
      log.error(
          "Demo transactions of the sample company could not be seeded; the rest of the start-up"
              + " seeding continues.",
          ex);
    }
  }

  @Override
  public int getOrder() {
    return 35;
  }
}
