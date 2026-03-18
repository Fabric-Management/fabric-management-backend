package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
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
  private final UUID materialId;
  private final UUID locationId;
  private final BigDecimal qtyReleased;

  public ReservationReleasedEvent(
      UUID tenantId, UUID reservationId, UUID materialId, UUID locationId, BigDecimal qtyReleased) {
    super(tenantId, "RESERVATION_RELEASED");
    this.reservationId = reservationId;
    this.materialId = materialId;
    this.locationId = locationId;
    this.qtyReleased = qtyReleased;
  }
}
