package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.flowboard.generator.app.catalogue.PlaygroundCatalogueWriter;
import com.fabricmanagement.flowboard.generator.app.catalogue.TaskTemplateCatalogueBackfillRunner;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * TASK-TEMPLATE-TENANCY-1 §7.4 / plan §3 (T-PLAY, T-RB3): the playground clone copies the catalogue
 * from the playground source inside its own system transaction, keeps each row's key and deletion
 * state, mints fresh uids, and rolls back with the clone.
 */
class PlaygroundCatalogueCloneIT extends AbstractIntegrationTest {

  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private TaskTemplateCatalogueBackfillRunner runner;
  @Autowired private JdbcTemplate jdbc;

  @MockitoSpyBean private PlaygroundCatalogueWriter playgroundWriter;

  /**
   * Demo data is seeded after the clone has committed, outside the transaction under test, and
   * depends on exchange rates for dates relative to today (it failed with "Exchange rate required:
   * USD → GBP" on 2026-09-26). It is irrelevant to the catalogue copy, so it is stubbed here.
   */
  @MockitoBean private DemoTransactionSeeder demoTransactionSeeder;

  @Test
  void cloneCarriesTheSourceCatalogueWithFreshUidsAndSystemProvenance() {
    UUID nexus = playgroundSourceWithCatalogue();
    List<String> sourceUids =
        jdbc.queryForList(
            "SELECT uid FROM flowboard.task_template WHERE tenant_id = ? AND catalog_key IS NOT NULL",
            String.class,
            nexus);

    Tenant playground = tenantClonerService.cloneTemplateToPlayground();

    String playgroundUid =
        jdbc.queryForObject(
            "SELECT uid FROM common_tenant.common_tenant WHERE id = ?",
            String.class,
            playground.getId());
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT catalog_key, uid, created_by FROM flowboard.task_template WHERE tenant_id = ?",
            playground.getId());
    assertThat(rows)
        .extracting(row -> (String) row.get("catalog_key"))
        .containsExactlyInAnyOrder(
            Arrays.stream(TaskTemplateCatalogue.values())
                .map(TaskTemplateCatalogue::key)
                .toArray(String[]::new));
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat((String) row.get("uid"))
                  .startsWith(playgroundUid + "-TMPL-")
                  .isNotIn(sourceUids);
              assertThat(row.get("created_by")).isEqualTo(SystemUser.ID);
            });
  }

  @Test
  void aTemplateDeletedInTheSourceArrivesDeletedAndTheBackfillDoesNotReviveIt() {
    UUID nexus = playgroundSourceWithCatalogue();
    String key = TaskTemplateCatalogue.QUOTE_SEND_REQUESTED__APPROVAL.key();
    jdbc.update(
        "UPDATE flowboard.task_template SET is_active = FALSE, deleted_at = now()"
            + " WHERE tenant_id = ? AND catalog_key = ?",
        nexus,
        key);
    try {
      Tenant playground = tenantClonerService.cloneTemplateToPlayground();

      assertThat(keyedRows(playground.getId(), key))
          .singleElement()
          .satisfies(
              row -> {
                assertThat(row.get("is_active")).isEqualTo(false);
                assertThat(row.get("deleted_at")).isNotNull();
              });

      runner.run();

      assertThat(keyedRows(playground.getId(), key))
          .singleElement()
          .satisfies(row -> assertThat(row.get("deleted_at")).isNotNull());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = ?"
                      + " AND event_type = 'QuoteSendRequested' AND is_active",
                  Integer.class,
                  playground.getId()))
          .isZero();
    } finally {
      jdbc.update(
          "UPDATE flowboard.task_template SET is_active = TRUE, deleted_at = NULL"
              + " WHERE tenant_id = ? AND catalog_key = ?",
          nexus,
          key);
    }
  }

  @Test
  void aCatalogueFailureAfterWritesRollsBackTheWholeClone() {
    playgroundSourceWithCatalogue();
    int playgroundsBefore = playgroundTenants();
    int playgroundTemplatesBefore = playgroundTemplates();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("injected after catalogue writes");
            })
        .when(playgroundWriter)
        .copyFromSource(any(), any(), any());

    assertThatThrownBy(() -> tenantClonerService.cloneTemplateToPlayground())
        .hasStackTraceContaining("injected after catalogue writes");

    assertThat(playgroundTenants()).isEqualTo(playgroundsBefore);
    assertThat(playgroundTemplates()).isEqualTo(playgroundTemplatesBefore);
  }

  @Test
  void aSourceMissingACatalogueKeyFailsTheCloneAndLeavesNothingBehind() {
    UUID nexus = playgroundSourceWithCatalogue();
    String key = TaskTemplateCatalogue.GOODS_RECEIPT_CONFIRMED__WAREHOUSE.key();
    UUID row =
        jdbc.queryForObject(
            "SELECT id FROM flowboard.task_template WHERE tenant_id = ? AND catalog_key = ?",
            UUID.class,
            nexus,
            key);
    int playgroundsBefore = playgroundTenants();
    int playgroundTemplatesBefore = playgroundTemplates();
    jdbc.update("UPDATE flowboard.task_template SET catalog_key = NULL WHERE id = ?", row);
    try {
      assertThatThrownBy(() -> tenantClonerService.cloneTemplateToPlayground())
          .hasStackTraceContaining("lacks " + key);

      assertThat(playgroundTenants()).isEqualTo(playgroundsBefore);
      assertThat(playgroundTemplates()).isEqualTo(playgroundTemplatesBefore);
    } finally {
      jdbc.update("UPDATE flowboard.task_template SET catalog_key = ? WHERE id = ?", key, row);
    }
  }

  /** The playground source, with its catalogue distributed by the startup runner. */
  private UUID playgroundSourceWithCatalogue() {
    List<UUID> existing =
        jdbc.queryForList(
            "SELECT id FROM common_tenant.common_tenant WHERE slug = ?",
            UUID.class,
            TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    UUID nexus;
    if (existing.isEmpty()) {
      nexus = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
              + " status, settings, is_active, created_at, updated_at, version)"
              + " VALUES (?, ?, ?, 'Nexus Fabrics', 'TEMPLATE', 'test@example.com', 'ACTIVE', '{}',"
              + " true, now(), now(), 0)",
          nexus,
          UUID.randomUUID().toString(),
          TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    } else {
      nexus = existing.getFirst();
    }
    runner.run();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = ?"
                    + " AND catalog_key IS NOT NULL",
                Integer.class,
                nexus))
        .as("precondition: the playground source holds the catalogue")
        .isEqualTo(TaskTemplateCatalogue.values().length);
    return nexus;
  }

  private List<Map<String, Object>> keyedRows(UUID tenant, String key) {
    return jdbc.queryForList(
        "SELECT is_active, deleted_at FROM flowboard.task_template"
            + " WHERE tenant_id = ? AND catalog_key = ?",
        tenant,
        key);
  }

  private int playgroundTenants() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_tenant.common_tenant WHERE type = 'PLAYGROUND'",
        Integer.class);
  }

  private int playgroundTemplates() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM flowboard.task_template t"
            + " WHERE NOT EXISTS (SELECT 1 FROM common_tenant.common_tenant c WHERE c.id = t.tenant_id)"
            + " OR t.tenant_id IN (SELECT id FROM common_tenant.common_tenant WHERE type = 'PLAYGROUND')",
        Integer.class);
  }
}
