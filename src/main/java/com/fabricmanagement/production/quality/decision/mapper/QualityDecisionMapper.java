package com.fabricmanagement.production.quality.decision.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.quality.decision.domain.QualityDecision;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionEligibility;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionUnitDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class, imports = QualityDecisionEligibility.class)
public interface QualityDecisionMapper {

  @Mapping(target = "scope", source = "decisionScope")
  @Mapping(target = "sequence", source = "seq")
  QualityDecisionDto toDto(QualityDecision decision);

  @Mapping(
      target = "unitStatusEligible",
      expression = "java(QualityDecisionEligibility.isUnitStatusEligible(stockUnit.getStatus()))")
  QualityDecisionUnitDto toUnitDto(StockUnit stockUnit);
}
