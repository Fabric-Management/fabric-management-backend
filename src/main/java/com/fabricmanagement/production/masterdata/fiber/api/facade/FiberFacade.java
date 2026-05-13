package com.fabricmanagement.production.masterdata.fiber.api.facade;

import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
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
   * Find fiber by product ID.
   *
   * @param productId Product ID
   * @return Fiber DTO if found
   */
  Optional<FiberDto> findByProductId(UUID productId);

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

  /**
   * Search fibers by name across tenant and system fibers. Includes both tenant-specific and global
   * system fibers.
   *
   * @param query Case-insensitive name fragment to search for
   * @return Matching active fibers ordered by name
   */
  List<FiberDto> findByNameContaining(String query);

  /**
   * List all active fiber categories. Used for AI-assisted fiber creation and classification.
   *
   * @return Active categories ordered by display order
   */
  List<FiberCategoryDto> listActiveCategories();

  /**
   * Find fibers by multiple product IDs (batch lookup). Includes both tenant-specific and global
   * system fibers. Primarily used for cross-reference searches in the product module.
   *
   * @param productIds Collection of product IDs to search
   * @return Matching fibers as DTOs
   */
  List<FiberDto> findByProductIds(java.util.Collection<java.util.UUID> productIds);
}
