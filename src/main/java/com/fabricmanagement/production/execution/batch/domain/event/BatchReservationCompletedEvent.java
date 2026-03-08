package com.fabricmanagement.production.execution.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class BatchReservationCompletedEvent extends DomainEvent {
  private final UUID batchId;
  private final UUID reservationId;
  private final BigDecimal releasedQuantity;
  private final String unit;
  private final UUID locationId;

  public BatchReservationCompletedEvent(
      UUID tenantId,
      UUID batchId,
      UUID reservationId,
      BigDecimal releasedQuantity,
      String unit,
      UUID locationId) {
    super(tenantId, "BATCH_RESERVATION_COMPLETED");
    this.batchId = batchId;
    this.reservationId = reservationId;
    this.releasedQuantity = releasedQuantity;
    this.unit = unit;
    this.locationId = locationId;
  }
}
