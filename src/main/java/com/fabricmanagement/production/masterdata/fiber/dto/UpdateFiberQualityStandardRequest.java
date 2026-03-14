package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFiberQualityStandardRequest {

  private Long version;

  @NotNull(message = "ISO code ID is required")
  private UUID isoCodeId;

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
  @Range(min = 0, max = 100, message = "Elongation min must be between 0 and 100")
  private Double elongationPctMin;

  @Range(min = 0, max = 100, message = "Elongation target must be between 0 and 100")
  private Double elongationPctTarget;

  @Range(min = 0, max = 100, message = "Elongation max must be between 0 and 100")
  private Double elongationPctMax;

  // Moisture (%)
  @Range(min = 0, max = 100, message = "Moisture min must be between 0 and 100")
  private Double moisturePctMin;

  @Range(min = 0, max = 100, message = "Moisture target must be between 0 and 100")
  private Double moisturePctTarget;

  @Range(min = 0, max = 100, message = "Moisture max must be between 0 and 100")
  private Double moisturePctMax;

  // Trash content (%)
  @Range(min = 0, max = 100, message = "Trash content min must be between 0 and 100")
  private Double trashContentPctMin;

  @Range(min = 0, max = 100, message = "Trash content target must be between 0 and 100")
  private Double trashContentPctTarget;

  @Range(min = 0, max = 100, message = "Trash content max must be between 0 and 100")
  private Double trashContentPctMax;
}
