package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
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
 * <p><b>User-Friendly Design:</b> Product can be auto-created automatically.
 *
 * <p>If productId is provided, existing Product will be used.
 *
 * <p>If productId is null, Product will be auto-created with type=FIBER and provided unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberRequest {

  private Long version;

  /**
   * Product ID (optional).
   *
   * <p>If null, Product will be auto-created with type=FIBER and unit.
   */
  private UUID productId;

  /**
   * Unit for Product (required if productId is null).
   *
   * <p>Used when auto-creating Product. Examples: "kg", "ton", "m", etc.
   */
  private String unit;

  /**
   * Fiber Category ID (optional for blended fibers — backend derives MIXED_BLEND).
   *
   * <p>Required for pure fibers. For blended fibers, backend auto-resolves to MIXED_BLEND.
   */
  private UUID fiberCategoryId;

  /**
   * Fiber ISO Code ID (optional for blended fibers — backend derives from primary component).
   *
   * <p>Required for pure fibers. For blended fibers, backend uses the highest-percentage base
   * fiber's ISO code.
   */
  private UUID fiberIsoCodeId;

  @NotBlank(message = "Fiber name is required")
  private String fiberName;

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
