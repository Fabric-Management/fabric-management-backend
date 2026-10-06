package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Choice;
import com.fabricmanagement.sales.salesorder.domain.OrderEditMerge.Slot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderAgreementContextValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderContactValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDeliveryTermsValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResolution;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldEdit;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldEditOperation;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineEdit;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineEditOperation;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLinePricingValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineQuantityValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineSpecificationValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineToleranceValue;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineWidthValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads a safe-edit request into named instructions and checks the contract rules that need no data
 * (CEDIT-02 §4.2/§4.3): SET needs a non-blank value, CLEAR takes none, required keys are never
 * cleared, line operations carry exactly the ids they need, no line or client id appears twice. The
 * canonical fingerprint keeps intent apart: an absent key, a SET and a CLEAR never hash alike, and
 * false, zero and null stay distinct. Pure; nothing here reads or writes the database.
 */
public final class SalesOrderEditInstructions {

  /** Column lengths of the header texts; the other texts are unlimited. */
  private static final Map<OrderEditKey, Integer> TEXT_LIMITS =
      Map.of(
          OrderEditKey.CUSTOMER_REFERENCE, 100,
          OrderEditKey.PAYMENT_TERMS, 200,
          OrderEditKey.SHIPPING_ADDRESS, 500,
          OrderEditKey.BILLING_ADDRESS, 500,
          OrderEditKey.SHIPPING_METHOD, 50);

  private SalesOrderEditInstructions() {}

  /** One field instruction: the key, whether it clears, and the request's value for SET. */
  public record FieldInstruction(OrderEditKey key, boolean clear, Object value) {}

  /** One line operation with its field instructions. */
  public record LineOperation(
      SalesOrderLineEditOperation operation,
      UUID lineId,
      UUID clientLineId,
      UUID productId,
      Map<OrderEditKey, FieldInstruction> fields) {}

  /** A checked request. */
  public record Parsed(
      UUID operationId,
      UUID baseId,
      Map<OrderEditKey, FieldInstruction> header,
      List<LineOperation> lines,
      Map<Slot, Choice> resolutions,
      String fingerprint) {}

  /**
   * Checks {@code request} for the order in the path and derives its fingerprint, which includes
   * the order id: the same operation id on another order is never the same save.
   */
  public static Parsed parse(UUID orderId, SalesOrderEditRequest request, int maxLineOperations) {
    if (request.getOperationId() == null || request.getBaseId() == null) {
      DomainException failure =
          invalid("VALIDATION_ERROR", "A save names its operation and base", null, null, null);
      if (request.getOperationId() == null) {
        failure.withFieldError("operationId", "must not be null");
      }
      if (request.getBaseId() == null) {
        failure.withFieldError("baseId", "must not be null");
      }
      throw failure;
    }
    List<SalesOrderLineEdit> lineEdits =
        request.getLines() == null ? List.of() : request.getLines();
    if (lineEdits.size() > maxLineOperations) {
      throw invalidAt(
          "lines",
          "A save carries at most " + maxLineOperations + " line operations",
          "lines",
          null,
          null);
    }

    Map<OrderEditKey, FieldInstruction> header = new LinkedHashMap<>();
    if (request.getHeader() != null) {
      request
          .getHeader()
          .instructions()
          .forEach((wire, edit) -> add(header, wire, edit, false, null, null, "header." + wire));
    }

    List<LineOperation> lines = new ArrayList<>();
    Set<UUID> lineIds = new HashSet<>();
    Set<UUID> clientLineIds = new HashSet<>();
    for (int index = 0; index < lineEdits.size(); index++) {
      LineOperation operation = line(lineEdits.get(index), "lines[" + index + "]");
      if (operation.lineId() != null && !lineIds.add(operation.lineId())) {
        throw invalid(
            "LINE_OPERATION_DUPLICATED",
            "A line appears in more than one operation",
            OrderEditKey.LINE,
            operation.lineId(),
            null);
      }
      if (operation.clientLineId() != null && !clientLineIds.add(operation.clientLineId())) {
        throw invalid(
            "CLIENT_LINE_ID_DUPLICATED",
            "A new line's client id appears twice",
            OrderEditKey.LINE,
            null,
            operation.clientLineId());
      }
      lines.add(operation);
    }

    Map<Slot, Choice> resolutions = new LinkedHashMap<>();
    if (request.getResolutions() != null) {
      for (SalesOrderEditResolution resolution : request.getResolutions()) {
        Slot slot = slot(resolution);
        if (resolutions.put(slot, Choice.valueOf(resolution.choice().name())) != null) {
          throw invalid(
              "RESOLUTION_MISMATCH",
              "A conflict is decided twice",
              resolution.key(),
              resolution.lineId(),
              resolution.clientLineId());
        }
      }
    }

    if (header.isEmpty() && lines.isEmpty() && resolutions.isEmpty()) {
      throw invalid("EMPTY_EDIT", "The save carries no change and no decision", null, null, null);
    }
    return new Parsed(
        request.getOperationId(),
        request.getBaseId(),
        Collections.unmodifiableMap(header),
        List.copyOf(lines),
        Collections.unmodifiableMap(resolutions),
        fingerprint(
            orderId, request.getOperationId(), request.getBaseId(), header, lines, request));
  }

  /** One line operation; {@code path} is where it sits in the request, such as lines[0]. */
  private static LineOperation line(SalesOrderLineEdit edit, String path) {
    SalesOrderLineEditOperation operation = edit.getOperation();
    if (operation == null) {
      throw invalidAt(
          path + ".operation", "Choose ADD, UPDATE or REMOVE", OrderEditKey.LINE, null, null);
    }
    UUID lineId = edit.getLineId();
    UUID clientLineId = edit.getClientLineId();
    Map<String, SalesOrderFieldEdit> given =
        edit.getFields() == null ? Map.of() : edit.getFields().instructions();
    Map<OrderEditKey, FieldInstruction> fields = new LinkedHashMap<>();
    switch (operation) {
      case ADD -> {
        if (lineId != null) {
          throw invalidAt(
              path + ".lineId", "A new line has no line id yet", OrderEditKey.LINE, lineId, null);
        }
        if (clientLineId == null) {
          throw invalidAt(
              path + ".clientLineId",
              "A new line needs its client id",
              OrderEditKey.LINE,
              null,
              null);
        }
        if (edit.getProductId() == null) {
          throw invalidAt(
              path + ".productId",
              "A new line names its product",
              OrderEditKey.LINE,
              null,
              clientLineId);
        }
        given.forEach(
            (wire, field) ->
                add(
                    fields,
                    wire,
                    field,
                    true,
                    null,
                    clientLineId,
                    path + ".fields." + jsonName(wire)));
        if (!fields.containsKey(OrderEditKey.LINE_QUANTITY)) {
          throw invalidAt(
              path + ".fields.quantity",
              "A new line needs its quantity",
              OrderEditKey.LINE_QUANTITY.wireName(),
              null,
              clientLineId);
        }
      }
      case UPDATE -> {
        if (lineId == null) {
          throw invalidAt(
              path + ".lineId", "Name the line to change", OrderEditKey.LINE, null, clientLineId);
        }
        if (clientLineId != null || edit.getProductId() != null) {
          throw invalidAt(
              path + (clientLineId != null ? ".clientLineId" : ".productId"),
              "A line's product changes only through the product correction; a client id belongs"
                  + " to a new line",
              OrderEditKey.LINE,
              lineId,
              null);
        }
        given.forEach(
            (wire, field) ->
                add(fields, wire, field, false, lineId, null, path + ".fields." + jsonName(wire)));
        if (fields.isEmpty()) {
          throw invalidAt(
              path + ".fields", "Say what changes on the line", OrderEditKey.LINE, lineId, null);
        }
      }
      case REMOVE -> {
        if (lineId == null) {
          throw invalidAt(
              path + ".lineId", "Name the line to remove", OrderEditKey.LINE, null, clientLineId);
        }
        if (clientLineId != null || edit.getProductId() != null || edit.getFields() != null) {
          throw invalidAt(
              path
                  + (clientLineId != null
                      ? ".clientLineId"
                      : edit.getProductId() != null ? ".productId" : ".fields"),
              "A removal carries only the line id",
              OrderEditKey.LINE,
              lineId,
              null);
        }
      }
    }
    return new LineOperation(
        operation, lineId, clientLineId, edit.getProductId(), Collections.unmodifiableMap(fields));
  }

  private static void add(
      Map<OrderEditKey, FieldInstruction> target,
      String wire,
      SalesOrderFieldEdit edit,
      boolean adding,
      UUID lineId,
      UUID clientLineId,
      String path) {
    OrderEditKey key =
        OrderEditKey.fromWireName(wire)
            .orElseThrow(() -> new IllegalStateException("Unmapped edit key " + wire));
    SalesOrderFieldEditOperation operation = edit.getOperation();
    if (operation == null) {
      throw invalidAt(path + ".operation", "Choose SET or CLEAR", wire, lineId, clientLineId);
    }
    if (operation == SalesOrderFieldEditOperation.CLEAR) {
      if (adding) {
        throw invalid(
            "CLEAR_NOT_ALLOWED_ON_ADD", "A new line only sets values", wire, lineId, clientLineId);
      }
      if (edit.valueGiven()) {
        throw invalid("CLEAR_TAKES_NO_VALUE", "CLEAR takes no value", wire, lineId, clientLineId);
      }
      if (key.isRequired()) {
        throw invalid(
            "REQUIRED_FIELD_CANNOT_BE_CLEARED",
            "This field always has a value",
            wire,
            lineId,
            clientLineId);
      }
      target.put(key, new FieldInstruction(key, true, null));
      return;
    }
    Object value = edit.givenValue();
    if (value == null) {
      throw invalid("SET_REQUIRES_VALUE", "SET needs a value", wire, lineId, clientLineId);
    }
    if (isBlank(value)) {
      throw invalid(
          "BLANK_VALUE_USE_CLEAR",
          "An empty value is not saved with SET; use CLEAR",
          wire,
          lineId,
          clientLineId);
    }
    // The parts a composite needs, checked here and not by bean validation, so that an empty value
    // is answered BLANK_VALUE_USE_CLEAR first (CEDIT-02 §4.2).
    MissingPart missing = missingRequiredPart(value);
    if (missing != null) {
      throw invalidAt(
          path + ".value" + missing.part(), missing.message(), wire, lineId, clientLineId);
    }
    if (value instanceof String text
        && TEXT_LIMITS.containsKey(key)
        && text.length() > TEXT_LIMITS.get(key)) {
      throw invalidAt(
          path + ".value",
          "At most " + TEXT_LIMITS.get(key) + " characters",
          wire,
          lineId,
          clientLineId);
    }
    if (value instanceof SalesOrderContactValue contact
        && Boolean.TRUE.equals(contact.whatsapp())
        && blank(contact.phone())) {
      throw invalid(
          "CONTACT_WHATSAPP_NEEDS_PHONE",
          "WhatsApp notifications need the contact's phone number",
          wire,
          lineId,
          clientLineId);
    }
    target.put(key, new FieldInstruction(key, false, value));
  }

  /** Blank text, or a composite value whose every part is empty. */
  private static boolean isBlank(Object value) {
    return switch (value) {
      case String text -> text.isBlank();
      case SalesOrderDeliveryTermsValue terms ->
          terms.term() == null
              && blank(terms.place())
              && terms.incotermsVersion() == null
              && terms.status() == null
              && blank(terms.contractReference());
      case SalesOrderAgreementContextValue context ->
          context.context() == null && blank(context.note());
      case SalesOrderContactValue contact ->
          blank(contact.name())
              && blank(contact.email())
              && blank(contact.phone())
              && !Boolean.TRUE.equals(contact.whatsapp());
      case SalesOrderLineWidthValue width -> width.value() == null && blank(width.unit());
      case SalesOrderLineQuantityValue quantity ->
          quantity.requestedQty() == null && blank(quantity.unit());
      case SalesOrderLinePricingValue pricing ->
          blank(pricing.currency())
              && pricing.unitPrice() == null
              && pricing.discountAmount() == null
              && pricing.taxAmount() == null;
      case SalesOrderLineToleranceValue tolerance ->
          tolerance.upPct() == null && tolerance.downPct() == null;
      case SalesOrderLineSpecificationValue specification ->
          specification.moduleType() == null
              && specification.moduleSpecs() == null
              && specification.requirementProfile() == null;
      default -> false;
    };
  }

  /**
   * The JSON property of a line key inside {@code fields}: the edit key without its {@code line.}
   * prefix ({@code line.quantity} is sent as {@code fields.quantity}).
   */
  private static String jsonName(String wire) {
    return wire.startsWith(OrderEditKey.LINE + ".")
        ? wire.substring(OrderEditKey.LINE.length() + 1)
        : wire;
  }

  /** A part a composite lacks: its path below the value ({@code .term}) and the message. */
  private record MissingPart(String part, String message) {}

  /** What a non-blank composite still lacks, or null when it is complete. */
  private static MissingPart missingRequiredPart(Object value) {
    return switch (value) {
      case SalesOrderDeliveryTermsValue terms when terms.term() == null ->
          new MissingPart(".term", "Choose the delivery term");
      case SalesOrderDeliveryTermsValue terms when blank(terms.place()) ->
          new MissingPart(".place", "A delivery term needs its named place");
      case SalesOrderAgreementContextValue context when context.context() == null ->
          new MissingPart(".context", "Choose where the order was agreed");
      case SalesOrderContactValue contact when contact.whatsapp() == null ->
          new MissingPart(".whatsapp", "Say whether the contact may be notified on WhatsApp");
      case SalesOrderLineQuantityValue quantity when quantity.requestedQty() == null ->
          new MissingPart(".requestedQty", "Requested quantity is required");
      case SalesOrderLineQuantityValue quantity when blank(quantity.unit()) ->
          new MissingPart(".unit", "Unit is required");
      case SalesOrderLineWidthValue width when width.value() == null ->
          new MissingPart(".value", "Finished width and its unit are given together");
      case SalesOrderLineWidthValue width when blank(width.unit()) ->
          new MissingPart(".unit", "Finished width and its unit are given together");
      default -> null;
    };
  }

  /** The header texts with a length limit, by key: the limit the schema publishes too. */
  static Map<OrderEditKey, Integer> textLimits() {
    return TEXT_LIMITS;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Slot slot(SalesOrderEditResolution resolution) {
    String key = resolution.key();
    if (!OrderEditKey.LINE.equals(key) && OrderEditKey.fromWireName(key).isEmpty()) {
      throw invalid(
          "RESOLUTION_MISMATCH",
          "There is no such conflict to decide",
          key,
          resolution.lineId(),
          resolution.clientLineId());
    }
    return new Slot(key, resolution.lineId(), resolution.clientLineId());
  }

  /** Canonical content of the save: what it asks, never how the JSON happened to be written. */
  private static String fingerprint(
      UUID orderId,
      UUID operationId,
      UUID baseId,
      Map<OrderEditKey, FieldInstruction> header,
      List<LineOperation> lines,
      SalesOrderEditRequest request) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("orderId", orderId);
    view.put("operationId", operationId);
    view.put("baseId", baseId);
    view.put("header", fieldsView(header));
    List<Object> lineViews = new ArrayList<>();
    for (LineOperation line : lines) {
      Map<String, Object> lineView = new LinkedHashMap<>();
      lineView.put("operation", line.operation().name());
      lineView.put("lineId", line.lineId());
      lineView.put("clientLineId", line.clientLineId());
      lineView.put("productId", line.productId());
      lineView.put("fields", fieldsView(line.fields()));
      lineViews.add(lineView);
    }
    view.put("lines", lineViews);
    List<Object> resolutionViews = new ArrayList<>();
    if (request.getResolutions() != null) {
      for (SalesOrderEditResolution resolution : request.getResolutions()) {
        Map<String, Object> resolutionView = new LinkedHashMap<>();
        resolutionView.put("key", resolution.key());
        resolutionView.put("lineId", resolution.lineId());
        resolutionView.put("clientLineId", resolution.clientLineId());
        resolutionView.put("choice", resolution.choice().name());
        resolutionViews.add(resolutionView);
      }
    }
    view.put("resolutions", resolutionViews);
    return CanonicalJsonFingerprint.of(view);
  }

  /**
   * What the request itself sent per conflict slot, before anything is resolved against a base: a
   * key's SET/CLEAR and wire value, and a whole line's operation with its fields. A resolution of a
   * conflict is checked against these (CEDIT-02 §5.7): USE_MINE sends the same instruction again,
   * NEW_VALUE a different one, whatever the base the value is completed from.
   */
  public static Map<Slot, String> instructionTokens(Parsed parsed) {
    Map<Slot, String> tokens = new LinkedHashMap<>();
    parsed
        .header()
        .forEach((key, instruction) -> tokens.put(Slot.header(key), fieldToken(instruction)));
    for (LineOperation line : parsed.lines()) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("operation", line.operation().name());
      switch (line.operation()) {
        case UPDATE -> {
          line.fields()
              .forEach(
                  (key, instruction) ->
                      tokens.put(Slot.line(key, line.lineId()), fieldToken(instruction)));
          view.put("fields", fieldsView(line.fields()));
          tokens.put(Slot.wholeLine(line.lineId()), CanonicalJsonFingerprint.of(view));
        }
        case REMOVE -> tokens.put(Slot.wholeLine(line.lineId()), CanonicalJsonFingerprint.of(view));
        case ADD -> {
          view.put("productId", line.productId());
          view.put("fields", fieldsView(line.fields()));
          tokens.put(Slot.addedLine(line.clientLineId()), CanonicalJsonFingerprint.of(view));
        }
      }
    }
    return Collections.unmodifiableMap(tokens);
  }

  private static String fieldToken(FieldInstruction instruction) {
    return CanonicalJsonFingerprint.of(fieldView(instruction));
  }

  private static Map<String, Object> fieldView(FieldInstruction instruction) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("operation", instruction.clear() ? "CLEAR" : "SET");
    if (!instruction.clear()) {
      field.put("value", instruction.value());
    }
    return field;
  }

  private static Map<String, Object> fieldsView(Map<OrderEditKey, FieldInstruction> fields) {
    Map<String, Object> view = new LinkedHashMap<>();
    fields.forEach((key, instruction) -> view.put(key.wireName(), fieldView(instruction)));
    return view;
  }

  /**
   * A VALIDATION_ERROR (422) of one request field: where it is as {@code key}/{@code lineId}, and
   * its request path in the problem's {@code errors}, as bean validation reports a field.
   */
  static DomainException invalidAt(
      String path, String message, String key, UUID lineId, UUID clientLineId) {
    return invalid("VALIDATION_ERROR", message, key, lineId, clientLineId)
        .withFieldError(path, message);
  }

  /** A contract violation of the request (422) naming where it is. */
  static DomainException invalid(
      String code, String message, String key, UUID lineId, UUID clientLineId) {
    DomainException failure = OrderDomainException.invalid(code, message);
    return withPlace(failure, key, lineId, clientLineId);
  }

  /** Adds where a failure is: the edit key and the line it is about. */
  static DomainException withPlace(
      DomainException failure, String key, UUID lineId, UUID clientLineId) {
    if (key != null && !failure.getDetails().containsKey("key")) {
      failure.withDetail("key", key);
    }
    if (lineId != null && !failure.getDetails().containsKey("lineId")) {
      failure.withDetail("lineId", lineId);
    }
    if (clientLineId != null && !failure.getDetails().containsKey("clientLineId")) {
      failure.withDetail("clientLineId", clientLineId);
    }
    return failure;
  }
}
