package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The event a delivery date refers to, derived from the order's delivery term. A requested date and
 * a committed date of one order always refer to the same event, so they can be compared; a date for
 * one event is never read as another (ready is not shipped, shipped is not delivered).
 */
@Schema(name = "DeliveryEvent", enumAsRef = true)
public enum DeliveryEvent {
  /** EXW: placed at the buyer's disposal at the named place, not loaded on any vehicle. */
  AVAILABLE_FOR_COLLECTION(false),
  /**
   * FCA, CPT, CIP: handed to the carrier at the named point; under FCA at the seller's premises
   * this means loaded on the buyer's vehicle.
   */
  HANDED_TO_CARRIER(false),
  /** FAS: placed alongside the vessel at the named port of shipment. */
  ALONGSIDE_VESSEL(false),
  /** FOB, CFR, CIF: placed on board the vessel at the port of shipment. */
  ON_BOARD_VESSEL(false),
  /** DAP, DDP: at the named destination on the arriving vehicle, ready for unloading. */
  READY_FOR_UNLOADING_AT_DESTINATION(true),
  /** DPU (2020), DAT (2010): unloaded at the named destination. */
  UNLOADED_AT_DESTINATION(true);

  private final boolean atDestination;

  DeliveryEvent(boolean atDestination) {
    this.atDestination = atDestination;
  }

  /** Whether the event happens at the customer's destination (an arrival, not a dispatch). */
  public boolean isAtDestination() {
    return atDestination;
  }
}
