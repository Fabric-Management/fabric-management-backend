package com.fabricmanagement.sales.salesorder.domain.port;

import java.util.UUID;

public interface SalesOrderReservationPort {
  boolean hasActiveReservation(UUID salesOrderLineId);
}
