package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.platform.realtime.domain.LiveLeaseKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseField;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The sales order lease catalogue and the keys a save needs (CEDIT-07 §3.2). */
class SalesOrderLeaseKeysTest {

  /** Configured like the application: unknown properties are refused by the types themselves. */
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .findAndRegisterModules()
          .configure(
              com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              false);

  private final UUID orderId = UUID.randomUUID();
  private final UUID l1 = UUID.randomUUID();
  private final UUID l2 = UUID.randomUUID();

  @Test
  @DisplayName("L03: the catalogue is exactly the safe-edit keys plus the whole line")
  void catalogueMirrorsTheEditKeys() {
    List<String> wire =
        Arrays.stream(SalesOrderEditLeaseField.values())
            .map(SalesOrderEditLeaseField::wireName)
            .toList();
    List<String> expected =
        new java.util.ArrayList<>(
            Arrays.stream(OrderEditKey.values()).map(OrderEditKey::wireName).toList());
    expected.add("line");
    assertThat(wire).containsExactlyInAnyOrderElementsOf(expected);
    assertThatThrownBy(() -> SalesOrderEditLeaseField.fromWireName("line.pricing.currency"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalesOrderEditLeaseField.fromWireName("contact.email"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("L03: header keys name no line; line keys name theirs; the wire round-trips")
  void keysMapBothWays() {
    LiveLeaseKey notes = SalesOrderLeaseKeys.of(OrderEditKey.NOTES, null);
    assertThat(notes).isEqualTo(new LiveLeaseKey("header", "notes"));
    LiveLeaseKey pricing = SalesOrderLeaseKeys.of(OrderEditKey.LINE_PRICING, l1);
    assertThat(pricing).isEqualTo(new LiveLeaseKey("line:" + l1, "line.pricing"));
    assertThat(SalesOrderLeaseKeys.wholeLine(l1).overlaps(pricing)).isTrue();
    assertThat(SalesOrderLeaseKeys.of(OrderEditKey.LINE_PRICING, l2).overlaps(pricing)).isFalse();

    assertThat(SalesOrderLeaseKeys.toWire(pricing))
        .isEqualTo(new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.LINE_PRICING, l1));
    assertThat(SalesOrderLeaseKeys.toWire(SalesOrderLeaseKeys.wholeLine(l1)))
        .isEqualTo(new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.LINE, l1));
    assertThat(SalesOrderLeaseKeys.toWire(notes))
        .isEqualTo(new SalesOrderEditLeaseKey(SalesOrderEditLeaseField.NOTES, null));

    assertThatThrownBy(() -> SalesOrderLeaseKeys.of(OrderEditKey.NOTES, l1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SalesOrderLeaseKeys.of(OrderEditKey.LINE_QUANTITY, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName(
      "§3.2 table: header and UPDATE instructions need their keys, REMOVE the whole line, ADD and"
          + " resolutions alone nothing; a SET to the same value still needs its key")
  void requiredKeysFollowTheIntent() throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operationId", UUID.randomUUID());
    body.put("baseId", UUID.randomUUID());
    body.put(
        "header",
        Map.of("notes", set("same as before"), "paymentTerms", Map.of("operation", "CLEAR")));
    body.put(
        "lines",
        List.of(
            Map.of(
                "operation",
                "UPDATE",
                "lineId",
                l1,
                "fields",
                Map.of(
                    "quantity", set(Map.of("requestedQty", new BigDecimal("10"), "unit", "M")),
                    "pricing", set(Map.of("currency", "GBP", "unitPrice", new BigDecimal("4"))))),
            Map.of("operation", "REMOVE", "lineId", l2),
            Map.of(
                "operation",
                "ADD",
                "clientLineId",
                UUID.randomUUID(),
                "productId",
                UUID.randomUUID(),
                "fields",
                Map.of(
                    "quantity", set(Map.of("requestedQty", new BigDecimal("5"), "unit", "M"))))));
    SalesOrderEditInstructions.Parsed parsed = parse(body);

    assertThat(SalesOrderLeaseKeys.required(parsed))
        .containsExactlyInAnyOrder(
            new LiveLeaseKey("header", "notes"),
            new LiveLeaseKey("header", "paymentTerms"),
            new LiveLeaseKey("line:" + l1, "line.quantity"),
            new LiveLeaseKey("line:" + l1, "line.pricing"),
            LiveLeaseKey.whole("line:" + l2));

    Map<String, Object> keepCurrentOnly = new LinkedHashMap<>();
    keepCurrentOnly.put("operationId", UUID.randomUUID());
    keepCurrentOnly.put("baseId", UUID.randomUUID());
    keepCurrentOnly.put("resolutions", List.of(Map.of("key", "notes", "choice", "KEEP_CURRENT")));
    assertThat(SalesOrderLeaseKeys.required(parse(keepCurrentOnly))).isEqualTo(Set.of());
  }

  @Test
  @DisplayName(
      "CEDIT-07-F1: lines gone since the base need no lease, for UPDATE and REMOVE alike; the header"
          + " and active lines still do")
  void goneLinesNeedNoLease() throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operationId", UUID.randomUUID());
    body.put("baseId", UUID.randomUUID());
    body.put("header", Map.of("notes", set("x")));
    UUID l3 = UUID.randomUUID();
    body.put(
        "lines",
        List.of(
            Map.of(
                "operation",
                "UPDATE",
                "lineId",
                l1,
                "fields",
                Map.of(
                    "pricing", set(Map.of("currency", "GBP", "unitPrice", new BigDecimal("4"))))),
            Map.of("operation", "REMOVE", "lineId", l2),
            Map.of(
                "operation",
                "UPDATE",
                "lineId",
                l3,
                "fields",
                Map.of(
                    "quantity", set(Map.of("requestedQty", new BigDecimal("10"), "unit", "M"))))));
    SalesOrderEditInstructions.Parsed parsed = parse(body);

    assertThat(SalesOrderLeaseKeys.required(parsed, Set.of(l1, l2)))
        .containsExactlyInAnyOrder(
            new LiveLeaseKey("header", "notes"), new LiveLeaseKey("line:" + l3, "line.quantity"));
    // Without gone lines, the same request needs every key.
    assertThat(SalesOrderLeaseKeys.required(parsed, Set.of()))
        .isEqualTo(SalesOrderLeaseKeys.required(parsed))
        .contains(new LiveLeaseKey("line:" + l1, "line.pricing"), LiveLeaseKey.whole("line:" + l2));
  }

  @Test
  @DisplayName("L11: the lease proof is not part of the save's identity")
  void proofIsNotInTheFingerprint() throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operationId", UUID.randomUUID());
    body.put("baseId", UUID.randomUUID());
    body.put("header", Map.of("notes", set("x")));
    String plain = parse(body).fingerprint();

    body.put("editSessionId", UUID.randomUUID());
    body.put("leaseTokens", List.of(UUID.randomUUID()));
    assertThat(parse(body).fingerprint()).isEqualTo(plain);
  }

  @Test
  @DisplayName("L19: explicit null session, null or repeated tokens are unreadable requests")
  void proofShapeIsStrict() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("operationId", UUID.randomUUID());
    body.put("baseId", UUID.randomUUID());
    body.put("editSessionId", null);
    assertThatThrownBy(() -> JSON.convertValue(body, SalesOrderEditRequest.class))
        .isInstanceOf(IllegalArgumentException.class);

    UUID token = UUID.randomUUID();
    body.remove("editSessionId");
    body.put("leaseTokens", List.of(token, token));
    assertThatThrownBy(() -> JSON.convertValue(body, SalesOrderEditRequest.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private SalesOrderEditInstructions.Parsed parse(Map<String, Object> body) {
    return SalesOrderEditInstructions.parse(
        orderId, JSON.convertValue(body, SalesOrderEditRequest.class), 200);
  }

  private static Map<String, Object> set(Object value) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("operation", "SET");
    field.put("value", value);
    return field;
  }
}
