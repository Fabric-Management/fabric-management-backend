package com.fabricmanagement.production.execution.lineage.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDto;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
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
 * REST API for batch lineage (traceability).
 *
 * <p>Provides forward trace ("what went into this batch?") and backward trace ("where was this
 * batch used?") queries for full production traceability.
 */
@RestController
@RequestMapping("/api/production/batches/lineage")
@RequiredArgsConstructor
@Slf4j
public class BatchLineageController {

  private final BatchLineageService batchLineageService;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchLineageDto>> createLineage(
      @Valid @RequestBody CreateBatchLineageRequest request) {
    BatchLineageDto lineage = batchLineageService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(lineage));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchLineageDto>>> getAll() {
    List<BatchLineageDto> lineages = batchLineageService.getAll();
    return ResponseEntity.ok(ApiResponse.success(lineages));
  }

  /** Forward trace: what input batches were consumed to produce this batch? */
  @GetMapping("/parents/{childBatchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchLineageDto>>> getParents(
      @PathVariable UUID childBatchId) {
    List<BatchLineageDto> parents = batchLineageService.getParents(childBatchId);
    return ResponseEntity.ok(ApiResponse.success(parents));
  }

  /** Backward trace: where was this parent batch used as input? */
  @GetMapping("/children/{parentBatchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchLineageDto>>> getChildren(
      @PathVariable UUID parentBatchId) {
    List<BatchLineageDto> children = batchLineageService.getChildren(parentBatchId);
    return ResponseEntity.ok(ApiResponse.success(children));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<Void>> deleteLineage(@PathVariable UUID id) {
    batchLineageService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Batch lineage deleted"));
  }
}
