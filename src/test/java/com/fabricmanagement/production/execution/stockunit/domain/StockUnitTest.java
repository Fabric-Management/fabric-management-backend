package com.fabricmanagement.production.execution.stockunit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockUnitTest {

  @Test
  void shouldRecordNullableLengthWithoutChangingWeight() {
    StockUnit unit = stockUnit();

    unit.recordLength(new BigDecimal("120.500"), "M");

    assertEquals(new BigDecimal("120.500"), unit.getLength());
    assertEquals("M", unit.getLengthUnit());
    assertEquals(new BigDecimal("45.000"), unit.getCurrentWeight());
    assertEquals("KG", unit.getUnit());
  }

  @Test
  void shouldRejectLengthUnitWithoutLength() {
    StockUnit unit = stockUnit();

    assertThrows(StockUnitDomainException.class, () -> unit.recordLength(null, "M"));
  }

  private StockUnit stockUnit() {
    return StockUnit.create(
        UUID.randomUUID(),
        UUID.randomUUID(),
        ProductType.FABRIC,
        "ROLL-001",
        null,
        PackageType.ROLL,
        new BigDecimal("45.000"),
        null,
        "KG",
        UUID.randomUUID(),
        StockUnitSourceType.PRODUCTION,
        UUID.randomUUID(),
        QualityDisposition.PENDING_INSPECTION);
  }
}
