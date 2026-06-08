package com.fabricmanagement.production.execution.inventory.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.inventory.app.query.InventoryBalanceQueryService;
import com.fabricmanagement.production.execution.inventory.dto.InventoryBalanceDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** REST API for inventory balances (read-optimized projections). */
@RestController
@RequestMapping("/api/v1/production/inventory/balances")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory Balance", description = "Inventory Balance operations")
public class InventoryBalanceController {

  private final InventoryBalanceQueryService balanceQueryService;

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InventoryBalanceDto>>> getByBatch(
      @PathVariable UUID batchId, @Valid PageRequestDto pageRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                balanceQueryService.getByBatchId(batchId, pageRequest.toPageable()))));
  }

  @GetMapping("/location/{locationId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InventoryBalanceDto>>> getByLocation(
      @PathVariable UUID locationId, @Valid PageRequestDto pageRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                balanceQueryService.getByLocationId(locationId, pageRequest.toPageable()))));
  }

  @GetMapping("/batch/{batchId}/location/{locationId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<InventoryBalanceDto>> getByBatchAndLocation(
      @PathVariable UUID batchId, @PathVariable UUID locationId) {
    return ResponseEntity.ok(
        ApiResponse.success(balanceQueryService.getByBatchAndLocation(batchId, locationId)));
  }
}
