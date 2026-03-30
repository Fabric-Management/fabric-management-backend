package com.fabricmanagement.logistics.shipment.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentLineBatchId implements Serializable {
  private UUID shipmentLine;
  private UUID batchId;
}
