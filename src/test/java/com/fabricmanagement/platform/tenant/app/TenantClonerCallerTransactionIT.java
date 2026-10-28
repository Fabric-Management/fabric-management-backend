package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ONBOARD-ATOMIC-1: the copies onboarding writes on its own transaction ({@code
 * …InCallerTransaction}) are the copies the system-connection methods write. They share their SQL,
 * and this test keeps it that way: the same template, cloned both ways into two fresh tenants, must
 * leave the same rows. (On this superuser base RLS does not apply, so it guards the statements, not
 * the policies; {@code OnboardingAtomicityIT} runs the whole signup as {@code fabric_app}.)
 */
@DisplayName("Onboarding template copies on the caller's transaction")
class TenantClonerCallerTransactionIT extends AbstractIntegrationTest {

  private static final List<String> COPIED_TABLES =
      List.of(
          "common_user.common_role",
          "common_user.permission_template",
          "sales.ownership_policy",
          "production.prod_property_definition",
          "production.prod_yarn_spinning_system",
          "production.prod_yarn_end_use",
          "production.prod_yarn_test_method",
          "production.prod_fabric_construction",
          "production.prod_fabric_finishing_process",
          "production.prod_fabric_finishing_price_class");

  @Autowired private TenantClonerService cloner;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("write the same rows as the system-connection copies")
  void callerTransactionCopiesEqualTheSystemCopies() {
    UUID template = cloner.findTemplateTenantId();
    assertThat(template).as("golden template tenant").isNotNull();
    UUID viaSystem = newTenant();
    UUID viaCaller = newTenant();

    int systemRoles = cloner.cloneRolesToTenant(template, viaSystem);
    int systemPermissions = cloner.clonePermissionTemplatesToTenant(template, viaSystem);
    int systemPolicies = cloner.cloneOwnershipPolicyToTenant(viaSystem);
    int systemProperties = cloner.copyMissingSystemPropertyDefinitions(template, viaSystem);
    int systemYarn = cloner.copyMissingSystemYarnCatalogues(template, viaSystem);
    int systemFabric = cloner.copyMissingSystemFabricCatalogues(template, viaSystem);

    int[] caller = new int[6];
    transactionTemplate.executeWithoutResult(
        status -> {
          caller[0] = cloner.cloneRolesInCallerTransaction(template, viaCaller);
          caller[1] = cloner.clonePermissionTemplatesInCallerTransaction(template, viaCaller);
          caller[2] = cloner.cloneOwnershipPolicyInCallerTransaction(template, viaCaller);
          caller[3] =
              cloner.copyMissingSystemPropertyDefinitionsInCallerTransaction(template, viaCaller);
          caller[4] =
              cloner.copyMissingSystemYarnCataloguesInCallerTransaction(template, viaCaller);
          caller[5] =
              cloner.copyMissingSystemFabricCataloguesInCallerTransaction(template, viaCaller);
        });

    assertThat(caller)
        .containsExactly(
            systemRoles,
            systemPermissions,
            systemPolicies,
            systemProperties,
            systemYarn,
            systemFabric);
    for (String table : COPIED_TABLES) {
      long viaSystemRows = rows(table, viaSystem);
      assertThat(viaSystemRows).as("%s copied by the system path", table).isPositive();
      assertThat(rows(table, viaCaller))
          .as("%s copied by the caller path", table)
          .isEqualTo(viaSystemRows);
    }
  }

  @Test
  @DisplayName("are idempotent: a second run copies nothing")
  void aSecondRunCopiesNothing() {
    UUID template = cloner.findTemplateTenantId();
    UUID tenant = newTenant();

    transactionTemplate.executeWithoutResult(
        status -> {
          cloner.cloneRolesInCallerTransaction(template, tenant);
          cloner.clonePermissionTemplatesInCallerTransaction(template, tenant);
          cloner.cloneOwnershipPolicyInCallerTransaction(template, tenant);
          cloner.copyMissingSystemPropertyDefinitionsInCallerTransaction(template, tenant);
          cloner.copyMissingSystemYarnCataloguesInCallerTransaction(template, tenant);
          cloner.copyMissingSystemFabricCataloguesInCallerTransaction(template, tenant);
        });
    long before = totalRows(tenant);

    int[] second = new int[6];
    transactionTemplate.executeWithoutResult(
        status -> {
          second[0] = cloner.cloneRolesInCallerTransaction(template, tenant);
          second[1] = cloner.clonePermissionTemplatesInCallerTransaction(template, tenant);
          second[2] = cloner.cloneOwnershipPolicyInCallerTransaction(template, tenant);
          second[3] =
              cloner.copyMissingSystemPropertyDefinitionsInCallerTransaction(template, tenant);
          second[4] = cloner.copyMissingSystemYarnCataloguesInCallerTransaction(template, tenant);
          second[5] = cloner.copyMissingSystemFabricCataloguesInCallerTransaction(template, tenant);
        });

    assertThat(second).containsOnly(0);
    assertThat(totalRows(tenant)).isEqualTo(before);
  }

  @Test
  @DisplayName("refuse to run outside the onboarding transaction")
  void refuseToRunWithoutATransaction() {
    UUID template = cloner.findTemplateTenantId();
    UUID tenant = newTenant();

    assertThatThrownBy(() -> cloner.cloneRolesInCallerTransaction(template, tenant))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(rows("common_user.common_role", tenant)).isZero();
  }

  private UUID newTenant() {
    UUID id = UUID.randomUUID();
    String suffix = id.toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status, settings,"
            + " is_active, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, ?, 'REGULAR', 'ACTIVE', '{}'::jsonb, true, now(), now(), 0)",
        id,
        "CLN-" + suffix.toUpperCase(),
        "cln-" + suffix,
        "Clone target " + suffix);
    return id;
  }

  private long rows(String table, UUID tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenantId);
  }

  private long totalRows(UUID tenantId) {
    return COPIED_TABLES.stream().mapToLong(table -> rows(table, tenantId)).sum();
  }
}
