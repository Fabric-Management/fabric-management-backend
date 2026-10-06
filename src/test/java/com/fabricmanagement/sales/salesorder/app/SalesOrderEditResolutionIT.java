package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.RequestedDeliveryEvent;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Conflict resolutions end to end on PostgreSQL (CEDIT-02 §5.7, §7.1; CEDIT-03 review R1, R2).
 *
 * <p>R1: USE_MINE saves the mine the conflict showed, even where the order form's instruction is
 * completed from the base it was sent against (the requested date's event and place, a partial
 * requirement profile, a specification that keeps its pinned profile). The shown value, the saved
 * value and the history's new value are the same; the conflict base stays the merge's base, so a
 * third change conflicts again with the same mine; a shown mine saved meanwhile is a no-change.
 *
 * <p>R2: a line-level decision (LINE_PRODUCT_CHANGED) stays in the history of every field the save
 * changed on that line, with its scope, after the receipt is cleaned up; a repeat writes none.
 */
class SalesOrderEditResolutionIT extends SalesOrderEditItSupport {

  private static final String DATE = "requestedDeliveryDate";
  private static final String SPECIFICATION = "line.specification";

  @Autowired private OrderPartiesService parties;
  @Autowired private ProductCorrectionService productCorrections;

  // ── R1: the requested date ────────────────────────────────────────────────

  @Test
  @DisplayName(
      "R1/S9.1: USE_MINE of a requested date saves the shown date, event and place; history"
          + " records the same value")
  void useMineOfTheRequestedDateSavesTheShownValue() {
    JsonNode first = requestedDateConflict();
    JsonNode shown = only(first, DATE).path("mine");
    assertShownMine(shown);
    assertThat(only(first, DATE).path("current").path("place").asText()).isEqualTo("York");

    UUID resolution = UUID.randomUUID();
    SalesOrderEditResult applied =
        saved(
            actorB,
            withResolutions(
                body(resolution, baseOf(first), DATE, set("2026-11-15")),
                List.of(resolution(DATE, null, "USE_MINE"))));

    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertRequestedDate("2026-11-15", "HANDED_TO_CARRIER", "Leeds");
    Map<String, Object> row = onlyHistoryOf(actorB, DATE);
    assertThat(read(row.get("new_value"))).isEqualTo(shown);
    assertThat(row.get("resolution")).isEqualTo("USE_MINE");
    assertThat(row.get("resolution_scope")).isEqualTo("FIELD");
    assertThat(row.get("operation_id")).isEqualTo(resolution);
  }

  @Test
  @DisplayName(
      "R1/S9.2: a third change after the conflict base conflicts again with the same shown mine;"
          + " resolved against the newest base it saves that mine")
  void thirdChangeAfterTheConflictBaseConflictsAgain() {
    JsonNode first = requestedDateConflict();
    JsonNode shown = only(first, DATE).path("mine");
    requestedDate(actorC, "2026-11-20", RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE, "York");

    JsonNode second =
        conflicted(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), baseOf(first), DATE, set("2026-11-15")),
                List.of(resolution(DATE, null, "USE_MINE"))));
    JsonNode again = only(second, DATE);
    assertThat(again.path("reason").asText()).isEqualTo("CHANGED_ON_SERVER");
    assertThat(again.path("mine")).isEqualTo(shown);
    assertThat(again.path("current").path("date").asText()).isEqualTo("2026-11-20");
    assertRequestedDate("2026-11-20", "RECEIVED_BY_CONSIGNEE", "York");

    saved(
        actorB,
        withResolutions(
            body(UUID.randomUUID(), baseOf(second), DATE, set("2026-11-15")),
            List.of(resolution(DATE, null, "USE_MINE"))));
    assertRequestedDate("2026-11-15", "HANDED_TO_CARRIER", "Leeds");
    assertThat(read(onlyHistoryOf(actorB, DATE).get("new_value"))).isEqualTo(shown);
  }

  @Test
  @DisplayName("R1: the shown mine saved by someone else meanwhile makes USE_MINE a no-change")
  void shownMineSavedMeanwhileIsNoChange() {
    JsonNode first = requestedDateConflict();
    requestedDate(actorC, "2026-11-15", RequestedDeliveryEvent.HANDED_TO_CARRIER, "Leeds");
    long version = orderVersion();

    SalesOrderEditResult result =
        saved(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), baseOf(first), DATE, set("2026-11-15")),
                List.of(resolution(DATE, null, "USE_MINE"))));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyOf(actorB)).isEmpty();
  }

  @Test
  @DisplayName(
      "R1/§5.7: NEW_VALUE is another value by the key's equality; the sent date takes the current"
          + " event and place, as the order form's date always does")
  void newValueIsJudgedByTheKeysEquality() {
    JsonNode first = requestedDateConflict();

    SalesOrderEditResult result =
        saved(
            actorB,
            withResolutions(
                body(UUID.randomUUID(), baseOf(first), DATE, set("2026-11-15")),
                List.of(resolution(DATE, null, "NEW_VALUE"))));

    assertThat(result.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertRequestedDate("2026-11-15", "RECEIVED_BY_CONSIGNEE", "York");
    Map<String, Object> row = onlyHistoryOf(actorB, DATE);
    assertThat(row.get("resolution")).isEqualTo("NEW_VALUE");
    assertThat(read(row.get("new_value"))).isNotEqualTo(only(first, DATE).path("mine"));
  }

  // ── R1: the specification ─────────────────────────────────────────────────

  @Test
  @DisplayName(
      "R1/S16: USE_MINE of a partial profile saves the profile shown, not the input resolved again"
          + " against the conflict base")
  void useMineOfAPartialProfileSavesTheShownProfile() {
    JsonNode first = partialProfileConflict();
    JsonNode shown = only(first, SPECIFICATION).path("mine");
    String fingerprint = shown.path("requirementProfile").path("fingerprint").asText();
    assertThat(references(shown.path("requirementProfile")))
        .containsExactlyInAnyOrder("CERTIFICATION=cert-1", "ORIGIN=origin-2");

    UUID resolution = UUID.randomUUID();
    SalesOrderEditResult applied =
        saved(
            actorB,
            withResolutions(
                withLines(
                    body(resolution, baseOf(first)),
                    List.of(update(l1, "specification", set(partial("ORIGIN", "origin-2"))))),
                List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));

    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(lineFingerprint(l1)).isEqualTo(fingerprint);
    assertThat(references(lineProfile(l1)))
        .containsExactlyInAnyOrder("CERTIFICATION=cert-1", "ORIGIN=origin-2");
    Map<String, Object> row = onlyHistoryOf(actorB, SPECIFICATION);
    assertThat(read(row.get("new_value")).path("requirementProfile").path("fingerprint").asText())
        .isEqualTo(fingerprint);
    assertThat(row.get("resolution")).isEqualTo("USE_MINE");
    assertThat(row.get("resolution_scope")).isEqualTo("FIELD");
  }

  @Test
  @DisplayName(
      "R1/S9.2/S16: a profile changed again after the conflict base conflicts with the same shown"
          + " profile, which the next resolution saves")
  void profileChangedAgainConflictsWithTheSameShownProfile() {
    JsonNode first = partialProfileConflict();
    String fingerprint =
        only(first, SPECIFICATION)
            .path("mine")
            .path("requirementProfile")
            .path("fingerprint")
            .asText();
    saved(
        actorC,
        withLines(
            body(UUID.randomUUID(), open(actorC).baseId()),
            List.of(update(l1, "specification", set(partial("CERTIFICATION", "cert-3"))))));

    JsonNode second =
        conflicted(
            actorB,
            withResolutions(
                withLines(
                    body(UUID.randomUUID(), baseOf(first)),
                    List.of(update(l1, "specification", set(partial("ORIGIN", "origin-2"))))),
                List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));
    assertThat(
            only(second, SPECIFICATION)
                .path("mine")
                .path("requirementProfile")
                .path("fingerprint")
                .asText())
        .isEqualTo(fingerprint);

    saved(
        actorB,
        withResolutions(
            withLines(
                body(UUID.randomUUID(), baseOf(second)),
                List.of(update(l1, "specification", set(partial("ORIGIN", "origin-2"))))),
            List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));
    assertThat(lineFingerprint(l1)).isEqualTo(fingerprint);
  }

  @Test
  @DisplayName(
      "R1 follow-up: USE_MINE of a partial profile whose explicit deviation no longer fits the"
          + " newer base saves the shown profile; it is not resolved against the conflict base")
  void useMineIsNotResolvedAgainstTheConflictBase() {
    JsonNode first = deviationConflict();
    JsonNode shown = only(first, SPECIFICATION).path("mine");
    String fingerprint = shown.path("requirementProfile").path("fingerprint").asText();
    assertThat(references(shown.path("requirementProfile")))
        .containsExactlyInAnyOrder("CERTIFICATION=cert-2", "ORIGIN=origin-1");
    assertThat(lineDeviations(lineProfile(l1))).containsExactly("ORIGIN=origin-2");

    UUID resolution = UUID.randomUUID();
    SalesOrderEditResult applied =
        saved(
            actorB,
            withResolutions(
                withLines(
                    body(resolution, baseOf(first)),
                    List.of(update(l1, "specification", set(certificationWithOriginDeviation())))),
                List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));

    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(lineFingerprint(l1)).isEqualTo(fingerprint);
    assertThat(references(lineProfile(l1)))
        .containsExactlyInAnyOrder("CERTIFICATION=cert-2", "ORIGIN=origin-1");
    assertThat(lineDeviations(lineProfile(l1))).containsExactly("ORIGIN=origin-1");
    assertThat(
            read(onlyHistoryOf(actorB, SPECIFICATION).get("new_value"))
                .path("requirementProfile")
                .path("fingerprint")
                .asText())
        .isEqualTo(fingerprint);
  }

  @Test
  @DisplayName(
      "R1 follow-up: after a third profile change the same USE_MINE conflicts again with the"
          + " same shown profile; a changed instruction under USE_MINE is still refused")
  void deviationMineConflictsAgainAndChangedInstructionsAreRefused() {
    JsonNode first = deviationConflict();
    String fingerprint =
        only(first, SPECIFICATION)
            .path("mine")
            .path("requirementProfile")
            .path("fingerprint")
            .asText();

    // Another instruction under USE_MINE: neither the recorded instruction nor the shown value.
    Object changed =
        save(
            actorB,
            withResolutions(
                withLines(
                    body(UUID.randomUUID(), baseOf(first)),
                    List.of(update(l1, "specification", set(partial("CERTIFICATION", "cert-2"))))),
                List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));
    assertThat(failureCode(changed)).isEqualTo("RESOLUTION_MISMATCH");

    saved(
        actorC,
        withLines(
            body(UUID.randomUUID(), open(actorC).baseId()),
            List.of(
                update(
                    l1,
                    "specification",
                    set(
                        profile(
                            List.of(facet("ORIGIN", "origin-3")),
                            List.of(deviation("ORIGIN", "origin-3"))))))));

    JsonNode second =
        conflicted(
            actorB,
            withResolutions(
                withLines(
                    body(UUID.randomUUID(), baseOf(first)),
                    List.of(update(l1, "specification", set(certificationWithOriginDeviation())))),
                List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));
    assertThat(
            only(second, SPECIFICATION)
                .path("mine")
                .path("requirementProfile")
                .path("fingerprint")
                .asText())
        .isEqualTo(fingerprint);

    saved(
        actorB,
        withResolutions(
            withLines(
                body(UUID.randomUUID(), baseOf(second)),
                List.of(update(l1, "specification", set(certificationWithOriginDeviation())))),
            List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));
    assertThat(lineFingerprint(l1)).isEqualTo(fingerprint);
  }

  @Test
  @DisplayName(
      "R1/§2.5: USE_MINE of module specs without a profile input keeps the profile the base"
          + " pinned, as shown; the line's newer profile is replaced by a new version of it")
  void useMineWithoutAProfileInputKeepsThePinnedProfile() {
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(update(l1, "specification", set(partial("CERTIFICATION", "cert-1"))))));
    String pinned = lineFingerprint(l1);
    UUID b0 = open(actorB).baseId();
    saved(
        actorC,
        withLines(
            body(UUID.randomUUID(), open(actorC).baseId()),
            List.of(update(l1, "specification", set(partial("CERTIFICATION", "cert-2"))))));
    assertThat(lineFingerprint(l1)).isNotEqualTo(pinned);
    Map<String, Object> specsOnly = pairs("moduleSpecs", Map.of("finish", "soft"));

    JsonNode first =
        conflicted(
            actorB,
            withLines(
                body(UUID.randomUUID(), b0), List.of(update(l1, "specification", set(specsOnly)))));
    JsonNode shown = only(first, SPECIFICATION).path("mine");
    assertThat(shown.path("requirementProfile").path("fingerprint").asText()).isEqualTo(pinned);
    int versionsBefore = profileVersions();

    saved(
        actorB,
        withResolutions(
            withLines(
                body(UUID.randomUUID(), baseOf(first)),
                List.of(update(l1, "specification", set(specsOnly)))),
            List.of(resolution(SPECIFICATION, l1, "USE_MINE"))));

    assertThat(lineFingerprint(l1)).isEqualTo(pinned);
    assertThat(lineText(l1, "module_specs")).contains("soft");
    assertThat(profileVersions()).isEqualTo(versionsBefore + 1);
    assertThat(
            read(onlyHistoryOf(actorB, SPECIFICATION).get("new_value"))
                .path("requirementProfile")
                .path("fingerprint")
                .asText())
        .isEqualTo(pinned);
  }

  // ── R2: a line-level decision in the field history ───────────────────────

  @Test
  @DisplayName(
      "R2/S7.6: USE_MINE of LINE_PRODUCT_CHANGED is the history's resolution of the line's"
          + " changed field, of line scope; a repeat writes none; it outlives the receipt")
  void productChangeUseMineIsKeptInTheHistory() {
    JsonNode first = productChangeConflict();
    UUID resolution = UUID.randomUUID();
    Map<String, Object> request =
        withResolutions(
            withLines(
                body(resolution, baseOf(first)),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.5000"))))),
            List.of(resolution("line", l1, "USE_MINE")));

    SalesOrderEditResult applied = saved(actorB, request);

    assertThat(applied.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.5000");
    Map<String, Object> row = onlyHistoryOf(actorB, "line.pricing");
    assertThat(row.get("line_id")).isEqualTo(l1);
    assertThat(row.get("resolution")).isEqualTo("USE_MINE");
    assertThat(row.get("resolution_scope")).isEqualTo("LINE");
    assertThat(row.get("operation_id")).isEqualTo(resolution);
    assertThat(row.get("actor_id")).isEqualTo(actorB.id());
    assertThat(read(row.get("old_value")).path("unitPrice").decimalValue())
        .isEqualByComparingTo("4.0000");
    assertThat(read(row.get("new_value")).path("unitPrice").decimalValue())
        .isEqualByComparingTo("4.5000");
    int rows = historyRows();

    SalesOrderEditResult repeated = saved(actorB, request);
    assertThat(repeated.replayed()).isTrue();
    assertThat(historyRows()).isEqualTo(rows);

    // Receipts and bases cleaned up (§6): the decision stays in the history on its own.
    jdbc.update("DELETE FROM sales_ord.order_edit_base WHERE sales_order_id = ?", orderId);
    jdbc.update("DELETE FROM sales_ord.order_edit_operation WHERE sales_order_id = ?", orderId);
    assertThat(receipts()).isZero();
    Map<String, Object> kept = onlyHistoryOf(actorB, "line.pricing");
    assertThat(kept.get("resolution")).isEqualTo("USE_MINE");
    assertThat(kept.get("resolution_scope")).isEqualTo("LINE");
    assertThat(kept.get("operation_id")).isEqualTo(resolution);
  }

  @Test
  @DisplayName("R2/S7.6: NEW_VALUE of LINE_PRODUCT_CHANGED is recorded with line scope")
  void productChangeNewValueIsKeptInTheHistory() {
    JsonNode first = productChangeConflict();

    saved(
        actorB,
        withResolutions(
            withLines(
                body(UUID.randomUUID(), baseOf(first)),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.6000"))))),
            List.of(resolution("line", l1, "NEW_VALUE"))));

    assertThat(lineDecimal(l1, "unit_price")).isEqualByComparingTo("4.6000");
    Map<String, Object> row = onlyHistoryOf(actorB, "line.pricing");
    assertThat(row.get("resolution")).isEqualTo("NEW_VALUE");
    assertThat(row.get("resolution_scope")).isEqualTo("LINE");
    assertThat(read(row.get("new_value")).path("unitPrice").decimalValue())
        .isEqualByComparingTo("4.6000");
  }

  @Test
  @DisplayName("R2: a change no resolution decided has neither resolution nor scope")
  void undecidedChangeHasNoResolution() {
    saved(actorB, body(UUID.randomUUID(), open(actorB).baseId(), "notes", set("Urgent")));

    Map<String, Object> row = onlyHistoryOf(actorB, "notes");
    assertThat(row.get("resolution")).isNull();
    assertThat(row.get("resolution_scope")).isNull();
  }

  // ── scenarios ─────────────────────────────────────────────────────────────

  /**
   * The requested date is 1 November, handed to the carrier in Leeds. B opens; A changes it to 8
   * November, received by the consignee in York; B sends only the date 15 November and conflicts.
   */
  private JsonNode requestedDateConflict() {
    requestedDate(actorA, "2026-11-01", RequestedDeliveryEvent.HANDED_TO_CARRIER, "Leeds");
    UUID b0 = open(actorB).baseId();
    requestedDate(actorA, "2026-11-08", RequestedDeliveryEvent.RECEIVED_BY_CONSIGNEE, "York");
    return conflicted(actorB, body(UUID.randomUUID(), b0, DATE, set("2026-11-15")));
  }

  /**
   * L1's profile is (cert-1, origin-1). B opens; C changes only the certification (cert-2); B sends
   * only the origin (origin-2) and conflicts, shown as (cert-1, origin-2).
   */
  private JsonNode partialProfileConflict() {
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(
                update(
                    l1,
                    "specification",
                    set(profile(facet("CERTIFICATION", "cert-1"), facet("ORIGIN", "origin-1")))))));
    UUID b0 = open(actorB).baseId();
    saved(
        actorC,
        withLines(
            body(UUID.randomUUID(), open(actorC).baseId()),
            List.of(update(l1, "specification", set(partial("CERTIFICATION", "cert-2"))))));
    return conflicted(
        actorB,
        withLines(
            body(UUID.randomUUID(), b0),
            List.of(update(l1, "specification", set(partial("ORIGIN", "origin-2"))))));
  }

  /**
   * L1's profile is (cert-1, origin-1) with a deviation recorded for origin-1. B opens; B's partial
   * input changes the certification (cert-2) and sends the origin's deviation (origin-1) again,
   * without the origin facet. Meanwhile A moves the origin and its deviation to origin-2. B's save
   * conflicts, shown as (cert-2, origin-1, deviation origin-1); that input resolved against the
   * conflict base would pair origin-2 with the origin-1 deviation, which is refused.
   */
  private JsonNode deviationConflict() {
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(
                update(
                    l1,
                    "specification",
                    set(
                        profile(
                            List.of(facet("CERTIFICATION", "cert-1"), facet("ORIGIN", "origin-1")),
                            List.of(deviation("ORIGIN", "origin-1"))))))));
    UUID b0 = open(actorB).baseId();
    saved(
        actorA,
        withLines(
            body(UUID.randomUUID(), open(actorA).baseId()),
            List.of(
                update(
                    l1,
                    "specification",
                    set(
                        profile(
                            List.of(facet("ORIGIN", "origin-2")),
                            List.of(deviation("ORIGIN", "origin-2"))))))));
    return conflicted(
        actorB,
        withLines(
            body(UUID.randomUUID(), b0),
            List.of(update(l1, "specification", set(certificationWithOriginDeviation())))));
  }

  /** B's instruction: the certification changes; the origin's deviation is sent as it was. */
  private Map<String, Object> certificationWithOriginDeviation() {
    return profile(
        List.of(facet("CERTIFICATION", "cert-2")), List.of(deviation("ORIGIN", "origin-1")));
  }

  /** B opens; A corrects L1's product (P1 → P9); B's pricing of L1 is LINE_PRODUCT_CHANGED. */
  private JsonNode productChangeConflict() {
    UUID b0 = open(actorB).baseId();
    UUID p9 = UUID.randomUUID();
    long l1Version = lineVersion(l1);
    Object corrected =
        as(
            actorA,
            () ->
                productCorrections.correct(
                    orderId,
                    new FulfilmentDtos.CorrectProduct(
                        p1,
                        p9,
                        List.of(new FulfilmentDtos.LineVersion(l1, l1Version)),
                        "Wrong article chosen"),
                    actorA.id()));
    assertThat(corrected).isInstanceOf(List.class);
    JsonNode problem =
        conflicted(
            actorB,
            withLines(
                body(UUID.randomUUID(), b0),
                List.of(update(l1, "pricing", set(pricing("GBP", "4.5000"))))));
    JsonNode conflict = only(problem, "line");
    assertThat(conflict.path("reason").asText()).isEqualTo("LINE_PRODUCT_CHANGED");
    assertThat(conflict.path("lineId").asText()).isEqualTo(l1.toString());
    return problem;
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Sets the order-level requested date with the order-parties command, as another writer. */
  private void requestedDate(Actor actor, String date, RequestedDeliveryEvent event, String place) {
    long version = orderVersion();
    Object result =
        as(
            actor,
            () ->
                parties.setRequestedDate(
                    orderId,
                    new OrderPartyDtos.SetRequestedDateRequest(
                        version,
                        new OrderPartyDtos.RequestedDateInput(
                            RequestedDateStatus.REQUESTED, LocalDate.parse(date), event, place)),
                    actor.id()));
    if (result instanceof RuntimeException failure) {
      throw failure;
    }
  }

  private static void assertShownMine(JsonNode shown) {
    assertThat(shown.path("date").asText()).isEqualTo("2026-11-15");
    assertThat(shown.path("event").asText()).isEqualTo("HANDED_TO_CARRIER");
    assertThat(shown.path("place").asText()).isEqualTo("Leeds");
  }

  private void assertRequestedDate(String date, String event, String place) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT requested_date_status, requested_delivery_date::text AS requested_date,"
                + " requested_delivery_event, requested_delivery_place"
                + " FROM sales_ord.sales_order WHERE id = ?",
            orderId);
    assertThat(row.get("requested_date_status")).isEqualTo("REQUESTED");
    assertThat(row.get("requested_date")).isEqualTo(date);
    assertThat(row.get("requested_delivery_event")).isEqualTo(event);
    assertThat(row.get("requested_delivery_place")).isEqualTo(place);
  }

  private static UUID baseOf(JsonNode problem) {
    return UUID.fromString(problem.path("currentBase").path("baseId").asText());
  }

  /** The one conflict of a problem with that key. */
  private static JsonNode only(JsonNode problem, String key) {
    List<JsonNode> found = new ArrayList<>();
    problem
        .path("conflicts")
        .forEach(
            conflict -> {
              if (key.equals(conflict.path("key").asText())) {
                found.add(conflict);
              }
            });
    assertThat(found).as("conflicts of %s in %s", key, problem).hasSize(1);
    return found.getFirst();
  }

  private List<Map<String, Object>> historyOf(Actor actor) {
    return history().stream().filter(row -> actor.id().equals(row.get("actor_id"))).toList();
  }

  private Map<String, Object> onlyHistoryOf(Actor actor, String key) {
    List<Map<String, Object>> rows =
        historyOf(actor).stream().filter(row -> key.equals(row.get("edit_key"))).toList();
    assertThat(rows).as("history of %s by the actor", key).hasSize(1);
    return rows.getFirst();
  }

  private JsonNode read(Object jsonText) {
    try {
      return objectMapper.readTree((String) jsonText);
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private String lineFingerprint(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT requirement_profile_fingerprint FROM sales_ord.sales_order_line WHERE id = ?",
        String.class,
        lineId);
  }

  private JsonNode lineProfile(UUID lineId) {
    return read(lineText(lineId, "requirement_profile_snapshot"));
  }

  private String lineText(UUID lineId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + "::text FROM sales_ord.sales_order_line WHERE id = ?",
        String.class,
        lineId);
  }

  private BigDecimal lineDecimal(UUID lineId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM sales_ord.sales_order_line WHERE id = ?",
        BigDecimal.class,
        lineId);
  }

  /** "KIND=reference" of each facet of a profile snapshot. */
  private static List<String> references(JsonNode profile) {
    List<String> references = new ArrayList<>();
    profile
        .path("facets")
        .forEach(
            facet ->
                references.add(
                    facet.path("kind").asText()
                        + "="
                        + facet.path("decisionBasis").path("reference").asText()));
    return references;
  }

  /** A profile input naming only these facets: the rest of the base's profile is kept (§2.5). */
  private Map<String, Object> partial(String kind, String reference) {
    return profile(facet(kind, reference));
  }

  @SafeVarargs
  private Map<String, Object> profile(Map<String, Object>... facets) {
    return profile(List.of(facets), List.of());
  }

  private Map<String, Object> profile(
      List<Map<String, Object>> facets, List<Map<String, Object>> deviations) {
    Map<String, Object> basis =
        pairs(
            "kind",
            "LINE_EXPLICIT",
            "actorId",
            actorA.id(),
            "decidedAt",
            "2026-10-01T10:00:00Z",
            "decisionReference",
            "line-1");
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
            facets,
            "unmodelledConstraints",
            List.of(),
            "deviations",
            deviations);
    return pairs("moduleSpecs", Map.of(), "requirementProfile", profile);
  }

  /** A recorded deviation of a facet kind, with the provenance of {@link #facet}. */
  private Map<String, Object> deviation(String kind, String reference) {
    return pairs(
        "facetIdentity",
        kind,
        "reference",
        reference,
        "actorId",
        actorA.id(),
        "decidedAt",
        "2026-10-01T10:00:00Z");
  }

  /** "KIND=reference" of each deviation of a profile snapshot. */
  private static List<String> lineDeviations(JsonNode profile) {
    List<String> deviations = new ArrayList<>();
    profile
        .path("deviations")
        .forEach(
            deviation ->
                deviations.add(
                    deviation.path("facetIdentity").asText()
                        + "="
                        + deviation.path("reference").asText()));
    return deviations;
  }

  /** A facet the customer left open on purpose: no bound, a traceable decision. */
  private Map<String, Object> facet(String kind, String reference) {
    return pairs(
        "kind",
        kind,
        "state",
        "UNCONSTRAINED",
        "comparison",
        "NONE",
        "decisionBasis",
        pairs(
            "source",
            "CUSTOMER_INSTRUCTION",
            "reference",
            reference,
            "actorId",
            actorA.id(),
            "decidedAt",
            "2026-10-01T10:00:00Z"));
  }
}
