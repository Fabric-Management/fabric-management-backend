package com.fabricmanagement.production.masterdata.fiber.api.facade;

import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fiber Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should interact with Fiber through this facade.
 *
 * <p>This is IN-PROCESS communication (no HTTP overhead).
 */
public interface FiberFacade {

  /**
   * Find fiber by ID.
   *
   * @param id Fiber ID
   * @return Fiber DTO if found
   */
  Optional<FiberDto> findById(UUID id);

  /**
   * Find fiber by material ID.
   *
   * @param materialId Material ID
   * @return Fiber DTO if found
   */
  Optional<FiberDto> findByMaterialId(UUID materialId);

  /**
   * Find all fibers for current tenant.
   *
   * @return List of fibers
   */
  List<FiberDto> findAll();

  /**
   * Check if fiber exists.
   *
   * @param id Fiber ID
   * @return true if exists
   */
  boolean exists(UUID id);

  /**
   * Create a new fiber.
   *
   * @param request Create fiber request
   * @return Created fiber DTO
   */
  FiberDto createFiber(CreateFiberRequest request);
}
