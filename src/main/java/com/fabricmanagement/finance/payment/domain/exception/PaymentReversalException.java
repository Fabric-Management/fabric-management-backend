package com.fabricmanagement.finance.payment.domain.exception;

import com.fabricmanagement.finance.common.exception.FinanceDomainException;

/** A payment reversal that the books do not allow, with a stable code the client can branch on. */
public final class PaymentReversalException extends FinanceDomainException {

  private PaymentReversalException(String code, String message, int status) {
    super(message, code, status);
  }

  public static PaymentReversalException conflict(String code, String message) {
    return new PaymentReversalException(code, message, 409);
  }

  public static PaymentReversalException invalid(String message) {
    return new PaymentReversalException("PAYMENT_REVERSAL_INVALID", message, 400);
  }
}
