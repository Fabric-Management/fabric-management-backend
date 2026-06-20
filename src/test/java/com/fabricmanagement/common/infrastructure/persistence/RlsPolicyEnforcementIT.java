package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RlsPolicyEnforcementIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private DataSource dataSource;

  @Test
  void shouldHaveRowLevelSecurityEnabledAndForcedOnAllTenantAwareTables() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    // Find all tables that have a tenant_id column but don't have RLS enabled/forced
    // or don't have a recognized RLS policy.
    String query =
        """
        SELECT c.table_schema, c.table_name
        FROM information_schema.columns c
        JOIN pg_class pc ON pc.relname = c.table_name
        JOIN pg_namespace pn ON pn.oid = pc.relnamespace AND pn.nspname = c.table_schema
        WHERE c.column_name = 'tenant_id'
          AND c.table_schema NOT IN ('information_schema', 'pg_catalog')
          AND (
            pc.relrowsecurity = false
            OR pc.relforcerowsecurity = false
            OR NOT EXISTS (
              SELECT 1
              FROM pg_policy p
              WHERE p.polrelid = pc.oid
                AND p.polname IN ('rls_tenant_isolation', 'rls_tenant_self_row', 'rls_tenant_read')
            )
          )
        """;

    List<String> violatingTables =
        jdbcTemplate.query(
            query, (rs, rowNum) -> rs.getString("table_schema") + "." + rs.getString("table_name"));

    assertThat(violatingTables)
        .withFailMessage(
            "The following tenant-aware tables are missing RLS configuration or a recognized policy: %s",
            violatingTables)
        .isEmpty();
  }

  @Test
  void shouldHaveSelfRowRlsOnTenantTable() {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    // Verify the tenant table has the self-row RLS policy (id = current_tenant, not tenant_id)
    String query =
        """
        SELECT count(*) FROM pg_policy p
        JOIN pg_class pc ON p.polrelid = pc.oid
        JOIN pg_namespace pn ON pn.oid = pc.relnamespace
        WHERE pn.nspname = 'common_tenant'
          AND pc.relname = 'common_tenant'
          AND p.polname = 'rls_tenant_self_row'
        """;

    Integer count = jdbcTemplate.queryForObject(query, Integer.class);
    assertThat(count)
        .withFailMessage("common_tenant.common_tenant is missing the 'rls_tenant_self_row' policy")
        .isEqualTo(1);
  }
}
