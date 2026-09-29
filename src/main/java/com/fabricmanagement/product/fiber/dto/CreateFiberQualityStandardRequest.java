package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.FiberQualityTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberQualityStandardRequest {

  private Long version;

  /** Target discriminator: ISO_CODE (shared pure ISO) or FIBER (exact fibre/mixture). */
  @NotNull(message = "Target type is required")
  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "ISO_CODE: send isoCodeId only. FIBER: send fiberId only")
  private FiberQualityTargetType targetType;

  /** Shared ISO code id; required for ISO_CODE, must be absent for FIBER. */
  @Schema(
      nullable = true,
      description = "Required when targetType=ISO_CODE; must be absent when targetType=FIBER")
  private UUID isoCodeId;

  /**
   * Visible Fiber.id (shared/own pure or own blend); required for FIBER, absent for ISO_CODE. The
   * backend captures the fibre's composition at creation; clients never send it.
   */
  @Schema(
      nullable = true,
      description = "Required when targetType=FIBER; must be absent when targetType=ISO_CODE")
  private UUID fiberId;

  @NotBlank(message = "Standard name is required")
  @Size(max = 100, message = "Standard name must be at most 100 characters")
  private String standardName;

  private Boolean isDefault;

  // Fineness
  @PositiveOrZero(message = "Fineness min must be ≥ 0")
  private Double finenessMin;

  @PositiveOrZero(message = "Fineness target must be ≥ 0")
  private Double finenessTarget;

  @PositiveOrZero(message = "Fineness max must be ≥ 0")
  private Double finenessMax;

  // Length (mm)
  @PositiveOrZero(message = "Length mm min must be ≥ 0")
  private Double lengthMmMin;

  @PositiveOrZero(message = "Length mm target must be ≥ 0")
  private Double lengthMmTarget;

  @PositiveOrZero(message = "Length mm max must be ≥ 0")
  private Double lengthMmMax;

  // Strength (cN/dtex)
  @PositiveOrZero(message = "Strength min must be ≥ 0")
  private Double strengthCndTexMin;

  @PositiveOrZero(message = "Strength target must be ≥ 0")
  private Double strengthCndTexTarget;

  @PositiveOrZero(message = "Strength max must be ≥ 0")
  private Double strengthCndTexMax;

  // Elongation (%)
  @DecimalMin(value = "0", message = "Elongation min must be between 0 and 100")
  @DecimalMax(value = "100", message = "Elongation min must be between 0 and 100")
  private Double elongationPctMin;

  @DecimalMin(value = "0", message = "Elongation target must be between 0 and 100")
  @DecimalMax(value = "100", message = "Elongation target must be between 0 and 100")
  private Double elongationPctTarget;

  @DecimalMin(value = "0", message = "Elongation max must be between 0 and 100")
  @DecimalMax(value = "100", message = "Elongation max must be between 0 and 100")
  private Double elongationPctMax;

  // Moisture (%)
  @DecimalMin(value = "0", message = "Moisture min must be between 0 and 100")
  @DecimalMax(value = "100", message = "Moisture min must be between 0 and 100")
  private Double moisturePctMin;

  @DecimalMin(value = "0", message = "Moisture target must be between 0 and 100")
  @DecimalMax(value = "100", message = "Moisture target must be between 0 and 100")
  private Double moisturePctTarget;

  @DecimalMin(value = "0", message = "Moisture max must be between 0 and 100")
  @DecimalMax(value = "100", message = "Moisture max must be between 0 and 100")
  private Double moisturePctMax;

  // Trash content (%)
  @DecimalMin(value = "0", message = "Trash content min must be between 0 and 100")
  @DecimalMax(value = "100", message = "Trash content min must be between 0 and 100")
  private Double trashContentPctMin;

  @DecimalMin(value = "0", message = "Trash content target must be between 0 and 100")
  @DecimalMax(value = "100", message = "Trash content target must be between 0 and 100")
  private Double trashContentPctTarget;

  @DecimalMin(value = "0", message = "Trash content max must be between 0 and 100")
  @DecimalMax(value = "100", message = "Trash content max must be between 0 and 100")
  private Double trashContentPctMax;

  // Uniformity index (%)
  @DecimalMin(value = "0", message = "Uniformity index min must be between 0 and 100")
  @DecimalMax(value = "100", message = "Uniformity index min must be between 0 and 100")
  private Double uniformityIndexMin;

  @DecimalMin(value = "0", message = "Uniformity index target must be between 0 and 100")
  @DecimalMax(value = "100", message = "Uniformity index target must be between 0 and 100")
  private Double uniformityIndexTarget;

  @DecimalMin(value = "0", message = "Uniformity index max must be between 0 and 100")
  @DecimalMax(value = "100", message = "Uniformity index max must be between 0 and 100")
  private Double uniformityIndexMax;
}
