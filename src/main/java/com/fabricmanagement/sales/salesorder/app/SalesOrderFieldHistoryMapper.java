package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.RequestedDeliveryEvent;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResolutionChoice;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Actor;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Agreement;
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
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Width;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFieldChangeHistoryRepository.Row;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Projects a stored field-history row onto its wire entry (CEDIT-09 §3.3). The only source of a
 * value is the row itself: nothing is completed from the current order, line, product or profile.
 *
 * <p>The stored key is returned as it is; it is also the entry's {@code knownEditKey} when it is in
 * the published catalogue, otherwise that is null and both values are UNAVAILABLE.
 *
 * <p>Each edit key has one closed shape, read from the JSON the save wrote ({@code
 * OrderEditSnapshot} values, a whole line as {@code OrderEditMerge.projection}). A missing or
 * unexpected field, a wrong type or an unknown enum name makes that one side UNAVAILABLE with
 * UNSUPPORTED_SHAPE; an incomplete requirement-profile reference makes it UNAVAILABLE with
 * REFERENCE_UNAVAILABLE. The entry's metadata always stays, the other side and the other entries
 * are unaffected, and the reason is logged without the value. A saved empty value (SQL or JSON
 * null) is KNOWN with null data, never UNAVAILABLE.
 *
 * <p>Decimals are read exactly (no double on the way) and returned as plain decimal strings. The
 * line's allocation digest and the profile's fingerprint are technical and dropped.
 */
@Slf4j
@Component
public class SalesOrderFieldHistoryMapper {

  /** Reads JSON text with every digit of a decimal kept, trailing zeros included. */
  private static final ObjectReader EXACT =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
          .build()
          .reader();

  private static final String LINE_ID = "lineId";
  private static final String PRODUCT_ID = "productId";
  private static final String ALLOCATION_DIGEST = "allocationDigest";
  private static final String FINGERPRINT = "fingerprint";

  /** The stored fields of a whole line: its id, its product and every line key. */
  private static final Set<String> LINE_FIELDS = lineFields();

  /** The entry of a row; {@code displayName} is the actor's current name, or null. */
  public Entry entry(Row row, String displayName) {
    SalesOrderEditResolutionChoice resolution =
        row.resolution() == null ? null : SalesOrderEditResolutionChoice.valueOf(row.resolution());
    ResolutionScope scope =
        row.resolutionScope() == null ? null : ResolutionScope.valueOf(row.resolutionScope());
    return new Entry(
        row.id(),
        row.operationId(),
        row.orderVersion(),
        row.changedAt(),
        new Actor(row.actorId(), displayName),
        row.lineId(),
        row.editKey(),
        SalesOrderFieldHistoryDtos.EDIT_KEY_CATALOGUE.contains(row.editKey())
            ? row.editKey()
            : null,
        ChangeKind.valueOf(row.changeKind()),
        value(row.id(), row.editKey(), row.oldValue()),
        value(row.id(), row.editKey(), row.newValue()),
        resolution,
        scope);
  }

  /** One side of a change, from the stored JSON text (null for SQL NULL). */
  public Value value(UUID entryId, String editKey, String json) {
    if (!SalesOrderFieldHistoryDtos.EDIT_KEY_CATALOGUE.contains(editKey)) {
      // No shape is known for this key: even a stored null cannot be told apart from "unknown".
      return unavailable(entryId, editKey, UnavailableReason.UNSUPPORTED_SHAPE, "unknown edit key");
    }
    if (json == null) {
      return Value.known(null);
    }
    try {
      JsonNode node = EXACT.readTree(json);
      if (node == null || node.isNull() || node.isMissingNode()) {
        return Value.known(null);
      }
      return Value.known(read(editKey, node));
    } catch (JacksonException unparsable) {
      return unavailable(entryId, editKey, UnavailableReason.UNSUPPORTED_SHAPE, "not JSON");
    } catch (Unreadable unreadable) {
      return unavailable(entryId, editKey, unreadable.reason, unreadable.getMessage());
    }
  }

  private static Value unavailable(
      UUID entryId, String editKey, UnavailableReason reason, String why) {
    log.warn(
        "Field history entry {} ({}): a stored value cannot be shown, {}: {}",
        entryId,
        editKey,
        reason,
        why);
    return Value.unavailable(reason);
  }

  private static Object read(String editKey, JsonNode node) {
    if (OrderEditKey.LINE.equals(editKey)) {
      return line(node);
    }
    OrderEditKey key =
        OrderEditKey.fromWireName(editKey).orElseThrow(() -> unsupported("unknown edit key"));
    return switch (key) {
      case CUSTOMER_REFERENCE,
          PAYMENT_TERMS,
          SHIPPING_ADDRESS,
          BILLING_ADDRESS,
          SHIPPING_METHOD,
          NOTES,
          LINE_PRODUCT_DESC ->
          text(node);
      case ORDER_DATE, DEADLINE, LINE_REQUESTED_DELIVERY_DATE -> date(node);
      case REQUESTED_DELIVERY_DATE -> requestedDate(node);
      case DELIVERY_TERMS -> deliveryTerms(node);
      case AGREEMENT_CONTEXT -> agreement(node);
      case CONTACT -> contact(node);
      case LINE_COLOR -> uuid(node);
      case LINE_FINISHED_WIDTH -> width(node);
      case LINE_SINGLE_LOT_REQUIRED -> bool(node);
      case LINE_SHIPMENT_PREFERENCE -> enumValue(node, LineShipmentPreference.class);
      case LINE_QUANTITY -> quantity(node);
      case LINE_PRICING -> pricing(node);
      case LINE_TOLERANCE -> tolerance(node);
      case LINE_SPECIFICATION -> specification(node);
    };
  }

  // ── composite shapes ──────────────────────────────────────────────────────

  private static RequestedDate requestedDate(JsonNode node) {
    closed(node, Set.of("status", "date", "event", "place"));
    return new RequestedDate(
        nullable(node.get("status"), value -> enumValue(value, RequestedDateStatus.class)),
        nullable(node.get("date"), SalesOrderFieldHistoryMapper::date),
        nullable(node.get("event"), value -> enumValue(value, RequestedDeliveryEvent.class)),
        nullable(node.get("place"), SalesOrderFieldHistoryMapper::text));
  }

  private static DeliveryTerms deliveryTerms(JsonNode node) {
    closed(node, Set.of("term", "place", "incotermsVersion", "status", "contractReference"));
    return new DeliveryTerms(
        nullable(node.get("term"), value -> enumValue(value, DeliveryTerm.class)),
        nullable(node.get("place"), SalesOrderFieldHistoryMapper::text),
        nullable(node.get("incotermsVersion"), value -> enumValue(value, IncotermsVersion.class)),
        nullable(node.get("status"), value -> enumValue(value, DeliveryTermStatus.class)),
        nullable(node.get("contractReference"), SalesOrderFieldHistoryMapper::text));
  }

  private static Agreement agreement(JsonNode node) {
    closed(node, Set.of("context", "note"));
    return new Agreement(
        nullable(node.get("context"), value -> enumValue(value, AgreementContext.class)),
        nullable(node.get("note"), SalesOrderFieldHistoryMapper::text));
  }

  private static Contact contact(JsonNode node) {
    closed(node, Set.of("name", "email", "phone", "whatsapp"));
    return new Contact(
        nullable(node.get("name"), SalesOrderFieldHistoryMapper::text),
        nullable(node.get("email"), SalesOrderFieldHistoryMapper::text),
        nullable(node.get("phone"), SalesOrderFieldHistoryMapper::text),
        bool(node.get("whatsapp")));
  }

  private static Width width(JsonNode node) {
    closed(node, Set.of("value", "unit"));
    return new Width(
        nullable(node.get("value"), SalesOrderFieldHistoryMapper::decimal),
        nullable(node.get("unit"), SalesOrderFieldHistoryMapper::text));
  }

  private static Quantity quantity(JsonNode node) {
    closed(node, Set.of("requestedQty", "unit"));
    return new Quantity(
        nullable(node.get("requestedQty"), SalesOrderFieldHistoryMapper::decimal),
        nullable(node.get("unit"), SalesOrderFieldHistoryMapper::text));
  }

  private static Pricing pricing(JsonNode node) {
    closed(node, Set.of("currency", "unitPrice", "discountAmount", "taxAmount"));
    return new Pricing(
        nullable(node.get("currency"), SalesOrderFieldHistoryMapper::text),
        nullable(node.get("unitPrice"), SalesOrderFieldHistoryMapper::decimal),
        nullable(node.get("discountAmount"), SalesOrderFieldHistoryMapper::decimal),
        nullable(node.get("taxAmount"), SalesOrderFieldHistoryMapper::decimal));
  }

  private static Tolerance tolerance(JsonNode node) {
    closed(node, Set.of("upPct", "downPct"));
    return new Tolerance(
        nullable(node.get("upPct"), SalesOrderFieldHistoryMapper::decimal),
        nullable(node.get("downPct"), SalesOrderFieldHistoryMapper::decimal));
  }

  private static Specification specification(JsonNode node) {
    closed(node, Set.of("moduleType", "moduleSpecs", "requirementProfile"));
    JsonNode specs = node.get("moduleSpecs");
    if (!specs.isNull() && !specs.isObject()) {
      throw unsupported("module specs are not an object");
    }
    return new Specification(
        nullable(node.get("moduleType"), value -> enumValue(value, ModuleType.class)),
        specs.isNull() ? null : specs.deepCopy(),
        nullable(node.get("requirementProfile"), SalesOrderFieldHistoryMapper::profile));
  }

  /**
   * A pinned profile version; the fingerprint is technical and dropped. A reference whose id or
   * version is absent or null, as a field or as a value, is incomplete: REFERENCE_UNAVAILABLE. Any
   * other field or a wrong type is UNSUPPORTED_SHAPE.
   */
  private static ProfileReference profile(JsonNode node) {
    if (!node.isObject()) {
      throw unsupported("not an object");
    }
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    names.remove(FINGERPRINT);
    if (!Set.of("profileId", "profileVersion").containsAll(names)) {
      throw unsupported("unexpected fields");
    }
    JsonNode id = node.get("profileId");
    JsonNode version = node.get("profileVersion");
    if (id == null || id.isNull() || version == null || version.isNull()) {
      throw new Unreadable(
          UnavailableReason.REFERENCE_UNAVAILABLE, "profile reference without id or version");
    }
    if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 1) {
      throw unsupported("profile version is not a positive integer");
    }
    return new ProfileReference(uuid(id), version.intValue());
  }

  /** A whole line as saved: id, product and the ten line keys; the allocation digest is dropped. */
  private static Line line(JsonNode node) {
    closed(node, LINE_FIELDS, Set.of(ALLOCATION_DIGEST));
    return new Line(
        uuid(node.get(LINE_ID)),
        nullable(node.get(PRODUCT_ID), SalesOrderFieldHistoryMapper::uuid),
        nullable(field(node, OrderEditKey.LINE_PRODUCT_DESC), SalesOrderFieldHistoryMapper::text),
        nullable(field(node, OrderEditKey.LINE_COLOR), SalesOrderFieldHistoryMapper::uuid),
        nullable(
            field(node, OrderEditKey.LINE_FINISHED_WIDTH), SalesOrderFieldHistoryMapper::width),
        nullable(
            field(node, OrderEditKey.LINE_REQUESTED_DELIVERY_DATE),
            SalesOrderFieldHistoryMapper::date),
        bool(field(node, OrderEditKey.LINE_SINGLE_LOT_REQUIRED)),
        nullable(
            field(node, OrderEditKey.LINE_SHIPMENT_PREFERENCE),
            value -> enumValue(value, LineShipmentPreference.class)),
        nullable(field(node, OrderEditKey.LINE_QUANTITY), SalesOrderFieldHistoryMapper::quantity),
        nullable(field(node, OrderEditKey.LINE_PRICING), SalesOrderFieldHistoryMapper::pricing),
        nullable(field(node, OrderEditKey.LINE_TOLERANCE), SalesOrderFieldHistoryMapper::tolerance),
        nullable(
            field(node, OrderEditKey.LINE_SPECIFICATION),
            SalesOrderFieldHistoryMapper::specification));
  }

  private static JsonNode field(JsonNode line, OrderEditKey key) {
    return line.get(key.wireName());
  }

  private static Set<String> lineFields() {
    Set<String> fields = new LinkedHashSet<>();
    fields.add(LINE_ID);
    fields.add(PRODUCT_ID);
    Arrays.stream(OrderEditKey.values())
        .filter(OrderEditKey::isLineKey)
        .map(OrderEditKey::wireName)
        .forEach(fields::add);
    return Set.copyOf(fields);
  }

  // ── scalars ───────────────────────────────────────────────────────────────

  private static String text(JsonNode node) {
    if (!node.isTextual()) {
      throw unsupported("not a string");
    }
    return node.textValue();
  }

  /** A calendar date as saved; no time zone is applied. */
  private static LocalDate date(JsonNode node) {
    try {
      return LocalDate.parse(text(node));
    } catch (DateTimeParseException malformed) {
      throw unsupported("not a calendar date");
    }
  }

  private static UUID uuid(JsonNode node) {
    String text = text(node);
    try {
      UUID value = UUID.fromString(text);
      if (!value.toString().equalsIgnoreCase(text)) {
        throw unsupported("not a canonical UUID");
      }
      return value;
    } catch (IllegalArgumentException malformed) {
      throw unsupported("not a UUID");
    }
  }

  private static boolean bool(JsonNode node) {
    if (node == null || !node.isBoolean()) {
      throw unsupported("not a boolean");
    }
    return node.booleanValue();
  }

  /** An exact decimal; a JSON number only, never a string or a float approximation. */
  private static String decimal(JsonNode node) {
    if (!node.isNumber() || node.isFloat() || node.isDouble()) {
      throw unsupported("not an exact number");
    }
    return node.decimalValue().toPlainString();
  }

  private static <E extends Enum<E>> E enumValue(JsonNode node, Class<E> type) {
    String name = text(node);
    try {
      return Enum.valueOf(type, name);
    } catch (IllegalArgumentException unknown) {
      throw unsupported("unknown " + type.getSimpleName());
    }
  }

  /** Null for a JSON null; otherwise the read value. A missing field never gets here. */
  private static <T> T nullable(JsonNode node, Function<JsonNode, T> reader) {
    if (node == null) {
      throw unsupported("missing field");
    }
    return node.isNull() ? null : reader.apply(node);
  }

  // ── closed objects ────────────────────────────────────────────────────────

  private static void closed(JsonNode node, Set<String> expected) {
    closed(node, expected, Set.of());
  }

  /**
   * An object with exactly the expected fields, besides dropped technical ones. A missing field is
   * never read as null: absent is not empty.
   */
  private static void closed(JsonNode node, Set<String> expected, Set<String> dropped) {
    if (!node.isObject()) {
      throw unsupported("not an object");
    }
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    names.removeAll(dropped);
    if (!names.equals(expected)) {
      throw unsupported("unexpected fields");
    }
  }

  private static Unreadable unsupported(String why) {
    return new Unreadable(UnavailableReason.UNSUPPORTED_SHAPE, why);
  }

  /** Why one stored value cannot be shown; carries no value and no stack. */
  private static final class Unreadable extends RuntimeException {
    private final UnavailableReason reason;

    Unreadable(UnavailableReason reason, String why) {
      super(why, null, false, false);
      this.reason = reason;
    }
  }
}
