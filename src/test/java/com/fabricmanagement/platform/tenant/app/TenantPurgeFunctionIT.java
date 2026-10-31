package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.flowboard.generator.app.catalogue.TaskTemplateCatalogueBackfillRunner;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import com.fabricmanagement.testsupport.PlaygroundSourceFixture;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * common_tenant.purge_tenant (V20261031120000) on real cloned tenants, called as fabric_system
 * (BYPASSRLS, not a superuser) as in production: the dry run changes nothing, the purge removes
 * every row of that tenant and nothing of any other tenant, and the guards refuse what must never
 * be deleted. Fixtures are written as the container's owner.
 */
class TenantPurgeFunctionIT extends AbstractFabricAppIntegrationTest {

  private static final String PURGE =
      "SELECT step, table_name, row_count FROM common_tenant.purge_tenant(?, ?, ?)";
  private static final UUID PLATFORM_TENANT = new UUID(0L, 0L);

  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private TaskTemplateCatalogueBackfillRunner catalogueBackfillRunner;

  /** fabric_system, the role the purge runs as in production. */
  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  private record ReportRow(String step, String table, long rows) {}

  @Test
  @DisplayName("Purges one tenant completely and leaves every other tenant untouched")
  void purgeRemovesOnlyTheTargetTenant() {
    PlaygroundSourceFixture.ensureWithCatalogue(owner(), catalogueBackfillRunner);
    Tenant victim = tenantClonerService.cloneTemplateToPlayground();
    Tenant bystander = tenantClonerService.cloneTemplateToPlayground();

    UUID orphanIdentity = insertIdentity();
    insertMembership(orphanIdentity, victim.getId(), insertUser(victim.getId()));
    UUID sharedIdentity = insertIdentity();
    insertMembership(sharedIdentity, victim.getId(), insertUser(victim.getId()));
    insertMembership(sharedIdentity, bystander.getId(), insertUser(bystander.getId()));

    String leadUid = "LEAD-PURGE-" + shortId();
    owner()
        .update(
            "INSERT INTO common_company.common_lead (uid, company_name, organization_type,"
                + " first_name, last_name, work_email, signup_intent, trial_tenant_id) VALUES (?,"
                + " 'Purge Lead Co', 'VERTICAL_MILL', 'Purge', 'Lead', ?, 'PLAYGROUND', ?)",
            leadUid,
            "purge-lead-" + shortId().toLowerCase(Locale.ROOT) + "@example.test",
            victim.getId());

    Map<String, Long> victimBefore = settledRowsByTable(victim.getId());
    Map<String, Long> bystanderBefore = settledRowsByTable(bystander.getId());
    assertThat(victimBefore).as("the clone gave the tenant data to purge").isNotEmpty();

    List<ReportRow> dryRun = purge(victim.getId(), victim.getName(), false);
    assertThat(perTable(dryRun, "would delete")).isEqualTo(victimBefore);
    assertThat(identityRow(dryRun).rows()).as("only the orphan identity goes").isEqualTo(1);
    assertThat(rowsByTable(victim.getId())).as("a dry run deletes nothing").isEqualTo(victimBefore);

    assertThatThrownBy(() -> purge(victim.getId(), victim.getName(), true))
        .rootCause()
        .hasMessageContaining("still active");
    deactivate(victim.getId());

    List<ReportRow> applied = purge(victim.getId(), victim.getName(), true);
    assertThat(perTable(applied, "deleted")).isEqualTo(victimBefore);
    assertThat(identityRow(applied).rows()).isEqualTo(1);

    assertThat(rowsByTable(victim.getId())).isEmpty();
    assertThat(tenantExists(victim.getId())).isFalse();
    assertThat(rowsByTable(bystander.getId())).isEqualTo(bystanderBefore);
    assertThat(tenantExists(bystander.getId())).isTrue();

    String identityById = "SELECT count(*) FROM common_auth.login_identity WHERE id = ?";
    assertThat(count(identityById, orphanIdentity)).isZero();
    assertThat(count(identityById, sharedIdentity)).isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM common_auth.membership WHERE login_identity_id = ?",
                sharedIdentity))
        .as("the identity keeps its other tenant")
        .isEqualTo(1);
    assertThat(
            systemJdbc.queryForObject(
                "SELECT trial_tenant_id IS NULL FROM common_company.common_lead WHERE uid = ?",
                Boolean.class,
                leadUid))
        .as("the lead is retained and loses its trial link")
        .isTrue();

    deactivate(bystander.getId());
    purge(bystander.getId(), bystander.getName(), true);
    owner().update("DELETE FROM common_company.common_lead WHERE uid = ?", leadUid);
  }

  @Test
  @DisplayName("Refuses the purge while another tenant references the tenant's rows")
  void crossTenantReferenceBlocksThePurge() {
    PlaygroundSourceFixture.ensureWithCatalogue(owner(), catalogueBackfillRunner);
    Tenant victim = tenantClonerService.cloneTemplateToPlayground();
    Tenant other = tenantClonerService.cloneTemplateToPlayground();

    UUID victimOrganization = insertOrganization(victim.getId());
    UUID strayDepartment = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_company.common_department (id, tenant_id, uid, organization_id,"
                + " department_name, department_code, department_group, is_system_department,"
                + " is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'Stray Dept',"
                + " ?, 'PRODUCTION', false, true, now(), now(), 0)",
            strayDepartment,
            other.getId(),
            UUID.randomUUID().toString(),
            victimOrganization,
            "DPT-" + shortId());
    deactivate(victim.getId());
    Map<String, Long> victimBefore = settledRowsByTable(victim.getId());
    Map<String, Long> otherBefore = settledRowsByTable(other.getId());

    List<ReportRow> dryRun = purge(victim.getId(), victim.getName(), false);
    assertThat(dryRun)
        .anySatisfy(
            row -> {
              assertThat(row.step()).startsWith("blocked: another tenant");
              assertThat(row.table()).contains("common_department");
              assertThat(row.rows()).isEqualTo(1);
            });
    assertThat(dryRun.getLast().step()).as("the summary says blocked").isEqualTo("blocked");

    assertThatThrownBy(() -> purge(victim.getId(), victim.getName(), true))
        .rootCause()
        .hasMessageContaining("nothing was deleted");
    assertThat(rowsByTable(victim.getId())).isEqualTo(victimBefore);
    assertThat(rowsByTable(other.getId())).isEqualTo(otherBefore);
    assertThat(
            count(
                "SELECT count(*) FROM common_company.common_department WHERE id = ?",
                strayDepartment))
        .isEqualTo(1);

    owner().update("DELETE FROM common_company.common_department WHERE id = ?", strayDepartment);
    assertThat(purge(victim.getId(), victim.getName(), true)).isNotEmpty();
    deactivate(other.getId());
    purge(other.getId(), other.getName(), true);
    assertThat(tenantExists(victim.getId())).isFalse();
    assertThat(tenantExists(other.getId())).isFalse();
  }

  @Test
  @DisplayName("Refuses templates, the platform tenant, a wrong name and an ungranted caller")
  void guardsRefuseWhatMustNotBeDeleted() {
    UUID templateId = PlaygroundSourceFixture.ensureWithCatalogue(owner(), catalogueBackfillRunner);
    String templateName =
        systemJdbc.queryForObject(
            "SELECT name FROM common_tenant.common_tenant WHERE id = ?", String.class, templateId);
    Tenant victim = tenantClonerService.cloneTemplateToPlayground();
    deactivate(victim.getId());
    Map<String, Long> victimBefore = settledRowsByTable(victim.getId());

    assertThatThrownBy(() -> purge(templateId, templateName, true))
        .rootCause()
        .hasMessageContaining("TEMPLATE");
    assertThatThrownBy(() -> purge(PLATFORM_TENANT, "Platform (System)", false))
        .rootCause()
        .hasMessageContaining("platform tenant");
    assertThatThrownBy(() -> purge(victim.getId(), victim.getName() + " ", true))
        .rootCause()
        .hasMessageContaining("confirmation does not match");
    assertThatThrownBy(() -> purge(UUID.randomUUID(), "anything", false))
        .rootCause()
        .hasMessageContaining("does not exist");

    // A role that can reach the schema but was never granted the function.
    JdbcTemplate owner = owner();
    owner.execute(
        "DO $$ BEGIN CREATE ROLE purge_probe NOLOGIN;"
            + " EXCEPTION WHEN duplicate_object THEN NULL; END $$");
    owner.execute("GRANT USAGE ON SCHEMA common_tenant TO purge_probe");
    try {
      assertThatThrownBy(
              () ->
                  owner.execute(
                      (ConnectionCallback<Void>)
                          connection -> {
                            try (Statement statement = connection.createStatement()) {
                              statement.execute("SET ROLE purge_probe");
                              try {
                                statement.executeQuery(
                                    "SELECT * FROM common_tenant.purge_tenant('"
                                        + victim.getId()
                                        + "', 'x', false)");
                              } finally {
                                statement.execute("RESET ROLE");
                              }
                            }
                            return null;
                          }))
          .rootCause()
          .hasMessageContaining("permission denied for function purge_tenant");
    } finally {
      owner.execute("DROP OWNED BY purge_probe");
      owner.execute("DROP ROLE purge_probe");
    }

    assertThat(rowsByTable(victim.getId())).isEqualTo(victimBefore);
    assertThat(tenantExists(victim.getId())).isTrue();
    assertThat(tenantExists(templateId)).isTrue();

    purge(victim.getId(), victim.getName(), true);
  }

  /** The container's owner (a superuser), for fixtures the application roles may not write. */
  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  private List<ReportRow> purge(UUID tenantId, String confirmName, boolean apply) {
    return systemJdbc.query(
        PURGE,
        (ResultSet rs, int rowNum) ->
            new ReportRow(
                rs.getString("step"), rs.getString("table_name"), rs.getLong("row_count")),
        tenantId,
        confirmName,
        apply);
  }

  /** The per-table rows of a report; the identity and tenant summary rows carry " (". */
  private static Map<String, Long> perTable(List<ReportRow> report, String step) {
    Map<String, Long> rows = new TreeMap<>();
    for (ReportRow row : report) {
      assertThat(row.step()).as("unexpected report row %s", row).isEqualTo(step);
      if (!row.table().contains(" (")) {
        rows.put(row.table(), row.rows());
      }
    }
    return rows;
  }

  private static ReportRow identityRow(List<ReportRow> report) {
    return report.stream()
        .filter(row -> row.table().startsWith("common_auth.login_identity "))
        .findFirst()
        .orElseThrow();
  }

  /**
   * The tenant's rows once they stop changing: the clone's demo data may still be landing from
   * listeners that run after its commit, and a count taken mid-way would not be the one purged.
   */
  private Map<String, Long> settledRowsByTable(UUID tenantId) {
    Map<String, Long> previous = rowsByTable(tenantId);
    for (int attempt = 0; attempt < 60; attempt++) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      Map<String, Long> current = rowsByTable(tenantId);
      if (current.equals(previous)) {
        return current;
      }
      previous = current;
    }
    throw new AssertionError("the rows of tenant " + tenantId + " kept changing for 30 seconds");
  }

  /** The tenant's rows in every table with a tenant_id column, named as the function names them. */
  private Map<String, Long> rowsByTable(UUID tenantId) {
    List<String> tables =
        systemJdbc.queryForList(
            "SELECT format('%I.%I', n.nspname, c.relname) FROM pg_class c"
                + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                + " JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id'"
                + " AND NOT a.attisdropped"
                + " WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition"
                + " AND c.relpersistence <> 't'"
                + " AND n.nspname NOT IN ('pg_catalog', 'information_schema')",
            String.class);
    Map<String, Long> rows = new TreeMap<>();
    for (String table : tables) {
      long n = count("SELECT count(*) FROM " + table + " WHERE tenant_id = ?", tenantId);
      if (n > 0) {
        rows.put(table, n);
      }
    }
    return rows;
  }

  private boolean tenantExists(UUID tenantId) {
    return count("SELECT count(*) FROM common_tenant.common_tenant WHERE id = ?", tenantId) == 1;
  }

  private long count(String sql, Object... args) {
    Long n = systemJdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  private static void deactivate(UUID tenantId) {
    owner()
        .update(
            "UPDATE common_tenant.common_tenant SET is_active = false, updated_at = now()"
                + " WHERE id = ?",
            tenantId);
  }

  private static UUID insertOrganization(UUID tenantId) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id,"
                + " organization_type, is_active, created_at, updated_at, version) VALUES (?, ?,"
                + " ?, 'Purge Org', ?, 'VERTICAL_MILL', true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            String.format(
                Locale.ROOT,
                "%010d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 10_000_000_000L)));
    return id;
  }

  private static UUID insertUser(UUID tenantId) {
    UUID organizationId = insertOrganization(tenantId);
    UUID roleId = UUID.randomUUID();
    JdbcTemplate owner = owner();
    owner.update(
        "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code,"
            + " role_scope, is_system_role, is_active, created_at, updated_at, version) VALUES (?,"
            + " ?, ?, 'Purge Role', ?, 'INTERNAL', false, true, now(), now(), 0)",
        roleId,
        tenantId,
        UUID.randomUUID().toString(),
        "ROLE-" + shortId());
    UUID userId = UUID.randomUUID();
    owner.update(
        "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id,"
            + " first_name, last_name, user_type, is_active, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, ?, ?, 'Purge', 'Fixture', 'INTERNAL', true, now(), now(), 0)",
        userId,
        tenantId,
        UUID.randomUUID().toString(),
        organizationId,
        roleId);
    return userId;
  }

  private static UUID insertIdentity() {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_auth.login_identity (id, email, password_hash) VALUES (?, ?, 'x')",
            id,
            "purge-" + shortId().toLowerCase(Locale.ROOT) + "@example.test");
    return id;
  }

  private static void insertMembership(UUID identityId, UUID tenantId, UUID userId) {
    owner()
        .update(
            "INSERT INTO common_auth.membership (login_identity_id, tenant_id, user_id, status,"
                + " is_default) VALUES (?, ?, ?, 'ACTIVE', false)",
            identityId,
            tenantId,
            userId);
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
