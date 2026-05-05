package com.fabricmanagement.procurement.quote.domain;

import java.util.Map;
import java.util.Set;

public enum SupplierQuoteStatus {
  RECEIVED,
  UNDER_REVIEW,
  ACCEPTED,
  REJECTED,
  EXPIRED;

  private static final Map<SupplierQuoteStatus, Set<SupplierQuoteStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(RECEIVED, Set.of(UNDER_REVIEW, ACCEPTED, REJECTED, EXPIRED)),
          Map.entry(UNDER_REVIEW, Set.of(ACCEPTED, REJECTED, EXPIRED)),
          Map.entry(ACCEPTED, Set.of()), // terminal
          Map.entry(REJECTED, Set.of()), // terminal
          Map.entry(EXPIRED, Set.of())); // terminal

  /** Returns true if transition from this status to target is allowed. */
  public boolean canTransitionTo(SupplierQuoteStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }
}
