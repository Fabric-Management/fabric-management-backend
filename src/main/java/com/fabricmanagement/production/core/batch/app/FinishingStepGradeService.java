package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepGrade;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeMetres;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishingStepGradeRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The quality-grade split of a finishing step's returned metres (FABRIC-3c-2b): checks a split a
 * caller sends, stores it with the receive, and reads it back for the step history. A split is a
 * record of the delivery; it never changes the lot's own quantity.
 */
@Service
@RequiredArgsConstructor
public class FinishingStepGradeService {

  private final BatchFinishingStepGradeRepository rows;
  private final QualityGradeQueryService qualityGrades;

  /**
   * The split a caller sent, made safe to store: well formed, adding up to the returned metres and
   * every grade an active fabric quality grade of the tenant (a grade that is not saleable, such as
   * a defective one, is fine: the firm still returns those metres).
   */
  public FinishingGradeBreakdown checked(
      List<BatchFinishingDtos.GradeMetresRequest> request, BigDecimal returnedMetres) {
    FinishingGradeBreakdown breakdown =
        FinishingGradeBreakdown.of(
            request == null
                ? List.of()
                : request.stream()
                    .map(row -> new FinishingGradeMetres(row.qualityGradeId(), row.metres()))
                    .toList());
    breakdown.requireTotal(returnedMetres);
    if (!breakdown.isEmpty()) {
      Set<UUID> usable =
          qualityGrades.findSalesGradeReferences(ProductType.FABRIC, true).stream()
              .map(QualityGradeReference::id)
              .collect(Collectors.toSet());
      for (FinishingGradeMetres row : breakdown.rows()) {
        if (!usable.contains(row.qualityGradeId())) {
          throw new NotFoundException(
              "Active fabric quality grade not found: " + row.qualityGradeId());
        }
      }
    }
    return breakdown;
  }

  /** Stores the split with the step that was just received. */
  public void save(UUID tenantId, UUID stepId, FinishingGradeBreakdown breakdown) {
    if (breakdown.isEmpty()) {
      return;
    }
    rows.saveAll(
        breakdown.rows().stream()
            .map(row -> BatchFinishingStepGrade.record(tenantId, stepId, row))
            .toList());
  }

  /** The stored splits of the steps, best grade first; a step without a split has no entry. */
  public Map<UUID, List<BatchFinishingDtos.StepGradeDto>> forSteps(
      UUID tenantId, Collection<UUID> stepIds) {
    if (stepIds.isEmpty()) {
      return Map.of();
    }
    List<BatchFinishingStepGrade> stored =
        rows.findByTenantIdAndStepIdInAndIsActiveTrue(tenantId, stepIds);
    if (stored.isEmpty()) {
      return Map.of();
    }
    Map<UUID, QualityGradeReference> references =
        references(stored.stream().map(BatchFinishingStepGrade::getQualityGradeId).toList());
    Comparator<BatchFinishingStepGrade> bestFirst =
        Comparator.comparingInt(
                (BatchFinishingStepGrade row) -> rank(references, row.getQualityGradeId()))
            .thenComparing(BatchFinishingStepGrade::getQualityGradeId);
    Map<UUID, List<BatchFinishingDtos.StepGradeDto>> byStep = new HashMap<>();
    stored.stream()
        .sorted(bestFirst)
        .forEach(
            row -> {
              QualityGradeReference grade = references.get(row.getQualityGradeId());
              byStep
                  .computeIfAbsent(row.getStepId(), id -> new java.util.ArrayList<>())
                  .add(
                      new BatchFinishingDtos.StepGradeDto(
                          row.getQualityGradeId(),
                          grade == null ? null : grade.code(),
                          grade == null ? null : grade.name(),
                          row.getMetres()));
            });
    return byStep;
  }

  /** The grades by id, retired ones included, so an old step still shows what the grade was. */
  public Map<UUID, QualityGradeReference> references(Collection<UUID> gradeIds) {
    return qualityGrades.findReferencesByIds(gradeIds).stream()
        .collect(Collectors.toMap(QualityGradeReference::id, Function.identity(), (a, b) -> a));
  }

  private static int rank(Map<UUID, QualityGradeReference> references, UUID gradeId) {
    QualityGradeReference grade = references.get(gradeId);
    return grade == null ? Integer.MAX_VALUE : grade.rank();
  }
}
