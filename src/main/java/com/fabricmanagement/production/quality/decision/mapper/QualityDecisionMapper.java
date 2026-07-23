package com.fabricmanagement.production.quality.decision.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationRef;
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
  @Mapping(
      target = "qualityRelocationAllowed",
      expression = "java(stockUnit.isQualityRelocationAllowed())")
  @Mapping(target = "locationId", ignore = true)
  @Mapping(target = "locationCode", ignore = true)
  @Mapping(target = "locationName", ignore = true)
  QualityDecisionUnitDto toUnitDto(StockUnit stockUnit);

  default QualityDecisionUnitDto toUnitDto(StockUnit stockUnit, WarehouseLocationRef locationRef) {
    QualityDecisionUnitDto unit = toUnitDto(stockUnit);
    return new QualityDecisionUnitDto(
        unit.id(),
        unit.barcode(),
        unit.packageType(),
        unit.currentWeight(),
        unit.unit(),
        unit.length(),
        unit.lengthUnit(),
        unit.status(),
        unit.qualityDisposition(),
        locationRef != null ? locationRef.id() : null,
        locationRef != null ? locationRef.code() : null,
        locationRef != null ? locationRef.name() : null,
        unit.unitStatusEligible(),
        unit.qualityRelocationAllowed());
  }
}
