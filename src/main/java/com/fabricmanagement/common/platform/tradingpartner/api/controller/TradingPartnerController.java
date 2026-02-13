package com.fabricmanagement.common.platform.tradingpartner.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.common.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST controller for trading partner management.
 *
 * <p>Provides endpoints for:
 *
 * <ul>
 *   <li>Creating trading partners (with automatic registry deduplication)
 *   <li>Listing partners by type (suppliers, customers, fason)
 *   <li>Searching partners
 *   <li>Managing partner lifecycle (suspend, block, reactivate)
 * </ul>
 *
 * <h2>Dual-Read Support:</h2>
 *
 * <p>GET endpoints support both new TradingPartner IDs and legacy Company IDs during the transition
 * period.
 */
@RestController
@RequestMapping("/api/common/trading-partners")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Trading Partners",
    description = "B2B partner management - suppliers, customers, fason partners")
public class TradingPartnerController {

  private final TradingPartnerService tradingPartnerService;

  // ═══════════════════════════════════════════════════════════════════════════
  // CREATE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Create trading partner",
      description =
          "Creates a new trading partner relationship. "
              + "Automatically deduplicates via tax_id + country. "
              + "If same partner already exists with different type, upgrades to BOTH.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Partner created successfully"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request")
  })
  public ResponseEntity<ApiResponse<TradingPartnerDto>> createPartner(
      @Valid @RequestBody CreateTradingPartnerRequest request) {
    log.info(
        "Creating trading partner: name={}, type={}",
        request.getCompanyName(),
        request.getPartnerType());

    TradingPartnerDto created = tradingPartnerService.createPartner(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Trading partner created successfully"));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // READ
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping
  @Operation(
      summary = "List all trading partners",
      description = "Lists all active trading partners for current tenant")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> listPartners() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Listing trading partners for tenant: {}", tenantId);

    List<TradingPartnerDto> partners = tradingPartnerService.findAll(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(partners, "Found " + partners.size() + " trading partners"));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Get trading partner",
      description = "Gets a trading partner by ID. Supports both new ID and legacy Company ID.")
  public ResponseEntity<ApiResponse<TradingPartnerDto>> getPartner(
      @Parameter(description = "Partner ID (or legacy Company ID)") @PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting trading partner: id={}, tenant={}", id, tenantId);

    TradingPartnerDto partner =
        tradingPartnerService
            .findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Trading partner not found: " + id));

    return ResponseEntity.ok(ApiResponse.success(partner));
  }

  @GetMapping("/suppliers")
  @Operation(summary = "List suppliers", description = "Lists partners with type SUPPLIER or BOTH")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> listSuppliers() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Listing suppliers for tenant: {}", tenantId);

    List<TradingPartnerDto> suppliers = tradingPartnerService.findSuppliers(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(suppliers, "Found " + suppliers.size() + " suppliers"));
  }

  @GetMapping("/customers")
  @Operation(summary = "List customers", description = "Lists partners with type CUSTOMER or BOTH")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> listCustomers() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Listing customers for tenant: {}", tenantId);

    List<TradingPartnerDto> customers = tradingPartnerService.findCustomers(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(customers, "Found " + customers.size() + " customers"));
  }

  @GetMapping("/fason")
  @Operation(summary = "List fason partners", description = "Lists subcontractor (fason) partners")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> listFasonPartners() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Listing fason partners for tenant: {}", tenantId);

    List<TradingPartnerDto> fasonPartners = tradingPartnerService.findFasonPartners(tenantId);

    return ResponseEntity.ok(
        ApiResponse.success(fasonPartners, "Found " + fasonPartners.size() + " fason partners"));
  }

  @GetMapping("/type/{type}")
  @Operation(
      summary = "List by type",
      description = "Lists partners by specific type. For SUPPLIER/CUSTOMER, also includes BOTH.")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> listByType(
      @Parameter(description = "Partner type") @PathVariable PartnerType type) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Listing partners by type: type={}, tenant={}", type, tenantId);

    List<TradingPartnerDto> partners = tradingPartnerService.findByType(tenantId, type);

    return ResponseEntity.ok(
        ApiResponse.success(partners, "Found " + partners.size() + " " + type + " partners"));
  }

  @GetMapping("/search")
  @Operation(
      summary = "Search partners",
      description = "Searches partners by name (custom name or official name)")
  public ResponseEntity<ApiResponse<List<TradingPartnerDto>>> searchPartners(
      @Parameter(description = "Search term") @RequestParam String q) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Searching partners: term={}, tenant={}", q, tenantId);

    List<TradingPartnerDto> results = tradingPartnerService.searchByName(tenantId, q);

    return ResponseEntity.ok(
        ApiResponse.success(results, "Found " + results.size() + " matching partners"));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/suspend")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Suspend partner",
      description = "Suspends a partner relationship. No new transactions allowed.")
  public ResponseEntity<ApiResponse<Void>> suspendPartner(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Suspending partner: id={}, tenant={}", id, tenantId);

    tradingPartnerService.suspend(tenantId, id);

    return ResponseEntity.ok(ApiResponse.success(null, "Partner suspended successfully"));
  }

  @PostMapping("/{id}/block")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Block partner",
      description = "Blocks a partner relationship. Relationship is terminated.")
  public ResponseEntity<ApiResponse<Void>> blockPartner(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Blocking partner: id={}, tenant={}", id, tenantId);

    tradingPartnerService.block(tenantId, id);

    return ResponseEntity.ok(ApiResponse.success(null, "Partner blocked successfully"));
  }

  @PostMapping("/{id}/reactivate")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRADING_PARTNER_MANAGER')")
  @Operation(
      summary = "Reactivate partner",
      description = "Reactivates a suspended or pending partner")
  public ResponseEntity<ApiResponse<Void>> reactivatePartner(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Reactivating partner: id={}, tenant={}", id, tenantId);

    tradingPartnerService.reactivate(tenantId, id);

    return ResponseEntity.ok(ApiResponse.success(null, "Partner reactivated successfully"));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Delete partner", description = "Soft-deletes a partner relationship")
  public ResponseEntity<ApiResponse<Void>> deletePartner(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Deleting partner: id={}, tenant={}", id, tenantId);

    tradingPartnerService.delete(tenantId, id);

    return ResponseEntity.ok(ApiResponse.success(null, "Partner deleted successfully"));
  }
}
