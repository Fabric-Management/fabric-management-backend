package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.testsupport.PostgresImage;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TenantCompanyNameMigrationIT {

  private static final String OLD_NAME = "Hawthorn Weaving Ltd";
  private static final String ROOT_NAME = "Hawthorn Cloth Ltd";

  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();

  private JdbcTemplate jdbc;

  @BeforeAll
  static void migrateToCompanyNameChange() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .target("20261114150000")
        .load()
        .migrate();
  }

  @Test
  void backfillCopiesOnlyUnambiguousRegularRootsAndCanBeReplayed() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    // The function scans every tenant. Keep the fixture and all its writes local to this test.
    transactions.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          UUID singleRoot = tenant("REGULAR");
          root(singleRoot, ROOT_NAME);
          UUID missingRoot = tenant("REGULAR");
          UUID multipleRoots = tenant("REGULAR");
          root(multipleRoots, ROOT_NAME);
          root(multipleRoots, "Willow Cloth Ltd");
          UUID blankRoot = tenant("REGULAR");
          root(blankRoot, " \t ");
          UUID playground = tenant("PLAYGROUND");
          root(playground, ROOT_NAME);
          UUID template = tenant("TEMPLATE");
          root(template, ROOT_NAME);
          UUID deleted = tenant("REGULAR");
          root(deleted, ROOT_NAME);
          jdbc.update(
              "UPDATE common_tenant.common_tenant SET deleted_at = now() WHERE id = ?", deleted);

          UUID system = TenantContext.SYSTEM_TENANT_ID;
          UUID goldenTemplate = TenantContext.TEMPLATE_TENANT_ID;
          String systemName = tenantName(system);
          String templateName = tenantName(goldenTemplate);
          // Make the fixed IDs eligible by type so the exclusions themselves are tested.
          jdbc.update(
              "UPDATE common_tenant.common_tenant SET type = 'REGULAR' WHERE id IN (?, ?)",
              system,
              goldenTemplate);

          Map<UUID, String> reports = backfill();
          assertThat(reports)
              .containsEntry(missingRoot, "NO_ROOT")
              .containsEntry(multipleRoots, "MULTIPLE_ROOTS")
              .containsEntry(blankRoot, "BLANK_ROOT_NAME")
              .doesNotContainKeys(
                  singleRoot, playground, template, deleted, system, goldenTemplate);
          assertThat(tenantName(singleRoot)).isEqualTo(ROOT_NAME);
          for (UUID skipped :
              new UUID[] {missingRoot, multipleRoots, blankRoot, playground, template, deleted}) {
            assertThat(tenantName(skipped)).isEqualTo(OLD_NAME);
          }
          assertThat(tenantName(system)).isEqualTo(systemName);
          assertThat(tenantName(goldenTemplate)).isEqualTo(templateName);
          long correctedVersion = tenantVersion(singleRoot);
          assertThat(correctedVersion).isEqualTo(1L);
          assertThat(backfill()).isEqualTo(reports);
          assertThat(tenantVersion(singleRoot)).isEqualTo(correctedVersion);
        });
  }

  private UUID tenant(String type) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type) VALUES (?, ?, ?, ?, ?)",
        id,
        "HW-" + id,
        "hawthorn-" + id,
        OLD_NAME,
        type);
    return id;
  }

  private void root(UUID tenant, String name) {
    jdbc.update(
        """
        INSERT INTO common_company.common_organization (tenant_id, uid, name, organization_type)
        VALUES (?, ?, ?, 'WEAVER')
        """,
        tenant,
        "ORG-" + UUID.randomUUID(),
        name);
  }

  private Map<UUID, String> backfill() {
    return jdbc
        .query(
            "SELECT tenant_id, reason FROM common_company.backfill_tenant_company_names()",
            (rs, row) -> Map.entry(rs.getObject("tenant_id", UUID.class), rs.getString("reason")))
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String tenantName(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT name FROM common_tenant.common_tenant WHERE id = ?", String.class, tenant);
  }

  private long tenantVersion(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT version FROM common_tenant.common_tenant WHERE id = ?", Long.class, tenant);
  }
}
