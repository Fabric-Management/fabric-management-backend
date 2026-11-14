package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TD-16: the sample company's demo transactions run once its organisation and users exist. */
class SampleCompanyDemoSeederTest {

  private static final UUID SAMPLE = UUID.fromString("77777777-7777-7777-7777-777777777777");

  private final TenantSystemService tenants = mock(TenantSystemService.class);
  private final TradingPartnerService partners = mock(TradingPartnerService.class);
  private final DemoTransactionSeeder demo = mock(DemoTransactionSeeder.class);
  private final SampleCompanyDemoSeeder seeder =
      new SampleCompanyDemoSeeder(tenants, partners, demo);

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("runs after the organisation and users, before the trading partners")
  void order() {
    // OrganizationSeeder 20, UserSeeder 30, TradingPartnerSeeder 40.
    assertThat(seeder.getOrder()).isGreaterThan(30).isLessThan(40);
  }

  @Test
  @DisplayName("nothing to do without the sample company")
  void noSampleCompany() {
    when(tenants.findBySlug(TenantSeeder.TENANT_SLUG)).thenReturn(Optional.empty());

    assertThat(seeder.isSeeded()).isTrue();
  }

  @Test
  @DisplayName("seeded once the demo customer exists in the sample company")
  void seededWhenDemoCustomerExists() {
    when(tenants.findBySlug(TenantSeeder.TENANT_SLUG)).thenReturn(Optional.of(sample()));
    when(partners.searchByName(SAMPLE, DemoTransactionSeeder.DEMO_CUSTOMER_NAME))
        .thenReturn(List.of())
        .thenReturn(List.of(TradingPartnerDto.builder().build()));

    assertThat(seeder.isSeeded()).isFalse();
    assertThat(seeder.isSeeded()).isTrue();
  }

  @Test
  @DisplayName("seeds the sample company's demo transactions; a failure does not stop start-up")
  void seedsAndSurvivesFailure() {
    when(tenants.findBySlug(TenantSeeder.TENANT_SLUG)).thenReturn(Optional.of(sample()));

    seeder.seed();
    verify(demo).seedIfEnabledFor(SAMPLE);

    doThrow(new IllegalStateException("boom")).when(demo).seedIfEnabledFor(SAMPLE);
    assertThatCode(seeder::seed).doesNotThrowAnyException();
  }

  private static TenantDto sample() {
    return TenantDto.builder().id(SAMPLE).name(TenantSeeder.TENANT_NAME).build();
  }
}
