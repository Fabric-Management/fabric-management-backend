package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.app.FiberQualityQueryService;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchCompositionSnapshot;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a FiberTestResult against the batch's applicable quality profile (FIBER-CATALOG-1).
 *
 * <ul>
 *   <li>The profile comes from the shared resolver applied to the batch's stored composition
 *       snapshot: a stored profile is re-checked and never switched; without one the exact FIBER
 *       default, then the ISO default for a pure fibre at 100%. A blend never borrows its dominant
 *       component's ISO profile.
 *   <li>No evaluable profile (none applies, stored one unavailable/inapplicable, or unknown
 *       composition): the result stays PENDING for manual review with an explicit diagnostic.
 *   <li>Otherwise all seven metrics are compared to min/target/max: all at target → APPROVED; all
 *       within limits, one or more off target → CONDITIONAL_ACCEPT; any outside → REJECTED.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberQcAutoEvaluator {

  private static final double TARGET_TOLERANCE = 1e-6;

  private final FiberQualityQueryService fiberQualityQueryService;

  /**
   * Result of auto-evaluation. {@code hasStandard=false} keeps the manual path; {@code
   * diagnosticCode} then names why (e.g. NO_APPLICABLE_QUALITY_PROFILE, BATCH_COMPOSITION_UNKNOWN).
   */
  public record EvaluationResult(
      TestApprovalStatus approvalStatus,
      boolean hasStandard,
      String targetLabel,
      String diagnosticCode) {}

  @Transactional(readOnly = true)
  public EvaluationResult evaluate(FiberTestResult result, Batch batch, UUID tenantId) {
    if (batch.getProductType() != ProductType.FIBER) {
      log.debug("Skipping QC auto-eval: batch productType={}", batch.getProductType());
      return new EvaluationResult(TestApprovalStatus.PENDING, false, null, null);
    }

    Optional<Map<UUID, BigDecimal>> snapshot = BatchCompositionSnapshot.read(batch.getAttributes());
    FiberQualityQueryService.StoredEvaluation stored =
        fiberQualityQueryService.evaluateStored(
            tenantId, batch.getProductId(), snapshot, batch.getQualityStandardId());

    if (stored.standard() == null) {
      log.info(
          "No evaluable quality profile for batch {} ({}): {}; stays PENDING for manual review",
          batch.getId(),
          stored.targetLabel(),
          stored.diagnosticCode());
      return new EvaluationResult(
          TestApprovalStatus.PENDING, false, stored.targetLabel(), stored.diagnosticCode());
    }

    FiberQualityStandard standard = stored.standard();
    TestApprovalStatus status = evaluateAgainstStandard(result, standard);
    log.info(
        "QC auto-eval: batchId={}, profile={}, result={}",
        batch.getId(),
        standard.getStandardName(),
        status);
    return new EvaluationResult(status, true, stored.targetLabel(), null);
  }

  private TestApprovalStatus evaluateAgainstStandard(
      FiberTestResult result, FiberQualityStandard standard) {
    boolean anyRejected = false;
    boolean anyConditional = false;

    if (checkRejected(
        result.getFineness(),
        standard.getFinenessMin(),
        standard.getFinenessTarget(),
        standard.getFinenessMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getFineness(),
        standard.getFinenessMin(),
        standard.getFinenessTarget(),
        standard.getFinenessMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getLengthMm(),
        standard.getLengthMmMin(),
        standard.getLengthMmTarget(),
        standard.getLengthMmMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getLengthMm(),
        standard.getLengthMmMin(),
        standard.getLengthMmTarget(),
        standard.getLengthMmMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getStrengthCndTex(),
        standard.getStrengthCndTexMin(),
        standard.getStrengthCndTexTarget(),
        standard.getStrengthCndTexMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getStrengthCndTex(),
        standard.getStrengthCndTexMin(),
        standard.getStrengthCndTexTarget(),
        standard.getStrengthCndTexMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getElongationPercent(),
        standard.getElongationPctMin(),
        standard.getElongationPctTarget(),
        standard.getElongationPctMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getElongationPercent(),
        standard.getElongationPctMin(),
        standard.getElongationPctTarget(),
        standard.getElongationPctMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getMoisturePercent(),
        standard.getMoisturePctMin(),
        standard.getMoisturePctTarget(),
        standard.getMoisturePctMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getMoisturePercent(),
        standard.getMoisturePctMin(),
        standard.getMoisturePctTarget(),
        standard.getMoisturePctMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getTrashContentPercent(),
        standard.getTrashContentPctMin(),
        standard.getTrashContentPctTarget(),
        standard.getTrashContentPctMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getTrashContentPercent(),
        standard.getTrashContentPctMin(),
        standard.getTrashContentPctTarget(),
        standard.getTrashContentPctMax())) {
      anyConditional = true;
    }

    if (checkRejected(
        result.getUniformityIndex(),
        standard.getUniformityIndexMin(),
        standard.getUniformityIndexTarget(),
        standard.getUniformityIndexMax())) {
      anyRejected = true;
    } else if (checkConditional(
        result.getUniformityIndex(),
        standard.getUniformityIndexMin(),
        standard.getUniformityIndexTarget(),
        standard.getUniformityIndexMax())) {
      anyConditional = true;
    }

    if (anyRejected) {
      return TestApprovalStatus.REJECTED;
    }
    if (anyConditional) {
      return TestApprovalStatus.CONDITIONAL_ACCEPT;
    }
    return TestApprovalStatus.APPROVED;
  }

  /** Value outside [min, max] or missing when standard enforces bounds → rejected. */
  private boolean checkRejected(Double value, Double min, Double target, Double max) {
    if (min == null && max == null) {
      return false;
    }
    if (value == null) {
      return true;
    }
    if (min != null && value < min) {
      return true;
    }
    if (max != null && value > max) {
      return true;
    }
    return false;
  }

  /**
   * Value within [min, max] but outside target, or missing when only target is defined →
   * conditional.
   */
  private boolean checkConditional(Double value, Double min, Double target, Double max) {
    if (target == null) {
      return false;
    }
    if (value == null) {
      return true;
    }
    boolean withinRange = (min == null || value >= min) && (max == null || value <= max);
    if (!withinRange) {
      return false;
    }
    return Math.abs(value - target) > TARGET_TOLERANCE;
  }
}
