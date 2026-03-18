package com.fabricmanagement.production.execution.workorder.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

public class WorkOrderDomainException extends ProductionDomainException {
  public WorkOrderDomainException(String message) {
    super(message);
  }

  public WorkOrderDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
