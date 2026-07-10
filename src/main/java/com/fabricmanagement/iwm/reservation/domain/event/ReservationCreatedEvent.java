package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Stok rezervasyonu oluşturulduğunda yayınlanır.
 *
 * <p>Dinleyenler:
 *
 * <ul>
 *   <li>StockLedger listener → reserved_quantity artır
 * </ul>
 */
@Getter
public class ReservationCreatedEvent extends DomainEvent {

  private final UUID reservationId;
  private final UUID productId;
  private final UUID locationId;
  private final String lotNumber;
  private final BigDecimal qtyReserved;

  @JsonCreator
  public ReservationCreatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("reservationId") UUID reservationId,
      @JsonProperty("productId") UUID productId,
      @JsonProperty("locationId") UUID locationId,
      @JsonProperty("lotNumber") String lotNumber,
      @JsonProperty("qtyReserved") BigDecimal qtyReserved) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RESERVATION_CREATED",
        occurredAt,
        correlationId);
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.lotNumber = lotNumber;
    this.qtyReserved = qtyReserved;
  }

  public ReservationCreatedEvent(
      UUID tenantId,
      UUID reservationId,
      UUID productId,
      UUID locationId,
      String lotNumber,
      BigDecimal qtyReserved) {
    super(tenantId, "RESERVATION_CREATED");
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.lotNumber = lotNumber;
    this.qtyReserved = qtyReserved;
  }
}
