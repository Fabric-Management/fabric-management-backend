package com.fabricmanagement.costing.api;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.api.dto.*;
import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.app.PriceListService;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.currency.ExchangeRateSnapshot;
import com.fabricmanagement.costing.domain.price.PriceList;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *   <li>POST /api/costing/exchange-rates — Capture exchange rate snapshot
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

  // TODO: @PreAuthorize("hasRole('COSTING_WRITE') or hasRole('SALES_MANAGER')")
  // Authorization will be implemented in Phase 12 (Security & RBAC module)
  @PostMapping("/calculations/estimated")
  @ResponseStatus(HttpStatus.CREATED)
  public CostCalculationResponse computeEstimated(
      @Valid @RequestBody ComputeEstimatedCostRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    CostCalculation calc =
        costCalculationService.computeEstimated(
            tenantId,
            req.quoteId(),
            req.moduleType(),
            req.materialId(),
            req.totalQuantityKg(),
            req.tradingPartnerId());
    return CostCalculationResponse.from(calc);
  }

  // TODO: @PreAuthorize("hasRole('COSTING_WRITE') or hasRole('PRODUCTION_MANAGER')")
  @PostMapping("/calculations/planned")
  @ResponseStatus(HttpStatus.CREATED)
  public CostCalculationResponse computePlanned(@Valid @RequestBody ComputePlannedCostRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    CostCalculation calc =
        costCalculationService.computePlanned(
            tenantId,
            req.workOrderId(),
            req.moduleType(),
            req.materialId(),
            req.plannedQuantityKg(),
            req.supplierId());
    return CostCalculationResponse.from(calc);
  }

  // TODO: @PreAuthorize("hasRole('COSTING_WRITE') or hasRole('PRODUCTION_MANAGER')")
  @PostMapping("/calculations/actual")
  @ResponseStatus(HttpStatus.CREATED)
  public CostCalculationResponse computeActual(@Valid @RequestBody ComputeActualCostRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    CostCalculation calc =
        costCalculationService.computeActual(
            tenantId,
            req.batchId(),
            req.moduleType(),
            req.materialId(),
            req.actualQuantityKg(),
            req.supplierId());
    return CostCalculationResponse.from(calc);
  }

  // ============================================================
  // PRICE LISTS
  // ============================================================

  // TODO: @PreAuthorize("hasRole('COSTING_ADMIN')")
  @PostMapping("/price-lists")
  @ResponseStatus(HttpStatus.CREATED)
  public PriceListResponse createPriceList(@Valid @RequestBody CreatePriceListRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    PriceList pl =
        priceListService.createPriceList(
            tenantId,
            req.name(),
            req.moduleType(),
            req.currency(),
            req.validFrom(),
            req.validUntil(),
            req.seasonTag());
    return PriceListResponse.from(pl);
  }

  // TODO: @PreAuthorize("hasRole('COSTING_READ')")
  @GetMapping("/price-lists")
  public List<PriceListResponse> listPriceLists(@RequestParam String moduleType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return priceListService.listPriceLists(tenantId, moduleType).stream()
        .map(PriceListResponse::from)
        .toList();
  }

  // TODO: @PreAuthorize("hasRole('COSTING_ADMIN')")
  @DeleteMapping("/price-lists/{priceListId}")
  public ResponseEntity<Void> deactivatePriceList(@PathVariable UUID priceListId) {
    priceListService.deactivatePriceList(priceListId);
    return ResponseEntity.noContent().build();
  }

  // ============================================================
  // EXCHANGE RATES
  // ============================================================

  // TODO: @PreAuthorize("hasRole('FINANCE_MANAGER')")
  @PostMapping("/exchange-rates")
  @ResponseStatus(HttpStatus.CREATED)
  public ExchangeRateResponse captureExchangeRate(
      @Valid @RequestBody CaptureExchangeRateRequest req) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    ExchangeRateSnapshot snap =
        priceListService.captureExchangeRate(
            tenantId, req.baseCurrency(), req.targetCurrency(), req.rate(), req.source());
    return ExchangeRateResponse.from(snap);
  }
}
