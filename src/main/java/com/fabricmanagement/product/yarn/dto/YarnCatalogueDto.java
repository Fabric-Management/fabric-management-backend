package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Yarn catalogue row; semantic fields are populated for their catalogue type")
public record YarnCatalogueDto(
    UUID id,
    String uid,
    String code,
    String name,
    String description,
    Integer displayOrder,
    boolean systemDefined,
    boolean active,
    SpinningTechnologyFamily technologyFamily,
    String standardRef,
    String instrument,
    String applicablePropertyKey) {

  public static YarnCatalogueDto from(YarnSpinningSystem system) {
    return new YarnCatalogueDto(
        system.getId(),
        system.getUid(),
        system.getCode(),
        system.getName(),
        system.getDescription(),
        system.getDisplayOrder(),
        system.isSystemDefined(),
        Boolean.TRUE.equals(system.getIsActive()),
        system.getTechnologyFamily(),
        null,
        null,
        null);
  }

  public static YarnCatalogueDto from(YarnEndUse endUse) {
    return new YarnCatalogueDto(
        endUse.getId(),
        endUse.getUid(),
        endUse.getCode(),
        endUse.getName(),
        endUse.getDescription(),
        endUse.getDisplayOrder(),
        endUse.isSystemDefined(),
        Boolean.TRUE.equals(endUse.getIsActive()),
        null,
        null,
        null,
        null);
  }

  public static YarnCatalogueDto from(YarnTestMethod method) {
    return new YarnCatalogueDto(
        method.getId(),
        method.getUid(),
        method.getCode(),
        method.getName(),
        method.getDescription(),
        method.getDisplayOrder(),
        method.isSystemDefined(),
        Boolean.TRUE.equals(method.getIsActive()),
        null,
        method.getStandardRef(),
        method.getInstrument(),
        method.getApplicablePropertyKey());
  }
}
