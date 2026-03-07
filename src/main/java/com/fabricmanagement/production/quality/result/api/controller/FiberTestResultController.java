package com.fabricmanagement.production.quality.result.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.quality.result.app.FiberTestResultService;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for fiber laboratory test results and quality gate decisions.
 *
 * <p>Security uses department-aware checks via {@code ProductionAccessService}. WRITE = record
 * tests / approve-reject (ADMIN, or MANAGER/SUPERVISOR in QC / R&D / Fiber dept). READ = any
 * authenticated user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/quality/fiber-tests")
@RequiredArgsConstructor
@Slf4j
public class FiberTestResultController {

  private final FiberTestResultService testResultService;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> createTestResult(
      @Valid @RequestBody CreateFiberTestResultRequest request) {
    log.info("Recording fiber test result: batchId={}", request.getFiberBatchId());
    FiberTestResultDto result = testResultService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'READ')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> getTestResult(@PathVariable UUID id) {
    return testResultService
        .getById(id)
        .map(result -> ResponseEntity.ok(ApiResponse.success(result)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getAllTestResults() {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getAll()));
  }

  @GetMapping("/batch/{fiberBatchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByBatch(
      @PathVariable UUID fiberBatchId) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByBatchId(fiberBatchId)));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByStatus(
      @PathVariable TestApprovalStatus status) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByApprovalStatus(status)));
  }

  @PatchMapping("/{id}/approval")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'QUALITY_TEST', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> updateApproval(
      @PathVariable UUID id, @Valid @RequestBody UpdateApprovalRequest request) {
    log.info("Updating test approval: id={}, status={}", id, request.getApprovalStatus());
    FiberTestResultDto result = testResultService.updateApproval(id, request);
    return ResponseEntity.ok(
        ApiResponse.success(result, "Test approval updated to " + request.getApprovalStatus()));
  }
}
