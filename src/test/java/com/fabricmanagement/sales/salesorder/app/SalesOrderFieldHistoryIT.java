package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The field history read end to end (CEDIT-09 §5): saves through the real safe edit, then the real
 * HTTP endpoint on PostgreSQL. Covers what was saved (H01, H09–H13, H16, H17), what was not (H14),
 * stable pages (H05–H07) and retention (H15). Access and refusals: {@link
 * SalesOrderFieldHistoryAccessIT}.
 */
class SalesOrderFieldHistoryIT extends SalesOrderFieldHistoryItSupport {

  @Autowired private SalesOrderEditRetentionJob retention;

  // ── H01: who, when, what, in which save ───────────────────────────────────

  @Test
  @DisplayName("H01: two people save different fields; each entry has its own actor and save")
  void twoActorsTwoSaves() {
    UUID baseA = open(actorA).baseId();
    UUID baseB = open(actorB).baseId();
    long v = orderVersion();
    UUID opA = UUID.randomUUID();
    UUID opB = UUID.randomUUID();
    saved(actorA, body(opA, baseA, "notes", set("Urgent")));
    saved(actorB, body(opB, baseB, "paymentTerms", clear()));

    JsonNode page = page(actorC, null, null);

    assertThat(page.path("snapshotVersion").asLong()).isEqualTo(v + 2);
    assertThat(page.path("hasMore").asBoolean()).isFalse();
    assertThat(page.path("nextCursor").isNull()).isTrue();
    JsonNode newest = page.path("items").get(0);
    JsonNode older = page.path("items").get(1);
    assertThat(page.path("items").size()).isEqualTo(2);

    assertThat(newest.path("editKey").asText()).isEqualTo("paymentTerms");
    assertThat(newest.path("changeKind").asText()).isEqualTo("CLEAR");
    assertThat(newest.path("operationId").asText()).isEqualTo(opB.toString());
    assertThat(newest.path("orderVersion").asLong()).isEqualTo(v + 2);
    assertThat(newest.path("actor").path("id").asText()).isEqualTo(actorB.id().toString());
    assertThat(newest.path("actor").path("displayName").asText()).isEqualTo("Blake Seller");
    assertThat(newest.path("lineId").isNull()).isTrue();
    assertThat(newest.path("changedAt").asText()).isNotBlank();
    assertKnown(newest.path("oldValue"), "30 days");
    assertThat(newest.path("newValue").path("state").asText()).isEqualTo("KNOWN");
    assertThat(newest.path("newValue").path("data").isNull()).isTrue();
    assertThat(newest.path("resolution").isNull()).isTrue();
    assertThat(newest.path("resolutionScope").isNull()).isTrue();

    assertThat(older.path("editKey").asText()).isEqualTo("notes");
    assertThat(older.path("changeKind").asText()).isEqualTo("SET");
    assertThat(older.path("operationId").asText()).isEqualTo(opA.toString());
    assertThat(older.path("orderVersion").asLong()).isEqualTo(v + 1);
    assertThat(older.path("actor").path("displayName").asText()).isEqualTo("Avery Seller");
    assertThat(older.path("oldValue").path("data").isNull()).isTrue();
    assertKnown(older.path("newValue"), "Urgent");
  }

  // ── R1: a stored key this version does not know ───────────────────────────

  @Test
  @DisplayName(
      "R1/H18: an unknown stored key keeps its raw key, line, save, actor and version; values are"
          + " UNAVAILABLE and the known entry next to it is untouched")
  void unknownStoredKeyKeepsItsMetadata() {
    UUID known = UUID.randomUUID();
    saved(actorA, body(known, open(actorA).baseId(), "notes", set("Urgent")));
    UUID unknownId = UUID.randomUUID();
    UUID unknownOperation = UUID.randomUUID();
    long version = orderVersion();
    jdbc.update(
        "INSERT INTO sales_ord.order_field_change (id, tenant_id, created_at, updated_at,"
            + " sales_order_id, operation_id, operation_receipt_id, line_id, edit_key,"
            + " change_kind, old_value, new_value, actor_id, order_version, changed_at)"
            + " VALUES (?, ?, now(), now(), ?, ?, ?, ?, 'line.futureKey', 'SET', NULL,"
            + " '{\"a\":1}'::jsonb, ?, ?, now())",
        unknownId,
        tenantId,
        orderId,
        unknownOperation,
        UUID.randomUUID(),
        l1,
        actorB.id(),
        version);

    List<JsonNode> entries = entries(actorC, MAX_LIMIT);
    assertThat(entries).hasSize(2);

    JsonNode unknown =
        entries.stream()
            .filter(entry -> unknownId.toString().equals(entry.path("id").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(unknown.path("editKey").asText()).isEqualTo("line.futureKey");
    assertThat(unknown.path("knownEditKey").isNull()).isTrue();
    assertThat(unknown.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(unknown.path("operationId").asText()).isEqualTo(unknownOperation.toString());
    assertThat(unknown.path("orderVersion").asLong()).isEqualTo(version);
    assertThat(unknown.path("actor").path("displayName").asText()).isEqualTo("Blake Seller");
    for (String side : List.of("oldValue", "newValue")) {
      assertThat(unknown.path(side).path("state").asText()).isEqualTo("UNAVAILABLE");
      assertThat(unknown.path(side).path("reason").asText()).isEqualTo("UNSUPPORTED_SHAPE");
      assertThat(unknown.path(side).path("data").isNull()).isTrue();
    }

    JsonNode notes = only(entries, "notes");
    assertThat(notes.path("knownEditKey").asText()).isEqualTo("notes");
    assertThat(notes.path("operationId").asText()).isEqualTo(known.toString());
    assertKnown(notes.path("newValue"), "Urgent");
  }

  // ── H05–H07: stable pages ─────────────────────────────────────────────────

  @Test
  @DisplayName(
      "H05: equal times, a clock going back and many rows of one save keep the version/id order")
  void orderIsByVersionAndIdNeverByTime() {
    saved(
        actorA,
        body(
            UUID.randomUUID(),
            open(actorA).baseId(),
            "notes",
            set("A"),
            "shippingMethod",
            set("Courier"),
            "customerReference",
            set("PO-1")));
    clock.advance(Duration.ofHours(-2));
    saved(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "paymentTerms", set("45 days")));
    clock.advance(Duration.ofHours(-2));
    saved(actorC, body(UUID.randomUUID(), open(actorC).baseId(), "notes", set("C")));

    List<String> expected = databaseOrder();
    assertThat(expected).hasSize(5);
    assertThat(ids(entries(actorA, MAX_LIMIT))).isEqualTo(expected);
    assertThat(ids(entries(actorA, 1))).isEqualTo(expected);

    jdbc.update(
        "UPDATE sales_ord.order_field_change SET changed_at = ? WHERE sales_order_id = ?",
        Timestamp.from(Instant.parse("2026-10-09T09:00:00Z")),
        orderId);
    assertThat(ids(entries(actorA, 2))).isEqualTo(expected);
    // The newest save, made with the earliest clock, still comes first.
    assertThat(entries(actorA, 1).getFirst().path("editKey").asText()).isEqualTo("notes");
  }

  @Test
  @DisplayName("H06: a save between pages is neither shown nor shifts the chain; restart shows it")
  void saveBetweenPagesNeverShiftsTheChain() {
    for (String notes : List.of("one", "two", "three")) {
      saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set(notes)));
    }
    List<String> before = databaseOrder();

    JsonNode first = page(actorB, 2, null);
    long snapshot = first.path("snapshotVersion").asLong();
    saved(actorC, body(UUID.randomUUID(), open(actorC).baseId(), "paymentTerms", set("60 days")));
    JsonNode second = page(actorB, 2, first.path("nextCursor").asText());

    List<String> chain = new ArrayList<>();
    first.path("items").forEach(entry -> chain.add(entry.path("id").asText()));
    second.path("items").forEach(entry -> chain.add(entry.path("id").asText()));
    assertThat(chain).isEqualTo(before);
    assertThat(second.path("snapshotVersion").asLong()).isEqualTo(snapshot);
    assertThat(second.path("hasMore").asBoolean()).isFalse();

    JsonNode restarted = page(actorB, 2, null);
    assertThat(restarted.path("snapshotVersion").asLong()).isEqualTo(snapshot + 1);
    assertThat(restarted.path("items").get(0).path("editKey").asText()).isEqualTo("paymentTerms");
    assertThat(ids(entries(actorB, 2))).isEqualTo(databaseOrder()).hasSize(4);
  }

  @Test
  @DisplayName("H07: a save split by a page boundary shows each of its fields exactly once")
  void saveSplitAcrossPages() {
    UUID single = UUID.randomUUID();
    saved(actorA, body(single, open(actorA).baseId(), "notes", set("first")));
    UUID triple = UUID.randomUUID();
    saved(
        actorB,
        body(
            triple,
            open(actorB).baseId(),
            "notes",
            set("second"),
            "shippingMethod",
            set("Air"),
            "paymentTerms",
            set("15 days")));

    List<JsonNode> pages = pages(actorC, 2);
    assertThat(pages).hasSize(2);
    assertThat(pages.get(0).path("hasMore").asBoolean()).isTrue();
    assertThat(pages.get(1).path("hasMore").asBoolean()).isFalse();
    assertThat(pages.get(1).path("nextCursor").isNull()).isTrue();

    List<JsonNode> entries = new ArrayList<>();
    pages.forEach(page -> page.path("items").forEach(entries::add));
    assertThat(ids(entries)).doesNotHaveDuplicates().isEqualTo(databaseOrder());
    Map<String, Long> perSave =
        entries.stream()
            .collect(
                Collectors.groupingBy(
                    entry -> entry.path("operationId").asText(), Collectors.counting()));
    assertThat(perSave).containsEntry(triple.toString(), 3L).containsEntry(single.toString(), 1L);
    // The three-field save starts on the first page and continues on the second.
    assertThat(pages.get(1).path("items").get(0).path("operationId").asText())
        .isEqualTo(triple.toString());

    JsonNode whole = page(actorC, 4, null);
    assertThat(whole.path("hasMore").asBoolean()).isFalse();
    assertThat(whole.path("nextCursor").isNull()).isTrue();
  }

  // ── H09–H11: values ───────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "H09–H11: false, cleared, unknown date, exact decimals and calendar dates as saved; a later"
          + " change never rewrites an earlier entry")
  void valuesAsSaved() {
    saved(
        actorA,
        withLines(
            body(
                UUID.randomUUID(),
                open(actorA).baseId(),
                "requestedDeliveryDate",
                set("2026-11-15")),
            List.of(
                update(
                    l1,
                    "singleLotRequired",
                    set(true),
                    "quantity",
                    set(quantity("1234.567", "M")),
                    "pricing",
                    set(pricing("GBP", "6.80"))))));
    saved(
        actorB,
        body(UUID.randomUUID(), open(actorB).baseId(), "requestedDeliveryDate", set("2026-12-01")));

    List<JsonNode> entries = entries(actorC, MAX_LIMIT);
    List<JsonNode> dates =
        entries.stream()
            .filter(entry -> "requestedDeliveryDate".equals(entry.path("editKey").asText()))
            .toList();
    assertThat(dates).hasSize(2);
    JsonNode firstDate = dates.get(1);
    JsonNode unknown = firstDate.path("oldValue").path("data");
    assertThat(firstDate.path("oldValue").path("state").asText()).isEqualTo("KNOWN");
    assertThat(unknown.isObject()).isTrue();
    assertThat(fieldNames(unknown)).containsExactlyInAnyOrder("status", "date", "event", "place");
    assertThat(unknown.path("date").isNull()).isTrue();
    assertThat(firstDate.path("newValue").path("data").path("date").asText())
        .isEqualTo("2026-11-15");
    assertThat(dates.get(0).path("oldValue").path("data").path("date").asText())
        .isEqualTo("2026-11-15");
    assertThat(dates.get(0).path("newValue").path("data").path("date").asText())
        .isEqualTo("2026-12-01");

    JsonNode singleLot = only(entries, "line.singleLotRequired");
    assertThat(singleLot.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(singleLot.path("oldValue").path("data").isBoolean()).isTrue();
    assertThat(singleLot.path("oldValue").path("data").booleanValue()).isFalse();
    assertThat(singleLot.path("newValue").path("data").booleanValue()).isTrue();

    JsonNode quantity = only(entries, "line.quantity");
    assertExactDecimal(quantity.path("oldValue").path("data").path("requestedQty"), "1000");
    assertExactDecimal(quantity.path("newValue").path("data").path("requestedQty"), "1234.567");
    assertThat(quantity.path("newValue").path("data").path("unit").asText()).isEqualTo("M");

    JsonNode pricing = only(entries, "line.pricing");
    assertExactDecimal(pricing.path("oldValue").path("data").path("unitPrice"), "4.0000");
    assertExactDecimal(pricing.path("newValue").path("data").path("unitPrice"), "6.80");
    assertThat(pricing.path("newValue").path("data").path("currency").asText()).isEqualTo("GBP");
  }

  // ── H12, H17: whole lines and pinned profiles ─────────────────────────────

  @Test
  @DisplayName(
      "H12/H17: an added and a removed line read from their own saved projection; no digest")
  void wholeLines() throws Exception {
    UUID clientLineId = UUID.randomUUID();
    UUID p3 = UUID.randomUUID();
    SalesOrderEditResult added =
        saved(
            actorA,
            withLines(
                body(UUID.randomUUID(), open(actorA).baseId()),
                List.of(add(clientLineId, p3, "quantity", set(quantity("200", "M"))))));
    UUID l3 = added.lineIds().getFirst().lineId();
    saved(actorB, withLines(body(UUID.randomUUID(), open(actorB).baseId()), List.of(remove(l1))));
    assertThat(activeLines()).isEqualTo(2);

    Answer answer = historyHttp(actorC, orderId, MAX_LIMIT, null);
    assertThat(answer.status()).isEqualTo(200);
    assertThat(answer.text()).doesNotContain("allocationDigest", "fingerprint");
    List<JsonNode> entries = new ArrayList<>();
    answer.body().path("data").path("items").forEach(entries::add);

    JsonNode removed = byKind(entries, "LINE_REMOVED");
    assertThat(removed.path("editKey").asText()).isEqualTo("line");
    assertThat(removed.path("lineId").asText()).isEqualTo(l1.toString());
    JsonNode removedLine = removed.path("oldValue").path("data");
    assertThat(removedLine.path("lineId").asText()).isEqualTo(l1.toString());
    assertThat(removedLine.path("productId").asText()).isEqualTo(p1.toString());
    assertExactDecimal(removedLine.path("quantity").path("requestedQty"), "1000");
    assertThat(removed.path("newValue").path("state").asText()).isEqualTo("KNOWN");
    assertThat(removed.path("newValue").path("data").isNull()).isTrue();

    JsonNode addedEntry = byKind(entries, "LINE_ADDED");
    assertThat(addedEntry.path("lineId").asText()).isEqualTo(l3.toString());
    assertThat(addedEntry.path("oldValue").path("data").isNull()).isTrue();
    assertThat(addedEntry.path("newValue").path("data").path("productId").asText())
        .isEqualTo(p3.toString());
    assertExactDecimal(
        addedEntry.path("newValue").path("data").path("quantity").path("requestedQty"), "200");
  }

  @Test
  @DisplayName("H17: a specification shows the profile version it pinned, not the current one")
  void specificationKeepsItsPinnedProfile() {
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l1, "specification", set(specification("first"))))));
    saved(
        actorB,
        withLines(
            body(UUID.randomUUID(), open(actorB).baseId()),
            List.of(update(l1, "specification", set(specification("second"))))));

    List<JsonNode> specifications =
        entries(actorC, MAX_LIMIT).stream()
            .filter(entry -> "line.specification".equals(entry.path("editKey").asText()))
            .toList();
    assertThat(specifications).hasSize(2);
    JsonNode later = specifications.get(0);
    JsonNode earlier = specifications.get(1);
    assertThat(earlier.path("oldValue").path("data").path("requirementProfile").isNull()).isTrue();
    JsonNode firstPin = earlier.path("newValue").path("data").path("requirementProfile");
    JsonNode secondPin = later.path("newValue").path("data").path("requirementProfile");
    assertThat(fieldNames(firstPin)).containsExactlyInAnyOrder("profileId", "profileVersion");
    assertThat(later.path("oldValue").path("data").path("requirementProfile")).isEqualTo(firstPin);
    assertThat(secondPin).isNotEqualTo(firstPin);
  }

  // ── H13: decisions as stored ──────────────────────────────────────────────

  @Test
  @DisplayName("H13: USE_MINE and NEW_VALUE of a field, USE_MINE of a whole line, as stored")
  void decisionsAsStored() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    JsonNode problem =
        conflicted(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));
    UUID useMine = UUID.randomUUID();
    saved(
        actorB,
        withResolutions(
            body(useMine, conflictBase(problem), "paymentTerms", set("60 days")),
            List.of(resolution("paymentTerms", null, "USE_MINE"))));

    UUID baseC = open(actorC).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("75 days")));
    JsonNode second =
        conflicted(actorC, body(UUID.randomUUID(), baseC, "paymentTerms", set("80 days")));
    UUID newValue = UUID.randomUUID();
    saved(
        actorC,
        withResolutions(
            body(newValue, conflictBase(second), "paymentTerms", set("90 days")),
            List.of(resolution("paymentTerms", null, "NEW_VALUE"))));

    UUID baseLine = open(actorB).baseId();
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l1, "pricing", set(pricing("GBP", "4.10"))))));
    JsonNode lineProblem =
        conflicted(actorB, withLines(body(UUID.randomUUID(), baseLine), List.of(remove(l1))));
    UUID removeMine = UUID.randomUUID();
    saved(
        actorB,
        withResolutions(
            withLines(body(removeMine, conflictBase(lineProblem)), List.of(remove(l1))),
            List.of(resolution("line", l1, "USE_MINE"))));

    List<JsonNode> entries = entries(actorC, MAX_LIMIT);
    JsonNode mine = byOperation(entries, useMine);
    assertThat(mine.path("resolution").asText()).isEqualTo("USE_MINE");
    assertThat(mine.path("resolutionScope").asText()).isEqualTo("FIELD");
    assertKnown(mine.path("newValue"), "60 days");
    JsonNode other = byOperation(entries, newValue);
    assertThat(other.path("resolution").asText()).isEqualTo("NEW_VALUE");
    assertThat(other.path("resolutionScope").asText()).isEqualTo("FIELD");
    assertKnown(other.path("newValue"), "90 days");
    JsonNode line = byOperation(entries, removeMine);
    assertThat(line.path("changeKind").asText()).isEqualTo("LINE_REMOVED");
    assertThat(line.path("resolution").asText()).isEqualTo("USE_MINE");
    assertThat(line.path("resolutionScope").asText()).isEqualTo("LINE");
    entries.stream()
        .filter(entry -> entry.path("resolution").isNull())
        .forEach(entry -> assertThat(entry.path("resolutionScope").isNull()).isTrue());
  }

  // ── H14: what is not a saved change ───────────────────────────────────────

  @Test
  @DisplayName("H14: conflict, no-op, replay, validation refusal and rollback add no entry")
  void unsavedAttemptsAddNothing() {
    UUID stale = open(actorB).baseId();
    UUID operation = UUID.randomUUID();
    Map<String, Object> request = body(operation, open(actorA).baseId(), "notes", set("Urgent"));
    saved(actorA, request);
    List<String> before = ids(entries(actorC, MAX_LIMIT));
    assertThat(before).hasSize(1);

    conflicted(actorB, body(UUID.randomUUID(), stale, "notes", set("Other")));
    SalesOrderEditResult same =
        saved(actorC, body(UUID.randomUUID(), open(actorC).baseId(), "notes", set("Urgent")));
    assertThat(same.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(saved(actorA, request).replayed()).isTrue();
    // A required key cannot be cleared: refused (422) before anything is written.
    Object refused =
        save(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "orderDate", clear()));
    assertThat(failureCode(refused)).isEqualTo("REQUIRED_FIELD_CANNOT_BE_CLEARED");
    assertThat(rolledBackSave()).isInstanceOf(RuntimeException.class);

    assertThat(ids(entries(actorC, MAX_LIMIT))).isEqualTo(before);
  }

  // ── H15: retention ────────────────────────────────────────────────────────

  @Test
  @DisplayName("H15: cleaning receipts and bases leaves every entry and decision unchanged")
  void retentionLeavesTheHistory() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    JsonNode problem =
        conflicted(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));
    saved(
        actorB,
        withResolutions(
            body(UUID.randomUUID(), conflictBase(problem), "paymentTerms", set("60 days")),
            List.of(resolution("paymentTerms", null, "USE_MINE"))));
    JsonNode before = page(actorC, MAX_LIMIT, null).path("items");

    retention.purgeAll(clock.instant().plus(Duration.ofDays(400)));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_edit_operation WHERE sales_order_id = ?",
                Integer.class,
                orderId))
        .isZero();

    JsonNode after = page(actorC, MAX_LIMIT, null).path("items");
    assertThat(after).isEqualTo(before);
    assertThat(after.get(0).path("resolution").asText()).isEqualTo("USE_MINE");
  }

  // ── H16: actor names ──────────────────────────────────────────────────────

  @Test
  @DisplayName("H16: the current name, a neutral null for an inactive person, never other PII")
  void actorNames() throws Exception {
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("A")));
    saved(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "paymentTerms", set("45 days")));
    jdbc.update(
        "UPDATE common_user.common_user SET first_name = 'Bailey' WHERE id = ?", actorB.id());
    jdbc.update("UPDATE common_user.common_user SET is_active = false WHERE id = ?", actorA.id());
    cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

    Answer answer = historyHttp(actorC, orderId, null, null);
    assertThat(answer.status()).isEqualTo(200);
    List<JsonNode> entries = new ArrayList<>();
    answer.body().path("data").path("items").forEach(entries::add);

    JsonNode byB = only(entries, "paymentTerms").path("actor");
    JsonNode byA = only(entries, "notes").path("actor");
    assertThat(byB.path("displayName").asText()).isEqualTo("Bailey Seller");
    assertThat(byA.path("id").asText()).isEqualTo(actorA.id().toString());
    assertThat(byA.path("displayName").isNull()).isTrue();
    assertThat(fieldNames(byA)).containsExactlyInAnyOrder("id", "displayName");
    assertThat(answer.text()).doesNotContain("@", "WORKER", "SALES");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** A save whose history insert fails at commit: everything it wrote rolls back. */
  private Object rolledBackSave() {
    UUID base = open(actorB).baseId();
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION sales_ord.cedit09_test_fail_history() RETURNS trigger"
            + " LANGUAGE plpgsql AS $$ BEGIN IF NEW.tenant_id = '"
            + tenantId
            + "'::uuid THEN RAISE EXCEPTION 'injected failure before commit'; END IF;"
            + " RETURN NULL; END $$");
    try {
      jdbc.execute(
          "CREATE CONSTRAINT TRIGGER cedit09_test_fail_history AFTER INSERT ON"
              + " sales_ord.order_field_change DEFERRABLE INITIALLY DEFERRED FOR EACH ROW"
              + " EXECUTE FUNCTION sales_ord.cedit09_test_fail_history()");
      return save(actorB, body(UUID.randomUUID(), base, "shippingMethod", set("Sea")));
    } finally {
      jdbc.execute(
          "DROP TRIGGER IF EXISTS cedit09_test_fail_history ON sales_ord.order_field_change");
      jdbc.execute("DROP FUNCTION IF EXISTS sales_ord.cedit09_test_fail_history()");
    }
  }

  private static UUID conflictBase(JsonNode problem) {
    return UUID.fromString(problem.path("currentBase").path("baseId").asText());
  }

  private static JsonNode byKind(List<JsonNode> entries, String kind) {
    List<JsonNode> found =
        entries.stream().filter(entry -> kind.equals(entry.path("changeKind").asText())).toList();
    assertThat(found).as("entries of kind %s", kind).hasSize(1);
    return found.getFirst();
  }

  private static JsonNode byOperation(List<JsonNode> entries, UUID operation) {
    List<JsonNode> found =
        entries.stream()
            .filter(entry -> operation.toString().equals(entry.path("operationId").asText()))
            .toList();
    assertThat(found).as("entries of save %s", operation).hasSize(1);
    return found.getFirst();
  }

  private static void assertKnown(JsonNode value, String text) {
    assertThat(value.path("state").asText()).isEqualTo("KNOWN");
    assertThat(value.path("reason").isNull()).isTrue();
    assertThat(value.path("data").asText()).isEqualTo(text);
  }

  /**
   * An exact decimal string with the expected value. The save writes values through a JSON tree,
   * which may drop trailing zeros; the value itself is never approximated.
   */
  private static void assertExactDecimal(JsonNode value, String expected) {
    assertThat(value.isTextual()).as("a decimal travels as text: %s", value).isTrue();
    assertThat(value.asText()).matches(SalesOrderFieldHistoryDtos.DECIMAL_PATTERN);
    assertThat(new BigDecimal(value.asText())).isEqualByComparingTo(expected);
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Map<String, Object> specification(String decisionReference) {
    Map<String, Object> basis =
        pairs(
            "kind",
            "LINE_EXPLICIT",
            "actorId",
            actorA.id(),
            "decidedAt",
            "2026-10-01T10:00:00Z",
            "decisionReference",
            decisionReference);
    Map<String, Object> profile =
        pairs(
            "basis",
            basis,
            "scopeVersion",
            "client-scope",
            "resolutionRuleVersion",
            "client-rule",
            "scope",
            List.of(),
            "facets",
            List.of(),
            "unmodelledConstraints",
            List.of(),
            "deviations",
            List.of());
    return pairs("moduleSpecs", Map.of(), "requirementProfile", profile);
  }
}
