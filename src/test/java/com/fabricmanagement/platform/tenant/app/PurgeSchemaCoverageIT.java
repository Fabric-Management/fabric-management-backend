package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PurgeSchemaCoverageIT extends AbstractIntegrationTest {

  private static final Map<String, String> PURGE_EXEMPT_TENANT_TABLES = purgeExemptTables();

  @Autowired private JdbcTemplate jdbc;

  @Test
  void tenantScopedDeleteTablesExistAndHaveTenantId() {
    List<String> directDeleteTables = TenantTransactionalPurgeService.tenantScopedDeleteTables();
    List<String> missingTables =
        directDeleteTables.stream().filter(table -> !tableExists(table)).sorted().toList();
    List<String> missingTenantId =
        directDeleteTables.stream()
            .filter(this::tableExists)
            .filter(table -> !hasTenantIdColumn(table))
            .sorted()
            .toList();

    assertThat(missingTables)
        .as("Tables in the tenant-scoped purge list that do not exist")
        .isEmpty();
    assertThat(missingTenantId)
        .as("Tables in the tenant-scoped purge list without a tenant_id column")
        .isEmpty();
  }

  @Test
  void everyTenantScopedTableIsPurgedOrDocumentedAsExempt() {
    Set<String> tenantScopedTables = new TreeSet<>(tenantScopedTablesFromSchema());
    Set<String> directDeleteTables =
        new TreeSet<>(TenantTransactionalPurgeService.tenantScopedDeleteTables());
    Set<String> exemptTables = new TreeSet<>(PURGE_EXEMPT_TENANT_TABLES.keySet());

    Set<String> uncovered = new TreeSet<>(tenantScopedTables);
    uncovered.removeAll(directDeleteTables);
    uncovered.removeAll(exemptTables);

    Set<String> staleExemptions = new TreeSet<>(exemptTables);
    staleExemptions.removeAll(tenantScopedTables);

    assertThat(uncovered)
        .as(
            "Tenant-scoped tables that are neither directly purged nor documented in "
                + "PURGE_EXEMPT_TENANT_TABLES")
        .isEmpty();
    assertThat(staleExemptions)
        .as("PURGE_EXEMPT_TENANT_TABLES entries that no longer exist as tenant-scoped tables")
        .isEmpty();
  }

  private boolean tableExists(String qualifiedTable) {
    TableName table = parse(qualifiedTable);
    Integer count =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = ?
              AND table_name = ?
              AND table_type = 'BASE TABLE'
            """,
            Integer.class,
            table.schema(),
            table.name());
    return count != null && count == 1;
  }

  private boolean hasTenantIdColumn(String qualifiedTable) {
    TableName table = parse(qualifiedTable);
    Integer count =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = ?
              AND column_name = 'tenant_id'
            """,
            Integer.class,
            table.schema(),
            table.name());
    return count != null && count == 1;
  }

  private List<String> tenantScopedTablesFromSchema() {
    return jdbc.queryForList(
        """
        SELECT DISTINCT c.table_schema || '.' || c.table_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema
         AND t.table_name = c.table_name
        WHERE c.column_name = 'tenant_id'
          AND t.table_type = 'BASE TABLE'
          AND c.table_schema NOT IN ('pg_catalog', 'information_schema')
          AND c.table_schema NOT LIKE 'pg_toast%'
          AND c.table_name <> 'flyway_schema_history'
        ORDER BY 1
        """,
        String.class);
  }

  private TableName parse(String qualifiedTable) {
    String[] parts = qualifiedTable.split("\\.", 2);
    assertThat(parts)
        .as("Purge table names must be schema-qualified: %s", qualifiedTable)
        .hasSize(2);
    return new TableName(parts[0], parts[1]);
  }

  private static Map<String, String> purgeExemptTables() {
    Map<String, String> tables = new LinkedHashMap<>();

    tables.put("common_audit.common_audit_log", "Audit history is retained across demo reset.");
    tables.put("common_auth.common_auth_user", "Seed-user auth rows are deleted by demo_seed CTE.");
    tables.put(
        "common_auth.common_refresh_token",
        "Seed-user refresh tokens are deleted by demo_seed CTE.");
    tables.put(
        "common_auth.common_registration_token",
        "Registration/setup tokens are retained for account lifecycle.");
    tables.put(
        "common_auth.common_trusted_device",
        "Seed-user trusted devices are deleted by demo_seed CTE.");
    tables.put(
        "common_auth.common_verification_code",
        "Verification codes are account lifecycle data, not demo business data.");
    tables.put(
        "common_communication.common_address",
        "Seed-user and external-partner addresses are deleted by dedicated CTEs.");
    tables.put(
        "common_communication.common_contact",
        "Seed-user and external-partner contacts are deleted by dedicated CTEs.");
    tables.put(
        "common_communication.common_routing_config", "Tenant routing configuration is retained.");
    tables.put("common_company.common_department", "Tenant departments are retained.");
    tables.put("common_company.common_feature_catalog", "Tenant feature catalog is retained.");
    tables.put("common_company.common_organization", "Tenant/root organizations are retained.");
    tables.put(
        "common_company.common_organization_address",
        "External-partner organization addresses are deleted by dedicated CTE.");
    tables.put(
        "common_company.common_organization_contact",
        "External-partner organization contacts are deleted by dedicated CTE.");
    tables.put("common_company.common_os_definition", "Tenant OS definitions are retained.");
    tables.put("common_company.common_subscription", "Subscriptions are retained.");
    tables.put("common_company.common_subscription_quota", "Subscription quotas are retained.");
    tables.put(
        "common_company.common_trading_partner",
        "Trading partners are deleted by dedicated registry cleanup CTE.");
    tables.put(
        "common_company.organization_certification",
        "Organization certifications are deleted by dedicated trading-partner cleanup.");
    tables.put(
        "common_company.partner_trading_partner_certification",
        "Partner certifications are deleted by dedicated trading-partner cleanup.");
    tables.put("common_policy.common_policy", "Tenant policy configuration is retained.");
    tables.put("common_user.common_role", "Roles are retained.");
    tables.put(
        "common_user.common_user",
        "Only demo_seed users are deleted by dedicated CTE; real users are retained.");
    tables.put(
        "common_user.common_user_address", "Seed-user address links are deleted by demo_seed CTE.");
    tables.put(
        "common_user.common_user_contact", "Seed-user contact links are deleted by demo_seed CTE.");
    tables.put(
        "common_user.common_user_department",
        "Seed-user department links are deleted by demo_seed CTE.");
    tables.put(
        "common_user.common_user_work_location",
        "Seed-user work locations are deleted by demo_seed CTE.");
    tables.put("common_user.job_title_preset", "Tenant job-title reference data is retained.");
    tables.put("common_user.permission_override", "Tenant permission overrides are retained.");
    tables.put("common_user.permission_template", "Tenant permission templates are retained.");
    tables.put(
        "common_user.user_nav_preferences",
        "Seed-user nav preferences are deleted by demo_seed CTE.");
    tables.put("human.human_employee", "Seed-user employees are deleted by demo_seed CTE.");
    tables.put("human.human_holiday_calendar", "Tenant HR calendar configuration is retained.");
    tables.put("human.human_hr_country_pack_mapping", "Tenant HR pack mapping is retained.");
    tables.put("human.human_hr_policy_binding", "Tenant HR policy binding is retained.");
    tables.put("human.human_hr_policy_pack", "Tenant HR policy pack is retained.");
    tables.put("human.human_hr_rule_audit_log", "HR rule audit history is retained.");
    tables.put("human.human_hr_rule_version", "Tenant HR rule versions are retained.");
    tables.put("human.human_leave_type", "Tenant leave type configuration is retained.");
    tables.put("i18n.supported_locale", "Tenant locale reference data is retained.");
    tables.put("i18n.tenant_locale_config", "Tenant locale configuration is retained.");
    tables.put("i18n.translation_key", "Tenant translation keys are retained.");
    tables.put("i18n.translation_value", "Tenant translation values are retained.");
    tables.put("i18n.user_locale_config", "Seed-user locale config is deleted by demo_seed CTE.");
    tables.put("notification.notification_template", "Tenant notification templates are retained.");
    tables.put(
        "notification.user_notification_preference",
        "Seed-user notification preferences are deleted by demo_seed CTE.");
    tables.put(
        "production.inheritance_rule_schema",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_fiber",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_fiber_category",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_fiber_certification",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_fiber_iso_code",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_fiber_quality_standard",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_product",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.prod_product_attribute",
        "Product reference data is deleted by dedicated product-reference cleanup.");
    tables.put(
        "production.quality_grade",
        "Product reference data is deleted by dedicated product-reference cleanup.");

    return Map.copyOf(tables);
  }

  private record TableName(String schema, String name) {}
}
