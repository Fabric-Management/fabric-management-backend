package com.fabricmanagement.finance.collection.domain.exception;

import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import java.time.LocalDate;

public final class CollectionRuleException extends FinanceDomainException {
  private CollectionRuleException(String code, String message, int status) {
    super(message, code, status);
  }

  private CollectionRuleException(String code, String message, int status, Object[] args) {
    super(message, code, status, args);
  }

  public static CollectionRuleException conflict(String code, String message) {
    return new CollectionRuleException(code, message, 409);
  }

  public static CollectionRuleException invalid(String message) {
    return new CollectionRuleException("COLLECTION_INVALID", message, 400);
  }

  /** A refused input that has a code of its own, so a screen can say what to change. */
  public static CollectionRuleException invalid(String code, String message) {
    return new CollectionRuleException(code, message, 400);
  }

  /**
   * Undoing money cannot be dated before the day it was matched. {@code earliest} is the first day
   * that works, so the person is told what to enter instead of just that the date is wrong.
   */
  public static CollectionRuleException dateBeforeMatch(LocalDate earliest) {
    CollectionRuleException refused =
        new CollectionRuleException(
            "COLLECTION_DATE_BEFORE_MATCH",
            "The date cannot precede the match it undoes, which took effect on " + earliest,
            400,
            new Object[] {earliest.toString()});
    refused.withDetail("earliestDate", earliest.toString());
    return refused;
  }

  /**
   * Releasing an invoice allocation is posted on the payment's own day. When that day lies in a
   * closed period the refund cannot make room that way; {@code paymentDate} names the day, so the
   * person can refund less or have the period reopened instead of reading a bare "period closed".
   */
  public static CollectionRuleException refundPaymentPeriodClosed(LocalDate paymentDate) {
    CollectionRuleException refused =
        new CollectionRuleException(
            "REFUND_PAYMENT_PERIOD_CLOSED",
            "The payment dated "
                + paymentDate
                + " lies in a closed financial period, so its invoice allocations cannot be"
                + " released",
            409,
            new Object[] {paymentDate.toString()});
    refused.withDetail("paymentDate", paymentDate.toString());
    return refused;
  }

  /**
   * A refund's events keep their order in time, so nothing can be dated before the day its history
   * has reached. {@code earliest} is that day: the person is told which date works instead of
   * having the event moved to a day they never chose.
   */
  public static CollectionRuleException refundDateBeforeHistory(LocalDate earliest) {
    CollectionRuleException refused =
        new CollectionRuleException(
            "REFUND_DATE_BEFORE_HISTORY",
            "The date cannot precede the refund's recorded history, which reaches " + earliest,
            400,
            new Object[] {earliest.toString()});
    refused.withDetail("earliestDate", earliest.toString());
    return refused;
  }
}
