package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge;
import com.fabricmanagement.sales.salesorder.domain.OrderEditSnapshot;
import com.fabricmanagement.sales.salesorder.domain.OrderFieldChange;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.RequestedDeliveryEvent;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResolutionChoice;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.ChangeKind;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Contact;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Entry;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Line;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Pricing;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.ProfileReference;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Quantity;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.RequestedDate;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.ResolutionScope;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Specification;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Tolerance;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.UnavailableReason;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Value;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.ValueState;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFieldChangeHistoryRepository.Row;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value projection of the field history (CEDIT-09 §3.3, H09–H11, H17, H18): every stored shape
 * read from the row alone, empty and unreadable kept apart, decimals exact, technical parts
 * dropped, one bad value never taking the entry or another value with it.
 */
class SalesOrderFieldHistoryMapperTest {

  /** Writes stored values the way the save does: Spring's mapper, dates as ISO text. */
  private static final JsonMapper WRITER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final SalesOrderFieldHistoryMapper mapper = new SalesOrderFieldHistoryMapper();
  private final UUID entry = UUID.randomUUID();

  // ── H09: empty, false, zero, unknown and unreadable stay apart ────────────

  @Test
  @DisplayName("H09: SQL NULL and JSON null are a KNOWN empty value; an empty text stays text")
  void emptyValuesAreKnown() {
    assertThat(mapper.value(entry, "paymentTerms", null)).isEqualTo(Value.known(null));
    assertThat(mapper.value(entry, "paymentTerms", "null")).isEqualTo(Value.known(null));
    assertThat(mapper.value(entry, "paymentTerms", "\"\"")).isEqualTo(Value.known(""));
    assertThat(mapper.value(entry, "paymentTerms", "\"30 days\""))
        .isEqualTo(Value.known("30 days"));
  }

  @Test
  @DisplayName("H09: false and zero are values, not empty")
  void falseAndZeroAreValues() {
    assertThat(mapper.value(entry, "line.singleLotRequired", "false"))
        .isEqualTo(Value.known(false));
    assertThat(mapper.value(entry, "line.tolerance", "{\"upPct\":0,\"downPct\":0.0}").data())
        .isEqualTo(new Tolerance("0", "0.0"));
  }

  @Test
  @DisplayName("H09: an unknown requested date keeps its own all-null shape")
  void unknownRequestedDateKeepsItsShape() {
    String stored = stored(OrderEditSnapshot.RequestedDateValue.UNKNOWN);

    Value value = mapper.value(entry, "requestedDeliveryDate", stored);

    assertThat(value.state()).isEqualTo(ValueState.KNOWN);
    assertThat(value.data()).isEqualTo(new RequestedDate(null, null, null, null));
  }

  @Test
  @DisplayName("H09: a cleared delivery term is the empty term, not an unreadable value")
  void clearedDeliveryTermIsTheEmptyTerm() {
    Value value =
        mapper.value(entry, "deliveryTerms", stored(OrderEditSnapshot.DeliveryTermsValue.NONE));

    assertThat(value).isEqualTo(Value.known(new DeliveryTerms(null, null, null, null, null)));
  }

  // ── H10: composite values come whole from the row ─────────────────────────

  @Test
  @DisplayName("H10: date with event and place, term with place and edition, contact, quantity")
  void compositeValuesAreWhole() {
    LocalDate date = LocalDate.of(2026, 11, 15);
    assertThat(
            mapper
                .value(
                    entry,
                    "requestedDeliveryDate",
                    stored(
                        new OrderEditSnapshot.RequestedDateValue(
                            RequestedDateStatus.REQUESTED,
                            date,
                            RequestedDeliveryEvent.HANDED_TO_CARRIER,
                            "Leeds")))
                .data())
        .isEqualTo(
            new RequestedDate(
                RequestedDateStatus.REQUESTED,
                date,
                RequestedDeliveryEvent.HANDED_TO_CARRIER,
                "Leeds"));
    assertThat(
            mapper
                .value(
                    entry,
                    "deliveryTerms",
                    stored(
                        new OrderEditSnapshot.DeliveryTermsValue(
                            DeliveryTerm.FCA,
                            "Leeds",
                            IncotermsVersion.INCOTERMS_2020,
                            DeliveryTermStatus.PROPOSED,
                            null)))
                .data())
        .isEqualTo(
            new DeliveryTerms(
                DeliveryTerm.FCA,
                "Leeds",
                IncotermsVersion.INCOTERMS_2020,
                DeliveryTermStatus.PROPOSED,
                null));
    assertThat(
            mapper
                .value(
                    entry,
                    "contact",
                    stored(new OrderEditSnapshot.ContactValue("Jane Hill", null, "+44 1", true)))
                .data())
        .isEqualTo(new Contact("Jane Hill", null, "+44 1", true));
    assertThat(
            mapper
                .value(
                    entry,
                    "line.quantity",
                    stored(new OrderEditSnapshot.QuantityValue(new BigDecimal("1000.5"), "M")))
                .data())
        .isEqualTo(new Quantity("1000.5", "M"));
    assertThat(
            mapper
                .value(
                    entry,
                    "line.pricing",
                    stored(
                        new OrderEditSnapshot.PricingValue(
                            "GBP", new BigDecimal("6.8"), null, BigDecimal.ZERO)))
                .data())
        .isEqualTo(new Pricing("GBP", "6.8", null, "0"));
  }

  @Test
  @DisplayName("H10: calendar dates are read as dates, never through a time zone")
  void calendarDatesStayDates() {
    assertThat(mapper.value(entry, "deadline", "\"2026-02-28\"").data())
        .isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(mapper.value(entry, "orderDate", "\"2026-12-31\"").data())
        .isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(mapper.value(entry, "deadline", "\"2026-02-30\""))
        .isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
    assertThat(mapper.value(entry, "deadline", "\"2026-02-28T00:00:00Z\""))
        .isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
  }

  // ── H11: decimals are exact strings ───────────────────────────────────────

  @Test
  @DisplayName("H11: high-precision decimals keep every digit and their trailing zeros")
  void decimalsAreExact() {
    String stored =
        "{\"currency\":\"GBP\",\"unitPrice\":12345678901234567890.123456789012345678,"
            + "\"discountAmount\":4.0000,\"taxAmount\":1000}";

    Pricing pricing = (Pricing) mapper.value(entry, "line.pricing", stored).data();

    assertThat(pricing.unitPrice()).isEqualTo("12345678901234567890.123456789012345678");
    assertThat(pricing.discountAmount()).isEqualTo("4.0000");
    assertThat(pricing.taxAmount()).isEqualTo("1000");
    assertThat(pricing.unitPrice()).matches(SalesOrderFieldHistoryDtos.DECIMAL_PATTERN);
  }

  @Test
  @DisplayName("H11: a value written the way the save writes it reads back to the same decimal")
  void writerShapedDecimalsKeepTheirValue() {
    String stored =
        storedAsTree(
            new OrderEditSnapshot.PricingValue(
                "GBP",
                new BigDecimal("0.000000000000000001"),
                new BigDecimal("99999999999.9999"),
                null));

    Pricing pricing = (Pricing) mapper.value(entry, "line.pricing", stored).data();

    assertThat(new BigDecimal(pricing.unitPrice()))
        .isEqualByComparingTo(new BigDecimal("0.000000000000000001"));
    assertThat(new BigDecimal(pricing.discountAmount()))
        .isEqualByComparingTo(new BigDecimal("99999999999.9999"));
    assertThat(pricing.unitPrice()).doesNotContain("E");
  }

  @Test
  @DisplayName("H11: a decimal stored as text or a JSON value of another type is unreadable")
  void decimalOfAnotherTypeIsUnreadable() {
    assertThat(mapper.value(entry, "line.quantity", "{\"requestedQty\":\"10\",\"unit\":\"M\"}"))
        .isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
    assertThat(mapper.value(entry, "line.quantity", "{\"requestedQty\":true,\"unit\":\"M\"}"))
        .isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
  }

  // ── H12, H17: whole lines and specifications ──────────────────────────────

  @Test
  @DisplayName("H12/H17: a whole line reads from its projection; digest and fingerprint drop")
  void wholeLineFromItsProjection() {
    UUID lineId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID colorId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    Map<String, Object> specs = new LinkedHashMap<>();
    specs.put("gsm", new BigDecimal("180.50"));
    specs.put("note", "<b>soft</b>");
    OrderEditSnapshot.Line line =
        new OrderEditSnapshot.Line(
            lineId,
            3L,
            productId,
            "Organic twill",
            colorId,
            new OrderEditSnapshot.WidthValue(new BigDecimal("150"), "CM"),
            LocalDate.of(2026, 12, 1),
            true,
            LineShipmentPreference.WHEN_COMPLETE,
            new OrderEditSnapshot.QuantityValue(new BigDecimal("500"), "M"),
            new OrderEditSnapshot.PricingValue("GBP", new BigDecimal("6.5"), null, null),
            new OrderEditSnapshot.ToleranceValue(new BigDecimal("5"), new BigDecimal("3")),
            new OrderEditSnapshot.SpecificationValue(
                ModuleType.FABRIC,
                specs,
                new OrderEditSnapshot.ProfileRef(profileId, 2, "sha256:abc")),
            "digest-123");

    String stored = stored(OrderEditMerge.projection(line));
    Value value = mapper.value(entry, OrderEditKey.LINE, stored);

    assertThat(value.state()).isEqualTo(ValueState.KNOWN);
    Line read = (Line) value.data();
    assertThat(read.lineId()).isEqualTo(lineId);
    assertThat(read.productId()).isEqualTo(productId);
    assertThat(read.productDesc()).isEqualTo("Organic twill");
    assertThat(read.colorId()).isEqualTo(colorId);
    assertThat(read.finishedWidth().value()).isEqualTo("150");
    assertThat(read.requestedDeliveryDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(read.singleLotRequired()).isTrue();
    assertThat(read.shipmentPreference()).isEqualTo(LineShipmentPreference.WHEN_COMPLETE);
    assertThat(read.quantity()).isEqualTo(new Quantity("500", "M"));
    assertThat(read.tolerance()).isEqualTo(new Tolerance("5", "3"));
    assertThat(read.specification().requirementProfile())
        .isEqualTo(new ProfileReference(profileId, 2));
    assertThat(read.specification().moduleSpecs().get("gsm").decimalValue())
        .isEqualByComparingTo("180.50");
    assertThat(read.specification().moduleSpecs().get("note").textValue()).isEqualTo("<b>soft</b>");

    String wire = json(value);
    assertThat(wire).doesNotContain("digest-123", "allocationDigest", "sha256:abc", "fingerprint");
    assertThat(wire).contains("\"requestedQty\":\"500\"");
  }

  @Test
  @DisplayName("H17: no profile is null; an incomplete reference is REFERENCE_UNAVAILABLE")
  void profileAbsentAndIncompleteDiffer() {
    String none =
        stored(
            new OrderEditSnapshot.SpecificationValue(
                ModuleType.FABRIC, Map.<String, Object>of("gsm", 180), null));
    String incomplete =
        stored(
            new OrderEditSnapshot.SpecificationValue(
                ModuleType.FABRIC,
                Map.of(),
                new OrderEditSnapshot.ProfileRef(null, null, "sha256:resolved")));

    assertThat(((Specification) mapper.value(entry, "line.specification", none).data()))
        .satisfies(spec -> assertThat(spec.requirementProfile()).isNull())
        .satisfies(spec -> assertThat(spec.moduleSpecs().get("gsm").intValue()).isEqualTo(180));
    assertThat(mapper.value(entry, "line.specification", incomplete))
        .isEqualTo(Value.unavailable(UnavailableReason.REFERENCE_UNAVAILABLE));

    String withoutVersionField =
        "{\"moduleType\":\"FABRIC\",\"moduleSpecs\":{},"
            + "\"requirementProfile\":{\"profileId\":\""
            + UUID.randomUUID()
            + "\"}}";
    assertThat(mapper.value(entry, "line.specification", withoutVersionField))
        .as("an absent id or version field is an incomplete reference too")
        .isEqualTo(Value.unavailable(UnavailableReason.REFERENCE_UNAVAILABLE));
    String extraField =
        "{\"moduleType\":\"FABRIC\",\"moduleSpecs\":{},"
            + "\"requirementProfile\":{\"profileId\":\""
            + UUID.randomUUID()
            + "\",\"profileVersion\":1,\"name\":\"x\"}}";
    assertThat(mapper.value(entry, "line.specification", extraField))
        .isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
  }

  // ── H18: unknown and broken values stay local ─────────────────────────────

  @Test
  @DisplayName("H18: unknown key, extra or missing field, unknown enum, wrong type, not JSON")
  void unreadableShapesAreUnavailable() {
    Value unsupported = Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE);
    assertThat(mapper.value(entry, "line.futureKey", "\"x\"")).isEqualTo(unsupported);
    assertThat(mapper.value(entry, "line.futureKey", null))
        .as("no shape is known, so a stored null is not a known empty value either")
        .isEqualTo(unsupported);
    assertThat(mapper.value(entry, "line.tolerance", "{\"upPct\":1,\"downPct\":1,\"x\":1}"))
        .isEqualTo(unsupported);
    assertThat(mapper.value(entry, "line.tolerance", "{\"upPct\":1}")).isEqualTo(unsupported);
    assertThat(mapper.value(entry, "line.shipmentPreference", "\"BY_SEA\"")).isEqualTo(unsupported);
    assertThat(mapper.value(entry, "notes", "42")).isEqualTo(unsupported);
    assertThat(mapper.value(entry, "line.colorId", "\"not-a-uuid\"")).isEqualTo(unsupported);
    assertThat(mapper.value(entry, "contact", "{\"name\":\"A\",\"email\":null,\"phone\":null}"))
        .isEqualTo(unsupported);
    assertThat(mapper.value(entry, "notes", "{not json")).isEqualTo(unsupported);
  }

  @Test
  @DisplayName("H18: markup in a text value is returned as text, unchanged")
  void markupStaysText() {
    String text = "<img src=x onerror=alert(1)>";
    assertThat(mapper.value(entry, "notes", stored(text))).isEqualTo(Value.known(text));
  }

  @Test
  @DisplayName("H18: one unreadable side keeps the entry's metadata and the other side")
  void oneBadSideKeepsTheEntry() {
    UUID id = UUID.randomUUID();
    UUID operation = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    Instant at = Instant.parse("2026-10-09T09:30:00.123456Z");
    Row row =
        new Row(
            id,
            operation,
            7L,
            at,
            actor,
            null,
            "notes",
            "SET",
            "{broken",
            "\"Urgent\"",
            "USE_MINE",
            "FIELD");

    Entry read = mapper.entry(row, "Avery Seller");

    assertThat(read.id()).isEqualTo(id);
    assertThat(read.operationId()).isEqualTo(operation);
    assertThat(read.orderVersion()).isEqualTo(7L);
    assertThat(read.changedAt()).isEqualTo(at);
    assertThat(read.actor().id()).isEqualTo(actor);
    assertThat(read.actor().displayName()).isEqualTo("Avery Seller");
    assertThat(read.editKey()).isEqualTo("notes");
    assertThat(read.knownEditKey()).isEqualTo("notes");
    assertThat(read.changeKind()).isEqualTo(ChangeKind.SET);
    assertThat(read.oldValue()).isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
    assertThat(read.newValue()).isEqualTo(Value.known("Urgent"));
    assertThat(read.resolution()).isEqualTo(SalesOrderEditResolutionChoice.USE_MINE);
    assertThat(read.resolutionScope()).isEqualTo(ResolutionScope.FIELD);
  }

  @Test
  @DisplayName("R1/H18: a key this version does not know keeps its raw key and every metadata")
  void unknownKeyKeepsTheEntry() {
    UUID id = UUID.randomUUID();
    UUID line = UUID.randomUUID();
    Row row =
        new Row(
            id,
            UUID.randomUUID(),
            4L,
            Instant.parse("2026-10-09T09:30:00Z"),
            UUID.randomUUID(),
            line,
            "line.futureKey",
            "SET",
            null,
            "{\"a\":1}",
            null,
            null);

    Entry read = mapper.entry(row, null);

    assertThat(read.id()).isEqualTo(id);
    assertThat(read.lineId()).isEqualTo(line);
    assertThat(read.orderVersion()).isEqualTo(4L);
    assertThat(read.editKey()).isEqualTo("line.futureKey");
    assertThat(read.knownEditKey()).isNull();
    assertThat(read.oldValue()).isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
    assertThat(read.newValue()).isEqualTo(Value.unavailable(UnavailableReason.UNSUPPORTED_SHAPE));
  }

  @Test
  @DisplayName("R1: the known key catalogue is every edit key wire name and the whole line")
  void knownKeyCatalogue() {
    List<String> expected = new ArrayList<>();
    Arrays.stream(OrderEditKey.values()).map(OrderEditKey::wireName).forEach(expected::add);
    expected.add(OrderEditKey.LINE);
    assertThat(SalesOrderFieldHistoryDtos.EDIT_KEY_CATALOGUE)
        .hasSize(23)
        .containsExactlyElementsOf(expected);
  }

  // ── H13, H25: enums mirror the stored domain enums ────────────────────────

  @Test
  @DisplayName("H13/H25: change kind, resolution and scope match the domain enums one to one")
  void enumsMirrorTheDomain() {
    assertThat(names(ChangeKind.values())).isEqualTo(names(OrderFieldChange.ChangeKind.values()));
    assertThat(names(ResolutionScope.values()))
        .isEqualTo(names(OrderFieldChange.ResolutionScope.values()));
    assertThat(names(SalesOrderEditResolutionChoice.values()))
        .isEqualTo(names(OrderEditMerge.Choice.values()));
  }

  @Test
  @DisplayName("H13: a resolution and its scope come together; a value states why it is missing")
  void wireInvariants() {
    assertThatThrownBy(
            () ->
                mapper.entry(
                    new Row(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1L,
                        Instant.now(),
                        UUID.randomUUID(),
                        null,
                        "notes",
                        "SET",
                        null,
                        "\"x\"",
                        "USE_MINE",
                        null),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Entry(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1L,
                    Instant.now(),
                    new SalesOrderFieldHistoryDtos.Actor(UUID.randomUUID(), null),
                    null,
                    "notes",
                    null,
                    ChangeKind.SET,
                    Value.known(null),
                    Value.known("x"),
                    null,
                    null))
        .as("a catalogue key is always known")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Value(ValueState.UNAVAILABLE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Value(ValueState.KNOWN, "x", UnavailableReason.UNSUPPORTED_SHAPE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static String stored(Object value) {
    try {
      return WRITER.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** Through a tree first, as {@code objectMapper.valueToTree} in the save does. */
  private static String storedAsTree(Object value) {
    JsonNode tree = WRITER.valueToTree(value);
    return stored(tree);
  }

  private static String json(Object value) {
    return stored(value);
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
