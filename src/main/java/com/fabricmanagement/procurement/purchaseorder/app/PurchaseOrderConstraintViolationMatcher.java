package com.fabricmanagement.procurement.purchaseorder.app;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/** Identifies database constraints shared by purchase-order race handlers. */
public final class PurchaseOrderConstraintViolationMatcher {

  public static final String ACTIVE_SUPPLIER_QUOTE_CONSTRAINT =
      "uq_purchase_order_tenant_supplier_quote_active";

  private PurchaseOrderConstraintViolationMatcher() {}

  public static boolean isActiveSupplierQuoteViolation(DataIntegrityViolationException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof ConstraintViolationException constraintViolation
          && ACTIVE_SUPPLIER_QUOTE_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
        return true;
      }

      String message = current.getMessage();
      if (message != null && message.contains(ACTIVE_SUPPLIER_QUOTE_CONSTRAINT)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
