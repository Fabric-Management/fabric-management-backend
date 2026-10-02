package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Request for updating an existing fiber.
 *
 * <p>Contains only mutable fields. Immutable fields like productId, fiberCategoryId, and
 * fiberIsoCodeId are excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFiberRequest {

  @NotNull(message = "Version is required for optimistic locking")
  private Long version;

  @NotBlank(message = "Fiber name is required")
  private String fiberName;

  /**
   * Null means no change; a value declares a previously undeclared own pure fibre exactly once.
   * Shared catalogue fibres are read-only ({@code FIBER_SHARED_READ_ONLY}).
   */
  @Schema(nullable = true)
  private MaterialSource materialSource;

  /**
   * Blend composition {@code Fiber.id -> percentage}. Null keeps the saved composition; an empty
   * map or a pure/blend kind change is rejected. Changing it keeps the name unless the name is
   * updated too; existing batches keep their own composition snapshot.
   */
  @Schema(
      nullable = true,
      description =
          "Null = unchanged. Blend only: new composition keyed by component Fiber.id; empty map"
              + " rejected")
  private Map<UUID, BigDecimal> composition;

  private String remarks;
}
