package com.fabricmanagement.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The evaluation catalogue is installed by the migrations, exactly as in production; no test adds
 * its own packages to make signup work. This fails fast, with a clear reason, if a clean database
 * somehow did not receive it, instead of letting every onboarding test fail with a catalogue error.
 */
public final class EvaluationCatalogueGuard {
  private EvaluationCatalogueGuard() {}

  public static void requireMigrationCatalogue(JdbcTemplate jdbc) {
    Integer offered =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM common_company.common_os_definition
            WHERE tenant_id = '00000000-0000-0000-0000-000000000000' AND os_code = 'FabricOS'
              AND is_active AND deleted_at IS NULL AND evaluation_available
            """,
            Integer.class);
    if (offered == null || offered == 0) {
      throw new IllegalStateException(
          "The migrations did not install the system catalogue: FabricOS is not offered");
    }
  }
}
