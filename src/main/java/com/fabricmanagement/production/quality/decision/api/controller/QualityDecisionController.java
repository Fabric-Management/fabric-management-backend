package com.fabricmanagement.production.quality.decision.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionQueryService;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.decision.app.TrustedDecisionContext;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionDto;
import com.fabricmanagement.production.quality.decision.dto.QualityDecisionUnitDto;
import com.fabricmanagement.production.quality.decision.dto.QualityQueueItemDto;
import com.fabricmanagement.production.quality.decision.dto.RecordQualityDecisionRequest;
import com.fabricmanagement.production.quality.decision.mapper.QualityDecisionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/quality")
@RequiredArgsConstructor
@Tag(name = "Quality Decisions", description = "Immutable stock quality disposition decisions")
public class QualityDecisionController {

  private final QualityDecisionService decisionService;
  private final QualityDecisionQueryService queryService;
  private final QualityDecisionMapper mapper;

  @PostMapping("/batches/{batchId}/decisions")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  @Operation(operationId = "recordQualityDecision", summary = "Record a quality decision")
  public ResponseEntity<ApiResponse<QualityDecisionDto>> record(
      @PathVariable UUID batchId, @Valid @RequestBody RecordQualityDecisionRequest request) {
    var decision =
        decisionService.recordDecision(
            TrustedDecisionContext.manual(TenantContext.getCurrentUserId()),
            request.toCommand(batchId));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(mapper.toDto(decision)));
  }

  @GetMapping("/queue")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  @Operation(operationId = "listPendingQualityQueue", summary = "List batches awaiting QC")
  public ResponseEntity<ApiResponse<PagedResponse<QualityQueueItemDto>>> queue(
      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(queryService.getQueue(pageable))));
  }

  @GetMapping("/batches/{batchId}/decisions")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  @Operation(operationId = "listQualityDecisionHistory", summary = "List batch decision history")
  public ResponseEntity<ApiResponse<PagedResponse<QualityDecisionDto>>> history(
      @PathVariable UUID batchId, @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(queryService.getHistory(batchId, pageable), mapper::toDto)));
  }

  @GetMapping("/batches/{batchId}/units")
  @PreAuthorize("@auth.can(authentication, 'quality', 'read')")
  @Operation(operationId = "listQualityDecisionUnits", summary = "List batch units for QC")
  public ResponseEntity<ApiResponse<PagedResponse<QualityDecisionUnitDto>>> units(
      @PathVariable UUID batchId,
      @ParameterObject @PageableDefault(size = 20, sort = "barcode") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(queryService.getUnits(batchId, pageable), mapper::toUnitDto)));
  }
}
