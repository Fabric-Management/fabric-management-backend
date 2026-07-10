package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Stok rezervasyonu tüketime dönüştürüldüğünde yayınlanır.
 *
 * <p>Dinleyenler:
 *
 * <ul>
 *   <li>StockLedger → decrease available, shipment flow
 * </ul>
 */
@Getter
public class ReservationConvertedEvent extends DomainEvent {

  private final UUID reservationId;
  private final UUID productId;
  private final UUID locationId;
  private final BigDecimal qtyConverted;

  @JsonCreator
  public ReservationConvertedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("reservationId") UUID reservationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("qtyConverted") BigDecimal qtyConverted) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RESERVATION_CONVERTED",
        occurredAt,
        correlationId);
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.qtyConverted = qtyConverted;
  }

  public ReservationConvertedEvent(
      UUID tenantId, UUID reservationId, UUID productId, UUID locationId, BigDecimal qtyConverted) {
    super(tenantId, "RESERVATION_CONVERTED");
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.qtyConverted = qtyConverted;
  }
}
