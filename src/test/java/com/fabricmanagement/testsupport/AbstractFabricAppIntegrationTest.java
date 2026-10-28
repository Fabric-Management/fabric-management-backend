package com.fabricmanagement.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration-test base whose application connects as the real runtime roles: {@code fabric_app}
 * ({@code NOBYPASSRLS}) for the primary data source and {@code fabric_system} ({@code BYPASSRLS})
 * for the system one, exactly as in production.
 *
 * <p>{@link AbstractIntegrationTest} connects as the container's superuser, which bypasses
 * row-level security even under {@code FORCE ROW LEVEL SECURITY}: a test on that base cannot tell a
 * write the policy admits from one it would reject. Use this base for behaviour whose correctness
 * depends on the policies, such as which connection a row is written on and whether the session is
 * bound to the right tenant (ONBOARD-ATOMIC-1).
 *
 * <p>It owns its container: the roles must exist before Flyway runs, because the migrations grant
 * to them, and the shared container of {@link AbstractIntegrationTest} was migrated without them.
 * The migrations themselves run as the container's owner. A test that needs to see across tenants
 * queries through the {@code systemJdbcTemplate} bean.
 *
 * <p>The web environment is {@code MOCK}; add {@code @AutoConfigureMockMvc} to drive controllers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class AbstractFabricAppIntegrationTest {

  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  static {
    postgres.start();
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'test'");
      statement.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to create the fabric_app / fabric_system roles", e);
    }
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    // Runtime data source: fabric_app (NOBYPASSRLS)
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    // Flyway runs as the container's owner, the role that creates the tables.
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    // The system data source reads the template tenant at start-up; as in production it must
    // bypass RLS (fabric_app would see no golden rows).
    registry.add("application.system-datasource.username", () -> "fabric_system");
    registry.add("application.system-datasource.password", () -> "system_test");
  }
}
