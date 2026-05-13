package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fiber DTO - Data transfer object for Fiber entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID productId;
  private UUID fiberCategoryId;
  private UUID fiberIsoCodeId;
  private FiberIsoCodeDto isoCode;
  private String fiberName;
  private FiberStatus status;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  @Builder.Default private Map<UUID, BigDecimal> composition = Map.of();

  /**
   * Map entity to DTO.
   *
   * <p><b>STANDARD:</b> All DTOs use this pattern
   */
  public static FiberDto from(Fiber entity) {
    return FiberDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .productId(entity.getProductId())
        .fiberCategoryId(entity.getFiberCategoryId())
        .fiberIsoCodeId(entity.getFiberIsoCodeId())
        .isoCode(
            entity.getFiberIsoCode() != null
                ? FiberIsoCodeDto.from(entity.getFiberIsoCode())
                : null)
        .fiberName(entity.getFiberName())
        .status(entity.getStatus())
        .composition(entity.getComposition())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
