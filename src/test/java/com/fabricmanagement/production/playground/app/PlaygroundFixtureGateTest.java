package com.fabricmanagement.production.playground.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort.RegisterFirstPlaygroundSignup;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Playground fixture eligibility (FIBER-CATALOG-1 §9, A03/A17 unit level): exactly a new legacy
 * PLAYGROUND tenant, or a register-first REGULAR + demo_mode tenant opened with the PLAYGROUND
 * signup intent. demo_mode alone, TRIAL, sales-led, existing identity, the catalogue owner and
 * templates never qualify.
 */
class PlaygroundFixtureGateTest {

  private final PlaygroundFixtureGate gate = new PlaygroundFixtureGate();
  private final UUID tenantId = UUID.randomUUID();

  private RegisterFirstPlaygroundSignup signup(
      String intent, boolean demoMode, boolean salesLed, boolean existingIdentity) {
    return new RegisterFirstPlaygroundSignup(
        tenantId, intent, demoMode, salesLed, existingIdentity);
  }

  @Test
  void registerFirstPlaygroundIntentOnARegularDemoTenantQualifies() {
    assertThat(
            gate.registerFirstEligible(signup("PLAYGROUND", true, false, false), "REGULAR", true))
        .isTrue();
    assertThat(
            gate.registerFirstEligible(signup(" playground ", true, false, false), "regular", true))
        .isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "demo mode alone with TRIAL intent, TRIAL, true, false, false, REGULAR, true",
    "missing intent, , true, false, false, REGULAR, true",
    "sales-led, PLAYGROUND, true, true, false, REGULAR, true",
    "existing identity, PLAYGROUND, true, false, true, REGULAR, true",
    "signup without demo mode, PLAYGROUND, false, false, false, REGULAR, true",
    "persisted tenant not in demo mode, PLAYGROUND, true, false, false, REGULAR, false",
    "persisted PLAYGROUND type, PLAYGROUND, true, false, false, PLAYGROUND, true",
    "persisted type TEMPLATE, PLAYGROUND, true, false, false, TEMPLATE, true"
  })
  void everyOtherRegisterFirstCombinationIsRejected(
      String description,
      String intent,
      boolean demoMode,
      boolean salesLed,
      boolean existingIdentity,
      String persistedType,
      boolean persistedDemoMode) {
    assertThat(
            gate.registerFirstEligible(
                signup(intent, demoMode, salesLed, existingIdentity),
                persistedType,
                persistedDemoMode))
        .as(description)
        .isFalse();
  }

  @Test
  void legacyCreationQualifiesOnlyForAPlaygroundTenant() {
    assertThat(gate.legacyEligible(tenantId, "PLAYGROUND")).isTrue();
    assertThat(gate.legacyEligible(tenantId, "REGULAR")).isFalse();
    assertThat(gate.legacyEligible(tenantId, "TEMPLATE")).isFalse();
    assertThat(gate.legacyEligible(null, "PLAYGROUND")).isFalse();
  }

  @Test
  void theCatalogueOwnerNeverQualifies() {
    assertThat(gate.legacyEligible(FiberCatalog.OWNER_ID, "PLAYGROUND")).isFalse();
    assertThat(
            gate.registerFirstEligible(
                new RegisterFirstPlaygroundSignup(
                    FiberCatalog.OWNER_ID, "PLAYGROUND", true, false, false),
                "REGULAR",
                true))
        .isFalse();
    assertThat(gate.registerFirstEligible(null, "REGULAR", true)).isFalse();
  }
}
