package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ColorCutoverRollbackRehearsalIT extends BatchColorCutoverSqlTestSupport {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("batch_color_rollback")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  ColorCutoverRollbackRehearsalIT() {
    super(POSTGRES);
  }

  @BeforeEach
  void setUpSchema() throws SQLException {
    resetToPreCutover();
    seedFoundation();
  }

  @Test
  void restoresLegacyRowsByteForByteClearsColorAndConsumesArchive() throws SQLException {
    UUID attribute = insertAttribute(TENANT_A, "COLOR", "ROLLBACK");
    UUID batch = insertBatch(TENANT_A, PRODUCT_A, "ROLLBACK");
    UUID source = insertBatchAttribute(TENANT_A, batch, attribute, NAVY_A.toString(), "ROLLBACK");

    applyScript(CUTOVER_SCRIPT);
    applyScript(ROLLBACK_SCRIPT);

    assertThat(
            count(
                """
                SELECT count(*)
                FROM production.production_execution_batch_attribute
                WHERE id = '%s'
                  AND tenant_id = '%s'
                  AND is_active = true
                  AND deleted_at IS NULL
                  AND created_at = '2026-07-01 10:00:00'
                  AND created_by IS NULL
                  AND updated_at = '2026-07-02 11:00:00'
                  AND updated_by IS NULL
                  AND version = 7
                """
                    .formatted(source, TENANT_A)))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch WHERE id = '"
                    + batch
                    + "' AND color_id IS NULL"))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM production.production_execution_batch_color_archive"))
        .isZero();
    assertThat(columnExists("production_execution_batch", "color_id")).isTrue();
    assertThat(tableExists("production_execution_batch_color_archive")).isTrue();
  }
}
