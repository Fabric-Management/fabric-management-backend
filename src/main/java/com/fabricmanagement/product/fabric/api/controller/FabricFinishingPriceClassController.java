package com.fabricmanagement.product.fabric.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.fabric.app.FabricFinishingPriceClassService;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingPriceClassRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceClassDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricFinishingPriceClassRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tiers a subcontractor prices a dyeing by (FABRIC-3c). They are part of the price list, so
 * they follow the costing permissions.
 */
@RestController
@RequestMapping("/api/v1/production/fabrics/finishing-price-classes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Fabric Articles", description = "Fabric master data and construction dictionary")
public class FabricFinishingPriceClassController {

  private final FabricFinishingPriceClassService classes;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'costing', 'read')")
  @Operation(operationId = "listFabricFinishingPriceClasses", summary = "The price classes")
  public ResponseEntity<ApiResponse<List<FabricFinishingPriceClassDto>>> list(
      @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(ApiResponse.success(classes.list(includeInactive)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @Operation(operationId = "createFabricFinishingPriceClass", summary = "Add a price class")
  public ResponseEntity<ApiResponse<FabricFinishingPriceClassDto>> create(
      @Valid @RequestBody CreateFabricFinishingPriceClassRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(classes.create(request)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'costing', 'write')")
  @Operation(
      operationId = "updateFabricFinishingPriceClass",
      summary = "Rename, reorder or retire a price class")
  public ResponseEntity<ApiResponse<FabricFinishingPriceClassDto>> update(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateFabricFinishingPriceClassRequest request) {
    return ResponseEntity.ok(ApiResponse.success(classes.update(id, request)));
  }
}
