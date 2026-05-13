package com.fabricmanagement.production.masterdata.product.dto;

import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;

@Builder
@Schema(description = "Product Attribute data transfer object")
public record ProductAttributeDto(
    @Schema(description = "Attribute ID") UUID id,
    @Schema(description = "Attribute Code", requiredMode = Schema.RequiredMode.REQUIRED)
        String attributeCode,
    @Schema(description = "Attribute Name", requiredMode = Schema.RequiredMode.REQUIRED)
        String attributeName,
    @Schema(description = "Attribute Group") String attributeGroup,
    @Schema(description = "Product Scope (FIBER, YARN, FABRIC, ALL)") String productScope,
    @Schema(description = "Description") String description,
    @Schema(description = "Display Order") Integer displayOrder) {

  public static ProductAttributeDto from(ProductAttribute entity) {
    return ProductAttributeDto.builder()
        .id(entity.getId())
        .attributeCode(entity.getAttributeCode())
        .attributeName(entity.getAttributeName())
        .attributeGroup(entity.getAttributeGroup())
        .productScope(entity.getProductScope())
        .description(entity.getDescription())
        .displayOrder(entity.getDisplayOrder())
        .build();
  }
}
