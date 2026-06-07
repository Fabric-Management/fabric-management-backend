package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestResultRepository;
import java.util.List;
import java.util.Optional;
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
  private final InAppNotificationService notificationService;

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
    } else if (eval.isoCodeLabel() != null) {
      notificationService.sendToTenantRoles(
          tenantId,
          InAppNotificationService.QUARANTINE_NOTIFY_ROLES,
          NotificationType.BATCH_NO_QUALITY_STANDARD,
          "No Quality Standard Defined",
          String.format(
              "No quality standard defined for %s. Manual review required.", eval.isoCodeLabel()),
          request.getBatchId(),
          "BATCH",
          NotificationDeliveryChannel.IN_APP);
      log.info(
          "BATCH_NO_QUALITY_STANDARD notification sent: batchId={}, isoCode={}",
          request.getBatchId(),
          eval.isoCodeLabel());
    }

    return FiberTestResultDto.from(saved);
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
