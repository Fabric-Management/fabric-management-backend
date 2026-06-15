package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.FinancePermissionBackfillRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"spring.flyway.enabled=true"})
@ActiveProfiles("test")
@Testcontainers
class FinancePermissionMigrationIT {

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
  }

  @Autowired private SystemTransactionExecutor systemTransactionExecutor;
  @Autowired private FinancePermissionBackfillRunner backfillRunner;

  @Test
  void shouldSynthesizeFinanceWriteForFinanceDepartmentAcrossMultipleTenantsWithoutTenantContext() {
    String tenant1 = "11111111-1111-1111-1111-111111111111";
    String tenant2 = "22222222-2222-2222-2222-222222222222";

    // 1. Insert dummy SALES permissions for two tenants to simulate an existing environment with
    // multiple tenants.
    // Use systemTransactionExecutor so we don't have to deal with RLS blocking the insert check.
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          jdbcTemplate.update(
              """
            INSERT INTO common_user.permission_template (
                id, tenant_id, uid, role_code, department_code, resource, action, data_scope, is_active, created_at, updated_at
            ) VALUES
            (gen_random_uuid(), ?::uuid, gen_random_uuid()::varchar, 'MANAGER', 'SALES', 'sales', 'read', 'ORGANIZATION', true, NOW(), NOW()),
            (gen_random_uuid(), ?::uuid, gen_random_uuid()::varchar, 'MANAGER', 'SALES', 'sales', 'read', 'ORGANIZATION', true, NOW(), NOW())
            """,
              tenant1,
              tenant2);
          return null;
        });

    // 2. Run the backfill (it uses systemTransactionExecutor internally, NO app.current_tenant
    // needed, simulating production boot!)
    backfillRunner.runBackfill();

    // 3. Assert that FINANCE department has finance:write permission synthesized for BOTH tenants
    // Notice how we don't set app.current_tenant here either.
    String financeWriteQuery =
        """
        SELECT count(*) FROM common_user.permission_template
        WHERE department_code = 'FINANCE'
          AND resource = 'finance'
          AND action = 'write'
          AND tenant_id = ?::uuid
        """;

    Integer countTenant1 =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate -> jdbcTemplate.queryForObject(financeWriteQuery, Integer.class, tenant1));
    assertThat(countTenant1)
        .withFailMessage("Tenant 1 should have finance:write permissions synthesized.")
        .isGreaterThan(0);

    Integer countTenant2 =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate -> jdbcTemplate.queryForObject(financeWriteQuery, Integer.class, tenant2));
    assertThat(countTenant2)
        .withFailMessage("Tenant 2 should have finance:write permissions synthesized.")
        .isGreaterThan(0);

    // 4. Assert that PARTNER roles do not have finance access
    String partnerFinanceQuery =
        """
        SELECT count(*) FROM common_user.permission_template
        WHERE role_code LIKE 'PARTNER_%'
          AND resource = 'finance'
        """;
    Integer partnerCount =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate -> jdbcTemplate.queryForObject(partnerFinanceQuery, Integer.class));
    assertThat(partnerCount)
        .withFailMessage("PARTNER roles should not have 'finance' resource access.")
        .isEqualTo(0);

    // 5. Assert that SALES department and wildcard MANAGER roles carried over finance:read
    String salesAndManagerReadQuery =
        """
        SELECT count(*) FROM common_user.permission_template
        WHERE resource = 'finance' AND action = 'read' AND tenant_id = ?::uuid
          AND (department_code = 'SALES' OR department_code IS NULL)
        """;
    Integer carryOverCount1 =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate ->
                jdbcTemplate.queryForObject(salesAndManagerReadQuery, Integer.class, tenant1));
    assertThat(carryOverCount1)
        .withFailMessage("SALES/MANAGER roles should have 'finance:read' cloned in tenant 1.")
        .isGreaterThan(0);

    Integer carryOverCount2 =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate ->
                jdbcTemplate.queryForObject(salesAndManagerReadQuery, Integer.class, tenant2));
    assertThat(carryOverCount2)
        .withFailMessage("SALES/MANAGER roles should have 'finance:read' cloned in tenant 2.")
        .isGreaterThan(0);

    // 6. Verify Idempotency
    String totalFinanceRowsQuery =
        """
        SELECT count(*) FROM common_user.permission_template WHERE resource = 'finance'
        """;
    Integer initialCount =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate -> jdbcTemplate.queryForObject(totalFinanceRowsQuery, Integer.class));

    backfillRunner.runBackfill(); // run again

    Integer finalCount =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate -> jdbcTemplate.queryForObject(totalFinanceRowsQuery, Integer.class));
    assertThat(finalCount)
        .withFailMessage("Migration should be idempotent")
        .isEqualTo(initialCount);
  }
}
