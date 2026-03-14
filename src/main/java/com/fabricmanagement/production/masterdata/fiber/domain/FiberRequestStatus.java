package com.fabricmanagement.production.masterdata.fiber.domain;

/**
 * Fiber request lifecycle status.
 *
 * <p>Represents the state of a tenant-initiated request to add a new fiber to the platform catalog.
 *
 * <h2>Status Flow:</h2>
 *
 * <pre>
 * PENDING   → APPROVED (platform approved)
 * PENDING   → REJECTED (platform rejected)
 * </pre>
 */
public enum FiberRequestStatus {

  /** Tenant submitted the request; awaiting platform review. */
  PENDING,

  /** Platform approved the request; fiber can be added to catalog. */
  APPROVED,

  /** Platform rejected the request. */
  REJECTED
}
