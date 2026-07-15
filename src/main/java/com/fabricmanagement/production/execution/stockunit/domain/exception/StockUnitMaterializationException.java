package com.fabricmanagement.production.execution.stockunit.domain.exception;

import java.util.Objects;

/**
 * Raised when a confirmed goods receipt cannot be materialized into physical StockUnits.
 *
 * <p>The reason is deliberately part of both the exception and its message so log-based monitoring
 * can separate expected PO backlog from malformed receipts and genuine processing failures. The
 * current stuck-event metric is event-type based and does not expose this reason as a tag.
 */
public class StockUnitMaterializationException extends StockUnitDomainException {

  public static final String ERROR_CODE = "STOCK_UNIT_MATERIALIZATION_FAILED";

  public enum Reason {
    PO_MATERIALIZATION_PENDING,
    EMPTY_RECEIPT_ITEMS,
    MISSING_SC_OUTPUT_TYPE,
    PROCESSING_FAILED
  }

  private final Reason reason;

  public StockUnitMaterializationException(Reason reason, String message) {
    super(formatMessage(reason, message), ERROR_CODE, 500);
    this.reason = Objects.requireNonNull(reason, "reason must not be null");
  }

  public StockUnitMaterializationException(Reason reason, String message, Throwable cause) {
    super(formatMessage(reason, message), ERROR_CODE, 500, cause);
    this.reason = Objects.requireNonNull(reason, "reason must not be null");
  }

  public Reason getReason() {
    return reason;
  }

  private static String formatMessage(Reason reason, String message) {
    return "StockUnit materialization failed: reason="
        + Objects.requireNonNull(reason, "reason must not be null")
        + ", "
        + Objects.requireNonNull(message, "message must not be null");
  }
}
