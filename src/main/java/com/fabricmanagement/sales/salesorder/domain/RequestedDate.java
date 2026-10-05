package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.LocalDate;

/**
 * The customer's requested date as {@code {date, event, place}} (ADR-0014 D7). The event is what
 * the customer meant (may be {@link RequestedDeliveryEvent#UNSPECIFIED}); the place is where the
 * customer said it should happen ("our producer's warehouse, Manchester") and is kept apart from
 * the delivery term's named place. {@link #UNKNOWN}: not known whether the customer asked for a
 * date.
 */
public record RequestedDate(
    RequestedDateStatus status, LocalDate date, RequestedDeliveryEvent event, String place) {

  public static final int MAX_PLACE = 200;
  public static final RequestedDate UNKNOWN = new RequestedDate(null, null, null, null);
  public static final RequestedDate NOT_REQUESTED =
      new RequestedDate(RequestedDateStatus.NOT_REQUESTED, null, null, null);

  public static RequestedDate of(
      RequestedDateStatus status, LocalDate date, RequestedDeliveryEvent event, String place) {
    String where = Text.trimmed(place);
    if (status == null) {
      if (date != null || event != null || where != null) {
        throw new OrderDomainException("Say whether the customer asked for a date");
      }
      return UNKNOWN;
    }
    if (status == RequestedDateStatus.NOT_REQUESTED) {
      if (date != null || event != null || where != null) {
        throw new OrderDomainException("A date that was not requested has no day, event or place");
      }
      return NOT_REQUESTED;
    }
    if (date == null) {
      throw new OrderDomainException("Enter the date the customer asked for");
    }
    if (event == null) {
      throw new OrderDomainException(
          "Say what the requested date refers to, or record that the customer did not say");
    }
    return new RequestedDate(
        RequestedDateStatus.REQUESTED,
        date,
        event,
        Text.limited(where, MAX_PLACE, "The requested place is too long"));
  }

  public boolean isKnown() {
    return status != null;
  }
}
