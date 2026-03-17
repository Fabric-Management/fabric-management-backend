package com.fabricmanagement.production.execution.batch.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.batch.app.BatchAttributeService;
import com.fabricmanagement.production.execution.batch.app.BatchCertificationService;
import com.fabricmanagement.production.execution.batch.app.BatchOperationsService;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.domain.BatchCertificationScope;
import com.fabricmanagement.production.execution.batch.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * REST API for managing batches with named-reservation logic.
 *
 * <p>Security uses department-aware checks via {@code ProductionAccessService}. WRITE = create /
 * reserve / release / consume / adjust (ADMIN, or MANAGER/SUPERVISOR in production depts). READ =
 * any authenticated user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/batches")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

  private final BatchService batchService;
  private final BatchOperationsService batchOperationsService;
  private final BatchCertificationService batchCertificationService;
  private final BatchAttributeService batchAttributeService;

  // ── Batch CRUD ─────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> createBatch(
      @Valid @RequestBody CreateBatchRequest request) {
    BatchDto batch = batchService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchDto>>> getAllBatches() {
    List<BatchDto> batches = batchService.getAll();
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<BatchDto>> getBatch(@PathVariable UUID id) {
    return batchService
        .getById(id)
        .map(batch -> ResponseEntity.ok(ApiResponse.success(batch)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/material/{materialId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchDto>>> getBatchesByMaterialId(
      @PathVariable UUID materialId) {
    List<BatchDto> batches = batchService.getByMaterialId(materialId);
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  @GetMapping("/certification-autofill")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<BatchCertificationAutoFillResponse>> getCertificationAutoFill(
      @RequestParam BatchCertificationScope scope,
      @RequestParam(required = false) UUID partnerCertificationId,
      @RequestParam(required = false) UUID orgCertificationId) {
    BatchCertificationAutoFillResponse result =
        batchCertificationService.autoFill(scope, partnerCertificationId, orgCertificationId);
    return result != null
        ? ResponseEntity.ok(ApiResponse.success(result))
        : ResponseEntity.ok(ApiResponse.success(null));
  }

  // ── Named Reservation ──────────────────────────────────────────────────────

  @PostMapping("/{id}/reserve")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchReservationDto>> reserve(
      @PathVariable UUID id, @Valid @RequestBody ReserveRequest request) {
    BatchReservationDto reservation = batchService.reserve(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reservation));
  }

  @DeleteMapping("/{id}/reservations/{reservationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> releaseReservation(
      @PathVariable UUID id, @PathVariable UUID reservationId) {
    BatchDto batch = batchService.releaseReservation(id, reservationId);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/reservations/{reservationId}/complete")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> completeReservation(
      @PathVariable UUID id, @PathVariable UUID reservationId) {
    BatchDto batch = batchService.completeReservation(id, reservationId);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @GetMapping("/{id}/reservations")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchReservationDto>>> getReservations(
      @PathVariable UUID id) {
    List<BatchReservationDto> reservations = batchService.getReservations(id);
    return ResponseEntity.ok(ApiResponse.success(reservations));
  }

  // ── Consume ────────────────────────────────────────────────────────────────

  @PostMapping("/{id}/consume")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> consume(
      @PathVariable UUID id, @Valid @RequestBody ConsumeRequest request) {
    BatchDto batch = batchService.consume(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Waste ──────────────────────────────────────────────────────────────────

  @PostMapping("/{id}/waste")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> recordWaste(
      @PathVariable UUID id, @Valid @RequestBody RecordWasteRequest request) {
    BatchDto batch = batchService.recordWaste(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Inventory Adjustment ───────────────────────────────────────────────────

  @PostMapping("/{id}/adjust")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> adjust(
      @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
    BatchDto batch = batchOperationsService.adjust(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Start Production (WIP) ─────────────────────────────────────────────────

  @PostMapping("/{id}/start-production")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> startProduction(
      @PathVariable UUID id, @Valid @RequestBody StartProductionRequest request) {
    BatchDto batch = batchOperationsService.startProduction(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Split & Transfer ───────────────────────────────────────────────────────

  /**
   * Create a blended batch from multiple parent batches. Consumption percentages must sum to 100%.
   * Returns the created child batch (201 Created).
   */
  @PostMapping("/blend")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  @Operation(summary = "Create a blended batch from multiple parent batches")
  public ResponseEntity<ApiResponse<BatchDto>> createBlendedBatch(
      @Valid @RequestBody CreateBlendedBatchRequest request) {
    BatchDto batch = batchOperationsService.createBlendedBatch(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  /**
   * Split batch: acceptedQuantity → new AVAILABLE batch; remainder stays in source with
   * RETURNED/DESTROYED. Returns both batches.
   */
  @PostMapping("/{id}/split")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<SplitBatchResponse>> splitBatch(
      @PathVariable UUID id, @Valid @RequestBody SplitBatchRequest request) {
    SplitBatchResponse response = batchOperationsService.splitBatch(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  /**
   * Partial acceptance split (QC kısmi kabul). Source batch remainder gets rejectedStatus; new
   * batch with acceptedQuantity is AVAILABLE.
   */
  @PostMapping("/{id}/split-partial-acceptance")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> splitPartialAcceptance(
      @PathVariable UUID id, @Valid @RequestBody PartialAcceptanceSplitRequest request) {
    BatchDto batch = batchOperationsService.splitPartialAcceptance(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/transfer")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> transferBatch(
      @PathVariable UUID id, @Valid @RequestBody TransferBatchRequest request) {
    BatchDto batch = batchOperationsService.transferBatch(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  /**
   * Override batch status (QC_REJECTED or QUARANTINE → AVAILABLE). Requires reason (min 10 chars).
   * Logged to override_log for audit. Manager-only: SUPERVISOR/WORKER/VIEWER → 403.
   */
  @PatchMapping("/{id}/override-status")
  @PreAuthorize("@productionAccessService.hasManagerPermission(authentication, 'BATCH')")
  public ResponseEntity<ApiResponse<BatchDto>> overrideStatus(
      @PathVariable UUID id, @Valid @RequestBody OverrideStatusRequest request) {
    BatchDto batch = batchOperationsService.overrideStatus(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Certifications ────────────────────────────────────────────────────────

  @GetMapping("/{id}/certifications")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchCertificationDto>>> getCertifications(
      @PathVariable UUID id) {
    List<BatchCertificationDto> list = batchCertificationService.findByBatchId(id);
    return ResponseEntity.ok(ApiResponse.success(list));
  }

  @PostMapping("/{id}/certifications/copy-from/{sourceBatchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  @Operation(
      summary = "Copy certifications from another batch",
      description =
          "Copies all active certifications from the source batch to this batch (target)."
              + " changeReason is INITIAL. Fails with a clear message if the target already has any"
              + " of the same certifications (same cert + scope + partner/facility). Tenant and"
              + " WRITE permission enforced.")
  public ResponseEntity<ApiResponse<List<BatchCertificationDto>>> copyCertificationsFromBatch(
      @Parameter(description = "Target batch ID") @PathVariable UUID id,
      @Parameter(description = "Source batch ID") @PathVariable UUID sourceBatchId) {
    List<BatchCertificationDto> copied = batchCertificationService.copyFromBatch(id, sourceBatchId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(copied));
  }

  @PostMapping("/{id}/certifications")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  @Operation(
      summary = "Add batch certification",
      description =
          "Adds a certification to a batch. When scope is SUPPLIER or FACILITY, the response may"
              + " include a **warnings** array (e.g. referenced supplier/facility certification has"
              + " expired). Operation still succeeds; warnings are for GOTS compliance awareness.")
  public ResponseEntity<ApiResponse<BatchCertificationDto>> addCertification(
      @PathVariable UUID id, @Valid @RequestBody AddBatchCertificationRequest request) {
    var result = batchCertificationService.add(id, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            result.getWarnings().isEmpty()
                ? ApiResponse.success(result.getData())
                : ApiResponse.success(result.getData(), result.getWarnings()));
  }

  @PutMapping("/{id}/certifications/{certificationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  @Operation(
      summary = "Update batch certification",
      description =
          "Updates an existing batch certification (partial update). changeReason is required;"
              + " other fields are optional. Soft-deleted records cannot be updated. Response may"
              + " include a **warnings** array when the referenced supplier/facility certification"
              + " has expired (GOTS compliance).")
  public ResponseEntity<ApiResponse<BatchCertificationDto>> updateCertification(
      @Parameter(description = "Batch ID") @PathVariable UUID id,
      @Parameter(description = "Batch certification record ID") @PathVariable UUID certificationId,
      @Valid @RequestBody UpdateBatchCertificationRequest request) {
    var result = batchCertificationService.update(id, certificationId, request);
    return ResponseEntity.ok(
        result.getWarnings().isEmpty()
            ? ApiResponse.success(result.getData())
            : ApiResponse.success(result.getData(), result.getWarnings()));
  }

  @DeleteMapping("/{id}/certifications/{certificationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  @Operation(
      summary = "Delete batch certification",
      description = "Soft-deletes a batch certification. Already deleted records return 404.")
  public ResponseEntity<Void> deleteCertification(
      @Parameter(description = "Batch ID") @PathVariable UUID id,
      @Parameter(description = "Batch certification record ID") @PathVariable
          UUID certificationId) {
    batchCertificationService.delete(id, certificationId);
    return ResponseEntity.noContent().build();
  }

  // ── Attributes ─────────────────────────────────────────────────────────────

  @GetMapping("/{id}/attributes")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchAttributeDto>>> getAttributes(@PathVariable UUID id) {
    List<BatchAttributeDto> list = batchAttributeService.findByBatchId(id);
    return ResponseEntity.ok(ApiResponse.success(list));
  }

  @PostMapping("/{id}/attributes")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchAttributeDto>> addAttribute(
      @PathVariable UUID id, @Valid @RequestBody AddBatchAttributeRequest request) {
    BatchAttributeDto created = batchAttributeService.add(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
  }

  @DeleteMapping("/{id}/attributes/{attributeId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<Void> deleteAttribute(
      @PathVariable UUID id, @PathVariable UUID attributeId) {
    batchAttributeService.delete(id, attributeId);
    return ResponseEntity.noContent().build();
  }

  // ── Treatments (stub – returns empty until full implementation) ──────────

  /** Treatment catalog reference. Returns empty list until treatment master data exists. */
  @GetMapping("/treatments")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<?>>> getTreatmentCatalog() {
    return ResponseEntity.ok(ApiResponse.success(List.of()));
  }

  /** Batch treatments. Returns empty list until full implementation. */
  @GetMapping("/{id}/treatments")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<?>>> getBatchTreatments(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(List.of()));
  }

  /** Add treatment – 501 until full implementation. */
  @PostMapping("/{id}/treatments")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<Void>> addBatchTreatment(
      @PathVariable UUID id, @RequestBody(required = false) Object request) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(ApiResponse.error("NOT_IMPLEMENTED", "Batch treatments not yet implemented"));
  }

  /** Remove treatment – 501 until full implementation. */
  @DeleteMapping("/{id}/treatments/{treatmentId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<Void>> removeBatchTreatment(
      @PathVariable UUID id, @PathVariable UUID treatmentId) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(ApiResponse.error("NOT_IMPLEMENTED", "Batch treatments not yet implemented"));
  }
}
