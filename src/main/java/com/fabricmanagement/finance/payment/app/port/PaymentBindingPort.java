package com.fabricmanagement.finance.payment.app.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What other finance modules have bound to a payment. Owned by payment and implemented by the
 * module that holds the binding, exactly like {@link InvoicePaymentPort}, so payment never depends
 * on it.
 */
public interface PaymentBindingPort {

  /**
   * True while a cheque clearing relies on the payment. Only that instrument's own flow may release
   * it, so the instrument keeps its evidence; a plain reversal must not.
   */
  boolean isBoundToInstrument(UUID tenantId, UUID paymentId);

  /**
   * True while an active collection match relies on the payment. Voiding would hide the match from
   * every balance without a dated posting, so such a payment is reversed instead.
   */
  boolean hasActiveMatches(UUID tenantId, UUID paymentId);

  /**
   * Refuses, before anything is posted, a release date earlier than the day an active match took
   * effect: the match cannot be undone on a day before it existed. The error names the earliest day
   * that works. Checking first means no allocation or number has been touched when it fails.
   */
  void assertMatchesReleasableOn(UUID tenantId, UUID paymentId, LocalDate effectiveDate);

  /**
   * Refuses, before anything is posted, a release date earlier than the history of a refund the
   * release would move: the refunds this payment settles (they reopen) or the unpaid ones it holds
   * (they are cancelled). A refund's events keep their order in time; the error names the earliest
   * day that works.
   */
  void assertRefundsReleasableOn(UUID tenantId, UUID paymentId, LocalDate effectiveDate);

  /**
   * Reverses every collection match still active on the payment, as of {@code effectiveDate}.
   * Returns how many were reversed. The matches stay in the ledger with their reversal, never
   * deleted.
   */
  int releaseMatches(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason);

  /**
   * Money of this inbound payment that finance has set aside to give back to the customer, whether
   * or not the refund has been paid yet. It cannot be allocated to an invoice or matched to an
   * expectation again, and while any is held the payment cannot be voided or reversed.
   */
  BigDecimal refundHeld(UUID tenantId, UUID paymentId);

  /**
   * The part of {@link #refundHeld} that has already been paid out: that money left, so reversing
   * the inbound payment leaves it as recorded and the customer owes it.
   */
  BigDecimal refundPaid(UUID tenantId, UUID paymentId);

  default boolean holdsRefund(UUID tenantId, UUID paymentId) {
    BigDecimal held = refundHeld(tenantId, paymentId);
    return held != null && held.signum() > 0;
  }

  /**
   * True while this outbound payment is what pays a refund. The money has gone back to the
   * customer, so it cannot also be allocated to an invoice.
   */
  boolean settlesRefund(UUID tenantId, UUID paymentId);

  /**
   * The cheque that cleared this inbound payment was dishonoured or returned, so the money never
   * arrived: refunds of it that nobody has paid yet are no longer owed and are cancelled as of
   * {@code effectiveDate}. A refund that was already paid stays as recorded, because that money did
   * leave. Returns how many were cancelled.
   */
  int cancelUnpaidRefunds(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason);

  /**
   * This outbound payment paid refunds and is being voided or reversed, so those refunds are owed
   * again as of {@code effectiveDate}. Returns how many reopened.
   */
  int reopenRefunds(
      UUID tenantId, UUID paymentId, LocalDate effectiveDate, UUID actor, String reason);
}
