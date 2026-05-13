package com.fabricmanagement.production.execution.output.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProductionOutputConfirmedEvent extends DomainEvent {
  private final UUID recordId;
  private final UUID workOrderId;
  private final UUID batchId;
  private final UUID outputProductId;
  private final ProductType outputProductType;
  private final String unit;
  private final UUID confirmedByUserId;
  private final List<OutputItemData> items;

  public ProductionOutputConfirmedEvent(
      UUID tenantId,
      UUID recordId,
      UUID workOrderId,
      UUID batchId,
      UUID outputProductId,
      ProductType outputProductType,
      String unit,
      UUID confirmedByUserId,
      List<OutputItemData> items) {
    super(tenantId, "PRODUCTION_OUTPUT_CONFIRMED");
    this.recordId = recordId;
    this.workOrderId = workOrderId;
    this.batchId = batchId;
    this.outputProductId = outputProductId;
    this.outputProductType = outputProductType;
    this.unit = unit;
    this.confirmedByUserId = confirmedByUserId;
    this.items = items;
  }

  public record OutputItemData(
      UUID itemId,
      String barcode,
      PackageType packageType,
      BigDecimal netWeight,
      BigDecimal grossWeight,
      UUID locationId) {}
}
