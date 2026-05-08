package com.fabricmanagement.production.masterdata.material.dto;

import com.fabricmanagement.production.masterdata.material.domain.reference.MaterialAttribute;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(description = "Material Attribute data transfer object")
public record MaterialAttributeDto(
    @Schema(description = "Attribute ID") UUID id,
    @Schema(description = "Attribute Code", requiredMode = Schema.RequiredMode.REQUIRED)
        String attributeCode,
    @Schema(description = "Attribute Name", requiredMode = Schema.RequiredMode.REQUIRED)
        String attributeName,
    @Schema(description = "Attribute Group") String attributeGroup,
    @Schema(description = "Material Scope (FIBER, YARN, FABRIC, ALL)") String materialScope,
    @Schema(description = "Description") String description,
    @Schema(description = "Display Order") Integer displayOrder) {

  public static MaterialAttributeDto from(MaterialAttribute entity) {
    return MaterialAttributeDto.builder()
        .id(entity.getId())
        .attributeCode(entity.getAttributeCode())
        .attributeName(entity.getAttributeName())
        .attributeGroup(entity.getAttributeGroup())
        .materialScope(entity.getMaterialScope())
        .description(entity.getDescription())
        .displayOrder(entity.getDisplayOrder())
        .build();
  }
}
