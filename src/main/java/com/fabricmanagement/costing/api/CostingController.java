package com.fabricmanagement.costing.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.app.PriceListService;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.price.PriceList;
import com.fabricmanagement.costing.dto.*;
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
 * REST API for the Costing module.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/costing/calculations/estimated — Compute ESTIMATED cost for a Quote
 *   <li>POST /api/costing/calculations/planned — Compute PLANNED cost for a WorkOrder
 *   <li>POST /api/costing/calculations/actual — Compute ACTUAL cost for a Batch
 *   <li>POST /api/costing/price-lists — Create price list
 *   <li>GET /api/costing/price-lists — List price lists for a module
 *   <li>DELETE /api/costing/price-lists/{priceListId} — Deactivate price list
 * </ul>
 */
@RestController
@RequestMapping("/api/costing")
@RequiredArgsConstructor
@Slf4j
public class CostingController {

  private final CostCalculationService costCalculationService;
  private final PriceListService priceListService;

  // ============================================================
  // COST CALCULATIONS
  // ============================================================

  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @PostMapping("/calculations/estimated")
  public ResponseEntity<ApiResponse<CostCalculationResponse>> computeEstimated(
      @Valid @RequestBody ComputeEstimatedCostRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    CostCalculation calc =
        costCalculationService.computeEstimated(
            tenantId,
            req.quoteId(),
            req.moduleType(),
            req.productId(),
            req.totalQuantityKg(),
            req.tradingPartnerId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CostCalculationResponse.from(calc)));
  }

  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @PostMapping("/calculations/planned")
  public ResponseEntity<ApiResponse<CostCalculationResponse>> computePlanned(
      @Valid @RequestBody ComputePlannedCostRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    CostCalculation calc =
        costCalculationService.computePlanned(
            tenantId,
            req.workOrderId(),
            req.moduleType(),
            req.productId(),
            req.plannedQuantityKg(),
            req.supplierId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CostCalculationResponse.from(calc)));
  }

  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @PostMapping("/calculations/actual")
  public ResponseEntity<ApiResponse<CostCalculationResponse>> computeActual(
      @Valid @RequestBody ComputeActualCostRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    CostCalculation calc =
        costCalculationService.computeActual(
            tenantId,
            req.batchId(),
            req.moduleType(),
            req.productId(),
            req.actualQuantityKg(),
            req.supplierId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(CostCalculationResponse.from(calc)));
  }

  /**
   * Full cost report for a WorkOrder: PLANNED vs ACTUAL with per-product breakdown.
   *
   * <p>Either stage may be absent if not yet calculated. Frontend should handle null sections.
   *
   * @param workOrderId the WorkOrder UUID
   * @return WorkOrderCostReportResponse with variance analysis and raw product breakdown
   */
  @PreAuthorize("@auth.can(authentication, 'costing', 'read')")
  @GetMapping("/calculations/work-orders/{workOrderId}")
  public ResponseEntity<ApiResponse<WorkOrderCostReportResponse>> getWorkOrderCostReport(
      @PathVariable UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    return ResponseEntity.ok(
        ApiResponse.success(costCalculationService.getWorkOrderCostReport(tenantId, workOrderId)));
  }

  // ============================================================
  // PRICE LISTS
  // ============================================================

  @PreAuthorize("@auth.can(authentication, 'costing', 'manage')")
  @PostMapping("/price-lists")
  public ResponseEntity<ApiResponse<PriceListResponse>> createPriceList(
      @Valid @RequestBody CreatePriceListRequest req) {
    UUID tenantId = TenantContext.requireTenantId();
    PriceList pl =
        priceListService.createPriceList(
            tenantId,
            req.name(),
            req.moduleType(),
            req.currency(),
            req.validFrom(),
            req.validUntil(),
            req.seasonTag());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(PriceListResponse.from(pl)));
  }

  @PreAuthorize("@auth.can(authentication, 'costing', 'read')")
  @GetMapping("/price-lists")
  public ResponseEntity<ApiResponse<List<PriceListResponse>>> listPriceLists(
      @RequestParam String moduleType) {
    UUID tenantId = TenantContext.requireTenantId();
    List<PriceListResponse> list =
        priceListService.listPriceLists(tenantId, moduleType).stream()
            .map(PriceListResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(list));
  }

  @PreAuthorize("@auth.can(authentication, 'costing', 'manage')")
  @DeleteMapping("/price-lists/{priceListId}")
  public ResponseEntity<ApiResponse<Void>> deactivatePriceList(@PathVariable UUID priceListId) {
    priceListService.deactivatePriceList(priceListId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
