package com.fabricmanagement.production.quality.decision.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationRef;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.domain.exception.QcRelocationException;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionEligibility;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class QualityDecisionMapperTest {

  private final QualityDecisionMapper mapper = Mappers.getMapper(QualityDecisionMapper.class);

  @Test
  void exposesUnitStatusEligibilityWithoutClaimingBatchLevelDecidability() {
    EnumSet.allOf(StockUnitStatus.class)
        .forEach(
            status -> {
              StockUnit unit = StockUnit.builder().status(status).build();

              assertThat(mapper.toUnitDto(unit).unitStatusEligible())
                  .isEqualTo(QualityDecisionEligibility.isUnitStatusEligible(status));
            });
  }

  @Test
  void exposesLocationAndQualityRelocationCapabilityFromTheDomainPredicate() {
    UUID locationId = UUID.randomUUID();
    StockUnit pendingAvailable =
        StockUnit.builder()
            .status(StockUnitStatus.AVAILABLE)
            .qualityDisposition(QualityDisposition.PENDING_INSPECTION)
            .build();

    var dto =
        mapper.toUnitDto(
            pendingAvailable, new WarehouseLocationRef(locationId, "QC-01", "QC Hold"));

    assertThat(dto.locationId()).isEqualTo(locationId);
    assertThat(dto.locationCode()).isEqualTo("QC-01");
    assertThat(dto.locationName()).isEqualTo("QC Hold");
    assertThat(dto.qualityRelocationAllowed()).isTrue();
  }

  @Test
  void relocationCapabilityAndAssertionUseTheSameMatrix() {
    assertCapability(QualityDisposition.RELEASED, StockUnitStatus.AVAILABLE, false);
    assertCapability(QualityDisposition.PENDING_INSPECTION, StockUnitStatus.AVAILABLE, true);
    assertCapability(QualityDisposition.NONCONFORMING, StockUnitStatus.DISPOSED, false);
    assertCapability(QualityDisposition.QUARANTINED, StockUnitStatus.QUARANTINE, true);
  }

  private void assertCapability(
      QualityDisposition disposition, StockUnitStatus status, boolean expected) {
    StockUnit unit = StockUnit.builder().status(status).qualityDisposition(disposition).build();

    assertThat(mapper.toUnitDto(unit).qualityRelocationAllowed()).isEqualTo(expected);
    if (expected) {
      unit.assertAllowsQualityRelocation();
    } else {
      assertThrows(QcRelocationException.class, unit::assertAllowsQualityRelocation);
    }
  }
}
