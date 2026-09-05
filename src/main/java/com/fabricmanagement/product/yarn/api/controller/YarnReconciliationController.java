package com.fabricmanagement.product.yarn.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.product.yarn.app.backfill.YarnReadinessService;
import com.fabricmanagement.product.yarn.app.backfill.YarnReconciliationService;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.dto.YarnReadinessDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationCandidatePageDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationChooseRequest;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationItemDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/yarns")
@RequiredArgsConstructor
@Validated
@Tag(name = "Yarn Reconciliation", description = "Legacy reconciliation and readiness visibility")
public class YarnReconciliationController {

  private final YarnReconciliationService reconciliationService;
  private final YarnReadinessService readinessService;

  @GetMapping("/reconciliations")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "listYarnReconciliations", summary = "List reconciliation rows")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<PagedResponse<YarnReconciliationItemDto>>> list(
      @RequestParam(name = "status", defaultValue = "OPEN") YarnBackfillQueueStatus status,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int size) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(reconciliationService.list(status, PageRequest.of(page, size)))));
  }

  @GetMapping("/reconciliations/{id}/candidates")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(
      operationId = "listYarnReconciliationCandidates",
      summary = "List byte-equal candidate groups")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page")
  public ResponseEntity<ApiResponse<YarnReconciliationCandidatePageDto>> candidates(
      @PathVariable("id") UUID id,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(200) int size) {
    return ResponseEntity.ok(ApiResponse.success(reconciliationService.candidates(id, page, size)));
  }

  @PostMapping("/reconciliations/{id}/choose")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(
      operationId = "chooseYarnReconciliationCandidate",
      summary = "Adopt one stored candidate")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Resolved")
  public ResponseEntity<ApiResponse<Void>> choose(
      @PathVariable("id") UUID id, @Valid @RequestBody YarnReconciliationChooseRequest request) {
    reconciliationService.choose(id, request);
    return ResponseEntity.ok(ApiResponse.<Void>success(null));
  }

  @PostMapping("/reconciliations/{id}/dismiss")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'write')")
  @Operation(operationId = "dismissYarnReconciliation", summary = "Dismiss all stored candidates")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Resolved")
  public ResponseEntity<ApiResponse<Void>> dismiss(@PathVariable("id") UUID id) {
    reconciliationService.dismiss(id);
    return ResponseEntity.ok(ApiResponse.<Void>success(null));
  }

  @GetMapping("/readiness")
  @PreAuthorize("@auth.can(authentication, 'yarn', 'read')")
  @Operation(operationId = "getYarnReadiness", summary = "Report tenant yarn readiness")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Readiness report")
  public ResponseEntity<ApiResponse<YarnReadinessDto>> readiness(
      @RequestParam(name = "blockersLimit", defaultValue = "50") @Min(1) @Max(200)
          int blockersLimit) {
    return ResponseEntity.ok(ApiResponse.success(readinessService.readiness(blockersLimit)));
  }
}
