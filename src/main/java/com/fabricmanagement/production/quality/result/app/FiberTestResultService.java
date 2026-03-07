package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.fiber.infra.repository.FiberBatchRepository;
import com.fabricmanagement.production.quality.result.domain.FiberTestResult;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import com.fabricmanagement.production.quality.result.infra.repository.FiberTestResultRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiberTestResultService {

  private final FiberTestResultRepository testResultRepository;
  private final FiberBatchRepository fiberBatchRepository;

  @Transactional
  public FiberTestResultDto create(CreateFiberTestResultRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    fiberBatchRepository
        .findByIdAndTenantId(request.getFiberBatchId(), tenantId)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Fiber batch not found: " + request.getFiberBatchId()));

    FiberTestResult result =
        FiberTestResult.builder()
            .fiberBatchId(request.getFiberBatchId())
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
    log.info(
        "Fiber test result created: id={}, batchId={}", saved.getId(), saved.getFiberBatchId());

    return FiberTestResultDto.from(saved);
  }

  @Transactional(readOnly = true)
  public Optional<FiberTestResultDto> getById(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return testResultRepository.findByTenantIdAndId(tenantId, id).map(FiberTestResultDto::from);
  }

  @Transactional(readOnly = true)
  public List<FiberTestResultDto> getByBatchId(UUID fiberBatchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return testResultRepository
        .findByTenantIdAndFiberBatchIdAndIsActiveTrue(tenantId, fiberBatchId)
        .stream()
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

    return FiberTestResultDto.from(saved);
  }
}
