package com.fabricmanagement.production.core.stockunit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitDomainException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockUnitInitialLengthTest {
  @Test
  void verifiedRemainderDoesNotRewriteBirthMeasurement() {
    StockUnit unit = new StockUnit();
    unit.recordInitialLength(new BigDecimal("100"), " m ");
    unit.setId(UUID.randomUUID());
    unit.recordLength(new BigDecimal("70"), "M");
    assertThat(unit.getInitialLength()).isEqualByComparingTo("100");
    assertThat(unit.getInitialLengthUnit()).isEqualTo("M");
    assertThat(unit.getLength()).isEqualByComparingTo("70");
    assertThatThrownBy(() -> unit.recordInitialLength(new BigDecimal("70"), "M"))
        .isInstanceOf(StockUnitDomainException.class);
  }

  @Test
  void legacyPieceAndProductionTargetNeverAcquireInventedInitialLength() {
    StockUnit unit = new StockUnit();
    unit.setId(UUID.randomUUID());
    unit.recordLength(new BigDecimal("70"), "M");
    assertThat(unit.getInitialLength()).isNull();
    assertThat(unit.getInitialLengthUnit()).isNull();
    assertThat(new StockUnit().getInitialLength()).isNull();
    assertThatThrownBy(() -> unit.recordInitialLength(new BigDecimal("100"), "M"))
        .isInstanceOf(StockUnitDomainException.class);
  }

  @Test
  void initialMeasurementIsPositivePairedValidAndWriteOnce() {
    for (BigDecimal invalid : new BigDecimal[] {null, BigDecimal.ZERO, BigDecimal.ONE.negate()}) {
      assertThatThrownBy(() -> new StockUnit().recordInitialLength(invalid, "M"))
          .isInstanceOf(StockUnitDomainException.class);
    }
    for (String invalid : new String[] {null, "", "KG", "unknown"}) {
      assertThatThrownBy(() -> new StockUnit().recordInitialLength(BigDecimal.ONE, invalid))
          .isInstanceOf(StockUnitDomainException.class);
    }
    StockUnit unit = new StockUnit();
    unit.recordInitialLength(BigDecimal.ONE, "M");
    assertThatThrownBy(() -> unit.recordInitialLength(BigDecimal.TEN, "M"))
        .isInstanceOf(StockUnitDomainException.class);
  }
}
