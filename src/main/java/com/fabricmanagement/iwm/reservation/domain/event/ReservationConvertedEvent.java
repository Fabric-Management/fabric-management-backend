package com.fabricmanagement.iwm.reservation.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
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

  public ReservationConvertedEvent(
      UUID tenantId, UUID reservationId, UUID productId, UUID locationId, BigDecimal qtyConverted) {
    super(tenantId, "RESERVATION_CONVERTED");
    this.reservationId = reservationId;
    this.productId = productId;
    this.locationId = locationId;
    this.qtyConverted = qtyConverted;
  }
}
