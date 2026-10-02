package com.fabricmanagement.production.core.workorder.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/** Refusals of the hold flow with the business code the frontend explains (SOI D8, A12). */
public class WorkOrderHoldException extends ProductionDomainException {

  public WorkOrderHoldException(String code, String message, int httpStatus) {
    super(message, "WORK_ORDER_HOLD_" + code, httpStatus);
  }

  public static WorkOrderHoldException notRunning() {
    return new WorkOrderHoldException("NOT_RUNNING", "No running work for this line", 409);
  }

  public static WorkOrderHoldException alreadyRequested() {
    return new WorkOrderHoldException(
        "ALREADY_REQUESTED", "A hold is already requested or in force for this line", 409);
  }

  public static WorkOrderHoldException customerChangeOpen() {
    return new WorkOrderHoldException(
        "CUSTOMER_CHANGE_OPEN", "The customer change behind the hold is not settled", 422);
  }

  public static WorkOrderHoldException checksIncomplete() {
    return new WorkOrderHoldException(
        "CHECKS_INCOMPLETE", "Technical and material checks are not complete", 422);
  }
}
