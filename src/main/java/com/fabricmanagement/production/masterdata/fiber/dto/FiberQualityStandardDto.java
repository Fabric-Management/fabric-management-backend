package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
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
public class FiberQualityStandardDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID isoCodeId;
  private String standardName;
  private Boolean isDefault;

  private Double finenessMin;
  private Double finenessTarget;
  private Double finenessMax;

  private Double lengthMmMin;
  private Double lengthMmTarget;
  private Double lengthMmMax;

  private Double strengthCndTexMin;
  private Double strengthCndTexTarget;
  private Double strengthCndTexMax;

  private Double elongationPctMin;
  private Double elongationPctTarget;
  private Double elongationPctMax;

  private Double moisturePctMin;
  private Double moisturePctTarget;
  private Double moisturePctMax;

  private Double trashContentPctMin;
  private Double trashContentPctTarget;
  private Double trashContentPctMax;

  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static FiberQualityStandardDto from(FiberQualityStandard entity) {
    return FiberQualityStandardDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .isoCodeId(entity.getIsoCode() != null ? entity.getIsoCode().getId() : null)
        .standardName(entity.getStandardName())
        .isDefault(entity.getIsDefault())
        .finenessMin(entity.getFinenessMin())
        .finenessTarget(entity.getFinenessTarget())
        .finenessMax(entity.getFinenessMax())
        .lengthMmMin(entity.getLengthMmMin())
        .lengthMmTarget(entity.getLengthMmTarget())
        .lengthMmMax(entity.getLengthMmMax())
        .strengthCndTexMin(entity.getStrengthCndTexMin())
        .strengthCndTexTarget(entity.getStrengthCndTexTarget())
        .strengthCndTexMax(entity.getStrengthCndTexMax())
        .elongationPctMin(entity.getElongationPctMin())
        .elongationPctTarget(entity.getElongationPctTarget())
        .elongationPctMax(entity.getElongationPctMax())
        .moisturePctMin(entity.getMoisturePctMin())
        .moisturePctTarget(entity.getMoisturePctTarget())
        .moisturePctMax(entity.getMoisturePctMax())
        .trashContentPctMin(entity.getTrashContentPctMin())
        .trashContentPctTarget(entity.getTrashContentPctTarget())
        .trashContentPctMax(entity.getTrashContentPctMax())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
