package com.fabricmanagement.production.common.exception;

/**
 * Exception thrown when a state machine transition is not allowed for the given entity.
 *
 * <p>Used across all production execution entities (Batch, YarnBatch, FabricRoll, DyeOrder) to
 * enforce valid lifecycle transitions. The {@code from} and {@code to} status values are included
 * in the error response so the frontend can surface a meaningful message.
 *
 * <h2>HTTP Response — 409 Conflict</h2>
 *
 * <pre>
 * {
 *   "code": "INVALID_STATUS_TRANSITION",
 *   "message": "Batch cannot transition from CONSUMED to AVAILABLE.",
 *   "details": {
 *     "entityType": "Batch",
 *     "from": "CONSUMED",
 *     "to": "AVAILABLE"
 *   }
 * }
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * if (batch.getStatus() == BatchStatus.CONSUMED) {
 *   throw new InvalidStatusTransitionException("Batch", "CONSUMED", "AVAILABLE");
 * }
 * }</pre>
 */
public class InvalidStatusTransitionException extends ProductionDomainException {

  private final String entityType;
  private final String from;
  private final String to;

  public InvalidStatusTransitionException(String entityType, String from, String to) {
    super(buildMessage(entityType, from, to), "INVALID_STATUS_TRANSITION", 409);
    this.entityType = entityType;
    this.from = from;
    this.to = to;
  }

  private static String buildMessage(String entityType, String from, String to) {
    return String.format("%s cannot transition from %s to %s.", entityType, from, to);
  }

  public String getEntityType() {
    return entityType;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }
}
