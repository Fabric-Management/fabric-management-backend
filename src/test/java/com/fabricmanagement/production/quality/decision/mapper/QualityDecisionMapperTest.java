package com.fabricmanagement.production.quality.decision.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionEligibility;
import java.util.EnumSet;
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
}
