package com.fabricmanagement.finance.payment.domain.exception;

import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import java.math.BigDecimal;

/**
 * Part of an inbound payment is set aside to be given back to the customer, so the payment cannot
 * be spent again or taken back until that refund is cancelled.
 */
public final class PaymentHeldForRefundException extends FinanceDomainException {
  private static final String CODE = "PAYMENT_HELD_FOR_REFUND";
  private static final String SETTLES_CODE = "PAYMENT_SETTLES_REFUND";
  private static final String KEEPS_PAID_CODE = "PAYMENT_KEEPS_PAID_REFUND";

  private PaymentHeldForRefundException(String code, String message) {
    super(message, code, 409);
  }

  private PaymentHeldForRefundException(String code, String message, Object[] args) {
    super(message, code, 409, args);
  }

  /**
   * Voiding or reversing would undo money that is still owed back. A refund nobody has paid is
   * cancelled first; one that was paid cannot be cancelled and is dealt with by {@link
   * #keepsPaidRefund}.
   */
  public static PaymentHeldForRefundException cannotUndo(BigDecimal held) {
    return new PaymentHeldForRefundException(
        CODE,
        "Part of this payment ("
            + held.toPlainString()
            + ") is set aside to be refunded; cancel an unpaid refund before reversing the"
            + " payment. A payment whose refund was already paid is reversed, not voided");
  }

  /**
   * The refunds already paid out of this payment stay as recorded when it is reversed, because that
   * money left. The person confirms the amount the customer will owe before the receipt is taken
   * back; the amount travels as the first argument and as the {@code paidRefund} detail.
   */
  public static PaymentHeldForRefundException keepsPaidRefund(BigDecimal paid) {
    PaymentHeldForRefundException refused =
        new PaymentHeldForRefundException(
            KEEPS_PAID_CODE,
            "Refunds of "
                + paid.toPlainString()
                + " were already paid out of this payment and stay as recorded; confirm the amount"
                + " to reverse it",
            new Object[] {paid.toPlainString()});
    refused.withDetail("paidRefund", paid.toPlainString());
    return refused;
  }

  /** Allocating more than what is not set aside would spend the refund twice. */
  public static PaymentHeldForRefundException cannotSpend(BigDecimal held) {
    return new PaymentHeldForRefundException(
        CODE,
        "Part of this payment ("
            + held.toPlainString()
            + ") is set aside to be refunded"
            + " and cannot be allocated");
  }

  /**
   * An outbound payment that pays a refund has already given its money back to the customer;
   * allocating it to an invoice as well would spend the same money twice.
   */
  public static PaymentHeldForRefundException settlesRefund() {
    return new PaymentHeldForRefundException(
        SETTLES_CODE,
        "This payment pays a refund to the customer and cannot also be allocated to an invoice");
  }
}
