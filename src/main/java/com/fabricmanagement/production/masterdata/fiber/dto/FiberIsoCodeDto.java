package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberIsoCodeDto {

  private UUID id;
  private String isoCode;
  private String fiberName;
  private String fiberType;
  private String description;
  private Boolean isOfficialIso;
  private Integer displayOrder;

  public static FiberIsoCodeDto from(FiberIsoCode entity) {
    return FiberIsoCodeDto.builder()
        .id(entity.getId())
        .isoCode(entity.getIsoCode())
        .fiberName(entity.getFiberName())
        .fiberType(entity.getFiberType())
        .description(entity.getDescription())
        .isOfficialIso(entity.getIsOfficialIso())
        .displayOrder(entity.getDisplayOrder())
        .build();
  }
}
