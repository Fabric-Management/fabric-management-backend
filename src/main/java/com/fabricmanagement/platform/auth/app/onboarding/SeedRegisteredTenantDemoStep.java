package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Seeds registered self-service tenants with representative demo users and business data. */
@Order(11)
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedRegisteredTenantDemoStep implements OnboardingStep {

  private final UserSeeder userSeeder;
  private final DemoTransactionSeeder demoTransactionSeeder;

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
    demoTransactionSeeder.seedFor(context.getTenantId());

    log.info(
        "SeedRegisteredTenantDemoStep: seeded registered demo tenantId={}, personaUsers={}",
        context.getTenantId(),
        seededUsers);
  }
}
