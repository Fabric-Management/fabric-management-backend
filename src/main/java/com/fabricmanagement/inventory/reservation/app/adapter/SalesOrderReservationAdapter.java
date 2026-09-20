package com.fabricmanagement.inventory.reservation.app.adapter;

import com.fabricmanagement.inventory.reservation.domain.StockReservationStatus;
import com.fabricmanagement.inventory.reservation.infra.repository.StockReservationRepository;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReservationPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesOrderReservationAdapter implements SalesOrderReservationPort {
  private final StockReservationRepository reservations;

  @Override
  public boolean hasActiveReservation(UUID lineId) {
    return !reservations
        .findBySalesOrderLineIdAndStatusAndDeletedAtIsNull(lineId, StockReservationStatus.ACTIVE)
        .isEmpty();
  }
}
