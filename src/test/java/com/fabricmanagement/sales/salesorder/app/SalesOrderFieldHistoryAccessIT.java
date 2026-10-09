package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Who may read an order's field history, and which requests are refused (CEDIT-09 §3.1–§3.2; H02–
 * H04, H08): literal sales read, fresh object access on every page, nothing opened or written by a
 * read, and refusals that reveal no history.
 */
class SalesOrderFieldHistoryAccessIT extends SalesOrderFieldHistoryItSupport {

  // ── H02: read access only, any status, nothing opened ─────────────────────

  @Test
  @DisplayName(
      "H02: a read-only person reads a confirmed order's history; no base, session or lease opens")
  void readOnlyPersonOnAConfirmedOrder() throws Exception {
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Urgent")));
    jdbc.update("UPDATE sales_ord.sales_order SET status = 'CONFIRMED' WHERE id = ?", orderId);
    readOnlyUsers.add(actorC.id());
    long version = orderVersion();
    int bases = count("SELECT count(*) FROM sales_ord.order_edit_base WHERE sales_order_id = ?");
    int sessions =
        count("SELECT count(*) FROM common_infrastructure.live_edit_session WHERE tenant_id = ?");
    int leases =
        count("SELECT count(*) FROM common_infrastructure.live_edit_lease WHERE tenant_id = ?");
    int receipts = receipts();

    Answer answer = historyHttp(actorC, orderId, null, null);

    assertThat(answer.status()).as(answer.text()).isEqualTo(200);
    assertThat(answer.body().path("data").path("items").size()).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(count("SELECT count(*) FROM sales_ord.order_edit_base WHERE sales_order_id = ?"))
        .isEqualTo(bases);
    assertThat(
            count(
                "SELECT count(*) FROM common_infrastructure.live_edit_session WHERE tenant_id = ?"))
        .isEqualTo(sessions);
    assertThat(
            count("SELECT count(*) FROM common_infrastructure.live_edit_lease WHERE tenant_id = ?"))
        .isEqualTo(leases);
    assertThat(receipts()).isEqualTo(receipts);
  }

  // ── H03: 401, 403, 404 and a fresh decision on every page ─────────────────

  @Test
  @DisplayName("H03: no token 401; no sales read 403; out of scope, other tenant, unknown 404")
  void refusals() throws Exception {
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Urgent")));

    assertThat(historyHttpWithoutToken(orderId).status()).isEqualTo(401);

    revoke(actorC);
    assertThat(historyHttp(actorC, orderId, null, null).status()).isEqualTo(403);

    scopes.put(actorB.id(), DataScope.OWN);
    assertNotFound(historyHttp(actorB, orderId, null, null));

    OtherTenant other = otherTenantWithOrder();
    assertNotFound(historyHttp(other.actor(), orderId, null, null));
    assertNotFound(historyHttp(actorA, other.orderId(), null, null));
    assertNotFound(historyHttp(actorA, UUID.randomUUID(), null, null));

    jdbc.update("UPDATE sales_ord.sales_order SET is_active = false WHERE id = ?", orderId);
    assertNotFound(historyHttp(actorA, orderId, null, null));
  }

  @Test
  @DisplayName("H03: every cursor page decides access again; a stale cached grant is not enough")
  void everyPageIsCheckedFresh() throws Exception {
    for (String notes : List.of("one", "two", "three")) {
      saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set(notes)));
    }
    JsonNode first = page(actorB, 1, null);
    String cursor = first.path("nextCursor").asText();

    scopes.put(actorB.id(), DataScope.OWN);
    assertNotFound(historyHttp(actorB, orderId, 1, cursor));

    // The endpoint check passes on a stale cached grant; the fresh scope still refuses.
    cachedScopes.put(actorB.id(), Optional.of(DataScope.GLOBAL));
    scopes.remove(actorB.id());
    assertNotFound(historyHttp(actorB, orderId, 1, cursor));

    scopes.put(actorB.id(), DataScope.GLOBAL);
    cachedScopes.clear();
    assertThat(historyHttp(actorB, orderId, 1, cursor).status()).isEqualTo(200);
  }

  @Test
  @DisplayName("H03: RLS keeps another tenant's session from the history rows")
  void rowLevelSecurity() throws Exception {
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Urgent")));
    OtherTenant other = otherTenantWithOrder();
    String sql = "SELECT count(*) FROM sales_ord.order_field_change WHERE sales_order_id = ?";

    try (Connection own = appConnection(tenantId);
        Connection foreign = appConnection(other.tenantId())) {
      assertThat(countOn(own, sql)).isEqualTo(1);
      assertThat(countOn(foreign, sql)).isZero();
    }
  }

  // ── H04: inactive person, suspended tenant ────────────────────────────────

  @Test
  @DisplayName("H04: an inactive person or a suspended tenant is refused, even with a cursor")
  void inactivePersonAndSuspendedTenant() {
    for (String notes : List.of("one", "two")) {
      saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set(notes)));
    }
    SalesOrderFieldHistoryDtos.Page first =
        (SalesOrderFieldHistoryDtos.Page)
            as(actorB, () -> historyService.page(orderId, 1, null, actorB.id()));
    String cursor = first.nextCursor();
    assertThat(cursor).isNotNull();

    jdbc.update("UPDATE common_user.common_user SET is_active = false WHERE id = ?", actorB.id());
    assertThat(as(actorB, () -> historyService.page(orderId, 1, cursor, actorB.id())))
        .isInstanceOf(NotFoundException.class);
    assertThat(as(actorB, () -> historyService.page(orderId, 1, null, actorB.id())))
        .isInstanceOf(NotFoundException.class);
    jdbc.update("UPDATE common_user.common_user SET is_active = true WHERE id = ?", actorB.id());

    jdbc.update(
        "UPDATE common_tenant.common_tenant SET status = 'SUSPENDED' WHERE id = ?", tenantId);
    try {
      assertThat(as(actorB, () -> historyService.page(orderId, 1, cursor, actorB.id())))
          .isInstanceOf(NotFoundException.class);
    } finally {
      jdbc.update(
          "UPDATE common_tenant.common_tenant SET status = 'ACTIVE' WHERE id = ?", tenantId);
    }
  }

  // ── H08: refused queries ──────────────────────────────────────────────────

  @Test
  @DisplayName("H08: limit 0/101, a malformed, too long or foreign cursor: 422 naming the field")
  void refusedQueries() throws Exception {
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("one")));
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("two")));
    String cursor = page(actorB, 1, null).path("nextCursor").asText();

    assertValidation(historyHttp(actorB, orderId, 0, null), "limit");
    assertValidation(historyHttp(actorB, orderId, MAX_LIMIT + 1, null), "limit");
    assertValidation(historyHttp(actorB, orderId, null, "not-a-cursor"), "cursor");
    assertValidation(
        historyHttp(
            actorB, orderId, null, "A".repeat(SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH + 1)),
        "cursor");

    String otherOrder =
        new SalesOrderFieldHistoryCursor(tenantId, UUID.randomUUID(), 2L, 2L, UUID.randomUUID())
            .encode();
    assertValidation(historyHttp(actorB, orderId, null, otherOrder), "cursor");

    OtherTenant other = otherTenantWithOrder();
    assertValidation(historyHttp(other.actor(), other.orderId(), null, cursor), "cursor");

    long version = orderVersion();
    String beyond =
        new SalesOrderFieldHistoryCursor(
                tenantId, orderId, version + 5, version + 5, UUID.randomUUID())
            .encode();
    assertValidation(historyHttp(actorB, orderId, null, beyond), "cursor");

    Answer notANumber = historyHttpRaw(actorB, orderId, "limit=ten");
    assertThat(notANumber.status()).isEqualTo(400);
    assertThat(notANumber.text()).doesNotContain("\"items\"");

    assertThat(historyHttp(actorB, orderId, 1, cursor).status()).isEqualTo(200);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static void assertNotFound(Answer answer) {
    assertThat(answer.status()).as(answer.text()).isEqualTo(404);
    assertThat(answer.text()).doesNotContain("\"items\"", "Urgent");
  }

  private static void assertValidation(Answer answer, String field) {
    assertThat(answer.status()).as(answer.text()).isEqualTo(422);
    assertThat(answer.body().path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(answer.body().path("errors").has(field)).as(answer.text()).isTrue();
    assertThat(answer.text()).doesNotContain("\"items\"", "\"one\"", "\"two\"");
  }

  private int count(String sql) {
    Object key = sql.contains("tenant_id") ? tenantId : orderId;
    Integer value = jdbc.queryForObject(sql, Integer.class, key);
    return value == null ? 0 : value;
  }

  private int countOn(Connection connection, String sql) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, orderId);
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }
}
