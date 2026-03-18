package com.fabricmanagement.procurement.common.exception;

import com.fabricmanagement.sales.common.exception.OrderDomainException;

/** Thrown for procurement domain rule violations (PurchaseOrder, SubcontractOrder, RFQ). */
public class ProcurementDomainException extends OrderDomainException {

  public ProcurementDomainException(String message) {
    super(message);
  }

  public ProcurementDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
