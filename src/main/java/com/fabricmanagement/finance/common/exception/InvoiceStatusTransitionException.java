package com.fabricmanagement.finance.common.exception;

import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;

/**
 * Thrown when an invoice status transition is not allowed.
 *
 * <h2>HTTP Response -- 409 Conflict</h2>
 */
public class InvoiceStatusTransitionException extends FinanceDomainException {

  public InvoiceStatusTransitionException(InvoiceStatus from, InvoiceStatus to) {
    super(
        String.format("Invoice status transition %s -> %s is not allowed", from, to),
        "INVOICE_INVALID_TRANSITION",
        409);
    withDetail("from", from.name());
    withDetail("to", to.name());
  }

  public InvoiceStatusTransitionException(InvoiceStatus from, InvoiceStatus to, String reason) {
    super(
        String.format("Invoice status transition %s -> %s is not allowed: %s", from, to, reason),
        "INVOICE_INVALID_TRANSITION",
        409);
    withDetail("from", from.name());
    withDetail("to", to.name());
    withDetail("reason", reason);
  }
}
