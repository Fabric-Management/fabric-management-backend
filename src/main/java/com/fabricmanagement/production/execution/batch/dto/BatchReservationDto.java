package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** BatchReservation DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchReservationDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID batchId;
  private UUID referenceId;
  private String referenceType;
  private BigDecimal reservedQuantity;
  private BigDecimal consumedQuantity;
  private BigDecimal remainingQuantity;
  private String unit;
  private ReservationStatus status;
  private Instant reservedAt;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /** Map entity to DTO. */
  public static BatchReservationDto from(BatchReservation entity) {
    return BatchReservationDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .batchId(entity.getBatchId())
        .referenceId(entity.getReferenceId())
        .referenceType(entity.getReferenceType())
        .reservedQuantity(entity.getReservedQuantity())
        .consumedQuantity(entity.getConsumedQuantity())
        .remainingQuantity(entity.getRemainingQuantity())
        .unit(entity.getUnit())
        .status(entity.getStatus())
        .reservedAt(entity.getReservedAt())
        .remarks(entity.getRemarks())
        .isActive(entity.getIsActive())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
