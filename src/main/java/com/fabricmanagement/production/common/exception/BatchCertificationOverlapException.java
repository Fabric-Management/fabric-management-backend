package com.fabricmanagement.production.common.exception;

/**
 * Thrown when a batch certification cannot be added or updated because its validity period
 * (validFrom–validUntil) overlaps with an existing active certification for the same batch, cert,
 * and scope. Ensures exactly one validity period per (batch, cert, scope, partner/facility) at a
 * time for GOTS clarity.
 *
 * <h2>HTTP Response — 409 Conflict</h2>
 */
public class BatchCertificationOverlapException extends ProductionDomainException {

  public BatchCertificationOverlapException(String message) {
    super(message, "BATCH_CERTIFICATION_OVERLAP", 409);
  }
}
