package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Stok rezervasyonu serbest bırakıldığında yayınlanır.
 *
 * <p>Dinleyenler:
 *
 * <ul>
 *   <li>StockLedger listener → reserved_quantity azalt
 * </ul>
 */
@Getter
public class ReservationReleasedEvent extends DomainEvent {

  private final UUID reservationId;
  private final UUID productId;
  private final UUID locationId;
  private final BigDecimal qtyReleased;

  @JsonCreator
  public ReservationReleasedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("reservationId") UUID reservationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("qtyReleased") BigDecimal qtyReleased) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RESERVATION_RELEASED",
        occurredAt,
        correlationId);
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.qtyReleased = qtyReleased;
  }

  public ReservationReleasedEvent(
      UUID tenantId, UUID reservationId, UUID productId, UUID locationId, BigDecimal qtyReleased) {
    super(tenantId, "RESERVATION_RELEASED");
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.qtyReleased = qtyReleased;
  }
}
