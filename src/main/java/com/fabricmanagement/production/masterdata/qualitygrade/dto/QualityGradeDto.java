package com.fabricmanagement.production.masterdata.qualitygrade.dto;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import java.math.BigDecimal;
import java.util.UUID;

public record QualityGradeDto(
    UUID id,
    MaterialType materialType,
    String code,
    String name,
    int rank,
    BigDecimal priceFactor,
    boolean saleable,
    boolean requiresApproval,
    String colorHex,
    boolean isDefault,
    boolean isActive) {

  public static QualityGradeDto from(QualityGrade entity) {
    if (entity == null) {
      return null;
    }
    return new QualityGradeDto(
        entity.getId(),
        entity.getMaterialType(),
        entity.getCode(),
        entity.getName(),
        entity.getRank(),
        entity.getPriceFactor(),
        entity.isSaleable(),
        entity.isRequiresApproval(),
        entity.getColorHex(),
        entity.isDefault(),
        entity.getIsActive());
  }
}
