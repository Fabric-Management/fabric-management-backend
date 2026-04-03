package com.fabricmanagement.production.quality.result.dto;

import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberTestResultDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private UUID batchId;
  private UUID stockUnitId;
  private Instant testDate;
  private String testType;

  // Big 4
  private Double fineness;
  private Double lengthMm;
  private Double strengthCndTex;
  private Double elongationPercent;

  // Extended
  private Double moisturePercent;
  private Double trashContentPercent;

  // Quality gate
  private TestApprovalStatus approvalStatus;

  // Metadata
  private String testLab;
  private String testStandard;
  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static FiberTestResultDto from(FiberTestResult entity) {
    return FiberTestResultDto.builder()
        .id(entity.getId())
        .tenantId(entity.getTenantId())
        .uid(entity.getUid())
        .batchId(entity.getBatchId())
        .stockUnitId(entity.getStockUnitId())
        .testDate(entity.getTestDate())
        .testType(entity.getTestType())
        .fineness(entity.getFineness())
        .lengthMm(entity.getLengthMm())
        .strengthCndTex(entity.getStrengthCndTex())
        .elongationPercent(entity.getElongationPercent())
        .moisturePercent(entity.getMoisturePercent())
        .trashContentPercent(entity.getTrashContentPercent())
        .approvalStatus(entity.getApprovalStatus())
        .testLab(entity.getTestLab())
        .testStandard(entity.getTestStandard())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
