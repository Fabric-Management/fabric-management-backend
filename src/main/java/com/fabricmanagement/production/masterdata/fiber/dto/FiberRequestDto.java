package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequest;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequestStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for FiberRequest entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberRequestDto {

  private UUID id;
  private UUID tenantId;

  /** Tenant display name (platform list only). */
  private String tenantName;

  private String uid;
  private UUID requestedBy;
  private String isoCode;
  private String fiberName;
  private String fiberType;
  private String description;
  private FiberRequestStatus status;
  private UUID reviewedBy;
  private String reviewNote;
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * Build DTO from entity. Use from(entity, tenantName) for platform list to include tenant name.
   */
  public static FiberRequestDto from(FiberRequest entity) {
    return from(entity, null);
  }

  public static FiberRequestDto from(FiberRequest entity, String tenantName) {
    if (entity == null) {
      return null;
    }
    return FiberRequestDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .tenantName(tenantName)
        .uid(entity.getUid())
        .requestedBy(entity.getRequestedBy())
        .isoCode(entity.getIsoCode())
        .fiberName(entity.getFiberName())
        .fiberType(entity.getFiberType())
        .description(entity.getDescription())
        .status(entity.getStatus())
        .reviewedBy(entity.getReviewedBy())
        .reviewNote(entity.getReviewNote())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
