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

  @Test
  @DisplayName("give a tenant the notification catalogue, once, with its wording and defaults")
  void copiesTheNotificationCatalogueOnce() {
    UUID template = cloner.findTemplateTenantId();
    UUID tenant = newTenant();

    int[] copied = new int[2];
    transactionTemplate.executeWithoutResult(
        status -> copied[0] = cloner.cloneNotificationDataInCallerTransaction(template, tenant));
    transactionTemplate.executeWithoutResult(
        status -> copied[1] = cloner.cloneNotificationDataInCallerTransaction(template, tenant));

    long goldenTemplates = rows("notification.notification_template", template);
    assertThat(goldenTemplates).as("golden notification templates").isPositive();
    assertThat(copied[0]).isEqualTo((int) goldenTemplates);
    assertThat(rows("notification.notification_template", tenant)).isEqualTo(goldenTemplates);
    assertThat(copied[1]).as("a second run copies nothing").isZero();

    // The wording travels with the templates: no template points at a key the tenant lacks,
    // beyond what the golden tenant itself lacks.
    assertThat(templatesWithoutKey(tenant)).isEqualTo(templatesWithoutKey(template));
    assertThat(notificationValues(tenant))
        .as("notification wording values")
        .isEqualTo(notificationValues(template))
        .isPositive();
    assertThat(notificationKeys(tenant)).isEqualTo(notificationKeys(template));

    // The e-mail defaults (B1) are copied, not reset.
    assertThat(emailsOnByDefault(template)).isPositive();
    assertThat(emailsOnByDefault(tenant)).isEqualTo(emailsOnByDefault(template));
  }

  @Test
  @DisplayName("fill in only what is missing, and leave a tenant's own template alone")
  void aTenantsOwnTemplateIsNotOverwritten() {
    UUID template = cloner.findTemplateTenantId();
    UUID tenant = newTenant();
    transactionTemplate.executeWithoutResult(
        status -> cloner.cloneNotificationDataInCallerTransaction(template, tenant));

    jdbc.update(
        "UPDATE notification.notification_template SET enabled_by_default = NOT enabled_by_default"
            + " WHERE tenant_id = ? AND event_type = 'APPROVAL_PENDING' AND channel = 'EMAIL'",
        tenant);
    jdbc.update(
        "DELETE FROM notification.notification_template"
            + " WHERE tenant_id = ? AND event_type = 'MIN_STOCK_ALERT' AND channel = 'EMAIL'",
        tenant);

    int[] copied = new int[1];
    transactionTemplate.executeWithoutResult(
        status -> copied[0] = cloner.cloneNotificationDataInCallerTransaction(template, tenant));

    assertThat(copied[0]).as("only the deleted template comes back").isEqualTo(1);
    Boolean flag =
        jdbc.queryForObject(
            "SELECT enabled_by_default FROM notification.notification_template"
                + " WHERE tenant_id = ? AND event_type = 'APPROVAL_PENDING' AND channel = 'EMAIL'",
            Boolean.class,
            tenant);
    Boolean golden =
        jdbc.queryForObject(
            "SELECT enabled_by_default FROM notification.notification_template"
                + " WHERE tenant_id = ? AND event_type = 'APPROVAL_PENDING' AND channel = 'EMAIL'",
            Boolean.class,
            template);
    assertThat(flag).as("the tenant's own choice survives a re-run").isNotEqualTo(golden);
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

  private long templatesWithoutKey(UUID tenantId) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM notification.notification_template t
        WHERE t.tenant_id = ?
          AND (NOT EXISTS (SELECT 1 FROM i18n.translation_key k
                           WHERE k.tenant_id = t.tenant_id AND k.key_code = t.title_key)
            OR NOT EXISTS (SELECT 1 FROM i18n.translation_key k
                           WHERE k.tenant_id = t.tenant_id AND k.key_code = t.body_key))
        """,
        Long.class,
        tenantId);
  }

  private long notificationKeys(UUID tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM i18n.translation_key"
            + " WHERE tenant_id = ? AND key_code LIKE 'notification.%'",
        Long.class, tenantId);
  }

  private long notificationValues(UUID tenantId) {
    return jdbc.queryForObject(
        """
        SELECT count(*) FROM i18n.translation_value v
        JOIN i18n.translation_key k ON k.id = v.translation_key_id
        WHERE v.tenant_id = ? AND k.tenant_id = v.tenant_id AND k.key_code LIKE 'notification.%'
        """,
        Long.class, tenantId);
  }

  private long emailsOnByDefault(UUID tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification.notification_template"
            + " WHERE tenant_id = ? AND channel = 'EMAIL' AND enabled_by_default",
        Long.class,
        tenantId);
  }
}
