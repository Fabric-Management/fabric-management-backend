package com.fabricmanagement.logistics.shipment.domain;

/**
 * Shipment lifecycle status.
 *
 * <p>Flow: PENDING → PREPARING → READY → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
 */
public enum ShipmentStatus {
  /** Shipment created, not yet prepared */
  PENDING,

  /** Shipment is being prepared/packed */
  PREPARING,

  /** Ready for pickup by carrier */
  READY,

  /** Picked up by carrier */
  PICKED_UP,

  /** In transit to destination */
  IN_TRANSIT,

  /** Out for delivery (final mile) */
  OUT_FOR_DELIVERY,

  /** Successfully delivered */
  DELIVERED,

  /** Delivery attempt failed */
  DELIVERY_FAILED,

  /** Returned to sender */
  RETURNED,

  /** Shipment cancelled */
  CANCELLED;

  /** Check if shipment is in a terminal state. */
  public boolean isTerminal() {
    return this == DELIVERED || this == RETURNED || this == CANCELLED;
  }

  /** Check if shipment is in transit. */
  public boolean isInTransit() {
    return this == PICKED_UP || this == IN_TRANSIT || this == OUT_FOR_DELIVERY;
  }

  /** Check if shipment can be cancelled. */
  public boolean canCancel() {
    return this == PENDING || this == PREPARING || this == READY;
  }

  /** Check if shipment has been dispatched. */
  public boolean isDispatched() {
    return this == PICKED_UP
        || this == IN_TRANSIT
        || this == OUT_FOR_DELIVERY
        || this == DELIVERED
        || this == DELIVERY_FAILED;
  }

  /** Check if shipment can be deleted (hard/soft). */
  public boolean canDelete() {
    return this == PENDING;
  }
}
