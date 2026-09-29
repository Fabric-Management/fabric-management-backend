package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profiles grouped by target type and target id (FIBER-CATALOG-1). A blend is its own FIBER group,
 * never a group under an ISO code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FiberQualityStandardGroupDto")
public class FiberQualityStandardGroupDto {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private FiberQualityTargetType targetType;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Set only when targetType=ISO_CODE")
  private UUID isoCodeId;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Set only when targetType=FIBER")
  private UUID fiberId;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "ISO code (e.g. CO) or fibre name with its captured composition label")
  private String targetLabel;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private List<FiberQualityStandardDto> profiles;
}
