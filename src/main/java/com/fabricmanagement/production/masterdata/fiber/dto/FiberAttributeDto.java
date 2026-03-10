package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberAttribute;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberAttributeDto {

  private UUID id;
  private String attributeCode;
  private String attributeName;
  private String attributeGroup;
  private String description;
  private Integer displayOrder;

  public static FiberAttributeDto from(FiberAttribute entity) {
    return FiberAttributeDto.builder()
        .id(entity.getId())
        .attributeCode(entity.getAttributeCode())
        .attributeName(entity.getAttributeName())
        .attributeGroup(entity.getAttributeGroup())
        .description(entity.getDescription())
        .displayOrder(entity.getDisplayOrder())
        .build();
  }
}
