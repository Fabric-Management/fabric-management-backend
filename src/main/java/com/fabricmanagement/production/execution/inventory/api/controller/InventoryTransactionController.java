package com.fabricmanagement.production.execution.inventory.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.inventory.app.InventoryTransactionService;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.dto.CreateInventoryTransactionRequest;
import com.fabricmanagement.production.execution.inventory.dto.InventoryTransactionDto;
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

  private final InventoryTransactionService transactionService;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<InventoryTransactionDto>> createTransaction(
      @Valid @RequestBody CreateInventoryTransactionRequest request) {
    InventoryTransactionDto txn = transactionService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(txn));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getAll() {
    return ResponseEntity.ok(ApiResponse.success(transactionService.getAll()));
  }

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getByBatch(
      @PathVariable UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(transactionService.getByBatchId(batchId)));
  }

  @GetMapping("/batch/{batchId}/type/{type}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<InventoryTransactionDto>>> getByBatchAndType(
      @PathVariable UUID batchId, @PathVariable InventoryTransactionType type) {
    return ResponseEntity.ok(
        ApiResponse.success(transactionService.getByBatchIdAndType(batchId, type)));
  }
}
