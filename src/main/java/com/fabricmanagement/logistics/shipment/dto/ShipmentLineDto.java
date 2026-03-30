package com.fabricmanagement.logistics.shipment.dto;

import com.fabricmanagement.logistics.shipment.domain.ShipmentLine;
import com.fabricmanagement.logistics.shipment.domain.ShipmentLineStatus;
import com.fabricmanagement.logistics.shipment.domain.ShipmentUnit;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShipmentLineDto {
  private UUID id;
  private UUID shipmentId;
  private UUID salesOrderLineId;
  private Integer lineNumber;
  private BigDecimal quantity;
  private BigDecimal totalLoadedQuantity;
  private ShipmentUnit unit;
  private ShipmentLineStatus status;
  private List<ShipmentLineBatchDto> batches;

  public static ShipmentLineDto from(ShipmentLine entity) {
    if (entity == null) {
      return null;
    }
    return ShipmentLineDto.builder()
        .id(entity.getId())
        .shipmentId(entity.getShipmentId())
        .salesOrderLineId(entity.getSalesOrderLineId())
        .lineNumber(entity.getLineNumber())
        .quantity(entity.getQuantity())
        .totalLoadedQuantity(entity.totalLoadedQuantity())
        .unit(entity.getUnit())
        .status(entity.getStatus())
        .batches(
            entity.getBatches() != null
                ? entity.getBatches().stream()
                    .map(ShipmentLineBatchDto::from)
                    .collect(Collectors.toList())
                : Collections.emptyList())
        .build();
  }
}
