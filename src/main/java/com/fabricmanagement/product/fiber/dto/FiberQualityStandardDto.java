package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private FiberQualityTargetType targetType;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Shared ISO code id; set only when targetType=ISO_CODE")
  private UUID isoCodeId;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Target Fiber.id; set only when targetType=FIBER")
  private UUID fiberId;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description =
          "FIBER target only: server-captured composition {Fiber.id: percentage}; read-only")
  private Map<UUID, BigDecimal> targetComposition;

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

  private Double uniformityIndexMin;
  private Double uniformityIndexTarget;
  private Double uniformityIndexMax;

  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static FiberQualityStandardDto from(FiberQualityStandard entity) {
    return FiberQualityStandardDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .targetType(entity.getTargetType())
        .isoCodeId(entity.getIsoCodeId())
        .fiberId(entity.getFiberId())
        .targetComposition(entity.getTargetComposition())
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
        .uniformityIndexMin(entity.getUniformityIndexMin())
        .uniformityIndexTarget(entity.getUniformityIndexTarget())
        .uniformityIndexMax(entity.getUniformityIndexMax())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
