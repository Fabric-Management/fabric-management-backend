package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestResultRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiberTestResultService {

  private final FiberTestResultRepository testResultRepository;
  private final BatchRepository batchRepository;
  private final StockUnitRepository stockUnitRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final FiberQcAutoEvaluator qcAutoEvaluator;

  @Transactional
  public FiberTestResultDto create(CreateFiberTestResultRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    UUID batchId;
    if (request.getStockUnitId() != null) {
      StockUnit su =
          stockUnitRepository
              .findByIdAndTenantIdAndIsActiveTrue(request.getStockUnitId(), tenantId)
              .orElseThrow(() -> new IllegalArgumentException("StockUnit not found"));
      batchId = su.getBatchId();
      if (request.getBatchId() != null && !request.getBatchId().equals(batchId)) {
        throw new IllegalArgumentException("Provided batchId does not match StockUnit's batch");
      }
    } else {
      if (request.getBatchId() == null) {
        throw new IllegalArgumentException("batchId is required for batch-level tests");
      }
      batchId = request.getBatchId();
    }

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Fiber batch not found: " + batchId));

    FiberTestResult result =
        FiberTestResult.builder()
            .batchId(batchId)
            .stockUnitId(request.getStockUnitId())
            .testDate(request.getTestDate())
            .testType(request.getTestType() != null ? request.getTestType() : "LABORATORY")
            .fineness(request.getFineness())
            .lengthMm(request.getLengthMm())
            .strengthCndTex(request.getStrengthCndTex())
            .elongationPercent(request.getElongationPercent())
            .moisturePercent(request.getMoisturePercent())
            .trashContentPercent(request.getTrashContentPercent())
            .uniformityIndex(request.getUniformityIndex())
            .approvalStatus(TestApprovalStatus.PENDING)
            .testLab(request.getTestLab())
            .testStandard(request.getTestStandard())
            .remarks(request.getRemarks())
            .build();

    FiberTestResult saved = testResultRepository.save(result);
    log.info("Fiber test result created: id={}, batchId={}", saved.getId(), saved.getBatchId());

    // QC auto-decision: evaluate against FiberQualityStandard
    FiberQcAutoEvaluator.EvaluationResult eval = qcAutoEvaluator.evaluate(saved, batch, tenantId);

    if (eval.hasStandard()) {
      saved.setApprovalStatus(eval.approvalStatus());
      saved = testResultRepository.save(saved);
      applicationEventPublisher.publishEvent(
          new FiberTestResultApprovedEvent(
              tenantId,
              saved.getBatchId(),
              saved.getStockUnitId(),
              saved.getApprovalStatus(),
              TenantContext.getCurrentUserId()));
    } else if (eval.diagnosticCode() != null) {
      String target = eval.targetLabel() != null ? eval.targetLabel() : batch.getBatchCode();
      applicationEventPublisher.publishEvent(
          new BatchQualityStandardMissingEvent(
              tenantId,
              batchId,
              batch.getBatchCode(),
              target,
              manualReviewReasonCode(eval.diagnosticCode())));
      log.info(
          "No applicable quality standard, manual review requested: batchId={}, target={}, reason={}",
          batchId,
          target,
          eval.diagnosticCode());
    }

    return FiberTestResultDto.from(saved);
  }

  /**
   * The reasons the notification has wording for; any other diagnostic reads as the general one.
   */
  static final Set<String> MANUAL_REVIEW_REASONS =
      Set.of(
          "BATCH_COMPOSITION_UNKNOWN",
          "FIBER_PRODUCT_NOT_FOUND",
          "QUALITY_PROFILE_UNAVAILABLE",
          "QUALITY_PROFILE_NOT_APPLICABLE");

  static final String GENERAL_MANUAL_REVIEW_REASON = "NO_QUALITY_PROFILE";

  /**
   * The reason code the notification translates ({@code notification.label.reason.*}); the
   * diagnostic code itself stays in the logs.
   */
  static String manualReviewReasonCode(String diagnosticCode) {
    return diagnosticCode != null && MANUAL_REVIEW_REASONS.contains(diagnosticCode)
        ? diagnosticCode
        : GENERAL_MANUAL_REVIEW_REASON;
  }

  @Transactional(readOnly = true)
  public Optional<FiberTestResultDto> getById(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    return testResultRepository.findByTenantIdAndId(tenantId, id).map(FiberTestResultDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
    return testResultRepository.findByTenantIdAndBatchIdAndIsActiveTrue(tenantId, batchId).stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getAll() {
    UUID tenantId = TenantContext.requireTenantId();
    return testResultRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByStockUnitId(UUID stockUnitId) {
    UUID tenantId = TenantContext.requireTenantId();
    return testResultRepository
        .findByTenantIdAndStockUnitIdAndIsActiveTrue(tenantId, stockUnitId)
        .stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByApprovalStatus(TestApprovalStatus status) {
    UUID tenantId = TenantContext.requireTenantId();
    return testResultRepository.findByTenantIdAndApprovalStatus(tenantId, status).stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  /**
   * Quality engineer approval — stamps the test result with a decision. Only PENDING results can be
   * approved/rejected.
   */
  @Transactional
  public FiberTestResultDto updateApproval(UUID id, UpdateApprovalRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    FiberTestResult result =
        testResultRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Test result not found: " + id));

    if (result.getApprovalStatus() != TestApprovalStatus.PENDING) {
      throw new IllegalStateException(
          "Test result already decided: "
              + result.getApprovalStatus()
              + ". Create a new test to re-evaluate.");
    }

    result.setApprovalStatus(request.getApprovalStatus());
    if (request.getRemarks() != null) {
      result.setRemarks(request.getRemarks());
    }

    FiberTestResult saved = testResultRepository.save(result);
    log.info(
        "Test result approval updated: id={}, status={}", saved.getId(), saved.getApprovalStatus());

    applicationEventPublisher.publishEvent(
        new FiberTestResultApprovedEvent(
            tenantId,
            saved.getBatchId(),
            saved.getStockUnitId(),
            saved.getApprovalStatus(),
            TenantContext.getCurrentUserId()));

    return FiberTestResultDto.from(saved);
  }
}
