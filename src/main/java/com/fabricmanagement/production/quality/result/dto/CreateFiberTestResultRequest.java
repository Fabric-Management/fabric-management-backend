package com.fabricmanagement.production.quality.result.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
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
public class CreateFiberTestResultRequest {

  private Long version;

  private UUID batchId;

  private UUID stockUnitId;

  @NotNull(message = "Test date is required")
  @PastOrPresent(message = "Test date cannot be in the future")
  private Instant testDate;

  private String testType;

  // Big 4
  @PositiveOrZero(message = "Fineness must be ≥ 0")
  private Double fineness;

  @PositiveOrZero(message = "Length must be ≥ 0")
  private Double lengthMm;

  @PositiveOrZero(message = "Strength must be ≥ 0")
  private Double strengthCndTex;

  @DecimalMin(value = "0", message = "Elongation % must be between 0 and 100")
  @DecimalMax(value = "100", message = "Elongation % must be between 0 and 100")
  private Double elongationPercent;

  // Extended
  @DecimalMin(value = "0", message = "Moisture % must be between 0 and 100")
  @DecimalMax(value = "100", message = "Moisture % must be between 0 and 100")
  private Double moisturePercent;

  @DecimalMin(value = "0", message = "Trash content % must be between 0 and 100")
  @DecimalMax(value = "100", message = "Trash content % must be between 0 and 100")
  private Double trashContentPercent;

  @DecimalMin(value = "0", message = "Uniformity index % must be between 0 and 100")
  @DecimalMax(value = "100", message = "Uniformity index % must be between 0 and 100")
  private Double uniformityIndex;

  // Metadata
  private String testLab;
  private String testStandard;
  private String remarks;
}
