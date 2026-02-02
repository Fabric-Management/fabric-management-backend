package com.fabricmanagement.logistics.shipment.domain;

/** Type of shipment. */
public enum ShipmentType {
  /** Outbound shipment to customer */
  OUTBOUND,

  /** Inbound shipment from supplier */
  INBOUND,

  /** Return shipment from customer */
  RETURN_INBOUND,

  /** Return shipment to supplier */
  RETURN_OUTBOUND,

  /** Internal transfer between locations */
  TRANSFER;

  /** Check if this is an outbound (leaving warehouse) shipment. */
  public boolean isOutbound() {
    return this == OUTBOUND || this == RETURN_OUTBOUND;
  }

  /** Check if this is an inbound (arriving at warehouse) shipment. */
  public boolean isInbound() {
    return this == INBOUND || this == RETURN_INBOUND;
  }
}
