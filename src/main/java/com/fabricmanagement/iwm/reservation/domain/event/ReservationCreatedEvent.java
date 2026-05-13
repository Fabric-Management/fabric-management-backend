package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
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
