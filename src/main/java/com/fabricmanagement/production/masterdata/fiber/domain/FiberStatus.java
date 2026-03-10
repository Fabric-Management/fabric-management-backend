package com.fabricmanagement.production.masterdata.fiber.domain;

/**
 * Fiber lifecycle status enumeration.
 *
 * <p>Represents the current state of a fiber in the production lifecycle.
 *
 * <h2>Status Flow:</h2>
 *
 * <pre>
 * ACTIVE → OBSOLETE
 * </pre>
 *
 * <p><b>Note:</b> Masterdata Fiber is a catalog definition. Physical usage tracking is handled by
 * Batch (execution layer). Status here is simplified: ACTIVE = available for use, OBSOLETE =
 * discontinued.
 */
public enum FiberStatus {

  /**
   * Fiber is active and available for use in production.
   *
   * <p>This is the default state for newly created fibers.
   */
  ACTIVE,

  /**
   * Fiber is obsolete, discontinued, or no longer valid.
   *
   * <p>Can be set from ACTIVE when fiber becomes outdated.
   */
  OBSOLETE
}
