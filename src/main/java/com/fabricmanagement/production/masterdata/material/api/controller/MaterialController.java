package com.fabricmanagement.production.masterdata.material.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.material.app.MaterialService;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Material Controller - REST endpoints for material management.
 */
@RestController
@RequestMapping("/api/production/materials")
@RequiredArgsConstructor
@Slf4j
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping
    public ResponseEntity<ApiResponse<MaterialDto>> createMaterial(
            @Valid @RequestBody CreateMaterialRequest request) {
        
        log.info("Creating material: name={}", request.getMaterialName());

        MaterialDto created = materialService.createMaterial(request);

        return ResponseEntity.ok(ApiResponse.success(created, "Material created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialDto>> getMaterial(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        MaterialDto material = materialService.findById(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        return ResponseEntity.ok(ApiResponse.success(material));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialDto>>> getAllMaterials() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        List<MaterialDto> materials = materialService.findByTenant(tenantId);

        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<MaterialDto>>> getMaterialsByType(@PathVariable MaterialType type) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        List<MaterialDto> materials = materialService.findByType(tenantId, type);

        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateMaterial(@PathVariable UUID id) {
        log.info("Deactivating material: id={}", id);

        materialService.deactivateMaterial(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Material deactivated successfully"));
    }
}

