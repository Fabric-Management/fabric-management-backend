package com.fabricmanagement.production.quality.result.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.quality.result.app.FiberTestResultService;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.dto.CreateFiberTestResultRequest;
import com.fabricmanagement.production.quality.result.dto.FiberTestResultDto;
import com.fabricmanagement.production.quality.result.dto.UpdateApprovalRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
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
 * <p>Security uses department-aware checks via {@code PermissionEvaluator}. WRITE = record tests /
 * approve-reject (ADMIN, or MANAGER/SUPERVISOR in QC / R&D / Fiber dept). READ = any authenticated
 * user in a production-related department.
 */
@RestController
@RequestMapping("/api/v1/production/quality/fiber-tests")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fiber Test Result", description = "Fiber Test Result operations")
public class FiberTestResultController {

  private final FiberTestResultService testResultService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> createTestResult(
      @Valid @RequestBody CreateFiberTestResultRequest request) {
    log.info("Recording fiber test result: batchId={}", request.getBatchId());
    FiberTestResultDto result = testResultService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> getTestResult(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(
            testResultService
                .getById(id)
                .orElseThrow(
                    () -> new EntityNotFoundException("Fiber test result not found: " + id))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getAllTestResults() {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getAll()));
  }

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByBatch(
      @PathVariable UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByBatchId(batchId)));
  }

  @GetMapping("/stock-unit/{stockUnitId}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByStockUnit(
      @PathVariable UUID stockUnitId) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByStockUnitId(stockUnitId)));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  public ResponseEntity<ApiResponse<List<FiberTestResultDto>>> getTestResultsByStatus(
      @PathVariable TestApprovalStatus status) {
    return ResponseEntity.ok(ApiResponse.success(testResultService.getByApprovalStatus(status)));
  }

  @PatchMapping("/{id}/approval")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  public ResponseEntity<ApiResponse<FiberTestResultDto>> updateApproval(
      @PathVariable UUID id, @Valid @RequestBody UpdateApprovalRequest request) {
    log.info("Updating test approval: id={}, status={}", id, request.getApprovalStatus());
    FiberTestResultDto result = testResultService.updateApproval(id, request);
    return ResponseEntity.ok(
        ApiResponse.success(result, "Test approval updated to " + request.getApprovalStatus()));
  }
}
