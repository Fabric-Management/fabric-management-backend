package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The safe save end to end on PostgreSQL, one save after another (CEDIT-03 §7): merged candidates
 * checked by the domain rules, all or nothing, APPLIED and NO_CHANGE, the next base and the order
 * DTO from the same state, requirement-profile side effects and the versions actually committed.
 * Overlapping transactions are in {@link SalesOrderEditConcurrencyIT}.
 */
class SalesOrderEditServiceIT extends SalesOrderEditItSupport {

  @Test
  @DisplayName("S1.1: independent keys of two users are both kept, one version each")
  void independentKeysOfTwoUsers() {
    long v = orderVersion();
    UUID baseA = open(actorA).baseId();
    UUID baseB = open(actorB).baseId();

    SalesOrderEditResult first =
        saved(actorA, body(UUID.randomUUID(), baseA, "notes", set("Urgent")));
    SalesOrderEditResult second =
        saved(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", clear()));

    assertThat(first.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(first.resultVersion()).isEqualTo(v + 1);
    assertThat(second.resultVersion()).isEqualTo(v + 2);
    assertThat(orderVersion()).isEqualTo(v + 2);
    assertThat(orderText("notes")).isEqualTo("Urgent");
    assertThat(orderText("payment_terms")).isNull();
    assertThat(receipts("APPLIED")).isEqualTo(2);
    assertThat(history())
        .extracting(row -> row.get("edit_key"), row -> row.get("change_kind"))
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("notes", "SET"),
            org.assertj.core.groups.Tuple.tuple("paymentTerms", "CLEAR"));
    assertThat(history())
        .filteredOn(row -> "paymentTerms".equals(row.get("edit_key")))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("old_value")).isEqualTo("\"30 days\"");
              assertThat(row.get("new_value")).isNull();
              assertThat(row.get("order_version")).isEqualTo(v + 2);
              assertThat(row.get("actor_id")).isEqualTo(actorB.id());
            });
  }

  @Test
  @DisplayName("S2.2: a key the request does not name is never changed")
  void untouchedKeyIsKept() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "shippingMethod", set("Courier")));

    saved(actorB, body(UUID.randomUUID(), baseB, "notes", set("X")));

    assertThat(orderText("shipping_method")).isEqualTo("Courier");
    assertThat(orderText("notes")).isEqualTo("X");
  }

  @Test
  @DisplayName("S2.4, S5.1: a conflict saves nothing and records a new base and its receipt")
  void conflictSavesNothing() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    long v = orderVersion();
    int historyBefore = historyRows();

    JsonNode problem =
        conflicted(
            actorB,
            body(UUID.randomUUID(), baseB, "notes", set("Urgent"), "paymentTerms", clear()));

    assertThat(problem.path("code").asText()).isEqualTo("EDIT_CONFLICT");
    assertThat(problem.path("status").asInt()).isEqualTo(409);
    assertThat(problem.path("conflicts")).hasSize(1);
    JsonNode conflict = problem.path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("paymentTerms");
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(conflict.path("base").asText()).isEqualTo("30 days");
    assertThat(conflict.path("current").asText()).isEqualTo("45 days");
    assertThat(conflict.path("mine").isNull()).isTrue();
    assertThat(conflict.path("choices"))
        .extracting(JsonNode::asText)
        .containsExactly("KEEP_CURRENT", "USE_MINE", "NEW_VALUE");
    assertThat(problem.path("currentBase").path("orderVersion").asLong()).isEqualTo(v);
    assertThat(problem.path("currentBase").path("order").path("paymentTerms").asText())
        .isEqualTo("45 days");

    assertThat(orderVersion()).isEqualTo(v);
    assertThat(orderText("notes")).isNull();
    assertThat(historyRows()).isEqualTo(historyBefore);
    assertThat(receipts("CONFLICT")).isEqualTo(1);
    UUID currentBase = UUID.fromString(problem.path("currentBase").path("baseId").asText());
    assertThat(
            jdbc.queryForObject(
                "SELECT origin FROM sales_ord.order_edit_base WHERE id = ?",
                String.class,
                currentBase))
        .isEqualTo("CONFLICT");
  }

  @Test
  @DisplayName("S3.2: the value saved meanwhile is a no-change; nothing moves")
  void sameValueSavedMeanwhileIsNoChange() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("60 days")));
    long v = orderVersion();
    int historyBefore = historyRows();

    SalesOrderEditResult result =
        saved(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(result.resultVersion()).isEqualTo(v);
    assertThat(result.nextBase().orderVersion()).isEqualTo(v);
    assertThat(orderVersion()).isEqualTo(v);
    assertThat(historyRows()).isEqualTo(historyBefore);
    assertThat(receipts("NO_CHANGE")).isEqualTo(1);
  }

  @Test
  @DisplayName("S11.3/§5.1: an expired base with nothing to change is a no-change, not a review")
  void expiredBaseWithoutChangeIsNoChange() {
    UUID base = open(actorB).baseId();
    ageBase(base, java.time.Duration.ofDays(1));
    long v = orderVersion();

    SalesOrderEditResult result =
        saved(actorB, body(UUID.randomUUID(), base, "paymentTerms", set("30 days")));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(orderVersion()).isEqualTo(v);
    assertThat(receipts("CONFLICT")).isZero();
    assertThat(receipts("NO_CHANGE")).isEqualTo(1);
  }

  @Test
  @DisplayName("S3.6, S8.1: an order with planning refuses even a no-change, without a receipt")
  void orderWithPlanningRefusesTheSave() {
    UUID base = open(actorB).baseId();
    sendToPlanning();

    Object result = save(actorB, body(UUID.randomUUID(), base, "paymentTerms", set("30 days")));

    assertThat(failureCode(result)).isEqualTo("ORDER_WITH_PLANNING");
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName("S4.1: two valid changes together break the discount rule; nothing is saved")
  void mergedCandidateIsCheckedAsAWhole() {
    UUID baseB = open(actorB).baseId();
    Map<String, Object> discount = pricing("GBP", "4.0000");
    discount.put("discountAmount", new BigDecimal("3500"));
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l1, "pricing", set(discount)))));
    long v = orderVersion();

    Object result =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), baseB),
                List.of(update(l1, "quantity", set(quantity("800", "M"))))));

    assertThat(failureCode(result)).isEqualTo("ORDER_RULE_VIOLATION");
    assertThat(((DomainException) result).getDetails())
        .containsEntry("lineId", l1)
        .containsEntry("key", "line.quantity");
    assertThat(orderVersion()).isEqualTo(v);
    assertThat(
            jdbc.queryForObject(
                "SELECT requested_qty FROM sales_ord.sales_order_line WHERE id = ?",
                BigDecimal.class,
                l1))
        .isEqualByComparingTo("1000");
    assertThat(receipts()).isEqualTo(1);
  }

  @Test
  @DisplayName("S4.5: a quantity below the allocated quantity is refused with the line")
  void quantityBelowAllocationIsRefused() {
    UUID base = open(actorB).baseId();

    Object result =
        save(
            actorB,
            withLines(
                body(UUID.randomUUID(), base),
                List.of(update(l2, "quantity", set(quantity("200", "M"))))));

    assertThat(result).isInstanceOf(DomainException.class);
    assertThat(((DomainException) result).getDetails()).containsEntry("lineId", l2);
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName("S6.1, S6.4: two added lines from two bases are both kept, each with its mapping")
  void addedLinesFromTwoBases() {
    UUID baseA = open(actorA).baseId();
    UUID baseB = open(actorB).baseId();
    UUID ca = UUID.randomUUID();
    UUID cb = UUID.randomUUID();
    UUID p3 = UUID.randomUUID();
    UUID p4 = UUID.randomUUID();

    SalesOrderEditResult a =
        saved(
            actorA,
            withLines(
                body(UUID.randomUUID(), baseA),
                List.of(add(ca, p3, "quantity", set(quantity("200", "M"))))));
    SalesOrderEditResult b =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), baseB),
                List.of(add(cb, p4, "quantity", set(quantity("300", "M"))))));

    assertThat(a.lineIds())
        .singleElement()
        .satisfies(m -> assertThat(m.clientLineId()).isEqualTo(ca));
    assertThat(b.lineIds())
        .singleElement()
        .satisfies(m -> assertThat(m.clientLineId()).isEqualTo(cb));
    assertThat(activeLines()).isEqualTo(4);
    assertThat(b.nextBase().order().getLines()).hasSize(4);
    assertThat(history())
        .filteredOn(row -> "LINE_ADDED".equals(row.get("change_kind")))
        .extracting(row -> row.get("line_id"))
        .containsExactlyInAnyOrder(
            a.lineIds().getFirst().lineId(), b.lineIds().getFirst().lineId());
    assertThat(
            jdbc.queryForObject(
                "SELECT client_line_id FROM sales_ord.sales_order_line WHERE id = ?",
                UUID.class,
                b.lineIds().getFirst().lineId()))
        .isEqualTo(cb);
  }

  @Test
  @DisplayName("S7.1: removing a line someone changed conflicts and keeps the line")
  void removingAChangedLineConflicts() {
    UUID baseB = open(actorB).baseId();
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l2, "pricing", set(pricing("GBP", "6.80"))))));

    JsonNode problem =
        conflicted(actorB, withLines(body(UUID.randomUUID(), baseB), List.of(remove(l2))));

    JsonNode conflict = problem.path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("line");
    assertThat(conflict.path("lineId").asText()).isEqualTo(l2.toString());
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_CHANGED_ON_SERVER");
    assertThat(conflict.path("current").has("line.pricing")).isTrue();
    assertThat(conflict.path("choices"))
        .extracting(JsonNode::asText)
        .containsExactly("KEEP_CURRENT", "USE_MINE");
    assertThat(activeLines()).isEqualTo(2);
  }

  @Test
  @DisplayName("S7.2, S7.3: a removed line is never brought back; removing it again is no change")
  void removedLineStaysRemoved() {
    UUID baseB = open(actorB).baseId();
    UUID baseC = open(actorC).baseId();
    saved(actorA, withLines(body(UUID.randomUUID(), open(actorA).baseId()), List.of(remove(l1))));

    JsonNode problem =
        conflicted(
            actorB,
            withLines(
                body(UUID.randomUUID(), baseB),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.10"))))));
    SalesOrderEditResult again =
        saved(actorC, withLines(body(UUID.randomUUID(), baseC), List.of(remove(l1))));

    JsonNode conflict = problem.path("conflicts").get(0);
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_REMOVED_ON_SERVER");
    assertThat(conflict.path("choices"))
        .extracting(JsonNode::asText)
        .containsExactly("KEEP_CURRENT");
    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(
            jdbc.queryForObject(
                "SELECT is_active FROM sales_ord.sales_order_line WHERE id = ?", Boolean.class, l1))
        .isFalse();
  }

  @Test
  @DisplayName("S7.7: removing an unchanged line soft-deletes it with its history")
  void removingAnUnchangedLine() {
    long v = orderVersion();

    SalesOrderEditResult result =
        saved(
            actorB, withLines(body(UUID.randomUUID(), open(actorB).baseId()), List.of(remove(l1))));

    assertThat(result.resultVersion()).isEqualTo(v + 1);
    assertThat(activeLines()).isEqualTo(1);
    assertThat(history())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("change_kind")).isEqualTo("LINE_REMOVED");
              assertThat(row.get("line_id")).isEqualTo(l1);
              assertThat((String) row.get("old_value")).contains(p1.toString());
              assertThat(row.get("new_value")).isNull();
            });
  }

  @Test
  @DisplayName("S9.1, S9.2: a resolved conflict applies; a third change meanwhile conflicts again")
  void resolutionAndInterveningChange() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    JsonNode first =
        conflicted(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));
    UUID b1 = UUID.fromString(first.path("currentBase").path("baseId").asText());

    // S9.2: C saves "50 days" after B1 was answered.
    saved(actorC, body(UUID.randomUUID(), open(actorC).baseId(), "paymentTerms", set("50 days")));
    JsonNode second =
        conflicted(
            actorB,
            withResolutions(
                body(
                    UUID.randomUUID(),
                    b1,
                    "paymentTerms",
                    set("60 days"),
                    "deadline",
                    set("2026-12-01")),
                List.of(resolution("paymentTerms", null, "USE_MINE"))));
    JsonNode conflict = second.path("conflicts").get(0);
    assertThat(conflict.path("base").asText()).isEqualTo("45 days");
    assertThat(conflict.path("current").asText()).isEqualTo("50 days");
    assertThat(conflict.path("mine").asText()).isEqualTo("60 days");
    assertThat(orderText("deadline")).isNull();

    // S9.1 against the newest base.
    UUID b2 = UUID.fromString(second.path("currentBase").path("baseId").asText());
    SalesOrderEditResult applied =
        saved(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), b2, "paymentTerms", set("60 days")),
                List.of(resolution("paymentTerms", null, "USE_MINE"))));
    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("payment_terms")).isEqualTo("60 days");
    assertThat(history())
        .filteredOn(row -> actorB.id().equals(row.get("actor_id")))
        .singleElement()
        .satisfies(row -> assertThat(row.get("resolution")).isEqualTo("USE_MINE"));
  }

  @Test
  @DisplayName("S9.3: a conflict base without resolutions is a resolution mismatch")
  void conflictBaseNeedsItsResolutions() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    JsonNode first =
        conflicted(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));
    UUID b1 = UUID.fromString(first.path("currentBase").path("baseId").asText());

    Object result = save(actorB, body(UUID.randomUUID(), b1, "paymentTerms", set("60 days")));

    assertThat(failureCode(result)).isEqualTo("RESOLUTION_MISMATCH");
    assertThat(((DomainException) result).getDetails()).containsEntry("key", "paymentTerms");
  }

  @Test
  @DisplayName("S9.6, S9.7: a stale revert needs confirmation; confirmed it applies")
  void staleRevertNeedsConfirmation() {
    UUID b0 = open(actorB).baseId();
    saved(
        actorA,
        body(
            UUID.randomUUID(),
            open(actorA).baseId(),
            "paymentTerms",
            set("45 days"),
            "notes",
            set("Urgent")));
    JsonNode first =
        conflicted(actorB, body(UUID.randomUUID(), b0, "paymentTerms", set("60 days")));
    UUID b1 = UUID.fromString(first.path("currentBase").path("baseId").asText());

    JsonNode revert =
        conflicted(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), b1, "paymentTerms", set("60 days"), "notes", clear()),
                List.of(resolution("paymentTerms", null, "USE_MINE"))));
    JsonNode notes = revert.path("conflicts").get(0);
    assertThat(revert.path("conflicts")).hasSize(1);
    assertThat(notes.path("key").asText()).isEqualTo("notes");
    assertThat(notes.path("reason").asText()).isEqualTo("UNCONFIRMED_REVERT");
    assertThat(notes.path("base").asText()).isEqualTo("Urgent");
    assertThat(notes.path("current").asText()).isEqualTo("Urgent");
    assertThat(notes.path("mine").isNull()).isTrue();
    assertThat(orderText("notes")).isEqualTo("Urgent");

    UUID b2 = UUID.fromString(revert.path("currentBase").path("baseId").asText());
    SalesOrderEditResult confirmed =
        saved(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), b2, "paymentTerms", set("60 days"), "notes", clear()),
                List.of(resolution("notes", null, "USE_MINE"))));
    assertThat(confirmed.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isNull();
    assertThat(orderText("payment_terms")).isEqualTo("60 days");
    assertThat(history())
        .filteredOn(
            row -> "notes".equals(row.get("edit_key")) && actorB.id().equals(row.get("actor_id")))
        .singleElement()
        .satisfies(row -> assertThat(row.get("resolution")).isEqualTo("USE_MINE"));
  }

  @Test
  @DisplayName("S9.8, S13.2: the user's own saved value is not guarded; the next base continues")
  void ownSavedValueIsNotGuarded() {
    SalesOrderEditResult first =
        saved(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("Urgent")));

    SalesOrderEditResult second =
        saved(actorB, body(UUID.randomUUID(), first.nextBase().baseId(), "notes", clear()));

    assertThat(second.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(orderText("notes")).isNull();
  }

  @Test
  @DisplayName("S9.9, S13.6: keeping the current value alone is a no-change")
  void keepCurrentAloneIsNoChange() {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    JsonNode first =
        conflicted(actorB, body(UUID.randomUUID(), baseB, "paymentTerms", set("60 days")));
    UUID b1 = UUID.fromString(first.path("currentBase").path("baseId").asText());
    Map<String, Object> keep = body(UUID.randomUUID(), b1);
    keep.put("resolutions", List.of(resolution("paymentTerms", null, "KEEP_CURRENT")));

    SalesOrderEditResult result = saved(actorB, keep);

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(orderText("payment_terms")).isEqualTo("45 days");
    assertThat(receipts("NO_CHANGE")).isEqualTo(1);
  }

  @Test
  @DisplayName("S12.10: the contact is compared after the domain's normalisation")
  void normalisedContactIsNoChange() {
    Map<String, Object> contact =
        pairs("name", " Jane Hill ", "email", "jane@example.com", "whatsapp", false);

    SalesOrderEditResult result =
        saved(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "contact", set(contact)));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
  }

  @Test
  @DisplayName("Base and order DTO come from one state; the next base equals the committed order")
  void nextBaseIsTheCommittedState() {
    SalesOrderEditBase opened = open(actorB);
    assertThat(opened.orderVersion()).isEqualTo(opened.order().getVersion());
    assertThat(opened.order().getLines()).hasSize(2);

    SalesOrderEditResult result =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), opened.baseId(), "notes", set("Urgent")),
                List.of(update(l2, "tolerance", set(pairs("upPct", 3, "downPct", 3))))));

    assertThat(result.nextBase().orderVersion()).isEqualTo(orderVersion());
    assertThat(result.nextBase().order().getVersion()).isEqualTo(orderVersion());
    assertThat(result.nextBase().order().getNotes()).isEqualTo("Urgent");
    assertThat(result.nextBase().expiresAt()).isAfter(result.nextBase().capturedAt());
    assertThat(
            jdbc.queryForObject(
                "SELECT order_version FROM sales_ord.order_edit_base WHERE id = ?",
                Long.class,
                result.nextBase().baseId()))
        .isEqualTo(orderVersion());
  }

  @Test
  @DisplayName(
      "S4.4: under an agreed delivery commitment the term changes only through a new commitment;"
          + " nothing is written")
  void deliveryTermsUnderACommitmentAreRefused() {
    // The agreed commitment holds FCA Leeds (Incoterms 2020), the order's own term.
    jdbc.update(
        "INSERT INTO sales_ord.delivery_commitment (id, tenant_id, created_at, updated_at,"
            + " is_active, version, sales_order_id, sequence_no, committed_on, delivery_term,"
            + " delivery_place, incoterms_version, delivery_event, origin, customer_contact,"
            + " channel, agreed_at, recorded_by, recorded_at)"
            + " VALUES (?, ?, now(), now(), true, 0, ?, 1, current_date, 'FCA', 'Leeds',"
            + " 'INCOTERMS_2020', 'HANDED_TO_CARRIER', 'INITIAL', 'Jane Hill', 'EMAIL', now(), ?,"
            + " now())",
        UUID.randomUUID(),
        tenantId,
        orderId,
        actorA.id());
    UUID base = open(actorB).baseId();
    long v = orderVersion();

    Object result =
        save(
            actorB,
            body(
                UUID.randomUUID(),
                base,
                "deliveryTerms",
                set(pairs("term", "DAP", "place", "York", "incotermsVersion", "INCOTERMS_2020")),
                "notes",
                set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("ORDER_RULE_VIOLATION");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(409);
    assertThat(((DomainException) result).getDetails()).containsEntry("key", "deliveryTerms");
    assertThat(orderText("delivery_term")).isEqualTo("FCA");
    assertThat(orderText("delivery_place")).isEqualTo("Leeds");
    assertThat(orderText("notes")).isNull();
    assertThat(orderVersion()).isEqualTo(v);
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName("S16.6, S16.1: a new profile is applied once; the same input again changes nothing")
  void profileAppliedOnceThenNoChange() {
    long v = orderVersion();
    SalesOrderEditResult applied =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), open(actorB).baseId()),
                List.of(update(l1, "specification", set(specification("line-1"))))));
    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(applied.resultVersion()).isEqualTo(v + 1);
    assertThat(profileVersions()).isEqualTo(1);
    assertThat(history())
        .singleElement()
        .satisfies(row -> assertThat(row.get("edit_key")).isEqualTo("line.specification"));

    SalesOrderEditResult again =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), applied.nextBase().baseId()),
                List.of(update(l1, "specification", set(specification("line-1"))))));

    assertThat(again.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(profileVersions()).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(v + 1);
    assertThat(historyRows()).isEqualTo(1);
  }

  @Test
  @DisplayName("S16.2, S16.3: the profile another user saved is no change; another one conflicts")
  void profileSavedMeanwhile() {
    UUID baseB = open(actorB).baseId();
    UUID baseC = open(actorC).baseId();
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l1, "specification", set(specification("line-1"))))));
    long v = orderVersion();

    SalesOrderEditResult same =
        saved(
            actorB,
            withLines(
                body(UUID.randomUUID(), baseB),
                List.of(update(l1, "specification", set(specification("line-1"))))));
    JsonNode other =
        conflicted(
            actorC,
            withLines(
                body(UUID.randomUUID(), baseC),
                List.of(update(l1, "specification", set(specification("line-2"))))));

    assertThat(same.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    JsonNode conflict = other.path("conflicts").get(0);
    assertThat(conflict.path("key").asText()).isEqualTo("line.specification");
    assertThat(conflict.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(conflict.path("mine").path("requirementProfile").path("fingerprint").asText())
        .isNotEqualTo(
            conflict.path("current").path("requirementProfile").path("fingerprint").asText());
    assertThat(profileVersions()).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(v);
  }

  /** A line-explicit requirement profile; the decision reference makes it distinct. */
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
