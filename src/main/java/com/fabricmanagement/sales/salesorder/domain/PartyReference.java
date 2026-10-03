package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.util.UUID;

/**
 * Who fills a party role on the order: the customer, another registered partner, or an unregistered
 * party stored as a snapshot. {@link #NONE} means the role is not decided yet.
 */
public record PartyReference(PartyMode mode, UUID partnerId, PartySnapshot snapshot) {

  public static final PartyReference NONE = new PartyReference(null, null, null);

  /**
   * Validates a role as entered. {@code role} names it in messages ("bill-to party", "consignee").
   * A registered partner that is the customer itself is the customer, not another partner.
   */
  public static PartyReference of(
      PartyMode mode, UUID partnerId, PartySnapshot snapshot, UUID customerId, String role) {
    if (mode == null) {
      if (partnerId != null || snapshot != null) {
        throw new OrderDomainException("Choose who the " + role + " is first");
      }
      return NONE;
    }
    return switch (mode) {
      case CUSTOMER -> {
        if (partnerId != null || snapshot != null) {
          throw new OrderDomainException(
              "The customer as " + role + " needs no partner or party details");
        }
        yield new PartyReference(PartyMode.CUSTOMER, null, null);
      }
      case PARTNER -> {
        if (partnerId == null) {
          throw new OrderDomainException("Choose the partner who is the " + role);
        }
        if (snapshot != null) {
          throw new OrderDomainException("A registered partner needs no party details");
        }
        if (partnerId.equals(customerId)) {
          throw new OrderDomainException(
              "That partner is the customer: choose the customer as " + role);
        }
        yield new PartyReference(PartyMode.PARTNER, partnerId, null);
      }
      case SNAPSHOT -> {
        if (snapshot == null) {
          throw new OrderDomainException("Enter the " + role + "'s name and contact details");
        }
        if (partnerId != null) {
          throw new OrderDomainException("An unregistered party has no partner record");
        }
        yield new PartyReference(PartyMode.SNAPSHOT, null, snapshot);
      }
    };
  }

  public boolean isDecided() {
    return mode != null;
  }

  public boolean isCustomer() {
    return mode == PartyMode.CUSTOMER;
  }
}
