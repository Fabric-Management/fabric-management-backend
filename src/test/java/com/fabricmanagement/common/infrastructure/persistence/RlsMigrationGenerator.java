package com.fabricmanagement.common.infrastructure.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
// @Disabled("Run manually to generate the RLS migration file")
class RlsMigrationGenerator {

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
  void generateRlsMigrationFile() throws IOException {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

    // Find all user-defined tables that have a tenant_id column
    String query =
        "SELECT table_schema, table_name "
            + "FROM information_schema.columns "
            + "WHERE column_name = 'tenant_id' "
            + "AND table_schema NOT IN ('information_schema', 'pg_catalog') "
            + "ORDER BY table_schema, table_name";

    List<TableInfo> tables =
        jdbcTemplate.query(
            query,
            (rs, rowNum) ->
                new TableInfo(rs.getString("table_schema"), rs.getString("table_name")));

    log.info("Found {} tables with 'tenant_id' column.", tables.size());

    StringBuilder sqlBuilder = new StringBuilder();
    sqlBuilder.append(
        "-- =========================================================================\n");
    sqlBuilder.append("-- AUTO-GENERATED: Enable RLS on all tenant-aware tables\n");
    sqlBuilder.append(
        "-- =========================================================================\n\n");

    for (TableInfo table : tables) {
      String fqn = table.schema() + "." + table.name();
      sqlBuilder.append("-- Table: ").append(fqn).append("\n");

      // We skip the tables already handled in
      // V20260602101021__fix_rls_session_variable_consistency.sql
      // But it's safe to just re-create or drop IF EXISTS for all of them.

      sqlBuilder.append(String.format("ALTER TABLE %s ENABLE ROW LEVEL SECURITY;\n", fqn));
      sqlBuilder.append(String.format("ALTER TABLE %s FORCE ROW LEVEL SECURITY;\n", fqn));
      sqlBuilder.append(String.format("DROP POLICY IF EXISTS rls_tenant_isolation ON %s;\n", fqn));

      sqlBuilder.append(String.format("CREATE POLICY rls_tenant_isolation ON %s\n", fqn));
      sqlBuilder.append("    FOR ALL\n");
      sqlBuilder.append(
          "    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)\n");
      sqlBuilder.append(
          "    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);\n\n");
    }

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    String filename = "V" + timestamp + "__enable_rls_all.sql";
    Path filepath = Paths.get("src/main/resources/db/migration/", filename);

    Files.writeString(filepath, sqlBuilder.toString());

    log.info("Successfully generated RLS migration file at: {}", filepath.toAbsolutePath());
  }

  record TableInfo(String schema, String name) {}
}
