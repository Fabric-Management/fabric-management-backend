package com.fabricmanagement.common.infrastructure.bootstrap;

/**
 * Interface for individual data seeders. Implementations should provide isolated seed logic for a
 * specific domain/module.
 */
public interface DataSeeder {

  /**
   * Determine if the seeder has already been run (idempotency check).
   *
   * @return true if data already exists, false otherwise.
   */
  boolean isSeeded();

  /** Run the actual seeding logic. */
  void seed();

  /**
   * Determines the execution order of the seeder. Lower values execute first. Examples: 10 =
   * Tenant/Platform 20 = Organization/Users 30 = TradingPartners 40 = Masterdata
   *
   * @return order index
   */
  int getOrder();
}
