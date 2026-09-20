package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fabricmanagement.flowboard.task.app.DomainTaskAction;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OrderCoverLifecycleIT extends OrderCoverIntegrationSupport {
  @Autowired TaskRepository tasks;
  @Autowired List<DomainTaskAction> actions;

  @Test
  void mountedLegacyStatusAndDeleteRoutesRefusePersistedGovernedTask() throws Exception {
    var cover = governed(1);
    long version = taskVersion(cover);
    mvc.perform(
            put("/api/v1/flowboard/tasks/{id}/status", cover.taskId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"CANCELLED\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FLOWBOARD_GOVERNED_STATUS_BYPASS"));
    mvc.perform(delete("/api/v1/flowboard/tasks/{id}", cover.taskId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FLOWBOARD_GOVERNED_STATUS_BYPASS"));
    assertThat(taskVersion(cover)).isEqualTo(version);
    assertThat(
            jdbc.queryForObject(
                "select closed_at from flowboard.task where id=?",
                java.sql.Timestamp.class,
                cover.taskId()))
        .isNull();
    assertNoSettlement();
  }

  @Test
  void partialThenFinalSettlementReturnsCommittedVersionAndShrinksPersistedScope() {
    var cover = governed(2);
    long originalVersion = taskVersion(cover);
    var firstCommand =
        request(
            cover,
            refresh(cover),
            List.of(cover.lineIds().getFirst()),
            UUID.randomUUID(),
            "Produce first line");
    var partial = settle(cover, firstCommand);
    assertThat(partial.taskState()).isNotIn(TaskStatus.DONE, TaskStatus.CANCELLED);
    assertThat(partial.remainingLineIds()).containsExactly(cover.lineIds().getLast());
    assertThat(partial.taskVersion()).isGreaterThan(originalVersion).isEqualTo(taskVersion(cover));
    assertThat(
            jdbc.queryForObject(
                "select closed_at from flowboard.task where id=?",
                java.sql.Timestamp.class,
                cover.taskId()))
        .isNull();
    assertThat(
            jdbc.queryForList(
                "select subject_id from flowboard.task_affected_subject where task_id=? and"
                    + " is_active",
                UUID.class,
                cover.taskId()))
        .containsExactly(cover.lineIds().getLast());
    assertThat(
            jdbc.queryForObject(
                "select state from sales_ord.order_cover_case where id=?",
                String.class,
                cover.caseId()))
        .isEqualTo("PARTIALLY_SETTLED");

    var finalCommand =
        request(
            cover,
            refresh(cover),
            partial.remainingLineIds(),
            UUID.randomUUID(),
            "Produce final line");
    var complete = settle(cover, finalCommand);
    assertThat(complete.taskState()).isEqualTo(TaskStatus.DONE);
    assertThat(complete.remainingLineIds()).isEmpty();
    assertThat(complete.taskVersion())
        .isGreaterThan(partial.taskVersion())
        .isEqualTo(taskVersion(cover));
    var closed =
        jdbc.queryForObject(
            "select closed_at from flowboard.task where id=?",
            java.sql.Timestamp.class,
            cover.taskId());
    assertThat(closed).isNotNull();
    var replay = settle(cover, firstCommand);
    assertThat(replay.result()).isEqualTo(partial.result());
    assertThat(replay.taskState()).isEqualTo(TaskStatus.DONE);
    assertThat(replay.remainingLineIds()).isEmpty();
    assertThat(replay.taskVersion()).isEqualTo(complete.taskVersion());
    settle(cover, finalCommand);
    assertThat(
            jdbc.queryForObject(
                "select closed_at from flowboard.task where id=?",
                java.sql.Timestamp.class,
                cover.taskId()))
        .isEqualTo(closed);
    assertThat(count("sales_ord.order_cover_result")).isEqualTo(2);
    assertThat(count("production.prod_work_order")).isEqualTo(2);
  }

  @Test
  void cancellationAfterPartialSettlementKeepsReceiptAndCancelsWorkOrderThroughExistingFlow() {
    var cover = governed(2);
    var partial =
        settle(
            cover,
            request(
                cover,
                refresh(cover),
                List.of(cover.lineIds().getFirst()),
                UUID.randomUUID(),
                "Produce first line"));
    var before = receiptRows();
    sales.cancelOrder(cover.orderId(), actor.getId());
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(
                      jdbc.queryForObject(
                          "select status from production.prod_work_order where tenant_id=?",
                          String.class,
                          tenant))
                  .isEqualTo("CANCELLED");
              assertThat(
                      jdbc.queryForObject(
                          "select closed_at from flowboard.task where id=?",
                          java.sql.Timestamp.class,
                          cover.taskId()))
                  .isNotNull();
              assertThat(
                      jdbc.queryForObject(
                          "select state from sales_ord.order_cover_case where id=?",
                          String.class,
                          cover.caseId()))
                  .isEqualTo("CANCELLED");
            });
    assertThat(receiptRows()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flowboard.task_transition_attempt where tenant_id=? and"
                    + " action_key='CANCEL_ORDER_COVER' and outcome='ACCEPTED'",
                Integer.class,
                tenant))
        .isEqualTo(1);
    assertThat(count("production.prod_work_order")).isEqualTo(1);
    assertThat(partial.result().lines()).hasSize(1);
  }

  @Test
  void laterProfileAndEvidenceDoNotRewriteDraftBindingOrHistoricalReceipt() {
    var cover = governed(2);
    var initial = refresh(cover);
    var partial =
        settle(
            cover,
            request(
                cover,
                initial,
                List.of(cover.lineIds().getFirst()),
                UUID.randomUUID(),
                "Pinned customer instruction"));
    UUID workOrderId = partial.result().lines().getFirst().downstream().getFirst().id();
    var receiptBefore = receiptRows();
    var bindingBefore =
        jdbc.queryForMap(
            "select"
                + " requirement_profile_id,requirement_profile_version,requirement_profile_snapshot::text"
                + " from production.prod_work_order where id=?",
            workOrderId);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          attachProfile(line, line.getRequirementProfileId(), 2, true);
          return null;
        });
    var refreshed = refresh(cover);
    assertThat(refreshed.revision()).isGreaterThan(initial.revision());
    assertThat(refreshed.inputFingerprint()).isNotEqualTo(initial.inputFingerprint());
    assertThat(receiptRows()).isEqualTo(receiptBefore);
    assertThat(
            jdbc.queryForMap(
                "select"
                    + " requirement_profile_id,requirement_profile_version,requirement_profile_snapshot::text"
                    + " from production.prod_work_order where id=?",
                workOrderId))
        .isEqualTo(bindingBefore);
  }

  @Test
  void applicationRoleCanReadOwnRowsButCannotReadAnotherTenantsCaseEvidenceOrReceipt()
      throws Exception {
    var cover = governed(1);
    settle(
        cover,
        request(cover, refresh(cover), cover.lineIds(), UUID.randomUUID(), "Produce to order"));
    List<String> tables =
        List.of(
            "order_cover_case",
            "order_cover_case_line",
            "order_cover_evidence",
            "order_cover_result",
            "order_cover_line_result");
    // A fresh NOSUPERUSER/NOBYPASSRLS role models the app's SELECT entitlement. Role creation and
    // grants are transactional and rolled back, so the shared container's roles remain untouched.
    String role = "oc_rls_" + suffix;
    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute("CREATE ROLE " + role + " NOSUPERUSER NOBYPASSRLS");
        sql.execute("GRANT USAGE ON SCHEMA sales_ord TO " + role);
        for (String table : tables)
          sql.execute("GRANT SELECT ON sales_ord." + table + " TO " + role);
        sql.execute("SET LOCAL ROLE " + role);
        setTenant(connection, tenant);
        for (String table : tables) assertThat(readCount(connection, table)).isPositive();
        setTenant(connection, UUID.randomUUID());
        for (String table : tables) assertThat(readCount(connection, table)).as(table).isZero();
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void registeredOrderCoverActionsCannotProduceAutomaticStockOutcome() {
    var cover = governed(1);
    var task = tasks.findById(cover.taskId()).orElseThrow();
    assertThat(
            actions.stream()
                .filter(action -> action.supports(task))
                .map(DomainTaskAction::actionKey)
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("CONFIRM_PRODUCTION_COVER", "CANCEL_ORDER_COVER");
    var result =
        settle(
            cover,
            request(
                cover,
                refresh(cover),
                cover.lineIds(),
                UUID.randomUUID(),
                "Human production decision"));
    assertThat(result.result().policyKey()).isNull();
    assertThat(
            jdbc.queryForList(
                "select outcome from sales_ord.order_cover_line_result where tenant_id=?",
                String.class,
                tenant))
        .containsOnly("MAKE_TO_ORDER");
  }

  private List<Map<String, Object>> receiptRows() {
    return jdbc.queryForList(
        """
        select r.id,r.rationale,r.evidence_id,r.evidence_revision,l.line_id,l.quantity,l.unit,
               l.suitability_at_decision,l.evidence_sources::text,l.work_order_id
        from sales_ord.order_cover_result r join sales_ord.order_cover_line_result l on l.result_id=r.id
        where r.tenant_id=? order by r.id,l.line_id
        """,
        tenant);
  }

  private static void setTenant(Connection connection, UUID tenantId) throws Exception {
    try (var statement =
        connection.prepareStatement("select set_config('app.current_tenant',?,true)")) {
      statement.setString(1, tenantId.toString());
      statement.execute();
    }
  }

  private static int readCount(Connection connection, String table) throws Exception {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("select count(*) from sales_ord." + table)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  @Test
  void everyNewOrderCoverTableForcesTenantRls() {
    List<String> tables =
        jdbc.queryForList(
            "select c.relname from pg_class c join pg_namespace n on n.oid=c.relnamespace where"
                + " n.nspname='sales_ord' and c.relname in"
                + " ('order_cover_activation','order_cover_case','order_cover_case_line','order_cover_result','order_cover_line_result')"
                + " and c.relrowsecurity and c.relforcerowsecurity order by c.relname",
            String.class);
    assertThat(tables)
        .containsExactly(
            "order_cover_activation",
            "order_cover_case",
            "order_cover_case_line",
            "order_cover_line_result",
            "order_cover_result");
  }

  @Test
  void activationRowsCarryTheStandardAuditColumnsUsedByNativeInserts() {
    List<String> columns =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_schema='sales_ord' and"
                + " table_name='order_cover_activation' and column_name in "
                + "('id','uid','created_at','created_by','updated_at','updated_by','is_active','deleted_at','version')",
            String.class);
    assertThat(columns)
        .containsExactlyInAnyOrder(
            "id",
            "uid",
            "created_at",
            "created_by",
            "updated_at",
            "updated_by",
            "is_active",
            "deleted_at",
            "version");
  }
}
