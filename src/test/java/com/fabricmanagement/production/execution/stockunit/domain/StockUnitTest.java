package com.fabricmanagement.production.execution.stockunit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fabricmanagement.production.execution.stockunit.domain.exception.QcRelocationException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitNotReleasedException;
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

  @Test
  void unreleasedDispositionsCannotBeReservedConsumedOrTransferred() {
    for (QualityDisposition disposition :
        new QualityDisposition[] {
          QualityDisposition.PENDING_INSPECTION,
          QualityDisposition.QUARANTINED,
          QualityDisposition.NONCONFORMING
        }) {
      StockUnit unit = stockUnit(disposition, UUID.randomUUID());

      StockUnitNotReleasedException reserveError =
          assertThrows(StockUnitNotReleasedException.class, unit::reserve);
      assertEquals("STOCK_UNIT_NOT_RELEASED", reserveError.getErrorCode());
      assertThrows(StockUnitNotReleasedException.class, () -> unit.consume(BigDecimal.ONE));
      assertThrows(
          StockUnitNotReleasedException.class, () -> unit.startTransfer(UUID.randomUUID()));
    }
  }

  @Test
  void releasedDispositionPassesCommitmentQualityGate() {
    StockUnit reservable = stockUnit(QualityDisposition.RELEASED, UUID.randomUUID());
    StockUnit consumable = stockUnit(QualityDisposition.RELEASED, UUID.randomUUID());
    StockUnit transferable = stockUnit(QualityDisposition.RELEASED, UUID.randomUUID());

    reservable.reserve();
    consumable.consume(BigDecimal.ONE);
    transferable.startTransfer(UUID.randomUUID());

    assertEquals(StockUnitStatus.RESERVED, reservable.getStatus());
    assertEquals(StockUnitStatus.PARTIAL, consumable.getStatus());
    assertEquals(StockUnitStatus.IN_TRANSIT, transferable.getStatus());
  }

  @Test
  void qualityRelocationChangesOnlyLocation() {
    UUID source = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    StockUnit quarantined = stockUnit(QualityDisposition.QUARANTINED, source);
    quarantined.quarantine();
    StockUnitStatus status = quarantined.getStatus();
    QualityDisposition disposition = quarantined.getQualityDisposition();

    quarantined.relocateForQuality(target);

    assertEquals(source, quarantined.getPreviousLocationId());
    assertEquals(target, quarantined.getLocationId());
    assertSame(status, quarantined.getStatus());
    assertSame(disposition, quarantined.getQualityDisposition());
  }

  @Test
  void disposedNonconformingUnitCannotBeRelocated() {
    StockUnit nonconforming = stockUnit(QualityDisposition.NONCONFORMING, UUID.randomUUID());
    nonconforming.hold();
    nonconforming.dispose();

    QcRelocationException error =
        assertThrows(
            QcRelocationException.class, () -> nonconforming.relocateForQuality(UUID.randomUUID()));

    assertEquals("QC_RELOCATE_STATUS_INVALID", error.getErrorCode());
    assertEquals(StockUnitStatus.DISPOSED, nonconforming.getStatus());
  }

  @Test
  void relocationToCurrentLocationIsRejected() {
    UUID location = UUID.randomUUID();
    StockUnit pending = stockUnit(QualityDisposition.PENDING_INSPECTION, location);

    QcRelocationException error =
        assertThrows(QcRelocationException.class, () -> pending.relocateForQuality(location));

    assertEquals("QC_RELOCATE_SAME_LOCATION", error.getErrorCode());
  }

  private StockUnit stockUnit() {
    return stockUnit(QualityDisposition.PENDING_INSPECTION, UUID.randomUUID());
  }

  private StockUnit stockUnit(QualityDisposition disposition, UUID locationId) {
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
        locationId,
        StockUnitSourceType.PRODUCTION,
        UUID.randomUUID(),
        disposition);
  }
}
