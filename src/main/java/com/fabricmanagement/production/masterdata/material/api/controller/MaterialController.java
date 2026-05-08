package com.fabricmanagement.production.masterdata.material.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.material.app.MaterialService;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialAttributeDto;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Material Controller - REST endpoints for material management.
 *
 * <p>Security uses department-aware checks via {@code PermissionEvaluator}. WRITE = create /
 * deactivate (ADMIN, or MANAGER in R&D / Prod. Planning / Fiber dept). READ = any authenticated
 * user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/materials")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Material", description = "Material Management API")
public class MaterialController {

  private final MaterialService materialService;

  @Operation(summary = "Create Material")
  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(
      @Valid @RequestBody CreateMaterialRequest request) {

    log.info("Creating material: type={}", request.getMaterialType());

    MaterialDto created = materialService.createMaterial(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Material created successfully"));
  }

  @Operation(summary = "Get Material by ID")
  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<MaterialDto>> getMaterial(
      @Parameter(description = "Material ID") @PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    MaterialDto material =
        materialService
            .findById(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Material not found: " + id));

    return ResponseEntity.ok(ApiResponse.success(material));
  }

  @Operation(summary = "Get All Materials")
  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<MaterialDto>>> getAllMaterials() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<MaterialDto> materials = materialService.findByTenant(tenantId);

    return ResponseEntity.ok(ApiResponse.success(materials));
  }

  @Operation(summary = "Get Materials by Type")
  @GetMapping("/type/{type}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<MaterialDto>>> getMaterialsByType(
      @Parameter(description = "Material Type") @PathVariable MaterialType type) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<MaterialDto> materials = materialService.findByType(tenantId, type);

    return ResponseEntity.ok(ApiResponse.success(materials));
  }

  @Operation(summary = "Get Material Attributes")
  @GetMapping("/attributes")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<MaterialAttributeDto>>> getAttributes(
      @Parameter(description = "Material Scope (e.g., FIBER, YARN, FABRIC)")
          @RequestParam(required = false)
          String scope) {
    List<MaterialAttributeDto> attributes = materialService.getAttributes(scope);
    return ResponseEntity.ok(ApiResponse.success(attributes));
  }

  @Operation(summary = "Deactivate Material")
  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<Void>> deactivateMaterial(
      @Parameter(description = "Material ID") @PathVariable UUID id) {
    log.info("Deactivating material: id={}", id);

    materialService.deactivateMaterial(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Material deactivated successfully"));
  }
}
