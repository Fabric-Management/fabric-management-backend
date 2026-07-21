package com.fabricmanagement.production.execution.batch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.execution.batch.domain.exception.BatchNotConsumableException;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchReleasedUnitConsumptionTest {

  @Test
  void mixedQualityProjectionDoesNotBlockReleasedUnitConsumption() {
    Batch batch = batch(BatchStatus.QUARANTINE);

    batch.consumeReleasedUnitFromAvailable(new BigDecimal("5.000"));

    assertThat(batch.getConsumedQuantity()).isEqualByComparingTo("5.000");
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.QUARANTINE);
  }

  @Test
  void operationalHoldStillBlocksReleasedUnitConsumption() {
    Batch batch = batch(BatchStatus.ON_HOLD);

    assertThatThrownBy(() -> batch.consumeReleasedUnitFromAvailable(BigDecimal.ONE))
        .isInstanceOf(BatchNotConsumableException.class)
        .extracting(error -> ((BatchNotConsumableException) error).getErrorCode())
        .isEqualTo("BATCH_NOT_CONSUMABLE");
  }

  private Batch batch(BatchStatus status) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .batchCode("LOT-MIXED-QC")
            .quantity(new BigDecimal("20.000"))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .unit("KG")
            .status(status)
            .build();
    batch.setId(UUID.randomUUID());
    return batch;
  }
}
