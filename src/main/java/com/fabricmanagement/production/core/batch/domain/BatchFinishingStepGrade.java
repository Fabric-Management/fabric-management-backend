package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The metres a finishing step returned in one quality grade (FABRIC-3c-2b). Written once, with the
 * receive; the rows of a step add up to its returned metres. The grade is referenced by id and read
 * through the quality-grade query service, never joined.
 */
@Entity
@Table(name = "batch_finishing_step_grade", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchFinishingStepGrade extends BaseEntity {

  @Column(name = "step_id", nullable = false, updatable = false)
  private UUID stepId;

  @Column(name = "quality_grade_id", nullable = false, updatable = false)
  private UUID qualityGradeId;

  @Column(name = "metres", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal metres;

  public static BatchFinishingStepGrade record(
      UUID tenantId, UUID stepId, FinishingGradeMetres row) {
    if (tenantId == null || stepId == null || row == null) {
      throw new IllegalArgumentException("Tenant, step and grade row are required");
    }
    BatchFinishingStepGrade grade = new BatchFinishingStepGrade();
    grade.setTenantId(tenantId);
    grade.stepId = stepId;
    grade.qualityGradeId = row.qualityGradeId();
    grade.metres = row.metres();
    return grade;
  }

  @Override
  protected String getModuleCode() {
    return "BFG";
  }
}
