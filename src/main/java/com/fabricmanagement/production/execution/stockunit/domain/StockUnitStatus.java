package com.fabricmanagement.production.execution.stockunit.domain;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle statuses for a {@link StockUnit} (physical inventory unit).
 *
 * <h2>State Machine</h2>
 *
 * <pre>
 *                     ┌──────────┐
 *          ┌─────────│ AVAILABLE │◄─────────────┐
 *          │         └─────┬────┘               │
 *          │               │                     │
 *     ┌────▼────┐    ┌────▼─────┐         ┌────┴──────┐
 *     │ ON_HOLD │    │ RESERVED │         │  PARTIAL   │
 *     └────┬────┘    └────┬─────┘         │(kısmi kul.)│
 *          │              │               └────┬──────┘
 *          │         ┌────▼─────┐              │
 *          │         │IN_TRANSIT│              │
 *          │         └────┬─────┘              │
 *          │              │                     │
 *          │         ┌────▼─────────────────────▼──┐
 *          └────────►│         DEPLETED            │ (terminal)
 *                    └─────────────────────────────┘
 *
 *     ┌───────────┐    ┌───────────┐
 *     │ QUARANTINE│    │ DISPOSED  │ (terminal — waste/scrap)
 *     └───────────┘    └───────────┘
 * </pre>
 *
 * <h2>Transition Rules</h2>
 *
 * <ul>
 *   <li>AVAILABLE → RESERVED, ON_HOLD, QUARANTINE, PARTIAL, IN_TRANSIT, DEPLETED
 *   <li>PARTIAL → AVAILABLE, ON_HOLD, QUARANTINE, DEPLETED
 *   <li>RESERVED → AVAILABLE, IN_TRANSIT, ON_HOLD
 *   <li>IN_TRANSIT → AVAILABLE, PARTIAL
 *   <li>ON_HOLD → AVAILABLE, QUARANTINE, DISPOSED
 *   <li>QUARANTINE → AVAILABLE, ON_HOLD, DISPOSED
 *   <li>DEPLETED → (terminal — no transitions)
 *   <li>DISPOSED → (terminal — no transitions)
 * </ul>
 *
 * <h2>Batch and quality gates</h2>
 *
 * <p>StockUnit statuses are independent of the parent Batch status and quality disposition.
 * Identified-unit operations enforce quality through {@link QualityDisposition}; true operational
 * batch states such as ON_HOLD remain service-layer gates. Statuses are not cascaded across all
 * units.
 *
 * @see StockUnit
 */
public enum StockUnitStatus {

  /**
   * Stock unit is available for consumption, reservation, or transfer.
   *
   * <p>Default status for newly created stock units after goods receipt confirmation.
   */
  AVAILABLE,

  /**
   * Stock unit has been partially consumed. Some weight remains.
   *
   * <p>Automatically set when {@code currentWeight < initialWeight && currentWeight > 0}.
   */
  PARTIAL,

  /**
   * Stock unit is reserved for a specific production order or shipment.
   *
   * <p>Reserved units cannot be consumed by other orders.
   */
  RESERVED,

  /**
   * Stock unit is in transit between warehouse locations.
   *
   * <p>Set when a transfer is initiated; transitions to AVAILABLE or PARTIAL on arrival.
   */
  IN_TRANSIT,

  /**
   * Stock unit is manually held — investigation, dispute, or audit in progress.
   *
   * <p>No consumption or transfer allowed while on hold.
   */
  ON_HOLD,

  /**
   * Stock unit is quarantined — suspected quality issue, pending review.
   *
   * <p>Requires QC Manager (Trust Level 3) to release.
   */
  QUARANTINE,

  /**
   * All weight consumed. Terminal state — no further transitions allowed.
   *
   * <p>Automatically set when {@code currentWeight == 0} after consumption.
   */
  DEPLETED,

  /**
   * Stock unit disposed (waste, scrap, or destruction). Terminal state.
   *
   * <p>Requires Admin (Trust Level 4) authorization.
   */
  DISPOSED;

  /** Terminal statuses — no outgoing transitions. */
  public static final Set<StockUnitStatus> TERMINAL = Set.of(DEPLETED, DISPOSED);

  /** Statuses that allow consumption operations. */
  public static final Set<StockUnitStatus> CONSUMABLE = Set.of(AVAILABLE, PARTIAL);

  /** Statuses that block any operational activity (consume, transfer, reserve). */
  public static final Set<StockUnitStatus> BLOCKED =
      Set.of(ON_HOLD, QUARANTINE, DEPLETED, DISPOSED);

  /**
   * State machine transition rules.
   *
   * <p>Each entry maps a source status to the set of valid target statuses. Terminal statuses map
   * to an empty set.
   */
  private static final Map<StockUnitStatus, Set<StockUnitStatus>> VALID_TRANSITIONS =
      Map.ofEntries(
          Map.entry(
              AVAILABLE, Set.of(RESERVED, ON_HOLD, QUARANTINE, PARTIAL, IN_TRANSIT, DEPLETED)),
          Map.entry(PARTIAL, Set.of(AVAILABLE, ON_HOLD, QUARANTINE, DEPLETED)),
          Map.entry(RESERVED, Set.of(AVAILABLE, IN_TRANSIT, ON_HOLD)),
          Map.entry(IN_TRANSIT, Set.of(AVAILABLE, PARTIAL)),
          Map.entry(ON_HOLD, Set.of(AVAILABLE, QUARANTINE, DISPOSED)),
          Map.entry(QUARANTINE, Set.of(AVAILABLE, ON_HOLD, DISPOSED)),
          Map.entry(DEPLETED, Set.of()),
          Map.entry(DISPOSED, Set.of()));

  /**
   * Checks whether a transition from this status to the target status is allowed.
   *
   * @param target the desired target status
   * @return true if transition is valid per the state machine rules
   */
  public boolean canTransitionTo(StockUnitStatus target) {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }

  /**
   * Returns the set of statuses reachable from this status.
   *
   * @return unmodifiable set of valid target statuses
   */
  public Set<StockUnitStatus> allowedTransitions() {
    return VALID_TRANSITIONS.getOrDefault(this, Set.of());
  }

  /**
   * Returns true if this is a terminal status (no outgoing transitions).
   *
   * @return true for DEPLETED and DISPOSED
   */
  public boolean isTerminal() {
    return TERMINAL.contains(this);
  }

  /**
   * Returns true if this status allows consumption operations.
   *
   * @return true for AVAILABLE and PARTIAL
   */
  public boolean isConsumable() {
    return CONSUMABLE.contains(this);
  }
}
