package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
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

  /** Null means no change; a value declares a previously undeclared pure fiber exactly once. */
  private MaterialSource materialSource;

  private Map<UUID, BigDecimal> composition;

  private String remarks;
}
