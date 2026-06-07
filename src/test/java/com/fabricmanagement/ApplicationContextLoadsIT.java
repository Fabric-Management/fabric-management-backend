package com.fabricmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Ensures the full Spring application context starts without circular dependencies or missing
 * beans. Run as part of {@code make check}; when Docker is available, this test catches context
 * failures (e.g. circular refs, Flyway issues) before {@code make run}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("Application context loads")
class ApplicationContextLoadsIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    // Init: fabric_app rol oluştur (container default user = owner)
    try (var conn =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      conn.createStatement()
          .execute(
              "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'test'");
      // D3: GRANT ALL ON DATABASE YOK — tablo grant'leri migration'dan gelir
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to create fabric_app role", e);
    }

    // Runtime datasource → fabric_app (NOBYPASSRLS)
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

    // Flyway → container default user (owner, tabloları oluşturan)
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("Full application context loads without circular dependencies")
  void contextLoads() {
    assertThat(context).isNotNull();
    assertThat(context.getBean("fabricManagementApplication")).isNotNull();
  }
}
