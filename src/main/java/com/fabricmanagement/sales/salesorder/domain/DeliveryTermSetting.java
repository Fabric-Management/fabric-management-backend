package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;

/**
 * A delivery term with its standing: proposed, or agreed by a prior contract (reference kept).
 * Agreement by the customer is never entered; it comes only from the customer's approval. The rules
 * match the order-level term ({@link SalesOrder#applyDeliveryTermStatus}).
 */
public record DeliveryTermSetting(
    DeliveryTerms terms, DeliveryTermStatus status, String contractReference) {

  public static final int MAX_CONTRACT_REFERENCE = 200;
  public static final DeliveryTermSetting NONE =
      new DeliveryTermSetting(DeliveryTerms.NONE, null, null);

  public static DeliveryTermSetting of(
      DeliveryTerms terms, DeliveryTermStatus status, String contractReference) {
    DeliveryTerms value = terms == null ? DeliveryTerms.NONE : terms;
    String reference = Text.trimmed(contractReference);
    if (!value.isAgreed()) {
      if (status != null || reference != null) {
        throw new OrderDomainException("Choose the delivery term before its status");
      }
      return NONE;
    }
    DeliveryTermStatus standing = status == null ? DeliveryTermStatus.PROPOSED : status;
    if (standing == DeliveryTermStatus.AGREED_BY_CUSTOMER) {
      throw new OrderDomainException(
          "A term is agreed by the customer only through the customer's approval");
    }
    if (standing == DeliveryTermStatus.AGREED_BY_CONTRACT && reference == null) {
      throw new OrderDomainException("Name the contract that fixed the delivery term");
    }
    if (standing != DeliveryTermStatus.AGREED_BY_CONTRACT && reference != null) {
      throw new OrderDomainException("A contract reference belongs to a term agreed by contract");
    }
    return new DeliveryTermSetting(
        value,
        standing,
        Text.limited(reference, MAX_CONTRACT_REFERENCE, "The contract reference is too long"));
  }

  public boolean isSet() {
    return terms.isAgreed();
  }
}
