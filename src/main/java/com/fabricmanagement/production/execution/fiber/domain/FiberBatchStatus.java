package com.fabricmanagement.production.execution.fiber.domain;

import java.util.Set;

/**
 * Lifecycle statuses for a FiberBatch.
 *
 * <pre>
 * AVAILABLE ──reserve()──▶ RESERVED ──markInUse()──▶ IN_PROGRESS ──markDepleted()──▶ DEPLETED
 *     │                       │                           │
 *     └──consume()────────────┴─────consume()─────────────┘
 *
 * AVAILABLE  : Batch received, no allocation yet. Stock is freely available.
 * RESERVED   : Quantity committed to a production order (reserve() called).
 * IN_PROGRESS: Batch is actively being consumed on the production floor.
 * DEPLETED   : All quantity consumed. Batch is closed (terminal state).
 * </pre>
 */
public enum FiberBatchStatus {
  AVAILABLE,
  RESERVED,
  IN_PROGRESS,
  DEPLETED;

  /**
   * Statuses that indicate a fiber is committed to or actively used in production.
   *
   * <p>Used by {@code FiberService.deactivateFiber()} to block deactivation of a fiber that still
   * has live batches on the production floor.
   */
  public static final Set<FiberBatchStatus> PRODUCTION_ACTIVE = Set.of(RESERVED, IN_PROGRESS);
}
