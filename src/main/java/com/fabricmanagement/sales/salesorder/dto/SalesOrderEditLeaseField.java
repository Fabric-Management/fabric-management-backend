package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Optional;

/**
 * The finite catalogue of what a sales order field lease can cover (CEDIT-07 §3.2): every safe-edit
 * key, by its wire name, and {@code line} for a whole line (its removal). A lease covers one key as
 * a whole: the components of a composite key (a price with its currency, a quantity with its unit,
 * a delivery term with its place) are never leased apart. Header keys take no line id; line keys
 * and {@code line} take the line's id. The published enum values are the wire names.
 */
@Schema(
    name = "SalesOrderEditLeaseField",
    enumAsRef = true,
    description =
        "A leasable key: a safe-edit key by its wire name (header keys such as paymentTerms, line"
            + " keys such as line.pricing), or line for a whole line. Composite keys are leased as"
            + " a whole.")
public enum SalesOrderEditLeaseField {
  CUSTOMER_REFERENCE(OrderEditKey.CUSTOMER_REFERENCE),
  ORDER_DATE(OrderEditKey.ORDER_DATE),
  REQUESTED_DELIVERY_DATE(OrderEditKey.REQUESTED_DELIVERY_DATE),
  DELIVERY_TERMS(OrderEditKey.DELIVERY_TERMS),
  PAYMENT_TERMS(OrderEditKey.PAYMENT_TERMS),
  AGREEMENT_CONTEXT(OrderEditKey.AGREEMENT_CONTEXT),
  CONTACT(OrderEditKey.CONTACT),
  SHIPPING_ADDRESS(OrderEditKey.SHIPPING_ADDRESS),
  BILLING_ADDRESS(OrderEditKey.BILLING_ADDRESS),
  SHIPPING_METHOD(OrderEditKey.SHIPPING_METHOD),
  NOTES(OrderEditKey.NOTES),
  DEADLINE(OrderEditKey.DEADLINE),
  LINE_PRODUCT_DESC(OrderEditKey.LINE_PRODUCT_DESC),
  LINE_COLOR(OrderEditKey.LINE_COLOR),
  LINE_FINISHED_WIDTH(OrderEditKey.LINE_FINISHED_WIDTH),
  LINE_REQUESTED_DELIVERY_DATE(OrderEditKey.LINE_REQUESTED_DELIVERY_DATE),
  LINE_SINGLE_LOT_REQUIRED(OrderEditKey.LINE_SINGLE_LOT_REQUIRED),
  LINE_SHIPMENT_PREFERENCE(OrderEditKey.LINE_SHIPMENT_PREFERENCE),
  LINE_QUANTITY(OrderEditKey.LINE_QUANTITY),
  LINE_PRICING(OrderEditKey.LINE_PRICING),
  LINE_TOLERANCE(OrderEditKey.LINE_TOLERANCE),
  LINE_SPECIFICATION(OrderEditKey.LINE_SPECIFICATION),
  /** The whole line: removing it. Overlaps every key of the same line. */
  LINE(null);

  private final OrderEditKey key;

  SalesOrderEditLeaseField(OrderEditKey key) {
    this.key = key;
  }

  /** The safe-edit key; empty for the whole line. */
  public Optional<OrderEditKey> editKey() {
    return Optional.ofNullable(key);
  }

  /** Whether the lease names a line (a line key or the whole line). */
  public boolean isLineScoped() {
    return key == null || key.isLineKey();
  }

  @JsonValue
  public String wireName() {
    return key == null ? OrderEditKey.LINE : key.wireName();
  }

  /** Strict: an unknown name is refused (400), never mapped to a near match. */
  @JsonCreator
  public static SalesOrderEditLeaseField fromWireName(String name) {
    return Arrays.stream(values())
        .filter(field -> field.wireName().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown lease key: " + name));
  }

  /** The field of a safe-edit key. */
  public static SalesOrderEditLeaseField of(OrderEditKey key) {
    return fromWireName(key.wireName());
  }
}
