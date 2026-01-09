package com.fabricmanagement.production.masterdata.material.api.facade;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Material Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should interact with Material through this facade.
 *
 * <p>This is IN-PROCESS communication (no HTTP overhead).
 */
public interface MaterialFacade {

  /**
   * Find material by ID.
   *
   * @param tenantId Tenant ID
   * @param id Material ID
   * @return Material DTO if found
   */
  Optional<MaterialDto> findById(UUID tenantId, UUID id);

  /**
   * Find all materials for tenant.
   *
   * @param tenantId Tenant ID
   * @return List of materials
   */
  List<MaterialDto> findByTenant(UUID tenantId);

  /**
   * Find materials by type.
   *
   * @param tenantId Tenant ID
   * @param type Material type
   * @return List of materials
   */
  List<MaterialDto> findByType(UUID tenantId, MaterialType type);

  /**
   * Check if material exists.
   *
   * @param tenantId Tenant ID
   * @param id Material ID
   * @return true if exists
   */
  boolean exists(UUID tenantId, UUID id);

  /**
   * Create a new material.
   *
   * @param request Create material request
   * @return Created material DTO
   */
  MaterialDto createMaterial(CreateMaterialRequest request);
}
