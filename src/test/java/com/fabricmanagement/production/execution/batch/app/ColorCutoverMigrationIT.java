package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ColorCutoverMigrationIT extends BatchColorCutoverSqlTestSupport {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("batch_color_cutover")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  ColorCutoverMigrationIT() {
    super(POSTGRES);
  }

  @BeforeEach
  void setUpSchema() throws SQLException {
    resetToPreCutover();
    seedFoundation();
  }

  @Test
  void migratesUuidCodeSynonymsPaddedCodesInactiveCardsAndMultipleTenants() throws SQLException {
    UUID color = insertAttribute(TENANT_A, "COLOR", "COLOR");
    UUID colour = insertAttribute(TENANT_A, "COLOUR", "COLOUR");
    UUID padded = insertAttribute(TENANT_A, " COLOR ", "PADDED");
    UUID colorId = insertAttribute(TENANT_A, "COLOR_ID", "COLOR-ID");
    UUID shade = insertAttribute(TENANT_B, "SHADE", "SHADE");

    UUID uuidBatch = insertBatch(TENANT_A, PRODUCT_A, "UUID");
    UUID codeBatch = insertBatch(TENANT_A, PRODUCT_A, "CODE");
    UUID sameCardBatch = insertBatch(TENANT_A, PRODUCT_A, "SAME");
    UUID paddedBatch = insertBatch(TENANT_A, PRODUCT_A, "PADDED");
    UUID inactiveBatch = insertBatch(TENANT_A, PRODUCT_A, "INACTIVE");
    UUID tenantBBatch = insertBatch(TENANT_B, PRODUCT_B, "TENANT-B");

    insertBatchAttribute(TENANT_A, uuidBatch, color, NAVY_A.toString(), "UUID");
    insertBatchAttribute(TENANT_A, codeBatch, colour, "ECRU-02", "CODE");
    insertBatchAttribute(TENANT_A, sameCardBatch, color, "NAVY-01", "SAME-1");
    insertBatchAttribute(TENANT_A, sameCardBatch, colour, NAVY_A.toString(), "SAME-2");
    insertBatchAttribute(TENANT_A, paddedBatch, padded, "NAVY-01", "PADDED");
    insertBatchAttribute(TENANT_A, inactiveBatch, colorId, "OLD-03", "INACTIVE");
    insertBatchAttribute(TENANT_B, tenantBBatch, shade, "NAVY-01", "TENANT-B");

    applyScript(CUTOVER_SCRIPT);

    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch WHERE color_id IS NOT NULL"))
        .isEqualTo(6);
    assertThat(count("SELECT count(*) FROM production.production_execution_batch_color_archive"))
        .isEqualTo(7);
    assertThat(
            count(
                """
                SELECT count(*)
                FROM production.production_execution_batch_color_archive archive
                JOIN production.production_execution_batch batch
                  ON batch.tenant_id = archive.tenant_id
                 AND batch.id = archive.batch_id
                 AND batch.color_id = archive.resolved_color_id
                """))
        .isEqualTo(7);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch_attribute WHERE is_active = false AND deleted_at IS NOT NULL"))
        .isEqualTo(7);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch_attribute WHERE is_active = true"))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch_color_archive WHERE resolved_color_id = '"
                    + OLD_A
                    + "'"))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(PreflightFailure.class)
  void preflightFailuresRollbackMigrationOwnedDdl(PreflightFailure failure) throws SQLException {
    failure.seed(this);

    assertThatThrownBy(() -> applyScript(CUTOVER_SCRIPT))
        .hasMessageContaining("preflight #" + failure.number);

    assertThat(columnExists("production_execution_batch", "color_id")).isFalse();
    assertThat(tableExists("production_execution_batch_color_archive")).isFalse();
  }

  @Test
  void preExistingNonNullColumnFailsWithoutRemovingOrChangingIt() throws SQLException {
    UUID batchId = insertBatch(TENANT_A, PRODUCT_A, "PREEXISTING");
    UUID unexpectedColor = UUID.randomUUID();
    execute("ALTER TABLE production.production_execution_batch ADD COLUMN color_id UUID");
    execute(
        "UPDATE production.production_execution_batch SET color_id = '"
            + unexpectedColor
            + "' WHERE id = '"
            + batchId
            + "'");

    assertThatThrownBy(() -> applyScript(CUTOVER_SCRIPT)).hasMessageContaining("preflight #6");

    assertThat(columnExists("production_execution_batch", "color_id")).isTrue();
    assertThat(tableExists("production_execution_batch_color_archive")).isFalse();
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch WHERE id = '"
                    + batchId
                    + "' AND color_id = '"
                    + unexpectedColor
                    + "'"))
        .isEqualTo(1);
  }

  private enum PreflightFailure {
    TENANT_MISMATCH(1) {
      @Override
      void seed(ColorCutoverMigrationIT test) throws SQLException {
        UUID attribute = test.insertAttribute(TENANT_B, "COLOR", "MISMATCH");
        UUID batch = test.insertBatch(TENANT_A, PRODUCT_A, "MISMATCH");
        test.insertBatchAttribute(TENANT_A, batch, attribute, "NAVY-01", "MISMATCH");
      }
    },
    BLANK_VALUE(2) {
      @Override
      void seed(ColorCutoverMigrationIT test) throws SQLException {
        UUID attribute = test.insertAttribute(TENANT_A, "COLOR", "BLANK");
        UUID batch = test.insertBatch(TENANT_A, PRODUCT_A, "BLANK");
        test.insertBatchAttribute(TENANT_A, batch, attribute, "   ", "BLANK");
      }
    },
    UNKNOWN_UUID(3) {
      @Override
      void seed(ColorCutoverMigrationIT test) throws SQLException {
        UUID attribute = test.insertAttribute(TENANT_A, "COLOR", "UUID");
        UUID batch = test.insertBatch(TENANT_A, PRODUCT_A, "UUID");
        test.insertBatchAttribute(TENANT_A, batch, attribute, UUID.randomUUID().toString(), "UUID");
      }
    },
    UNKNOWN_CODE(4) {
      @Override
      void seed(ColorCutoverMigrationIT test) throws SQLException {
        UUID attribute = test.insertAttribute(TENANT_A, "COLOR", "CODE");
        UUID batch = test.insertBatch(TENANT_A, PRODUCT_A, "CODE");
        test.insertBatchAttribute(TENANT_A, batch, attribute, "MISSING", "CODE");
      }
    },
    CONFLICT(5) {
      @Override
      void seed(ColorCutoverMigrationIT test) throws SQLException {
        UUID color = test.insertAttribute(TENANT_A, "COLOR", "CONFLICT-1");
        UUID colour = test.insertAttribute(TENANT_A, "COLOUR", "CONFLICT-2");
        UUID batch = test.insertBatch(TENANT_A, PRODUCT_A, "CONFLICT");
        test.insertBatchAttribute(TENANT_A, batch, color, "NAVY-01", "CONFLICT-1");
        test.insertBatchAttribute(TENANT_A, batch, colour, "ECRU-02", "CONFLICT-2");
      }
    };

    private final int number;

    PreflightFailure(int number) {
      this.number = number;
    }

    abstract void seed(ColorCutoverMigrationIT test) throws SQLException;
  }
}
