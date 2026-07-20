package com.fabricmanagement.production.execution.batch.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.batch.app.StockAvailabilityQueryService;
import com.fabricmanagement.production.execution.batch.dto.StockAvailabilityDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/stock/availability")
@RequiredArgsConstructor
@Tag(
    name = "Stock Availability",
    description =
        "Canonical FABRIC, YARN, and FIBER stock availability by product, colour, lot, and quality")
public class StockAvailabilityController {

  private final StockAvailabilityQueryService queryService;

  @GetMapping("/summary")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(
      operationId = "listStockAvailabilitySummary",
      summary = "List product-level stock availability summaries",
      description = "Returns whole-filter-set totals, paged deterministically over products.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Product availability summary page")
  public ResponseEntity<ApiResponse<PagedResponse<StockAvailabilityDtos.Summary>>> summary(
      @Parameter(description = "Batch colour-card identifier") @RequestParam(required = false)
          UUID colorId,
      @Parameter(description = "Filter explicitly colourless batches")
          @RequestParam(required = false)
          Boolean colourless,
      @Parameter(description = "Product identifier") @RequestParam(required = false) UUID productId,
      @Parameter(description = "Batch/lot identifier") @RequestParam(required = false) UUID batchId,
      @Parameter(description = "Piece-level quality-grade identifier")
          @RequestParam(required = false)
          UUID qualityGradeId,
      @Parameter(description = "Filter pieces whose quality grade is not assigned")
          @RequestParam(required = false)
          Boolean qualityUnassigned,
      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                queryService.summary(
                    colorId,
                    colourless,
                    productId,
                    batchId,
                    qualityGradeId,
                    qualityUnassigned,
                    pageable))));
  }

  @GetMapping("/lots")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(
      operationId = "listStockAvailabilityLots",
      summary = "List lot-level stock availability",
      description = "Returns deterministic lot pages with package and quality breakdowns.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Lot availability page")
  public ResponseEntity<ApiResponse<PagedResponse<StockAvailabilityDtos.Lot>>> lots(
      @Parameter(description = "Batch colour-card identifier") @RequestParam(required = false)
          UUID colorId,
      @Parameter(description = "Filter explicitly colourless batches")
          @RequestParam(required = false)
          Boolean colourless,
      @Parameter(description = "Product identifier") @RequestParam(required = false) UUID productId,
      @Parameter(description = "Batch/lot identifier") @RequestParam(required = false) UUID batchId,
      @Parameter(description = "Piece-level quality-grade identifier")
          @RequestParam(required = false)
          UUID qualityGradeId,
      @Parameter(description = "Filter pieces whose quality grade is not assigned")
          @RequestParam(required = false)
          Boolean qualityUnassigned,
      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(
                queryService.lots(
                    colorId,
                    colourless,
                    productId,
                    batchId,
                    qualityGradeId,
                    qualityUnassigned,
                    pageable))));
  }
}
