package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.FiberCatalogScope;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fiber DTO (FIBER-CATALOG-1). Built by {@code FiberDtoAssembler}, which resolves every composition
 * component in bulk; there is no entity-only mapping because a blend's label needs its components.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FiberDto")
public class FiberDto {

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID id;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID tenantId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private String uid;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID productId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "PURE or BLEND")
  private FiberKind kind;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "SHARED platform catalogue record (read-only for tenants) or TENANT record")
  private FiberCatalogScope catalogScope;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private UUID fiberCategoryId;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Shared ISO code row of a PURE fibre; always null for a BLEND")
  private UUID fiberIsoCodeId;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private FiberCategoryDto category;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Shared ISO code of a PURE fibre; always null for a BLEND")
  private FiberIsoCodeDto isoCode;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Display name; may be a tenant's custom name. Show it with the composition")
  private String fiberName;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      nullable = true,
      description = "Declared source of a PURE fibre; null means undeclared; always null for BLEND")
  private MaterialSource materialSource;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private FiberStatus status;

  @Schema(nullable = true)
  private String remarks;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean isActive;

  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private Long version;

  private Instant createdAt;
  private Instant updatedAt;

  /** Stored composition: {@code Fiber.id -> percentage}; empty for a pure fibre. */
  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Stored composition keyed by component Fiber.id; empty for a PURE fibre")
  @Builder.Default
  private Map<UUID, BigDecimal> composition = Map.of();

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description =
          "Description built from shared ISO codes, e.g. \"62.5% CO / 37.5% PES\"; not an ISO code"
              + " and not an identity. Clients never parse it")
  private String compositionLabel;

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description =
          "Ordered, complete components (one at 100% for a PURE fibre): percentage desc, ISO code"
              + " asc, source key asc, fibre id asc")
  @Builder.Default
  private List<FiberCompositionComponentDto> components = List.of();

  @Schema(
      requiredMode = Schema.RequiredMode.REQUIRED,
      description = "Backend capabilities for UPDATE, DEACTIVATE and DECLARE_SOURCE")
  @Builder.Default
  private List<FiberActionCapabilityDto> capabilities = List.of();
}
