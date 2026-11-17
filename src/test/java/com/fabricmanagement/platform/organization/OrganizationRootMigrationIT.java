package com.fabricmanagement.platform.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.PostgresImage;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrganizationRootMigrationIT {

  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();

  @Test
  void refusesHistoricalDuplicatesAndEnforcesTheRootPredicateAfterReconciliation() {
    migrateTo("20261115100000");
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    UUID tenant = tenant(jdbc, "TEMPLATE");
    UUID root = organisation(jdbc, tenant, null, "WEAVER", true);
    UUID branch = organisation(jdbc, tenant, null, "WEAVER", true);
    tenant(jdbc, "REGULAR"); // A missing customer root is reported without blocking the index.

    assertThatThrownBy(() -> migrateTo("20261115120000"))
        .isInstanceOf(FlywayException.class)
        .hasStackTraceContaining("Multiple active internal root organisations found")
        .hasStackTraceContaining(tenant.toString())
        .hasStackTraceContaining(root.toString())
        .hasStackTraceContaining(branch.toString());

    jdbc.update(
        "UPDATE common_company.common_organization SET parent_organization_id = ? WHERE id = ?",
        root,
        branch);
    migrateTo("20261115120000");

    UUID inactive = organisation(jdbc, tenant, null, "WEAVER", false);
    organisation(jdbc, tenant, null, "EXTERNAL_PARTNER", true);
    organisation(jdbc, tenant, null, "EXTERNAL_PARTNER", true);
    organisation(jdbc, tenant(jdbc, "REGULAR"), null, "WEAVER", true);

    assertThatThrownBy(() -> organisation(jdbc, tenant, null, "WEAVER", true))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("ux_organization_active_company_root");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE common_company.common_organization SET is_active = true WHERE id = ?",
                    inactive))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("ux_organization_active_company_root");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE common_company.common_organization SET parent_organization_id = NULL WHERE id = ?",
                    branch))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("ux_organization_active_company_root");
    assertThat(
            jdbc.queryForObject(
                "SELECT parent_organization_id FROM common_company.common_organization WHERE id = ?",
                UUID.class,
                branch))
        .isEqualTo(root);
  }

  private static void migrateTo(String target) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .target(target)
        .load()
        .migrate();
  }

  private static UUID tenant(JdbcTemplate jdbc, String type) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type) VALUES (?, ?, ?, ?, ?)",
        id,
        "HW-" + id,
        "hawthorn-" + id,
        "Hawthorn Weaving Ltd",
        type);
    return id;
  }

  private static UUID organisation(
      JdbcTemplate jdbc, UUID tenant, UUID parent, String type, boolean active) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO common_company.common_organization
            (id, tenant_id, uid, name, organization_type, parent_organization_id, is_active)
        VALUES (?, ?, ?, 'Hawthorn Leeds Branch', ?, ?, ?)
        """,
        id,
        tenant,
        "ORG-" + id,
        type,
        parent,
        active);
    return id;
  }
}
