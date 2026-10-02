package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;

/**
 * The delivery term agreed for an order: the rule, the named place and the Incoterms edition. All
 * three are absent until a term is agreed; nothing is assumed.
 */
public record DeliveryTerms(DeliveryTerm term, String place, IncotermsVersion version) {

  public static final DeliveryTerms NONE = new DeliveryTerms(null, null, null);
  public static final int MAX_PLACE_LENGTH = 200;

  /**
   * Validates and normalises a term as entered. A term needs its named place; the edition defaults
   * to the current one and must contain the term (DAT is 2010 only, DPU 2020 only). A place or an
   * edition without a term is rejected rather than silently dropped.
   */
  public static DeliveryTerms of(DeliveryTerm term, String place, IncotermsVersion version) {
    String trimmed = place == null || place.isBlank() ? null : place.trim();
    if (term == null) {
      if (trimmed != null || version != null) {
        throw new OrderDomainException("Choose the delivery term the named place belongs to");
      }
      return NONE;
    }
    if (trimmed == null) {
      throw new OrderDomainException("A delivery term needs its named place");
    }
    if (trimmed.length() > MAX_PLACE_LENGTH) {
      throw new OrderDomainException("The named place is too long");
    }
    IncotermsVersion edition = version == null ? IncotermsVersion.CURRENT : version;
    if (!term.existsIn(edition)) {
      throw new OrderDomainException(term + " is not a rule of " + edition);
    }
    return new DeliveryTerms(term, trimmed, edition);
  }

  public boolean isAgreed() {
    return term != null;
  }

  /** The event the order's dates refer to, or null while no term is agreed. */
  public DeliveryEvent event() {
    return term == null ? null : term.event();
  }
}
