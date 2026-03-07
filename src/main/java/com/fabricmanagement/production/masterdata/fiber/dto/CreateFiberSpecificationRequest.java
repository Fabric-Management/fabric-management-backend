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
public class CreateFiberSpecificationRequest {

  private Long version;

  @NotNull(message = "Fiber ID is required")
  private UUID fiberId;

  @NotBlank(message = "Specification name is required")
  @Size(max = 100, message = "Specification name must be at most 100 characters")
  private String specName;

  private Boolean isDefault;

  @Size(max = 100, message = "Test standard must be at most 100 characters")
  private String testStandard;

  // Fineness
  @PositiveOrZero(message = "Fineness min must be ≥ 0")
  private Double finenessMin;

  @PositiveOrZero(message = "Fineness target must be ≥ 0")
  private Double finenessTarget;

  @PositiveOrZero(message = "Fineness max must be ≥ 0")
  private Double finenessMax;

  // Length (mm)
  @PositiveOrZero(message = "Length min must be ≥ 0")
  private Double lengthMin;

  @PositiveOrZero(message = "Length target must be ≥ 0")
  private Double lengthTarget;

  @PositiveOrZero(message = "Length max must be ≥ 0")
  private Double lengthMax;

  // Strength (cN/dtex)
  @PositiveOrZero(message = "Strength min must be ≥ 0")
  private Double strengthMin;

  @PositiveOrZero(message = "Strength target must be ≥ 0")
  private Double strengthTarget;

  @PositiveOrZero(message = "Strength max must be ≥ 0")
  private Double strengthMax;

  // Elongation (%)
  @Range(min = 0, max = 100, message = "Elongation min must be between 0 and 100")
  private Double elongationMin;

  @Range(min = 0, max = 100, message = "Elongation target must be between 0 and 100")
  private Double elongationTarget;

  @Range(min = 0, max = 100, message = "Elongation max must be between 0 and 100")
  private Double elongationMax;

  // Moisture (%)
  @Range(min = 0, max = 100, message = "Moisture min must be between 0 and 100")
  private Double moistureMin;

  @Range(min = 0, max = 100, message = "Moisture target must be between 0 and 100")
  private Double moistureTarget;

  @Range(min = 0, max = 100, message = "Moisture max must be between 0 and 100")
  private Double moistureMax;

  // Trash content (%)
  @Range(min = 0, max = 100, message = "Trash content min must be between 0 and 100")
  private Double trashContentMin;

  @Range(min = 0, max = 100, message = "Trash content target must be between 0 and 100")
  private Double trashContentTarget;

  @Range(min = 0, max = 100, message = "Trash content max must be between 0 and 100")
  private Double trashContentMax;

  private String remarks;
}
