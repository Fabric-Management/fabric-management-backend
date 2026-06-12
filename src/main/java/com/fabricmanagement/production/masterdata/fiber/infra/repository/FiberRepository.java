package com.fabricmanagement.production.masterdata.fiber.infra.repository;

import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for Fiber entity. */
public interface FiberRepository extends JpaRepository<Fiber, UUID> {

  /** Find fiber by tenant and ID. */
  Optional<Fiber> findByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Find fiber by product ID.
   *
   * <p>Uses product.id relationship since productId field was replaced with @ManyToOne Product.
   */
  @Query("SELECT f FROM Fiber f WHERE f.product.id = :productId")
  Optional<Fiber> findByProductId(@Param("productId") UUID productId);

  /**
   * Find fibers by multiple product IDs (batch query for performance).
   *
   * <p>Used to optimize Product search when checking Fiber fiberName for multiple products.
   */
  @Query("SELECT f FROM Fiber f WHERE f.product.id IN :productIds")
  List<Fiber> findByProductIdIn(@Param("productIds") List<UUID> productIds);

  /** Find all active fibers for a tenant, ordered by name. */
  @Query(
      "SELECT f FROM Fiber f WHERE f.tenantId = :tenantId AND f.isActive = true ORDER BY f.fiberName")
  List<Fiber> findByTenantIdAndIsActiveTrueOrderByFiberName(@Param("tenantId") UUID tenantId);

  /** Find all active fibers for a tenant (unordered). */
  List<Fiber> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /**
   * Find all active fibers for the given tenants (e.g. current tenant + system tenant for platform
   * seed). Used so tenant organizations can use platform organization's fiber catalog.
   */
  @Query(
      "SELECT f FROM Fiber f WHERE f.tenantId IN :tenantIds AND f.isActive = true ORDER BY f.fiberName")
  List<Fiber> findByTenantIdInAndIsActiveTrueOrderByFiberName(
      @Param("tenantIds") List<UUID> tenantIds);

  /** Find fibers by name (case-insensitive). */
  List<Fiber> findByTenantIdAndFiberNameContainingIgnoreCase(UUID tenantId, String fiberName);

  /** Find fibers by name (case-insensitive), single tenant. */
  List<Fiber> findByTenantIdAndIsActiveTrueAndFiberNameContainingIgnoreCaseOrderByFiberName(
      UUID tenantId, String fiberName);

  /**
   * Find active fibers by name across multiple tenants (e.g. current tenant + template tenant).
   * Used so tenant organizations see both their own and platform seed fibers in search results.
   */
  List<Fiber> findByTenantIdInAndIsActiveTrueAndFiberNameContainingIgnoreCaseOrderByFiberName(
      List<UUID> tenantIds, String fiberName);

  /**
   * Find fiber by ID across multiple tenants. Returns the fiber if it belongs to any of the given
   * tenants. Used so template fibers listed in the catalog can also be opened for detail views.
   */
  Optional<Fiber> findByTenantIdInAndId(List<UUID> tenantIds, UUID id);

  /** Find fibers by status. */
  List<Fiber> findByTenantIdAndStatusAndIsActiveTrue(UUID tenantId, FiberStatus status);

  /** Find active fibers with product details. */
  @Query(
      "SELECT f FROM Fiber f "
          + "WHERE f.tenantId = :tenantId AND f.isActive = true "
          + "ORDER BY f.fiberName")
  List<Fiber> findActiveFibersWithDetails(@Param("tenantId") UUID tenantId);

  /**
   * ✅ Performance: Find fibers by query (filtered search).
   *
   * <p>Used for AI searches to avoid loading all fibers. Searches in fiberName (case-insensitive
   * LIKE).
   *
   * <p>Note: Uses existing findByTenantIdAndFiberNameContainingIgnoreCase method which already
   * filters by query. Limit is applied in Java code.
   */
  // findByTenantIdAndFiberNameContainingIgnoreCase already exists and does filtered search
}
