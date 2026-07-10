package com.fabricmanagement.common.infrastructure.bootstrap;

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Coordinator for executing development seed data. It dynamically discovers all `DataSeeder`
 * implementations, sorts them by order, and executes them ONLY in safe profiles (local, dev).
 *
 * <p>Ordered explicitly: Spring guarantees nothing about the order of two {@code
 * ApplicationReadyEvent} listeners, and {@link PermissionTemplateBackfillRunner} (200) must observe
 * the tenants this runner creates.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class DevSeedDataRunner {

  private final List<DataSeeder> seeders;
  private final Environment environment;

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    if (!environment.acceptsProfiles(Profiles.of("local", "dev", "docker"))) {
      log.info("DevSeedDataRunner is disabled in this profile. Terminating seeder.");
      return;
    }

    log.info("Starting Dev Seed Data Runner...");

    List<DataSeeder> sortedSeeders =
        seeders.stream().sorted(Comparator.comparingInt(DataSeeder::getOrder)).toList();

    int succeeded = 0;
    int skipped = 0;

    for (DataSeeder seeder : sortedSeeders) {
      String seederName = seeder.getClass().getSimpleName();
      int order = seeder.getOrder();
      try {
        if (seeder.isSeeded()) {
          log.info("[{}] (order={}) Data already seeded. Skipping.", seederName, order);
          skipped++;
        } else {
          log.info("[{}] (order={}) Seeding started...", seederName, order);
          long start = System.currentTimeMillis();
          seeder.seed();
          long elapsed = System.currentTimeMillis() - start;
          log.info(
              "[{}] (order={}) Seeding completed successfully in {}ms.",
              seederName,
              order,
              elapsed);
          succeeded++;
        }
      } catch (Exception e) {
        log.error(
            "[{}] (order={}) Error during data seeding. "
                + "Downstream seeders will be skipped due to potential dependency. Error: {}",
            seederName,
            order,
            e.getMessage(),
            e);
        // Break on first error — downstream seeders may depend on this one.
        // TransactionTemplate ensures the failed seeder's partial writes are rolled back.
        break;
      }
    }

    log.info(
        "Dev Seed Data Runner finished. Succeeded: {}, Skipped (already seeded): {}, Total: {}",
        succeeded,
        skipped,
        sortedSeeders.size());
  }
}
