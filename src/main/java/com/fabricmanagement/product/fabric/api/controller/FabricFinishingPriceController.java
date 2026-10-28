package com.fabricmanagement.product.fabric.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.fabric.app.FabricFinishingPriceService;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The finishing price list (FABRIC-3c): what a process costs per metre or kilogram, in general or
 * at one firm, from a date. Tariffs are commercial terms, so they follow the costing permissions.
 */
@RestController
@RequestMapping("/api/v1/production/fabrics/finishing-prices")
@RequiredArgsConstructor
@Validated
@Tag(name = "Fabric Articles", description = "Fabric master data and construction dictionary")
public class FabricFinishingPriceController {

  private final FabricFinishingPriceService prices;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'costing', 'read')")
  @Operation(
      operationId = "listFabricFinishingPrices",
      summary = "The finishing price list, optionally of one process or one firm")
  public ResponseEntity<ApiResponse<List<FabricFinishingPriceDto>>> list(
      @RequestParam(name = "processId", required = false) UUID processId,
      @RequestParam(name = "partnerId", required = false) UUID partnerId) {
    return ResponseEntity.ok(ApiResponse.success(prices.list(processId, partnerId)));
  }

  @GetMapping("/in-force")
  @PreAuthorize("@auth.can(authentication, 'costing', 'read')")
  @Operation(
      operationId = "findFabricFinishingPriceInForce",
      summary = "The price a process has at a firm on a date",
      description =
          "The firm's own latest tariff that has started, else the latest general price that has"
              + " started. The data is empty when nothing is priced. Leave the firm out for work"
              + " at an own site.")
  public ResponseEntity<ApiResponse<FabricFinishingPriceDto>> inForce(
      @RequestParam(name = "processId") UUID processId,
      @RequestParam(name = "partnerId", required = false) UUID partnerId,
      @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(
        ApiResponse.success(prices.inForce(processId, partnerId, date).orElse(null)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @Operation(
      operationId = "createFabricFinishingPrice",
      summary = "Add a price from a date; a change of price is a new price from a later date")
  public ResponseEntity<ApiResponse<FabricFinishingPriceDto>> create(
      @Valid @RequestBody CreateFabricFinishingPriceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(prices.create(request)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @Operation(
      operationId = "removeFabricFinishingPrice",
      summary = "Withdraw a price that was entered by mistake")
  public ResponseEntity<Void> remove(@PathVariable("id") UUID id) {
    prices.remove(id);
    return ResponseEntity.noContent().build();
  }
}
