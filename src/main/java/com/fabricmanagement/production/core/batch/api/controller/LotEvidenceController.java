package com.fabricmanagement.production.core.batch.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.batch.app.LotCompatibilityRequestService;
import com.fabricmanagement.production.core.batch.app.LotEvidenceService;
import com.fabricmanagement.production.core.batch.dto.LotEvidenceDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lot evidence used by order intake: finished width and lot compatibility (SOI IK-08, A04). */
@RestController
@RequestMapping("/api/v1/production/lot-evidence")
@RequiredArgsConstructor
@Tag(name = "Lot Evidence", description = "Measured finished width and lot compatibility")
public class LotEvidenceController {

  private final LotEvidenceService lotEvidenceService;
  private final LotCompatibilityRequestService requestService;

  @PostMapping("/lots/{batchId}/finished-widths")
  @PreAuthorize("@auth.can(authentication, 'quality', 'write')")
  @Operation(operationId = "recordLotFinishedWidth", summary = "Record a measured finished width")
  public ResponseEntity<ApiResponse<LotEvidenceDtos.FinishedWidthMeasurementDto>> recordWidth(
      @PathVariable UUID batchId,
      @Valid @RequestBody LotEvidenceDtos.RecordFinishedWidthRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(lotEvidenceService.recordFinishedWidth(batchId, request)));
  }

  @GetMapping("/lots/{batchId}/finished-widths")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(operationId = "listLotFinishedWidths", summary = "Finished width measurements")
  public ResponseEntity<ApiResponse<List<LotEvidenceDtos.FinishedWidthMeasurementDto>>> listWidths(
      @PathVariable UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(lotEvidenceService.finishedWidths(batchId)));
  }

  @PostMapping("/compatibility")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  @Operation(
      operationId = "confirmLotCompatibility",
      summary = "Confirm that lots may ship together under stated conditions")
  public ResponseEntity<ApiResponse<LotEvidenceDtos.CompatibilityConfirmationDto>> confirm(
      @Valid @RequestBody LotEvidenceDtos.ConfirmCompatibilityRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(lotEvidenceService.confirmCompatibility(request)));
  }

  @PostMapping("/compatibility/{confirmationId}/revoke")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  @Operation(
      operationId = "revokeLotCompatibility",
      summary = "Revoke a compatibility confirmation")
  public ResponseEntity<ApiResponse<LotEvidenceDtos.CompatibilityConfirmationDto>> revoke(
      @PathVariable UUID confirmationId) {
    return ResponseEntity.ok(
        ApiResponse.success(lotEvidenceService.revokeCompatibility(confirmationId)));
  }

  @GetMapping("/compatibility-requests")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "listOpenLotCompatibilityRequests",
      summary = "Open questions whether lots may ship together")
  public ResponseEntity<ApiResponse<List<LotEvidenceDtos.CompatibilityRequestDto>>> openRequests() {
    return ResponseEntity.ok(ApiResponse.success(requestService.open()));
  }

  @PostMapping("/compatibility-requests/{requestId}/decline")
  @PreAuthorize("@auth.can(authentication, 'quality', 'approve')")
  @Operation(
      operationId = "declineLotCompatibilityRequest",
      summary = "Record that the lots may not ship together")
  public ResponseEntity<ApiResponse<LotEvidenceDtos.CompatibilityRequestDto>> declineRequest(
      @PathVariable UUID requestId,
      @Valid @RequestBody LotEvidenceDtos.DeclineCompatibilityRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(requestService.decline(requestId, request.reason())));
  }

  @GetMapping("/compatibility")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "listLotCompatibility",
      summary = "Effective compatibility confirmations")
  public ResponseEntity<ApiResponse<List<LotEvidenceDtos.CompatibilityConfirmationDto>>> list(
      @RequestParam(required = false) UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(lotEvidenceService.compatibilityFor(batchId)));
  }
}
