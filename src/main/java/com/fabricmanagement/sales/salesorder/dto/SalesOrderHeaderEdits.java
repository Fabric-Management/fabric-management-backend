package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header instructions of a safe-edit save (CEDIT-02 §2.2). Only the keys the user changed against
 * the base are sent; an absent key is left as it is. Texts with SET are never blank, CLEAR empties
 * them. Lengths are the columns' own: customer reference 100, payment terms 200, addresses 500,
 * shipping method 50; notes are unlimited.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderHeaderEdits",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class SalesOrderHeaderEdits {

  private SalesOrderTextFieldEdit customerReference;
  private SalesOrderDateFieldEdit orderDate;
  private SalesOrderDateFieldEdit requestedDeliveryDate;
  private SalesOrderDeliveryTermsEdit deliveryTerms;
  private SalesOrderTextFieldEdit paymentTerms;
  private SalesOrderAgreementContextEdit agreementContext;
  private SalesOrderContactEdit contact;
  private SalesOrderTextFieldEdit shippingAddress;
  private SalesOrderTextFieldEdit billingAddress;
  private SalesOrderTextFieldEdit shippingMethod;
  private SalesOrderTextFieldEdit notes;
  private SalesOrderDateFieldEdit deadline;

  @Valid
  @Schema(description = "Customer's purchase order reference, at most 100 characters")
  public SalesOrderTextFieldEdit getCustomerReference() {
    return customerReference;
  }

  public void setCustomerReference(SalesOrderTextFieldEdit customerReference) {
    this.customerReference = present("customerReference", customerReference);
  }

  @Valid
  @Schema(description = "Order date; required, CLEAR is refused")
  public SalesOrderDateFieldEdit getOrderDate() {
    return orderDate;
  }

  public void setOrderDate(SalesOrderDateFieldEdit orderDate) {
    this.orderDate = present("orderDate", orderDate);
  }

  @Valid
  @Schema(description = "The date the customer asked for; an event chosen earlier is kept")
  public SalesOrderDateFieldEdit getRequestedDeliveryDate() {
    return requestedDeliveryDate;
  }

  public void setRequestedDeliveryDate(SalesOrderDateFieldEdit requestedDeliveryDate) {
    this.requestedDeliveryDate = present("requestedDeliveryDate", requestedDeliveryDate);
  }

  @Valid
  @Schema(description = "The delivery term with its place, edition and standing, as one key")
  public SalesOrderDeliveryTermsEdit getDeliveryTerms() {
    return deliveryTerms;
  }

  public void setDeliveryTerms(SalesOrderDeliveryTermsEdit deliveryTerms) {
    this.deliveryTerms = present("deliveryTerms", deliveryTerms);
  }

  @Valid
  @Schema(description = "Agreed payment terms, at most 200 characters")
  public SalesOrderTextFieldEdit getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(SalesOrderTextFieldEdit paymentTerms) {
    this.paymentTerms = present("paymentTerms", paymentTerms);
  }

  @Valid
  @Schema(description = "Where the order was agreed, with its description, as one key")
  public SalesOrderAgreementContextEdit getAgreementContext() {
    return agreementContext;
  }

  public void setAgreementContext(SalesOrderAgreementContextEdit agreementContext) {
    this.agreementContext = present("agreementContext", agreementContext);
  }

  @Valid
  @Schema(description = "The customer's contact person, as one key")
  public SalesOrderContactEdit getContact() {
    return contact;
  }

  public void setContact(SalesOrderContactEdit contact) {
    this.contact = present("contact", contact);
  }

  @Valid
  @Schema(description = "Deprecated shipping address, at most 500 characters")
  public SalesOrderTextFieldEdit getShippingAddress() {
    return shippingAddress;
  }

  public void setShippingAddress(SalesOrderTextFieldEdit shippingAddress) {
    this.shippingAddress = present("shippingAddress", shippingAddress);
  }

  @Valid
  @Schema(description = "Deprecated billing address, at most 500 characters")
  public SalesOrderTextFieldEdit getBillingAddress() {
    return billingAddress;
  }

  public void setBillingAddress(SalesOrderTextFieldEdit billingAddress) {
    this.billingAddress = present("billingAddress", billingAddress);
  }

  @Valid
  @Schema(description = "Deprecated shipping method, at most 50 characters")
  public SalesOrderTextFieldEdit getShippingMethod() {
    return shippingMethod;
  }

  public void setShippingMethod(SalesOrderTextFieldEdit shippingMethod) {
    this.shippingMethod = present("shippingMethod", shippingMethod);
  }

  @Valid
  @Schema(description = "Notes; spaces and line breaks are kept")
  public SalesOrderTextFieldEdit getNotes() {
    return notes;
  }

  public void setNotes(SalesOrderTextFieldEdit notes) {
    this.notes = present("notes", notes);
  }

  @Valid
  @Schema(description = "Customer deadline for all lines")
  public SalesOrderDateFieldEdit getDeadline() {
    return deadline;
  }

  public void setDeadline(SalesOrderDateFieldEdit deadline) {
    this.deadline = present("deadline", deadline);
  }

  /** The instructions the request carried, by edit-key name, in a fixed order. */
  public Map<String, SalesOrderFieldEdit> instructions() {
    Map<String, SalesOrderFieldEdit> instructions = new LinkedHashMap<>();
    put(instructions, "customerReference", customerReference);
    put(instructions, "orderDate", orderDate);
    put(instructions, "requestedDeliveryDate", requestedDeliveryDate);
    put(instructions, "deliveryTerms", deliveryTerms);
    put(instructions, "paymentTerms", paymentTerms);
    put(instructions, "agreementContext", agreementContext);
    put(instructions, "contact", contact);
    put(instructions, "shippingAddress", shippingAddress);
    put(instructions, "billingAddress", billingAddress);
    put(instructions, "shippingMethod", shippingMethod);
    put(instructions, "notes", notes);
    put(instructions, "deadline", deadline);
    return Collections.unmodifiableMap(instructions);
  }

  private static void put(
      Map<String, SalesOrderFieldEdit> instructions, String key, SalesOrderFieldEdit edit) {
    if (edit != null) {
      instructions.put(key, edit);
    }
  }

  /** An explicit null is not "no instruction": it is refused like any unreadable body. */
  private static <T> T present(String name, T value) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null; omit it to leave it unchanged");
    }
    return value;
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderHeaderEdits property: " + name);
  }
}
