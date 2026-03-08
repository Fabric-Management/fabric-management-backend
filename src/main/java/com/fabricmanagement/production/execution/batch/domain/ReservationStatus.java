package com.fabricmanagement.production.execution.batch.domain;

/**
 * Lifecycle states of a {@link BatchReservation}.
 *
 * <pre>
 * ACTIVE ──consume()──▶ PARTIALLY_CONSUMED ──consume()──▶ FULFILLED
 *   │                         │
 *   └──cancel()───────────────┴──cancel()──▶ CANCELLED
 * </pre>
 */
public enum ReservationStatus {
  /** Reservation is active with no consumption yet. */
  ACTIVE,

  /** Some quantity has been consumed but the reservation is not yet fulfilled. */
  PARTIALLY_CONSUMED,

  /** The full reserved quantity has been consumed. Terminal state. */
  FULFILLED,

  /** Reservation was cancelled (remaining quantity released back to batch). */
  CANCELLED
}
