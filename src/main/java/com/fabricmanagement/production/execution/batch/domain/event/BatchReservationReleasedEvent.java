package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchReservationReleasedEvent extends DomainEvent {
  private final UUID batchId;
  private final BigDecimal releasedQuantity;
  private final String unit;
  private final UUID locationId;
  private final UUID reservationId;

  public BatchReservationReleasedEvent(
      UUID tenantId,
      UUID batchId,
      BigDecimal releasedQuantity,
      String unit,
      UUID locationId,
      UUID reservationId) {
    super(tenantId, "BATCH_RESERVATION_RELEASED");
    this.batchId = batchId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
    this.reservationId = reservationId;
  }
}
