package com.fabricmanagement.production.playground.app;

import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort.RegisterFirstPlaygroundSignup;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Eligibility rules for playground fibre fixtures (FIBER-CATALOG-1 §9). Tenant type alone, {@code
 * demo_mode} alone or an old signup intent alone never qualifies; the catalogue owner and every
 * TEMPLATE tenant (including the demo company) never qualify.
 */
@Component
public class PlaygroundFixtureGate {

  static final String PLAYGROUND = "PLAYGROUND";
  static final String REGULAR = "REGULAR";

  /** Register-first: every onboarding fact and the persisted tenant state must match. */
  public boolean registerFirstEligible(
      RegisterFirstPlaygroundSignup signup, String persistedType, boolean persistedDemoMode) {
    return signup != null
        && signup.tenantId() != null
        && !FiberCatalog.isOwner(signup.tenantId())
        && PLAYGROUND.equals(normalize(signup.signupIntent()))
        && signup.demoMode()
        && !signup.salesLed()
        && !signup.existingIdentity()
        && REGULAR.equals(normalize(persistedType))
        && persistedDemoMode;
  }

  /** Legacy creation: the persisted tenant must be a PLAYGROUND. */
  public boolean legacyEligible(UUID tenantId, String persistedType) {
    return tenantId != null
        && !FiberCatalog.isOwner(tenantId)
        && PLAYGROUND.equals(normalize(persistedType));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }
}
