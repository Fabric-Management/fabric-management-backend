package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.RequestedDeliveryEvent;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Wire types of the field history read (CEDIT-09 §3.3): saved safe-edit changes of one order,
 * newest first, each with its actor, operation, resulting order version and the value before and
 * after as the history row stored it. Values are never completed from the current order. Decimal
 * amounts, quantities and measures are exact decimal strings. Technical parts of a stored value (a
 * line's allocation digest, a profile's fingerprint) are never returned.
 */
public final class SalesOrderFieldHistoryDtos {

  private SalesOrderFieldHistoryDtos() {}

  public static final int DEFAULT_LIMIT = 30;
  public static final int MAX_LIMIT = 100;
  public static final int MAX_CURSOR_LENGTH = 2048;

  /** The longest stored edit key (the column's length). */
  public static final int MAX_EDIT_KEY_LENGTH = 40;

  /**
   * The edit keys this version knows, as published in {@code SalesOrderFieldHistoryEditKey}: every
   * safe-edit key wire name, then {@code line} for a whole line added or removed.
   */
  public static final List<String> EDIT_KEY_CATALOGUE = editKeyCatalogue();

  private static List<String> editKeyCatalogue() {
    List<String> keys = new ArrayList<>();
    Arrays.stream(OrderEditKey.values()).map(OrderEditKey::wireName).forEach(keys::add);
    keys.add(OrderEditKey.LINE);
    return List.copyOf(keys);
  }

  /** An exact decimal as text: no exponent, no grouping, no leading zeros. */
  public static final String DECIMAL_PATTERN = "^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$";

  private static final String DECIMAL =
      "Exact decimal as text (no exponent); compare as a decimal, never as a float.";

  @Schema(
      name = "SalesOrderFieldHistoryChangeKind",
      enumAsRef = true,
      description =
          "SET: a value was saved. CLEAR: the key was cleared on purpose. LINE_ADDED / LINE_REMOVED:"
              + " a whole line was added or removed; the other side of the change is null.")
  public enum ChangeKind {
    SET,
    CLEAR,
    LINE_ADDED,
    LINE_REMOVED
  }

  @Schema(
      name = "SalesOrderFieldHistoryResolutionScope",
      enumAsRef = true,
      description =
          "FIELD: the conflict decision was made for this key. LINE: it was made for the whole line"
              + " the key belongs to (for example a product change accepted) or for a whole line"
              + " added or removed.")
  public enum ResolutionScope {
    FIELD,
    LINE
  }

  @Schema(
      name = "SalesOrderFieldHistoryValueState",
      enumAsRef = true,
      description =
          "KNOWN: data is the stored value; null data is a saved empty value. UNAVAILABLE: the"
              + " stored value cannot be shown in this version; it is not an empty value.")
  public enum ValueState {
    KNOWN,
    UNAVAILABLE
  }

  @Schema(
      name = "SalesOrderFieldHistoryUnavailableReason",
      enumAsRef = true,
      description =
          "UNSUPPORTED_SHAPE: the stored value does not have the shape this version reads for its"
              + " key. REFERENCE_UNAVAILABLE: a reference inside it (a requirement profile) is"
              + " incomplete and cannot be read.")
  public enum UnavailableReason {
    UNSUPPORTED_SHAPE,
    REFERENCE_UNAVAILABLE
  }

  /** One page of the order's field history. */
  @Schema(
      name = "SalesOrderFieldHistoryPage",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description =
          "Newest first by orderVersion, then id, both descending. Every page of one cursor chain"
              + " shows only changes up to snapshotVersion, so a change saved meanwhile never"
              + " shifts or repeats an entry; reading again without a cursor starts a new chain"
              + " with a new snapshotVersion. One save may continue on the next page.")
  public record Page(
      @ArraySchema(
              arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
              maxItems = MAX_LIMIT)
          List<Entry> items,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              minimum = "0",
              description =
                  "The order version this chain was read up to: the order's version when the"
                      + " first page was read. Order versions also advance by commands that do"
                      + " not write field history, so a higher order version does not mean that"
                      + " there are newer entries.")
          long snapshotVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasMore,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              maxLength = MAX_CURSOR_LENGTH,
              description =
                  "Opaque; send it unchanged as cursor to read the next older page. Null on the"
                      + " last page. It is not an access proof: access is checked on every page.")
          String nextCursor) {

    public Page {
      items = List.copyOf(items);
    }
  }

  /** One saved change of one key, or one whole line added or removed. */
  @Schema(
      name = "SalesOrderFieldHistoryEntry",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Entry(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              format = "uuid",
              description = "The save that made the change; entries of one save share it.")
          UUID operationId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              minimum = "0",
              description = "The order version the save produced.")
          long orderVersion,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "When it was saved; shown only, never used for ordering.")
          Instant changedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Actor actor,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              format = "uuid",
              description =
                  "The line of a line key or of a whole-line change; null for the header.")
          UUID lineId,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              maxLength = MAX_EDIT_KEY_LENGTH,
              description =
                  "The key exactly as stored, also when this version does not know it (then"
                      + " knownEditKey is null and both values are UNAVAILABLE). Never rewritten.")
          String editKey,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "editKey when it is in the published catalogue (SalesOrderFieldHistoryEditKey),"
                      + " otherwise null. It decides the shape of the values: text keys"
                      + " (customerReference, paymentTerms, shippingAddress, billingAddress,"
                      + " shippingMethod, notes, line.productDesc) a string; orderDate, deadline,"
                      + " line.requestedDeliveryDate a calendar date; line.colorId a UUID;"
                      + " line.singleLotRequired a boolean; line.shipmentPreference its enum"
                      + " name; requestedDeliveryDate SalesOrderFieldHistoryRequestedDate;"
                      + " deliveryTerms SalesOrderFieldHistoryDeliveryTerms; agreementContext"
                      + " SalesOrderFieldHistoryAgreement; contact SalesOrderFieldHistoryContact;"
                      + " line.finishedWidth SalesOrderFieldHistoryWidth; line.quantity"
                      + " SalesOrderFieldHistoryQuantity; line.pricing"
                      + " SalesOrderFieldHistoryPricing; line.tolerance"
                      + " SalesOrderFieldHistoryTolerance; line.specification"
                      + " SalesOrderFieldHistorySpecification; line (a whole line)"
                      + " SalesOrderFieldHistoryLine.")
          String knownEditKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChangeKind changeKind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Value oldValue,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Value newValue,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "The conflict decision that decided this change, as stored; null when the save"
                      + " had no conflict here. Null exactly when resolutionScope is null.")
          SalesOrderEditResolutionChoice resolution,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          ResolutionScope resolutionScope) {

    public Entry {
      Objects.requireNonNull(editKey, "An entry has its stored key");
      if (knownEditKey != null && !knownEditKey.equals(editKey)) {
        throw new IllegalArgumentException("A known key is the stored key itself");
      }
      if (knownEditKey == null && EDIT_KEY_CATALOGUE.contains(editKey)) {
        throw new IllegalArgumentException("A catalogue key is always known");
      }
      if ((resolution == null) != (resolutionScope == null)) {
        throw new IllegalArgumentException("A resolution and its scope come together");
      }
    }
  }

  /** Who saved the change. */
  @Schema(
      name = "SalesOrderFieldHistoryActor",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Actor(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "The person's current directory name from their user record in this tenant,"
                      + " not the name at the time of the change; kept when the account was"
                      + " deactivated or closed. Null when no user record of this tenant can be"
                      + " found.")
          String displayName) {}

  /** One side of a change: the stored value, or why it cannot be shown. */
  @Schema(
      name = "SalesOrderFieldHistoryValue",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
      description =
          "KNOWN with data null is a saved empty value (or the absent side of a whole-line"
              + " change). UNAVAILABLE carries no data and always a reason.")
  public record Value(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ValueState state,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "The value in the shape the entry's knownEditKey names.",
              anyOf = {
                RequestedDate.class,
                DeliveryTerms.class,
                Agreement.class,
                Contact.class,
                Width.class,
                Quantity.class,
                Pricing.class,
                Tolerance.class,
                Specification.class,
                Line.class
              })
          Object data,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          UnavailableReason reason) {

    public Value {
      Objects.requireNonNull(state, "A value has a state");
      if (state == ValueState.KNOWN && reason != null) {
        throw new IllegalArgumentException("A known value has no reason");
      }
      if (state == ValueState.UNAVAILABLE && (reason == null || data != null)) {
        throw new IllegalArgumentException("An unavailable value has a reason and no data");
      }
    }

    public static Value known(Object data) {
      return new Value(ValueState.KNOWN, data, null);
    }

    public static Value unavailable(UnavailableReason reason) {
      return new Value(ValueState.UNAVAILABLE, null, reason);
    }
  }

  /** The order's requested date with what the customer meant; all null when unknown. */
  @Schema(
      name = "SalesOrderFieldHistoryRequestedDate",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record RequestedDate(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          RequestedDateStatus status,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Calendar date; no time zone applies.")
          LocalDate date,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          RequestedDeliveryEvent event,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String place) {}

  /** Rule, named place, edition and standing of the delivery term; all null without a term. */
  @Schema(
      name = "SalesOrderFieldHistoryDeliveryTerms",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record DeliveryTerms(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) DeliveryTerm term,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String place,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          IncotermsVersion incotermsVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DeliveryTermStatus status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String contractReference) {}

  @Schema(
      name = "SalesOrderFieldHistoryAgreement",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Agreement(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          AgreementContext context,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String note) {}

  @Schema(
      name = "SalesOrderFieldHistoryContact",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Contact(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String email,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String phone,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean whatsapp) {}

  @Schema(
      name = "SalesOrderFieldHistoryWidth",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Width(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String value,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit) {}

  @Schema(
      name = "SalesOrderFieldHistoryQuantity",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Quantity(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String requestedQty,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit) {}

  @Schema(
      name = "SalesOrderFieldHistoryPricing",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Pricing(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String currency,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String unitPrice,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String discountAmount,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String taxAmount) {}

  @Schema(
      name = "SalesOrderFieldHistoryTolerance",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Tolerance(
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String upPct,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = DECIMAL_PATTERN,
              description = DECIMAL)
          String downPct) {}

  /** Module type, the module specs as saved, and the pinned requirement-profile version. */
  @Schema(
      name = "SalesOrderFieldHistorySpecification",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Specification(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ModuleType moduleType,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              type = "object",
              additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
              description =
                  "Free module specs exactly as saved. Numbers in it carry no unit or exactness"
                      + " guarantee; do not compute with them.")
          JsonNode moduleSpecs,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "The requirement-profile version pinned by the line at that time; null when the"
                      + " line had no profile. Never the profile's current version.")
          ProfileReference requirementProfile) {}

  /** A pinned requirement-profile version. */
  @Schema(
      name = "SalesOrderFieldHistoryProfileReference",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record ProfileReference(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID profileId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int profileVersion) {}

  /**
   * A whole line as saved when it was added or removed: its product and every line key. The current
   * line is never needed to read it; product and colour are references, the saved description is
   * the only product text.
   */
  @Schema(
      name = "SalesOrderFieldHistoryLine",
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Line(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "uuid")
          UUID productId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String productDesc,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "uuid")
          UUID colorId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Width finishedWidth,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Calendar date; no time zone applies.")
          LocalDate requestedDeliveryDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean singleLotRequired,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          LineShipmentPreference shipmentPreference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Quantity quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Pricing pricing,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Tolerance tolerance,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          Specification specification) {}
}
