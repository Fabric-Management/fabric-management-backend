package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort;
import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort.RegisterFirstPlaygroundSignup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds registered self-service demo tenants with representative demo users and business data.
 *
 * <p>FIBER-CATALOG-1: a register-first PLAYGROUND-intent signup additionally receives the
 * playground fibre fixtures, installed before the dependent demo transactions. The port re-checks
 * the signup facts and the persisted tenant; demo mode alone never qualifies, and reset or other
 * generic demo seeding never reaches this step.
 */
@Order(13) // After ProvisionTenantCatalogueStep (12): demo data may publish template events.
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedRegisteredTenantDemoStep implements OnboardingStep {

  private final UserSeeder userSeeder;
  private final DemoTransactionSeeder demoTransactionSeeder;
  private final PlaygroundFixtureProvisioningPort playgroundFixtures;

  @Override
  public void execute(OnboardingContext context) {
    if (context.isExistingIdentity() || context.isSalesLed() || !context.isDemoMode()) {
      log.debug(
          "SeedRegisteredTenantDemoStep: skipping tenantId={}, existingIdentity={}, salesLed={}, demoMode={}",
          context.getTenantId(),
          context.isExistingIdentity(),
          context.isSalesLed(),
          context.isDemoMode());
      return;
    }

    String ownerEmail =
        context.getAdminContactValue() != null
            ? context.getAdminContactValue()
            : context.getAdminContact();

    int seededUsers =
        userSeeder.seedFor(context.getTenantId(), ownerEmail, PersonaSubset.REPRESENTATIVE);
    playgroundFixtures.provisionRegisterFirstPlayground(
        new RegisterFirstPlaygroundSignup(
            context.getTenantId(),
            context.getSignupIntent(),
            context.isDemoMode(),
            context.isSalesLed(),
            context.isExistingIdentity()));
    demoTransactionSeeder.seedFor(context.getTenantId());

    log.info(
        "SeedRegisteredTenantDemoStep: seeded registered demo tenantId={}, personaUsers={}",
        context.getTenantId(),
        seededUsers);
  }
}
