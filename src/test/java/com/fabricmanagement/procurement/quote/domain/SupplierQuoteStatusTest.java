package com.fabricmanagement.procurement.quote.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupplierQuoteStatusTest {

  @Test
  void testReceivedTransitions() {
    assertTrue(SupplierQuoteStatus.RECEIVED.canTransitionTo(SupplierQuoteStatus.UNDER_REVIEW));
    assertTrue(SupplierQuoteStatus.RECEIVED.canTransitionTo(SupplierQuoteStatus.ACCEPTED));
    assertTrue(SupplierQuoteStatus.RECEIVED.canTransitionTo(SupplierQuoteStatus.REJECTED));

    assertTrue(SupplierQuoteStatus.RECEIVED.canTransitionTo(SupplierQuoteStatus.EXPIRED));
    assertFalse(SupplierQuoteStatus.RECEIVED.canTransitionTo(SupplierQuoteStatus.RECEIVED));
  }

  @Test
  void testUnderReviewTransitions() {
    assertTrue(SupplierQuoteStatus.UNDER_REVIEW.canTransitionTo(SupplierQuoteStatus.ACCEPTED));
    assertTrue(SupplierQuoteStatus.UNDER_REVIEW.canTransitionTo(SupplierQuoteStatus.REJECTED));

    assertFalse(SupplierQuoteStatus.UNDER_REVIEW.canTransitionTo(SupplierQuoteStatus.RECEIVED));
    assertTrue(SupplierQuoteStatus.UNDER_REVIEW.canTransitionTo(SupplierQuoteStatus.EXPIRED));
  }

  @Test
  void testTerminalStates() {
    assertFalse(SupplierQuoteStatus.ACCEPTED.canTransitionTo(SupplierQuoteStatus.RECEIVED));
    assertFalse(SupplierQuoteStatus.REJECTED.canTransitionTo(SupplierQuoteStatus.ACCEPTED));
    assertFalse(SupplierQuoteStatus.EXPIRED.canTransitionTo(SupplierQuoteStatus.ACCEPTED));
  }
}
