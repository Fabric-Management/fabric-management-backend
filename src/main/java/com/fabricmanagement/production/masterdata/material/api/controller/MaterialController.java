package com.fabricmanagement.production.masterdata.material.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.material.app.MaterialService;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
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
public class MaterialController {

  private final MaterialService materialService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(
      @Valid @RequestBody CreateMaterialRequest request) {

    log.info("Creating material: type={}", request.getMaterialType());

    MaterialDto created = materialService.createMaterial(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Material created successfully"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<MaterialDto>> getMaterial(@PathVariable UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    MaterialDto material =
        materialService
            .findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Material not found"));

    return ResponseEntity.ok(ApiResponse.success(material));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<MaterialDto>>> getAllMaterials() {
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<MaterialDto> materials = materialService.findByTenant(tenantId);

    return ResponseEntity.ok(ApiResponse.success(materials));
  }

  @GetMapping("/type/{type}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'read')")
  public ResponseEntity<ApiResponse<List<MaterialDto>>> getMaterialsByType(
      @PathVariable MaterialType type) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    List<MaterialDto> materials = materialService.findByType(tenantId, type);

    return ResponseEntity.ok(ApiResponse.success(materials));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public ResponseEntity<ApiResponse<Void>> deactivateMaterial(@PathVariable UUID id) {
    log.info("Deactivating material: id={}", id);

    materialService.deactivateMaterial(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Material deactivated successfully"));
  }
}
