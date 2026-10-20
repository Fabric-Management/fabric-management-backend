package com.fabricmanagement.product.fabric.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.fabric.app.FabricFinishingCatalogService;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.dto.CreateFabricFinishingProcessRequest;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.product.fabric.dto.UpdateFabricFinishingProcessRequest;
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

/** The finishing process dictionary (FABRIC-3a): what can be done to a fabric lot. */
@RestController
@RequestMapping("/api/v1/production/fabrics/finishing-processes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Fabric Articles", description = "Fabric master data and construction dictionary")
public class FabricFinishingProcessController {

  private final FabricFinishingCatalogService processes;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'fabric', 'read')")
  @Operation(operationId = "listFabricFinishingProcesses", summary = "The finishing dictionary")
  public ResponseEntity<ApiResponse<List<FabricFinishingProcessDto>>> list(
      @RequestParam(name = "category", required = false) FabricFinishingCategory category,
      @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
    return ResponseEntity.ok(ApiResponse.success(processes.list(category, includeInactive)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(
      operationId = "createFabricFinishingProcess",
      summary = "Add a tenant finishing process")
  public ResponseEntity<ApiResponse<FabricFinishingProcessDto>> create(
      @Valid @RequestBody CreateFabricFinishingProcessRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(processes.create(request)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fabric', 'write')")
  @Operation(
      operationId = "updateFabricFinishingProcess",
      summary = "Rename, re-unit, reorder or retire a finishing process")
  public ResponseEntity<ApiResponse<FabricFinishingProcessDto>> update(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateFabricFinishingProcessRequest request) {
    return ResponseEntity.ok(ApiResponse.success(processes.update(id, request)));
  }
}
