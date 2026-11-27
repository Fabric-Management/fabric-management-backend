package com.fabricmanagement.production.quality.result.dto;

import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
  private Double uniformityIndex;

  // Quality gate
  private TestApprovalStatus approvalStatus;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      description = "Evaluation at recording time. Absent for older results and non-fibre lots.")
  private FiberTestEvaluationDto evaluation;

  // Metadata
  @Schema(description = "Recorded external laboratory trading partner identifier, if selected.")
  private UUID laboratoryId;

  @Schema(description = "Recorded fibre test standard catalogue identifier, if selected.")
  private UUID testStandardId;

  @Schema(description = "Laboratory name captured at recording time, or legacy laboratory text.")
  private String testLab;

  @Schema(description = "Standard code captured at recording time, or legacy standard text.")
  private String testStandard;

  @Schema(description = "Standard name captured at recording time, if selected.")
  private String testStandardName;

  @Schema(description = "Standard revision captured at recording time, when specified.")
  private String testStandardRevision;

  private String remarks;
  private Boolean isActive;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  public static FiberTestResultDto from(FiberTestResult entity) {
    var evaluation = entity.getEvaluation();
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
        .uniformityIndex(entity.getUniformityIndex())
        .approvalStatus(entity.getApprovalStatus())
        .evaluation(evaluation == null ? null : FiberTestEvaluationDto.from(evaluation))
        .laboratoryId(entity.getLaboratoryId())
        .testStandardId(entity.getTestStandardId())
        .testLab(entity.getTestLab())
        .testStandard(entity.getTestStandard())
        .testStandardName(entity.getTestStandardName())
        .testStandardRevision(entity.getTestStandardRevision())
        .remarks(entity.getRemarks())
        .version(entity.getVersion())
        .isActive(entity.getIsActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
