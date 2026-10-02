package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
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
   * Shared fibre category. Required for a canonical pure fibre (catalogue owner only). A blend
   * always uses the shared MIXED_BLEND category; any other value is rejected.
   */
  @Schema(
      nullable = true,
      description =
          "Pure (catalogue owner only): shared category matching the ISO code's fibre type. Blend:"
              + " omit, or the shared MIXED_BLEND id")
  private UUID fiberCategoryId;

  /**
   * Shared ISO code of a canonical pure fibre. A blend has no ISO code of its own: sending one is
   * rejected with {@code FIBER_BLEND_ISO_FORBIDDEN}.
   */
  @Schema(
      nullable = true,
      description =
          "Pure (catalogue owner only): shared ISO code id. Blend: must be absent"
              + " (FIBER_BLEND_ISO_FORBIDDEN)")
  private UUID fiberIsoCodeId;

  /**
   * Display name. Required for a canonical pure fibre; optional for a blend, where the backend
   * defaults it to the canonical composition label (e.g. {@code 60% CO / 40% PES}).
   */
  @Schema(
      nullable = true,
      maxLength = 255,
      description = "Required for a pure fibre; optional for a blend (defaults to the label)")
  @Size(max = 255, message = "Fiber name must be at most 255 characters")
  private String fiberName;

  /**
   * Must be absent: a blend cannot carry one material source ({@code
   * FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN}) and a canonical shared fibre stays undeclared. Private
   * source variants come from the reviewed fiber request flow.
   */
  @Schema(nullable = true, description = "Must be absent; see the fiber request flow")
  private MaterialSource materialSource;

  /**
   * Composition {@code Fiber.id -> percentage} (never Product ids). Non-empty means a blend: at
   * least two distinct active pure fibres (shared or own), exact decimal sum 100.
   */
  @Schema(
      nullable = true,
      description =
          "Blend composition keyed by component Fiber.id (never Product.id); exact decimal sum"
              + " 100, 2-5 components, each at least 5. Empty/absent = canonical pure fibre")
  private Map<UUID, BigDecimal> composition;

  private String remarks;
}
