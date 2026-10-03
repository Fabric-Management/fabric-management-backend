package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The event the customer meant by a requested date (ADR-0014 D7). It keeps the customer's meaning:
 * choosing a delivery term never converts it. The term's own events are included so the two can be
 * compared; {@link #RECEIVED_BY_CONSIGNEE} covers "it must reach my producer by then", which no
 * term describes; {@link #UNSPECIFIED} records that the customer gave a date without saying what it
 * means.
 */
@Schema(name = "RequestedDeliveryEvent", enumAsRef = true)
public enum RequestedDeliveryEvent {
  AVAILABLE_FOR_COLLECTION(DeliveryEvent.AVAILABLE_FOR_COLLECTION),
  HANDED_TO_CARRIER(DeliveryEvent.HANDED_TO_CARRIER),
  ALONGSIDE_VESSEL(DeliveryEvent.ALONGSIDE_VESSEL),
  ON_BOARD_VESSEL(DeliveryEvent.ON_BOARD_VESSEL),
  READY_FOR_UNLOADING_AT_DESTINATION(DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION),
  UNLOADED_AT_DESTINATION(DeliveryEvent.UNLOADED_AT_DESTINATION),
  RECEIVED_BY_CONSIGNEE(null),
  UNSPECIFIED(null);

  private final DeliveryEvent termEvent;

  RequestedDeliveryEvent(DeliveryEvent termEvent) {
    this.termEvent = termEvent;
  }

  /** The delivery-term event this is, or null when no term describes it. */
  public DeliveryEvent termEvent() {
    return termEvent;
  }

  /**
   * Compares with the term's {@code event}. Unknown when the customer did not say what the date
   * means or there is no term; otherwise the same only when both name the same moment.
   */
  public EventComparison compareWith(DeliveryEvent event) {
    if (this == UNSPECIFIED || event == null) {
      return EventComparison.UNKNOWN;
    }
    return termEvent == event ? EventComparison.SAME : EventComparison.DIFFERENT;
  }
}
