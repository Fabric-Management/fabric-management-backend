package com.fabricmanagement.production.execution.inventory.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.inventory.app.query.InventoryTransactionQueryService;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.dto.InventoryTransactionDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for inventory transactions (stock movement ledger).
 *
 * <p>Records and queries immutable transaction events: receipts, consumption, waste, adjustments,
 * transfers, returns, and lab samples.
 */
@RestController
@RequestMapping("/api/production/inventory/transactions")
@RequiredArgsConstructor
@Slf4j
public class InventoryTransactionController {

  private final InventoryTransactionQueryService transactionQueryService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InventoryTransactionDto>>> getAll(
      @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(transactionQueryService.getAll(pageable))));
  }

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InventoryTransactionDto>>> getByBatch(
      @PathVariable UUID batchId,
      @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(transactionQueryService.getByBatchId(batchId, pageable))));
  }

  @GetMapping("/batch/{batchId}/type/{type}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InventoryTransactionDto>>> getByBatchAndType(
      @PathVariable UUID batchId,
      @PathVariable InventoryTransactionType type,
      @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                transactionQueryService.getByBatchIdAndType(batchId, type, pageable))));
  }

  @GetMapping("/reference/{referenceType}/{referenceId}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<java.util.List<InventoryTransactionDto>>> getByReference(
      @PathVariable String referenceType, @PathVariable UUID referenceId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            transactionQueryService.getTransactionsByReference(referenceId, referenceType)));
  }
}
