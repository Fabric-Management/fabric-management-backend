package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberSpecification;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberSpecificationDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID fiberId;
  private String specName;
  private Boolean isDefault;
  private String testStandard;

  private Double finenessMin;
  private Double finenessTarget;
  private Double finenessMax;

  private Double lengthMin;
  private Double lengthTarget;
  private Double lengthMax;

  private Double strengthMin;
  private Double strengthTarget;
  private Double strengthMax;

  private Double elongationMin;
  private Double elongationTarget;
  private Double elongationMax;

  private Double moistureMin;
  private Double moistureTarget;
  private Double moistureMax;

  private Double trashContentMin;
  private Double trashContentTarget;
  private Double trashContentMax;

  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static FiberSpecificationDto from(FiberSpecification entity) {
    return FiberSpecificationDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .fiberId(entity.getFiberId())
        .specName(entity.getSpecName())
        .isDefault(entity.getIsDefault())
        .testStandard(entity.getTestStandard())
        .finenessMin(entity.getFinenessMin())
        .finenessTarget(entity.getFinenessTarget())
        .finenessMax(entity.getFinenessMax())
        .lengthMin(entity.getLengthMin())
        .lengthTarget(entity.getLengthTarget())
        .lengthMax(entity.getLengthMax())
        .strengthMin(entity.getStrengthMin())
        .strengthTarget(entity.getStrengthTarget())
        .strengthMax(entity.getStrengthMax())
        .elongationMin(entity.getElongationMin())
        .elongationTarget(entity.getElongationTarget())
        .elongationMax(entity.getElongationMax())
        .moistureMin(entity.getMoistureMin())
        .moistureTarget(entity.getMoistureTarget())
        .moistureMax(entity.getMoistureMax())
        .trashContentMin(entity.getTrashContentMin())
        .trashContentTarget(entity.getTrashContentTarget())
        .trashContentMax(entity.getTrashContentMax())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
