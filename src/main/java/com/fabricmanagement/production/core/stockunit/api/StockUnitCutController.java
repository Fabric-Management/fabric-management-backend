package com.fabricmanagement.production.core.stockunit.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.stockunit.app.StockUnitCutService;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cuts taken from pieces and verification of the remaining length (SOI A06). */
@RestController
@RequestMapping("/api/v1/production/stock-units/{stockUnitId}/cuts")
@RequiredArgsConstructor
@Tag(name = "StockUnit", description = "Physical stock unit lifecycle management")
public class StockUnitCutController {

  private final StockUnitCutService cutService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(operationId = "recordStockUnitCut", summary = "Record a cut taken from a piece")
  public ResponseEntity<ApiResponse<StockUnitCutDtos.CutDto>> recordCut(
      @PathVariable UUID stockUnitId,
      @Valid @RequestBody StockUnitCutDtos.RecordCutRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(cutService.recordCut(stockUnitId, request)));
  }

  @PostMapping("/{cutId}/verify-remaining")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "verifyStockUnitCutRemaining",
      summary = "Verify the remaining length after a cut")
  public ResponseEntity<ApiResponse<StockUnitCutDtos.CutDto>> verifyRemaining(
      @PathVariable UUID stockUnitId, @PathVariable UUID cutId) {
    return ResponseEntity.ok(ApiResponse.success(cutService.verifyRemaining(stockUnitId, cutId)));
  }
}
