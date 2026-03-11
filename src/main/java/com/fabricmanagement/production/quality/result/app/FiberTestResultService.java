package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
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
  private final ApplicationEventPublisher applicationEventPublisher;

  @Transactional
  public FiberTestResultDto create(CreateFiberTestResultRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    batchRepository
        .findByIdAndTenantId(request.getBatchId(), tenantId)
        .orElseThrow(
            () -> new IllegalArgumentException("Fiber batch not found: " + request.getBatchId()));

    FiberTestResult result =
        FiberTestResult.builder()
            .batchId(request.getBatchId())
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

    return FiberTestResultDto.from(saved);
  }

  @Transactional(readOnly = true)
  public Optional<FiberTestResultDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return testResultRepository.findByTenantIdAndId(tenantId, id).map(FiberTestResultDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return testResultRepository.findByTenantIdAndBatchIdAndIsActiveTrue(tenantId, batchId).stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return testResultRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
        .map(FiberTestResultDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByApprovalStatus(TestApprovalStatus status) {
    UUID tenantId = TenantContext.getCurrentTenantId();
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
    UUID tenantId = TenantContext.getCurrentTenantId();

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
            saved.getApprovalStatus(),
            TenantContext.getCurrentUserId()));

    return FiberTestResultDto.from(saved);
  }
}
