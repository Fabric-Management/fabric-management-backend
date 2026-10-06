package com.fabricmanagement.sales.salesorder.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The fixed catalogue of safe-edit keys (CEDIT-02 §2.2/§2.3). A key is the unit that merges and
 * conflicts as a whole: components that mean something only together (a price in its currency, a
 * delivery term with its place) form one key and are never merged part by part. A key is not a free
 * field path; each one has a named applier in the sales domain.
 */
public enum OrderEditKey {
  CUSTOMER_REFERENCE("customerReference", false, false),
  ORDER_DATE("orderDate", false, true),
  REQUESTED_DELIVERY_DATE("requestedDeliveryDate", false, false),
  DELIVERY_TERMS("deliveryTerms", false, false),
  PAYMENT_TERMS("paymentTerms", false, false),
  AGREEMENT_CONTEXT("agreementContext", false, false),
  CONTACT("contact", false, false),
  SHIPPING_ADDRESS("shippingAddress", false, false),
  BILLING_ADDRESS("billingAddress", false, false),
  SHIPPING_METHOD("shippingMethod", false, false),
  NOTES("notes", false, false),
  DEADLINE("deadline", false, false),
  LINE_PRODUCT_DESC("line.productDesc", true, false),
  LINE_COLOR("line.colorId", true, false),
  LINE_FINISHED_WIDTH("line.finishedWidth", true, false),
  LINE_REQUESTED_DELIVERY_DATE("line.requestedDeliveryDate", true, false),
  LINE_SINGLE_LOT_REQUIRED("line.singleLotRequired", true, true),
  LINE_QUANTITY("line.quantity", true, true),
  LINE_PRICING("line.pricing", true, false),
  LINE_TOLERANCE("line.tolerance", true, false),
  LINE_SPECIFICATION("line.specification", true, true);

  /** The key of a conflict or history row about a whole line (removed, product changed, added). */
  public static final String LINE = "line";

  private final String wireName;
  private final boolean lineKey;
  private final boolean required;

  OrderEditKey(String wireName, boolean lineKey, boolean required) {
    this.wireName = wireName;
    this.lineKey = lineKey;
    this.required = required;
  }

  /** The name a client and a conflict use for this key. */
  public String wireName() {
    return wireName;
  }

  public boolean isLineKey() {
    return lineKey;
  }

  /** A required key has a value at all times; it cannot be cleared. */
  public boolean isRequired() {
    return required;
  }

  public static Optional<OrderEditKey> fromWireName(String name) {
    return Arrays.stream(values()).filter(key -> key.wireName.equals(name)).findFirst();
  }
}
