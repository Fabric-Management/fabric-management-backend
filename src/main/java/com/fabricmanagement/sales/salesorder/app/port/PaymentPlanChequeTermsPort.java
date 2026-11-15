package com.fabricmanagement.sales.salesorder.app.port;

import java.util.UUID;

/**
 * What a payment plan needs to agree the own / endorsed cheque share (PAYMENT-PLAN-7 R1, R2): the
 * tenant switch and the customer's default. The payment-plan code reads them only through this
 * port, never tenant settings or partner rows directly.
 */
public interface PaymentPlanChequeTermsPort {

  /** A customer default that was never set is read as all own cheques. */
  int UNSET_DEFAULT_OWN_CHEQUE_PERCENT = 100;

  /**
   * Whether the tenant agrees the split on plans with a cheque row. Fails rather than reading "off"
   * when the tenant's settings cannot be read, because "off" would skip a required term.
   */
  boolean chequeSplitTracked(UUID tenantId);

  /** The customer's default own share, 0–100, resolved: 100 when never set. */
  int defaultOwnChequePercent(UUID tenantId, UUID partnerId);
}
