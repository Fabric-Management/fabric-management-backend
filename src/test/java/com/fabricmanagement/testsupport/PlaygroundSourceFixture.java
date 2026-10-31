package com.fabricmanagement.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.generator.app.catalogue.TaskTemplateCatalogueBackfillRunner;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The playground source tenant ({@code nexus-fabrics}) as the playground clone needs it.
 *
 * <p>The clone copies the task-template catalogue from the source and refuses a source that lacks
 * an entry ("lacks &lt;key&gt;", surfacing as 409 through the playground endpoints). Only the
 * start-up runner distributes the catalogue, so a source inserted by a test after the Spring
 * context started holds none until the runner is run again. Every test that clones a playground
 * goes through here, so none of them depends on test order or on an earlier test class having
 * installed the catalogue (TASK-TEMPLATE-TENANCY-1).
 */
public final class PlaygroundSourceFixture {

  private PlaygroundSourceFixture() {}

  /**
   * Finds the playground source, inserting a bare one when absent, gives it a company organization
   * when it has none, and distributes the task-template catalogue to it.
   *
   * @return the source tenant's id
   */
  public static UUID ensureWithCatalogue(
      JdbcTemplate jdbc, TaskTemplateCatalogueBackfillRunner catalogueRunner) {
    List<UUID> existing =
        jdbc.queryForList(
            "SELECT id FROM common_tenant.common_tenant WHERE slug = ? LIMIT 1",
            UUID.class,
            TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    UUID source;
    if (existing.isEmpty()) {
      source = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
              + " status, settings, is_active, created_at, updated_at, version)"
              + " VALUES (?, ?, ?, 'Nexus Fabrics', 'TEMPLATE', 'test@example.com', 'ACTIVE', '{}',"
              + " true, now(), now(), 0)",
          source,
          UUID.randomUUID().toString(),
          TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    } else {
      source = existing.getFirst();
    }
    ensureCompanyOrganization(jdbc, source);
    catalogueRunner.run();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = ?"
                    + " AND catalog_key IS NOT NULL",
                Integer.class,
                source))
        .as("precondition: the playground source holds the task-template catalogue")
        .isEqualTo(TaskTemplateCatalogue.values().length);
    return source;
  }

  /**
   * Gives the source the company organization the real one has. Without it a clone has no root
   * organization, so the finance demo seeding cannot set USD as the reporting currency, falls back
   * to the platform default and converts its EUR invoices to GBP through the ECB, which publishes
   * no rate at weekends: the clone then failed or passed by the day of the week and by whether an
   * earlier test had added an organization.
   */
  private static void ensureCompanyOrganization(JdbcTemplate jdbc, UUID source) {
    Long roots =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_company.common_organization WHERE tenant_id = ?"
                + " AND parent_organization_id IS NULL AND organization_type <> 'EXTERNAL_PARTNER'",
            Long.class,
            source);
    if (roots != null && roots > 0) {
      return;
    }
    jdbc.update(
        "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id,"
            + " organization_type, is_active, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, 'Nexus Fabrics', ?, 'VERTICAL_MILL', true, now(), now(), 0)",
        UUID.randomUUID(),
        source,
        UUID.randomUUID().toString(),
        "NEXUS-" + UUID.randomUUID().toString().substring(0, 8));
  }
}
