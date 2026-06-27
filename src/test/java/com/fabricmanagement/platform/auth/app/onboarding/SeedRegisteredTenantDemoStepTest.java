package com.fabricmanagement.platform.auth.app.onboarding;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SeedRegisteredTenantDemoStepTest {

  private final UserSeeder userSeeder = Mockito.mock(UserSeeder.class);
  private final DemoTransactionSeeder demoTransactionSeeder =
      Mockito.mock(DemoTransactionSeeder.class);
  private final SeedRegisteredTenantDemoStep step =
      new SeedRegisteredTenantDemoStep(userSeeder, demoTransactionSeeder);

  @Test
  void shouldSeedSelfServiceDemoTenantIntoRegisteredTenant() {
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setSalesLed(false);
    context.setDemoMode(true);
    context.setAdminContact("owner@example.com");
    context.setAdminContactValue("owner@example.com");
    when(userSeeder.seedFor(tenantId, "owner@example.com", PersonaSubset.REPRESENTATIVE))
        .thenReturn(15);

    step.execute(context);

    verify(userSeeder).seedFor(tenantId, "owner@example.com", PersonaSubset.REPRESENTATIVE);
    verify(demoTransactionSeeder).seedFor(tenantId);
  }

  @Test
  void shouldSkipSalesLedTenant() {
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setSalesLed(true);
    context.setDemoMode(false);
    context.setAdminContact("owner@example.com");

    step.execute(context);

    verify(userSeeder, never())
        .seedFor(Mockito.any(), Mockito.anyString(), Mockito.any(PersonaSubset.class));
    verify(demoTransactionSeeder, never()).seedFor(Mockito.any());
  }

  @Test
  void shouldSkipNonDemoSelfServiceTenant() {
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setSalesLed(false);
    context.setDemoMode(false);
    context.setAdminContact("owner@example.com");

    step.execute(context);

    verify(userSeeder, never())
        .seedFor(Mockito.any(), Mockito.anyString(), Mockito.any(PersonaSubset.class));
    verify(demoTransactionSeeder, never()).seedFor(Mockito.any());
  }
}
