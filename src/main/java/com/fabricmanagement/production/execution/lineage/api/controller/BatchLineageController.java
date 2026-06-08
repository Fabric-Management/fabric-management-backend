package com.fabricmanagement.production.execution.lineage.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDetailDto;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDto;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import com.fabricmanagement.production.execution.lineage.dto.TraceNodeDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/production/batches/lineage")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Batch Lineage", description = "Batch Lineage operations")
public class BatchLineageController {

  private final BatchLineageService batchLineageService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<ApiResponse<BatchLineageDto>> createLineage(
      @Valid @RequestBody CreateBatchLineageRequest request) {
    BatchLineageDto lineage = batchLineageService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(lineage));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<BatchLineageDto>>> getAll(Pageable pageable) {
    Page<BatchLineageDto> lineages = batchLineageService.getAll(pageable);
    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(lineages)));
  }

  /** Forward trace: what input batches were consumed to produce this batch? */
  @GetMapping("/parents/{childBatchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<List<BatchLineageDto>>> getParents(
      @PathVariable UUID childBatchId) {
    List<BatchLineageDto> parents = batchLineageService.getParents(childBatchId);
    return ResponseEntity.ok(ApiResponse.success(parents));
  }

  /** Aggregated lineage detail: focal batch + enriched parents & children in one call. */
  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<BatchLineageDetailDto>> getLineageDetail(
      @PathVariable UUID batchId) {
    BatchLineageDetailDto detail = batchLineageService.getLineageDetail(batchId);
    return ResponseEntity.ok(ApiResponse.success(detail));
  }

  /** Backward trace: where was this parent batch used as input? */
  @GetMapping("/children/{parentBatchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<List<BatchLineageDto>>> getChildren(
      @PathVariable UUID parentBatchId) {
    List<BatchLineageDto> children = batchLineageService.getChildren(parentBatchId);
    return ResponseEntity.ok(ApiResponse.success(children));
  }

  /** Recursive backward trace: full ancestry tree (cotton → yarn → fabric). */
  @GetMapping("/trace-backward/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<TraceNodeDto>> traceBackward(@PathVariable UUID batchId) {
    TraceNodeDto tree = batchLineageService.traceBackward(batchId);
    return ResponseEntity.ok(ApiResponse.success(tree));
  }

  /** Recursive forward trace: full descendant tree (cotton → yarn → fabric). */
  @GetMapping("/trace-forward/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<TraceNodeDto>> traceForward(@PathVariable UUID batchId) {
    TraceNodeDto tree = batchLineageService.traceForward(batchId);
    return ResponseEntity.ok(ApiResponse.success(tree));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<ApiResponse<Void>> deleteLineage(@PathVariable UUID id) {
    batchLineageService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Batch lineage deleted"));
  }
}
