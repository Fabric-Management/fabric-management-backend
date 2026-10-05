package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;

/**
 * The legal entity invoiced for the order (ADR-0014 D4, OD-3a). Invoicing someone other than the
 * customer is a credit decision: the relationship to the customer and the reason are recorded, and
 * finance accepts it before commercial approval (BillToAcceptance, step 1c).
 */
public record BillTo(
    PartyReference party, AddressSnapshot address, BillToRelationship relationship, String reason) {

  public static final int MAX_REASON = 500;
  public static final BillTo NONE = new BillTo(PartyReference.NONE, null, null, null);

  public static BillTo of(
      PartyReference party,
      AddressSnapshot address,
      BillToRelationship relationship,
      String reason) {
    PartyReference who = party == null ? PartyReference.NONE : party;
    String why = Text.trimmed(reason);
    if (!who.isDecided()) {
      if (address != null || relationship != null || why != null) {
        throw new OrderDomainException("Choose who is invoiced first");
      }
      return NONE;
    }
    if (who.isCustomer()) {
      if (relationship != null || why != null) {
        throw new OrderDomainException(
            "A relationship and reason belong to invoicing someone other than the customer");
      }
      return new BillTo(who, address, null, null);
    }
    if (relationship == null || why == null) {
      throw new OrderDomainException(
          "Say how the invoiced party relates to the customer and why it is invoiced");
    }
    return new BillTo(
        who, address, relationship, Text.limited(why, MAX_REASON, "The reason is too long"));
  }

  /** Whether a party other than the customer carries the receivable. */
  public boolean differsFromCustomer() {
    return party.isDecided() && !party.isCustomer();
  }
}
