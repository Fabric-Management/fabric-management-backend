package com.fabricmanagement.logistics.shipment.dto;

import com.fabricmanagement.logistics.shipment.domain.ShipmentLineBatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShipmentLineBatchDto {
  private UUID id;
  private UUID shipmentLineId;
  private UUID batchId;
  private BigDecimal quantity;
  private String qualityGradeSnapshot;
  private Instant loadedAt;

  public static ShipmentLineBatchDto from(ShipmentLineBatch entity) {
    if (entity == null) {
      return null;
    }
    return ShipmentLineBatchDto.builder()
        .shipmentLineId(entity.getShipmentLine() != null ? entity.getShipmentLine().getId() : null)
        .batchId(entity.getBatchId())
        .quantity(entity.getQuantity())
        .qualityGradeSnapshot(entity.getQualityGradeSnapshot())
        .loadedAt(entity.getLoadedAt())
        .build();
  }
}
