package com.fabricmanagement.production.execution.inventory.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.production.execution.inventory.app.query.InventoryBalanceQueryService;
import com.fabricmanagement.production.execution.inventory.dto.InventoryBalanceDto;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** REST API for inventory balances (read-optimized projections). */
@RestController
@RequestMapping("/api/production/inventory/balances")
@RequiredArgsConstructor
@Slf4j
public class InventoryBalanceController {

  private final InventoryBalanceQueryService balanceQueryService;

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<Page<InventoryBalanceDto>>> getByBatch(
      @PathVariable UUID batchId, @Valid PageRequestDto pageRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(balanceQueryService.getByBatchId(batchId, pageRequest.toPageable())));
  }

  @GetMapping("/location/{locationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<Page<InventoryBalanceDto>>> getByLocation(
      @PathVariable UUID locationId, @Valid PageRequestDto pageRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(
            balanceQueryService.getByLocationId(locationId, pageRequest.toPageable())));
  }

  @GetMapping("/batch/{batchId}/location/{locationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<InventoryBalanceDto>> getByBatchAndLocation(
      @PathVariable UUID batchId, @PathVariable UUID locationId) {
    return ResponseEntity.ok(
        ApiResponse.success(balanceQueryService.getByBatchAndLocation(batchId, locationId)));
  }
}
