package com.fabricmanagement.procurement.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Thrown for procurement domain rule violations (PurchaseOrder, SubcontractOrder, RFQ). */
public class ProcurementDomainException extends DomainException {

  public ProcurementDomainException(String message) {
    super(message, "PROCUREMENT_RULE_VIOLATION", 400);
  }

  public ProcurementDomainException(String message, Throwable cause) {
    super(message, "PROCUREMENT_RULE_VIOLATION", 400, cause);
  }
}
