package com.fabricmanagement.platform.tenant.domain.port;

import java.util.UUID;

/**
 * Installs the playground fibre fixtures (FIBER-CATALOG-1 §9) during initial playground
 * provisioning only. Declared by platform, implemented by the owning business module; platform
 * never reads or writes the fixture data itself.
 *
 * <p>Exactly two trusted origins exist: a newly created legacy type=PLAYGROUND tenant, and a newly
 * created register-first REGULAR + demo_mode tenant whose backend-normalised signup intent is
 * PLAYGROUND, sales-led=false and existing-identity=false. Reset, login, startup, backfill, a
 * generic demo seed and anything after go-real never call this port. Implementations re-check the
 * persisted tenant and never widen the guard to {@code type == PLAYGROUND || demoMode}.
 */
public interface PlaygroundFixtureProvisioningPort {

  /**
   * Legacy creation, step 1: records the trusted PENDING marker. Must be called inside the clone's
   * {@code SystemTransactionExecutor} transaction, so the marker commits atomically with the new
   * PLAYGROUND tenant. Throws if that transaction is not active.
   */
  void registerLegacyPlaygroundCreation(UUID tenantId);

  /**
   * Legacy creation, step 2 (after the clone committed and before dependent demo transactions):
   * installs or repairs the fixtures of a tenant carrying a PENDING legacy marker. A tenant without
   * that marker, or with a completed one, is left untouched.
   */
  void provisionLegacyPlayground(UUID tenantId);

  /**
   * Register-first signup: runs inside the onboarding transaction with the tenant context and
   * database session bound to the new tenant, before the registered-demo transactions. Installs
   * nothing unless every origin condition and the persisted tenant state match.
   */
  void provisionRegisterFirstPlayground(RegisterFirstPlaygroundSignup signup);

  /** Server-side onboarding facts of a register-first signup; never taken from a client flag. */
  record RegisterFirstPlaygroundSignup(
      UUID tenantId,
      String signupIntent,
      boolean demoMode,
      boolean salesLed,
      boolean existingIdentity) {}
}
