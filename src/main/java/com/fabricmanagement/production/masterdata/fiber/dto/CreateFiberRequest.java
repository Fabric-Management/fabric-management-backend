package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for creating new fiber (pure or blended).
 *
 * <p><b>Unified Design:</b> Single DTO for both pure and blended fibers.
 *
 * <ul>
 *   <li><b>Pure fiber:</b> composition is null or empty
 *   <li><b>Blended fiber:</b> composition contains base fiber IDs with percentages
 * </ul>
 *
 * <p><b>User-Friendly Design:</b> Material can be auto-created automatically.
 *
 * <p>If materialId is provided, existing Material will be used.
 *
 * <p>If materialId is null, Material will be auto-created with type=FIBER and provided unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberRequest {

  private Long version;

  /**
   * Material ID (optional).
   *
   * <p>If null, Material will be auto-created with type=FIBER and unit.
   */
  private UUID materialId;

  /**
   * Unit for Material (required if materialId is null).
   *
   * <p>Used when auto-creating Material. Examples: "kg", "ton", "m", etc.
   */
  private String unit;

  @NotNull(message = "Fiber Category ID is required")
  private UUID fiberCategoryId;

  private UUID fiberIsoCodeId;

  @NotBlank(message = "Fiber name is required")
  private String fiberName;

  private String fiberGrade;

  /**
   * Composition map: baseFiberId → percentage (optional).
   *
   * <p><b>Pure fiber:</b> null or empty
   *
   * <p><b>Blended fiber:</b> Map with base fiber IDs and percentages (must sum to 100%)
   *
   * <p>Example: {cottonId: 60.0, viscoseId: 40.0}
   */
  private Map<UUID, BigDecimal> composition;

  private String remarks;
}
