package com.fabricmanagement.product.core.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.FabricManagementApplication;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
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
class PropertyRegistryStartupValidationIT {

  private static final String SECOND_CONTEXT_USER = "property_registry_boot";
  private static final String SECOND_CONTEXT_PASSWORD = "property-registry-test";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("property_registry_startup")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @Test
  void secondContextRefusesStructurallyValidButDriftedSystemRow() {
    createSecondContextLogin();
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_property_definition SET canonical_unit_code = 'TPI' "
                  + "WHERE tenant_id = ? AND property_key = 'YARN_TWIST_TPM'",
              TenantContext.TEMPLATE_TENANT_ID);
          return null;
        });

    try {
      assertThatThrownBy(
              () ->
                  new SpringApplicationBuilder(FabricManagementApplication.class)
                      .profiles("test")
                      .web(WebApplicationType.SERVLET)
                      .run(
                          "--server.port=0",
                          "--spring.datasource.url=" + postgres.getJdbcUrl(),
                          "--spring.datasource.username=" + SECOND_CONTEXT_USER,
                          "--spring.datasource.password=" + SECOND_CONTEXT_PASSWORD,
                          "--spring.flyway.url=" + postgres.getJdbcUrl(),
                          "--spring.flyway.user=" + SECOND_CONTEXT_USER,
                          "--spring.flyway.password=" + SECOND_CONTEXT_PASSWORD,
                          "--spring.flyway.enabled=false",
                          "--application.system-datasource.username=" + SECOND_CONTEXT_USER,
                          "--application.system-datasource.password=" + SECOND_CONTEXT_PASSWORD))
          .hasRootCauseInstanceOf(PropertyRegistryException.class)
          .hasStackTraceContaining("YARN_TWIST_TPM");
    } finally {
      systemTransactionExecutor.executeInTransaction(
          jdbc -> {
            jdbc.update(
                "UPDATE production.prod_property_definition SET canonical_unit_code = 'TPM' "
                    + "WHERE tenant_id = ? AND property_key = 'YARN_TWIST_TPM'",
                TenantContext.TEMPLATE_TENANT_ID);
            return null;
          });
    }
  }

  private void createSecondContextLogin() {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.execute(
              """
              DO $$
              BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_roles WHERE rolname = 'property_registry_boot'
                ) THEN
                  CREATE ROLE property_registry_boot
                    LOGIN SUPERUSER PASSWORD 'property-registry-test';
                ELSE
                  ALTER ROLE property_registry_boot
                    WITH LOGIN SUPERUSER PASSWORD 'property-registry-test';
                END IF;
              END $$
              """);
          return null;
        });
  }
}
