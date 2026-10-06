package com.fabricmanagement.sales.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all order domain rule violations. */
public class OrderDomainException extends DomainException {

  public OrderDomainException(String message) {
    super(message, "ORDER_RULE_VIOLATION", 400);
  }

  /**
   * State-conflict variant — allows 409 for edit guard violations. Only to be used for resource
   * state conflicts, not for validation/domain rule failures.
   */
  public OrderDomainException(String message, int httpStatus) {
    super(message, "ORDER_RULE_VIOLATION", httpStatus);
  }

  private OrderDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  /**
   * The order is with planning, so what planning evaluated cannot change until sales withdraws it
   * to the draft with a reason.
   */
  public static OrderDomainException withPlanning(String message) {
    return new OrderDomainException(message, "ORDER_WITH_PLANNING", 409);
  }

  /** Someone else holds the work, or the caller already does. */
  public static OrderDomainException workTaken(String message) {
    return new OrderDomainException(message, "WORK_ALREADY_TAKEN", 409);
  }

  /** The work has no responsible person yet; it has to be claimed or assigned first. */
  public static OrderDomainException workNotClaimed(String message) {
    return new OrderDomainException(message, "WORK_NOT_CLAIMED", 409);
  }

  /** The order's flow stage or status does not allow this step now. */
  public static OrderDomainException stage(String code, String message) {
    return new OrderDomainException(message, code, 409);
  }

  /** A rule the request breaks, with a code the client can tell apart. */
  public static OrderDomainException rule(String code, String message) {
    return new OrderDomainException(message, code, 400);
  }

  /** The request breaks the safe-edit contract (CEDIT-02 §4.2/§4.3): 422 with its own code. */
  public static OrderDomainException invalid(String code, String message) {
    return new OrderDomainException(message, code, 422);
  }

  /** The request conflicts with recorded state other than another user's edit (409). */
  public static OrderDomainException conflict(String code, String message) {
    return new OrderDomainException(message, code, 409);
  }

  /** The server broke its own invariant; the transaction is rolled back (500). */
  public static OrderDomainException internal(String code, String message) {
    return new OrderDomainException(message, code, 500);
  }

  public OrderDomainException(String message, Throwable cause) {
    super(message, "ORDER_RULE_VIOLATION", 400, cause);
  }
}
