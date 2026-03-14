package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates FiberTestResult against FiberQualityStandard for automatic QC decision.
 *
 * <p>When a FiberTestResult is added:
 *
 * <ul>
 *   <li>Finds default FiberQualityStandard by batch's iso_code_id (via material → Fiber)
 *   <li>If no standard: batch stays PENDING_QC, BATCH_NO_QUALITY_STANDARD notification sent
 *   <li>If standard exists: compares all values to min/target/max:
 *       <ul>
 *         <li>All within min-max, all at target → APPROVED → AVAILABLE
 *         <li>All within min-max, one+ outside target → CONDITIONAL_ACCEPT → QUARANTINE
 *         <li>One+ outside min or max → REJECTED → QC_REJECTED
 *       </ul>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiberQcAutoEvaluator {

  private static final double TARGET_TOLERANCE = 1e-6;

  private final FiberRepository fiberRepository;
  private final FiberQualityStandardRepository qualityStandardRepository;

  /** Result of auto-evaluation. Empty optional = no standard defined (manual review). */
  public record EvaluationResult(
      TestApprovalStatus approvalStatus, boolean hasStandard, String isoCodeLabel) {}

  /**
   * Evaluates test result against quality standard. Only runs for FIBER batches.
   *
   * @param result saved FiberTestResult
   * @param batch the batch (must be FIBER type)
   * @param tenantId tenant context
   * @return EvaluationResult with approval status if standard exists; empty hasStandard=false if
   *     not
   */
  @Transactional(readOnly = true)
  public EvaluationResult evaluate(FiberTestResult result, Batch batch, UUID tenantId) {
    if (batch.getMaterialType() != MaterialType.FIBER) {
      log.debug("Skipping QC auto-eval: batch materialType={}", batch.getMaterialType());
      return new EvaluationResult(TestApprovalStatus.PENDING, false, null);
    }

    Optional<Fiber> fiberOpt = fiberRepository.findByMaterialId(batch.getMaterialId());
    if (fiberOpt.isEmpty()) {
      fiberOpt = fiberRepository.findById(batch.getMaterialId());
    }
    if (fiberOpt.isEmpty()) {
      log.warn("Fiber not found for materialId={}, skipping QC auto-eval", batch.getMaterialId());
      return new EvaluationResult(TestApprovalStatus.PENDING, false, null);
    }

    Optional<FiberQualityStandard> standardOpt;
    UUID isoCodeId = null;
    if (batch.getQualityStandardId() != null) {
      standardOpt =
          qualityStandardRepository.findByTenantIdAndId(tenantId, batch.getQualityStandardId());
    } else {
      isoCodeId = fiberOpt.get().getFiberIsoCodeId();
      if (isoCodeId == null) {
        log.warn("Fiber has no iso_code_id, materialId={}", batch.getMaterialId());
        return new EvaluationResult(TestApprovalStatus.PENDING, false, null);
      }
      standardOpt =
          qualityStandardRepository.findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
              tenantId, isoCodeId);
    }

    if (standardOpt.isEmpty()) {
      String isoCode =
          fiberOpt.get().getFiberIsoCode() != null
              ? fiberOpt.get().getFiberIsoCode().getIsoCode()
              : "?";
      log.info(
          "No default quality standard for isoCodeId={} ({}), batch stays PENDING_QC",
          isoCodeId,
          isoCode);
      return new EvaluationResult(TestApprovalStatus.PENDING, false, isoCode);
    }

    FiberQualityStandard standard = standardOpt.get();
    String isoCodeLabel = standard.getIsoCode() != null ? standard.getIsoCode().getIsoCode() : "?";

    TestApprovalStatus status = evaluateAgainstStandard(result, standard);
    log.info(
        "QC auto-eval: batchId={}, isoCode={}, result={}", batch.getId(), isoCodeLabel, status);

    return new EvaluationResult(status, true, isoCodeLabel);
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

    if (anyRejected) {
      return TestApprovalStatus.REJECTED;
    }
    if (anyConditional) {
      return TestApprovalStatus.CONDITIONAL_ACCEPT;
    }
    return TestApprovalStatus.APPROVED;
  }

  /** Value outside [min, max] → rejected. */
  private boolean checkRejected(Double value, Double min, Double target, Double max) {
    if (value == null || (min == null && max == null)) {
      return false;
    }
    if (min != null && value < min) {
      return true;
    }
    if (max != null && value > max) {
      return true;
    }
    return false;
  }

  /** Value within [min, max] but outside target → conditional. */
  private boolean checkConditional(Double value, Double min, Double target, Double max) {
    if (value == null || target == null || (min == null && max == null)) {
      return false;
    }
    boolean withinRange = (min == null || value >= min) && (max == null || value <= max);
    if (!withinRange) {
      return false;
    }
    return Math.abs(value - target) > TARGET_TOLERANCE;
  }
}
